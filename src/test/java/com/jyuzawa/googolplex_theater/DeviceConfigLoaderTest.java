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
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class DeviceConfigLoaderTest {

    private static final String VALUE1 =
            "devices:\n  - name: NameOfYourDevice2\n    settings:\n      url: https://example2.com/\n      refreshSeconds: 9600";

    private static final String VALUE2 =
            "devices:\n  - name: NameOfYourDevice2\n    settings:\n      url: https://example2.com/updated\n      refreshSeconds: 600";

    @Test
    void loaderTest() throws IOException, InterruptedException {
        // For a simple file system with Unix-style paths and behavior:
        FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setWatchServiceConfiguration(WatchServiceConfiguration.polling(10, TimeUnit.MILLISECONDS))
                .build());
        Path conf = fs.getPath("/conf");
        Files.createDirectory(conf);
        Path path = conf.resolve("devices.yml");
        try (BufferedWriter bufferedWriter =
                Files.newBufferedWriter(path, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            bufferedWriter.write(VALUE1);
        }
        BlockingQueue<DeviceConfig> queue = new ArrayBlockingQueue<>(10);
        GoogolplexService controller = Mockito.mock(GoogolplexService.class);
        Mockito.when(controller.processDeviceConfig(Mockito.any())).then(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                queue.add(invocation.getArgument(0, DeviceConfig.class));
                return null;
            }
        });
        DeviceConfigLoader loader = new DeviceConfigLoader(controller, path);
        loader.start();
        try {
            DeviceConfig config = queue.take();
            assertEquals(1, config.getDevices().size());
            DeviceInfo device = config.getDevices().get(0);
            assertEquals("NameOfYourDevice2", device.getName());
            assertEquals(
                    "https://example2.com/", device.getSettings().get("url").asText());
            assertEquals(9600, device.getSettings().get("refreshSeconds").asInt());

            // see if an update is detected
            try (BufferedWriter bufferedWriter =
                    Files.newBufferedWriter(path, CharsetUtil.UTF_8, StandardOpenOption.WRITE)) {
                bufferedWriter.write(VALUE2);
            }
            config = queue.poll(1, TimeUnit.MINUTES);
            assertNotNull(config);
            assertEquals(1, config.getDevices().size());
            device = config.getDevices().get(0);
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
