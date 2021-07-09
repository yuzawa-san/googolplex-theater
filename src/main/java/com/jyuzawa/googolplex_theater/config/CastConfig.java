package com.jyuzawa.googolplex_theater.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import com.jyuzawa.googolplex_theater.util.JsonUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This is a POJO for JSON deserialization. This class represents a collection of named devices and
 * their settings. The settings is a generic JsonNode to allow for flexibility. The settings will be
 * conveyed to the receiver application verbatim.
 *
 * @author jyuzawa
 */
public final class CastConfig {
  public static final String DEFAULT_PATH = "conf/cast_config.json";
  private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");

  public final String appId;
  public final int port;
  public final String preferredInterface;
  public final List<DeviceInfo> devices;

  @JsonCreator
  public CastConfig(
      @JsonProperty("appId") String appId,
      @JsonProperty("port") Integer port,
      @JsonProperty("preferredInterface") String preferredInterface,
      @JsonProperty("devices") List<DeviceInfo> devices) {
    if (appId == null) {
      appId = GoogolplexClientHandler.DEFAULT_APPLICATION_ID;
    }
    if (!APP_ID_PATTERN.matcher(appId).find()) {
      throw new IllegalArgumentException(
          "invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
    }
    this.appId = appId;
    this.port = (port != null) ? port : GoogolplexServer.DEFAULT_PORT;
    this.preferredInterface = preferredInterface;
    this.devices =
        (devices == null) ? Collections.emptyList() : Collections.unmodifiableList(devices);
  }

  public static CastConfig fromPath(Path path) throws IOException {
    try (InputStream stream = Files.newInputStream(path)) {
      return JsonUtil.MAPPER.readValue(stream, CastConfig.class);
    }
  }
}
