package com.jyuzawa.googolplex_theater.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

class CastConfigLoaderTest {

  private static final String VALUE1 =
      "{ \"devices\": [{ \"name\": \"NameOfYourDevice2\", \"blah\":true, \"settings\": { \"url\": \"https://example2.com/\", \"refreshSeconds\": 9600 } }] }";

  @Test
  void loaderTest() throws IOException, InterruptedException {
    File file = File.createTempFile("CastConfigLoaderTest-", ".json");
    Path path = file.toPath();
    Files.writeString(path, VALUE1);
    BlockingQueue<CastConfig> queue = new ArrayBlockingQueue<>(10);
    CastConfigLoader loader = new CastConfigLoader(queue::add, path);
    try {
      CastConfig config = queue.take();
      assertEquals(1, config.devices.size());
      DeviceInfo device = config.devices.get(0);
      assertEquals("NameOfYourDevice2", device.name);
      assertEquals("https://example2.com/", device.settings.get("url").asText());
      assertEquals(9600, device.settings.get("refreshSeconds").asInt());
    } finally {
      loader.close();
      file.deleteOnExit();
    }
  }

  @Test
  void cliTest() throws ParseException {
    String confJson = "./src/dist/conf/cast_config.json";
    assertThrows(
        ParseException.class,
        () -> {
          Config config = new Config(new String[] {});
          assertEquals(GoogolplexClientHandler.DEFAULT_APPLICATION_ID, config.getAppId());
          assertEquals(GoogolplexServer.DEFAULT_PORT, config.getServerPort());
        });

    assertThrows(
        ParseException.class,
        () -> {
          new Config(new String[] {"-a", "blah12425"});
        });

    assertThrows(
        ParseException.class,
        () -> {
          // fake interface
          new Config(new String[] {"-i", "en1234", "-c", confJson});
        });

    Config config =
        new Config(
            new String[] {
              "-a", "ABCDEFGH", "-p", "9999", "-c", "./src/dist/conf/cast_config.json"
            });
    assertEquals("ABCDEFGH", config.getAppId());
    assertEquals(9999, config.getServerPort());
    assertTrue(config.getCastConfigPath().toFile().exists());
    assertNull(config.getInterfaceAddress());

    Config config2 = new Config(new String[] {"-c", confJson});
    assertEquals(GoogolplexClientHandler.DEFAULT_APPLICATION_ID, config2.getAppId());
    assertEquals(GoogolplexServer.DEFAULT_PORT, config2.getServerPort());
    assertTrue(config2.getCastConfigPath().toFile().exists());
    assertNull(config2.getInterfaceAddress());
  }
}
