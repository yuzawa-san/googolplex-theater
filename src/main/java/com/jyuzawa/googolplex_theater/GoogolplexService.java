/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import java.io.Closeable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.util.NamedThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * This class represents the state of the application. All modifications to state occur in the same
 * thread.
 *
 * @author jyuzawa
 */
@Slf4j
@Component
public class GoogolplexService implements Closeable {
    private static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";
    private static final List<String> DIAGNOSTIC_PROPERTIES = Collections.unmodifiableList(
            Arrays.asList("os.name", "os.version", "os.arch", "java.vendor", "java.version"));

    static {
        log.info("Website: " + PROJECT_WEBSITE);
        for (String property : DIAGNOSTIC_PROPERTIES) {
            log.info("Runtime[{}]: {}", property, System.getProperty(property));
        }
    }

    private final GoogolplexClient client;
    private final Map<String, DeviceInfo> nameToDeviceInfo;
    private final Map<String, InetSocketAddress> nameToAddress;
    private final Map<String, Channel> nameToChannel;
    private final ExecutorService executor;

    @Autowired
    public GoogolplexService(GoogolplexClient client) {
        this.client = client;
        // the state is maintained in these maps
        this.nameToDeviceInfo = new ConcurrentHashMap<>();
        this.nameToAddress = new ConcurrentHashMap<>();
        this.nameToChannel = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("controller"));
    }

    private record Channel(AtomicReference<Instant> birth, Disposable disposable) {}

    /**
     * Load the config and propagate the changes to the any currently connected devices.
     *
     * @param config the settings loaded from the file
     */
    public Future<?> processDeviceConfig(DeviceConfig config) {
        return executor.submit(() -> {
            Set<String> namesToRemove = new HashSet<>(nameToDeviceInfo.keySet());
            for (DeviceInfo deviceInfo : config.getDevices()) {
                String name = deviceInfo.getName();
                // mark that we should not remove this device
                namesToRemove.remove(name);
                DeviceInfo oldDeviceInfo = nameToDeviceInfo.get(name);
                // ignore unchanged devices
                if (!deviceInfo.equals(oldDeviceInfo)) {
                    log.info("CONFIG_UPDATED '{}'", name);
                    nameToDeviceInfo.put(name, deviceInfo);
                    apply(name);
                }
            }
            // remove devices that were missing in the new config
            for (String name : namesToRemove) {
                log.info("CONFIG_REMOVED '{}'", name);
                nameToDeviceInfo.remove(name);
                apply(name);
            }
        });
    }

    /**
     * Add a discovered device and initialize a new connection to the device if one does not exist
     * already.
     *
     * @param event mdns info
     */
    public Future<?> register(ServiceEvent event) {
        return executor.submit(() -> {
            // the device information may not be full
            ServiceInfo info = event.getInfo();
            String name = info.getPropertyString("fn");
            if (name == null) {
                log.debug("Found unnamed cast:\n{}", info);
                return;
            }
            InetAddress[] addresses = info.getInetAddresses();
            if (addresses == null || addresses.length == 0) {
                log.debug("Found unaddressable cast:\n{}", info);
                return;
            }
            /*
             * we choose the first address. there should usually be just one. the mdns library returns ipv4
             * addresses before ipv6.
             */
            InetSocketAddress address = new InetSocketAddress(addresses[0], info.getPort());
            InetSocketAddress oldAddress = nameToAddress.put(name, address);
            if (!address.equals(oldAddress)) {
                /*
                 * this is a newly discovered device, or an existing device whose address was updated.
                 */
                log.info("REGISTER '{}' {}", name, address);
                apply(name);
            }
        });
    }

    /**
     * Apply changes to a device. This closes any existing connections. If there were no existing
     * connections, then a new connection is made. The connection will be reliable.
     *
     * @param name device's name
     */
    private void apply(String name) {
        Channel oldChannel = nameToChannel.get(name);
        if (oldChannel != null) {
            /*
             * kill the channel. it may reconnect below.
             */
            oldChannel.disposable.dispose();
        }
        // ensure that there is enough information to connect
        InetSocketAddress address = nameToAddress.get(name);
        if (address == null) {
            return;
        }
        DeviceInfo deviceInfo = nameToDeviceInfo.get(name);
        if (deviceInfo == null) {
            return;
        }
        AtomicReference<Instant> birth = new AtomicReference<>();
        Disposable disposable = client.connect(address, deviceInfo, birth).subscribe();
        nameToChannel.put(name, new Channel(birth, disposable));
    }

    /**
     * Trigger a refresh by closing channels which will cause a reconnect.
     *
     * @param name the device to refresh
     */
    public Future<?> refresh(String name) {
        return executor.submit(() -> {
            // closing channels will cause them to reconnect
            if (name == null) {
                // close all channels
                for (String theName : nameToChannel.keySet()) {
                    apply(theName);
                }
            } else {
                // close specific channel
                apply(name);
            }
        });
    }

    public List<DeviceStatus> getDeviceInfo() {
        List<DeviceStatus> out = new ArrayList<>();
        Set<String> allNames = new TreeSet<>();
        allNames.addAll(nameToDeviceInfo.keySet());
        allNames.addAll(nameToAddress.keySet());
        Instant now = Instant.now();
        for (String name : allNames) {
            DeviceStatus.DeviceStatusBuilder device = DeviceStatus.builder();
            device.name(name);
            DeviceInfo deviceInfo = nameToDeviceInfo.get(name);
            if (deviceInfo != null) {
                device.settings(deviceInfo.getSettings());
            }
            InetSocketAddress ipAddress = nameToAddress.get(name);
            if (ipAddress != null) {
                device.ipAddress(ipAddress.getAddress().getHostAddress());
            }
            Channel channel = nameToChannel.get(name);
            if (channel != null) {
                Instant realBirth = channel.birth.get();
                device.birth(realBirth);
                if (realBirth != null) {
                    device.uptime(calculateDuration(Duration.between(realBirth, now)));
                }
            }
            out.add(device.build());
        }
        return out;
    }

    @Override
    public void close() {
        nameToDeviceInfo.clear();
        refresh(null);
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            // pass
        }
    }

    /**
     * Generate a human readable connection age string.
     *
     * @param duration
     * @return
     */
    static String calculateDuration(Duration duration) {
        StringBuilder out = new StringBuilder();
        long deltaSeconds = duration.getSeconds();
        long seconds = deltaSeconds;
        long days = seconds / 86400L;
        if (deltaSeconds >= 86400L) {
            out.append(days).append("d");
        }
        seconds %= 86400L;

        long hours = seconds / 3600L;
        if (deltaSeconds >= 3600L) {
            out.append(hours).append("h");
        }
        seconds %= 3600L;

        long minutes = seconds / 60L;
        if (deltaSeconds >= 60L) {
            out.append(minutes).append("m");
        }
        seconds %= 60L;

        out.append(seconds).append("s");
        return out.toString();
    }
}
