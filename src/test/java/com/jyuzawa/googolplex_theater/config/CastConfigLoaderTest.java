package com.jyuzawa.googolplex_theater.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import io.netty.util.CharsetUtil;
import io.vertx.core.json.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
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
    BlockingQueue<CastConfig> queue = new ArrayBlockingQueue<>(10);
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
          public void processConfig(CastConfig config) {
            queue.add(config);
          }
        };
    CastConfigLoader loader = new CastConfigLoader(controller, path);
    try {
      CastConfig config = queue.take();
      assertEquals(1, config.devices.size());
      DeviceInfo device = config.devices.get(0);
      assertEquals("NameOfYourDevice2", device.name);
      assertEquals("https://example2.com/", device.settings.get("url").asText());
      assertEquals(9600, device.settings.get("refreshSeconds").asInt());

      // see if an update is detected
      try (BufferedWriter bufferedWriter =
          Files.newBufferedWriter(path, CharsetUtil.UTF_8, StandardOpenOption.WRITE)) {
        bufferedWriter.write(VALUE2);
      }
      config = queue.poll(1, TimeUnit.MINUTES);
      assertNotNull(config);
      assertEquals(1, config.devices.size());
      device = config.devices.get(0);
      assertEquals("NameOfYourDevice2", device.name);
      assertEquals("https://example2.com/updated", device.settings.get("url").asText());
      assertEquals(600, device.settings.get("refreshSeconds").asInt());
    } finally {
      loader.close();
    }
  }
}
