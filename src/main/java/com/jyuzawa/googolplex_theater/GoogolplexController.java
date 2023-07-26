/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import java.io.Closeable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
public final class GoogolplexController implements Closeable {
    private static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";
    private static final List<String> DIAGNOSTIC_PROPERTIES = Collections.unmodifiableList(
            Arrays.asList("os.name", "os.version", "os.arch", "java.vendor", "java.version"));

    static {
        log.info("Starting up Googolplex Theater!");
        log.info("Website: " + PROJECT_WEBSITE);
        Package thePackage = GoogolplexTheater.class.getPackage();
        log.info("Version: {} ({})", thePackage.getSpecificationVersion(), thePackage.getImplementationVersion());
        for (String property : DIAGNOSTIC_PROPERTIES) {
            log.info("Runtime[{}]: {}", property, System.getProperty(property));
        }
    }

    private final GoogolplexClient client;
    private final Map<String, DeviceInfo> nameToDeviceInfo;
    private final Map<String, InetSocketAddress> nameToAddress;
    private final Map<String, Conn> nameToChannel;
    private final ExecutorService executor;

    @Autowired
    public GoogolplexController(GoogolplexClient client) {
        this.client = client;
        // the state is maintained in these maps
        this.nameToDeviceInfo = new ConcurrentHashMap<>();
        this.nameToAddress = new ConcurrentHashMap<>();
        this.nameToChannel = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("controller"));
    }

    private record Conn(Instant birth, Disposable disposable) {}

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
        Conn oldChannel = nameToChannel.get(name);
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
        Disposable disposable = client.connect(address, deviceInfo).subscribe();
        nameToChannel.put(name, new Conn(Instant.now(), disposable));
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

    public List<Map<String, Object>> getDeviceInfo() {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> allNames = new TreeSet<>();
        allNames.addAll(nameToDeviceInfo.keySet());
        allNames.addAll(nameToAddress.keySet());
        for (String name : allNames) {
            Map<String, Object> device = new LinkedHashMap<>();
            device.put("name", name);
            DeviceInfo deviceInfo = nameToDeviceInfo.get(name);
            if (deviceInfo != null) {
                device.put("settings", deviceInfo.getSettings());
            }
            InetSocketAddress ipAddress = nameToAddress.get(name);
            if (ipAddress != null) {
                device.put("ipAddress", ipAddress.getAddress().getHostAddress());
            }
            Conn channel = nameToChannel.get(name);
            if (channel != null) {
                Instant birth = channel.birth();
                if (birth != null) {
                    device.put("birthMs", birth.toEpochMilli());
                }
            }
            out.add(device);
        }
        return out;
    }

    @Override
    public void close() {
        nameToDeviceInfo.clear();
        refresh(null);
        executor.close();
    }
}
