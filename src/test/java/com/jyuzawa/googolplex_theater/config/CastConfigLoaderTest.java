package com.jyuzawa.googolplex_theater.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import io.netty.util.CharsetUtil;
import io.vertx.core.json.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.jmdns.ServiceEvent;
import org.junit.jupiter.api.Test;

class CastConfigLoaderTest {

  private static final String VALUE1 =
      "{ \"devices\": [{ \"name\": \"NameOfYourDevice2\", \"blah\":true, \"settings\": { \"url\": \"https://example2.com/\", \"refreshSeconds\": 9600 } }] }";

  private static final String VALUE2 =
      "{ \"devices\": [{ \"name\": \"NameOfYourDevice2\", \"blah\":true, \"settings\": { \"url\": \"https://example2.com/updated\", \"refreshSeconds\": 600 } }] }";

  @Test
  void loaderTest() throws IOException, InterruptedException {
    // For a simple file system with Unix-style paths and behavior:
    FileSystem fs =
        Jimfs.newFileSystem(
            Configuration.unix().toBuilder()
                .setWatchServiceConfiguration(
                    WatchServiceConfiguration.polling(10, TimeUnit.MILLISECONDS))
                .build());
    Path conf = fs.getPath("/conf");
    Files.createDirectory(conf);
    Path path = conf.resolve("cast_config.json");
    try (BufferedWriter bufferedWriter =
        Files.newBufferedWriter(
            path, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
      bufferedWriter.write(VALUE1);
    }
    BlockingQueue<List<DeviceInfo>> queue = new ArrayBlockingQueue<>(10);
    GoogolplexController controller =
        new GoogolplexController() {

          @Override
          public void register(ServiceEvent event) {}

          @Override
          public void refresh(String name) {}

          @Override
          public List<JsonObject> getDeviceInfo() {
            return null;
          }

          @Override
          public void processDevices(List<DeviceInfo> devices) {
            queue.add(devices);
          }
        };
    CastConfigLoader loader = new CastConfigLoader(path);
    loader.watch(controller);
    try {
      List<DeviceInfo> devices = queue.poll(1, TimeUnit.MINUTES);
      assertEquals(devices, loader.getInitialConfig().devices);
      assertEquals(1, devices.size());
      DeviceInfo device = devices.get(0);
      assertEquals("NameOfYourDevice2", device.name);
      assertEquals("https://example2.com/", device.settings.get("url").asText());
      assertEquals(9600, device.settings.get("refreshSeconds").asInt());

      // see if an update is detected
      try (BufferedWriter bufferedWriter =
          Files.newBufferedWriter(path, CharsetUtil.UTF_8, StandardOpenOption.WRITE)) {
        bufferedWriter.write(VALUE2);
      }
      devices = queue.poll(1, TimeUnit.MINUTES);
      assertNotNull(devices);
      assertEquals(1, devices.size());
      device = devices.get(0);
      assertEquals("NameOfYourDevice2", device.name);
      assertEquals("https://example2.com/updated", device.settings.get("url").asText());
      assertEquals(600, device.settings.get("refreshSeconds").asInt());

      // device equality
      DeviceInfo duplicateDevice = new DeviceInfo(device.name, device.settings);
      assertTrue(device.equals(device));
      assertTrue(device.equals(duplicateDevice));
      assertFalse(device.equals("a string"));
      DeviceInfo otherDevice = new DeviceInfo("other", device.settings);
      assertFalse(device.equals(otherDevice));
      Set<DeviceInfo> set = new HashSet<>();
      set.add(device);
      set.add(duplicateDevice);
      assertEquals(1, set.size());
    } finally {
      loader.close();
    }
    assertThrows(
        IOException.class,
        () -> {
          new CastConfigLoader(conf.resolve("not_a_file.json"));
        });
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new CastConfig("bad app id", null, null, null);
        });
  }
}
