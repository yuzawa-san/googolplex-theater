/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class DeviceConfigLoaderTest {

    private static final String VALUE1 =
            "devices:\n  - name: NameOfYourDevice2\n    settings:\n      url: https://example2.com/\n      refreshSeconds: 9600\n  - name: ProxiedDevice\n    settings:\n      url: ${PROXY}/foo/bar";

    private static final String VALUE2 =
            "devices:\n  - name: NameOfYourDevice2\n    settings:\n      url: https://example2.com/updated\n      refreshSeconds: 600\n  - name: ProxiedDevice\n    settings:\n      url: ${PROXY}/foo/bar";

    @Test
    void loaderTest() throws IOException, InterruptedException {
        // For a simple file system with Unix-style paths and behavior:
        FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setWatchServiceConfiguration(WatchServiceConfiguration.polling(10, TimeUnit.MILLISECONDS))
                .build());
        Path rootPath = fs.getPath("/");
        Path conf = rootPath.resolve("conf");
        Files.createDirectory(conf);
        Path devicePath = conf.resolve("devices.yml");
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(
                devicePath, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            bufferedWriter.write(VALUE1);
        }
        BlockingQueue<List<DeviceInfo>> queue = new ArrayBlockingQueue<>(10);
        GoogolplexService controller = Mockito.mock(GoogolplexService.class);
        Mockito.when(controller.processDeviceConfig(Mockito.any())).then(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                List<DeviceInfo> newDevices = invocation.getArgument(0);
                queue.add(newDevices);
                return null;
            }
        });
        ServiceDiscovery serviceDiscovery = Mockito.mock(ServiceDiscovery.class);
        String ipAddress = "192.168.1.239";
        InetAddress address = InetAddress.getByName(ipAddress);
        Mockito.when(serviceDiscovery.getInetAddress()).thenReturn(address);
        ProxyProperties proxyProperties = new ProxyProperties();
        DeviceConfigLoader loader = new DeviceConfigLoader(
                controller, conf, devicePath.toString(), proxyProperties, serviceDiscovery);
        loader.start();
        try {
            List<DeviceInfo> devices = queue.take();
            assertEquals(2, devices.size());
            DeviceInfo device = devices.get(0);
            assertEquals("NameOfYourDevice2", device.getName());
            assertEquals(
                    "https://example2.com/", device.getSettings().get("url").asText());
            assertEquals(9600, device.getSettings().get("refreshSeconds").asInt());
            device = devices.get(1);
            assertEquals("ProxiedDevice", device.getName());
            assertEquals(
                    "http://" + ipAddress + ":"+proxyProperties.port+"/foo/bar",
                    device.getSettings().get("url").asText());

            // see if an update is detected
            try (BufferedWriter bufferedWriter =
                    Files.newBufferedWriter(devicePath, CharsetUtil.UTF_8, StandardOpenOption.WRITE)) {
                bufferedWriter.write(VALUE2);
            }
            devices = queue.poll(1, TimeUnit.MINUTES);
            assertNotNull(devices);
            assertEquals(2, devices.size());
            device = devices.get(0);
            assertEquals("NameOfYourDevice2", device.getName());
            assertEquals(
                    "https://example2.com/updated",
                    device.getSettings().get("url").asText());
            assertEquals(600, device.getSettings().get("refreshSeconds").asInt());
        } finally {
            loader.close();
        }
    }
}
