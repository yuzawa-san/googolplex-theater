package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jyuzawa.googolplex_theater.config.CastConfig;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.config.CastConfigLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;

class CastConfigLoaderTest {

  private static final String VALUE1 =
      "{ \"devices\": [{ \"name\": \"NameOfYourDevice2\", \"blah\":true, \"settings\": { \"url\": \"https://example2.com/\", \"refreshSeconds\": 9600 } }] }";

  @Test
  void test() throws IOException, InterruptedException {
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
      file.delete();
    }
  }
}
