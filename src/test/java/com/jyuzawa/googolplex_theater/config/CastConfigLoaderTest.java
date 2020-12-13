package com.jyuzawa.googolplex_theater.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import io.netty.util.CharsetUtil;
import io.vertx.core.json.JsonObject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.jmdns.ServiceEvent;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CastConfigLoaderTest {

  private static final String VALUE1 =
      "{ \"devices\": [{ \"name\": \"NameOfYourDevice2\", \"blah\":true, \"settings\": { \"url\": \"https://example2.com/\", \"refreshSeconds\": 9600 } }] }";

  private static final String VALUE2 =
      "{ \"devices\": [{ \"name\": \"NameOfYourDevice2\", \"blah\":true, \"settings\": { \"url\": \"https://example2.com/updated\", \"refreshSeconds\": 600 } }] }";

  @TempDir File tempDir;

  @Test
  void loaderTest() throws IOException, InterruptedException {
    File file = new File(tempDir, "cast_config.json");
    Path path = file.toPath();
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

  @Test
  void cliTest() throws ParseException {
    String confJson = "./src/dist/conf/cast_config.json";

    assertThrows(
        ParseException.class,
        () -> {
          new Config(new String[] {"-a", "blah12425"});
        });

    assertThrows(
        ParseException.class,
        () -> {
          new Config(new String[] {"-c", "my_missing_config.json"});
        });

    Config config =
        new Config(
            new String[] {
              "-i", "en0", "-a", "ABCDEFGH", "-p", "9999", "-c", "./src/dist/conf/cast_config.json"
            });
    assertEquals("ABCDEFGH", config.getAppId());
    assertEquals(9999, config.getServerPort());
    assertEquals("en0", config.getPreferredInterface());
    assertTrue(config.getCastConfigPath().toFile().exists());

    Config config2 = new Config(new String[] {"-c", confJson});
    assertEquals(GoogolplexClientHandler.DEFAULT_APPLICATION_ID, config2.getAppId());
    assertEquals(GoogolplexServer.DEFAULT_PORT, config2.getServerPort());
    assertTrue(config2.getCastConfigPath().toFile().exists());
    assertNull(config2.getPreferredInterface());
  }
}
