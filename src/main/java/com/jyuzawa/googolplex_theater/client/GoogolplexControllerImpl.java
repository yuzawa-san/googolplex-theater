/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * All rights reserved. Licensed under the MIT License.
 */
package com.jyuzawa.googolplex_theater.client;

import com.jyuzawa.googolplex_theater.config.DeviceConfig;
import com.jyuzawa.googolplex_theater.config.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.config.GoogolplexTheaterConfig;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.RetrySpec;

/**
 * This class represents the state of the application. All modifications to state occur in the same
 * thread.
 *
 * @author jyuzawa
 */
@Slf4j
@Component
public final class GoogolplexControllerImpl implements GoogolplexController {
    private final TcpClient bootstrap;
    private final Map<String, DeviceInfo> nameToDeviceInfo;
    private final Map<String, InetSocketAddress> nameToAddress;
    private final Map<String, Conn> nameToChannel;

    public GoogolplexControllerImpl(GoogolplexTheaterConfig config) throws IOException {
        String appId = config.getRecieverAppId();
        // the state is maintained in these maps
        this.nameToDeviceInfo = new ConcurrentHashMap<>();
        this.nameToAddress = new ConcurrentHashMap<>();
        this.nameToChannel = new ConcurrentHashMap<>();
        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        log.info("Using cast application id: {}", appId);
        // configure the socket client
        // TODO: shared resources
        this.bootstrap = TcpClient.create()
                .secure(spec -> spec.sslContext(sslContext))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
    }

    private record Conn(Instant birth, Disposable disposable) {}

    /**
     * Load the config and propagate the changes to the any currently connected devices.
     *
     * @param config the settings loaded from the file
     */
    @Override
    public void processDeviceConfig(DeviceConfig config) {
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
    }

    /**
     * Add a discovered device and initialize a new connection to the device if one does not exist
     * already.
     *
     * @param event mdns info
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Override
    public void register(ServiceEvent event) {

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
    }

    /**
     * Apply changes to a device. This closes any existing connections. If there were no existing
     * connections, then a new connection is made. The new connection will call this function again
     * when it is closed. This logic allows for new connections to take on the proper settings.
     *
     * @param name device's name
     */
    private void apply(String name) {
        Conn oldChannel = nameToChannel.get(name);
        if (oldChannel != null) {
            log.info("DISCONNECT '{}'", name);
            /*
             * kill the channel, so it will reconnect and this method will be called again, but skip this code path.
             */
            safeClose(oldChannel);
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
        log.info("CONNECT '{}'", name);

        Disposable sub = bootstrap
                .remoteAddress(() -> address)
                .connect()
                .flatMap(new GoogolplexClientHandler(GoogolplexClientHandler.DEFAULT_APPLICATION_ID, 5, 10, deviceInfo))
                .retryWhen(RetrySpec.fixedDelay(1024, Duration.ofSeconds(10)).doBeforeRetry(err -> {
                    log.warn("ERROR " + name, err.failure());
                }))
                .subscribe();
        nameToChannel.put(name, new Conn(Instant.now(), sub));
    }

    /**
     * This closes a connection to a device and causes it to reconnect immediately.
     *
     * @param channel
     * @return
     */
    private void safeClose(Conn channel) {
        if (channel != null) {
            channel.disposable().dispose();
        }
    }

    /**
     * Trigger a refresh by closing channels which will cause a reconnect.
     *
     * @param name the device to refresh
     */
    @Override
    public void refresh(String name) {
        // closing channels will cause them to reconnect
        if (name == null) {
            // close all channels
            for (Conn channel : nameToChannel.values()) {
                safeClose(channel);
            }
        } else {
            // close specific channel
            Conn channel = nameToChannel.get(name);
            safeClose(channel);
        }
    }

    @Override
    public List<Map<String, Object>> getDeviceInfo() {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> allNames = new TreeSet<>();
        allNames.addAll(nameToDeviceInfo.keySet());
        allNames.addAll(nameToAddress.keySet());
        Instant now = Instant.now();
        for (String name : allNames) {
            Map<String, Object> device = new LinkedHashMap<>();
            device.put("name", name);
            DeviceInfo deviceInfo = nameToDeviceInfo.get(name);
            if (deviceInfo != null) {
                device.put("settings", deviceInfo.getSettings().toPrettyString());
            }
            InetSocketAddress ipAddress = nameToAddress.get(name);
            if (ipAddress != null) {
                device.put("ipAddress", ipAddress.getAddress().getHostAddress());
            }
            Conn channel = nameToChannel.get(name);
            if (channel != null) {
                Instant birth = channel.birth();
                if (birth != null) {
                    String duration = calculateDuration(Duration.between(birth, now));
                    device.put("duration", duration);
                }
            }
            out.add(device);
        }
        return out;
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
