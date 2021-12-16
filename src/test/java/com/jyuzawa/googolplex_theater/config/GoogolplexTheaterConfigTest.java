package com.jyuzawa.googolplex_theater.config;

import static org.junit.jupiter.api.Assertions.*;

import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GoogolplexTheaterConfigTest {

  @Test
  void test() throws IOException {
    GoogolplexTheaterConfig config = GoogolplexTheaterConfig.load();
    assertEquals(GoogolplexClientHandler.DEFAULT_APPLICATION_ID, config.getAppId());
    assertEquals(8000, config.getServerAddress().getPort());
    assertEquals("localhost", config.getServerAddress().getHostName());
    assertNull(config.getPreferredInterface());
    assertTrue(config.getDevicesPath().toFile().exists());
  }

  @Test
  void appIdTest() {
    GoogolplexTheaterConfig.ConfigYaml config = new GoogolplexTheaterConfig.ConfigYaml();
    config.setAppId("ABCDEFGH");
    new GoogolplexTheaterConfig(config);
    GoogolplexTheaterConfig.ConfigYaml config2 = new GoogolplexTheaterConfig.ConfigYaml();
    config2.setAppId("not-an-app-id");
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new GoogolplexTheaterConfig(config2);
        });
  }

  @Test
  void missingDevicesTest() {
    GoogolplexTheaterConfig.ConfigYaml config = new GoogolplexTheaterConfig.ConfigYaml();
    config.setDevicesPath("not/a/real/file.yml");
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new GoogolplexTheaterConfig(config);
        });
  }
}
