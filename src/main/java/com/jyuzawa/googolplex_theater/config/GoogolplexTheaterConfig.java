package com.jyuzawa.googolplex_theater.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jyuzawa.googolplex_theater.GoogolplexTheater;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.util.MapperUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A representation of the application config.
 *
 * @author jtyuzawa
 */
public final class GoogolplexTheaterConfig {

  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexTheaterConfig.class);
  private static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";
  private static final List<String> DIAGNOSTIC_PROPERTIES =
      Collections.unmodifiableList(
          Arrays.asList("os.name", "os.version", "os.arch", "java.vendor", "java.version"));

  static {
    LOG.info("Starting up Googolplex Theater!");
    LOG.info("Website: " + PROJECT_WEBSITE);
    Package thePackage = GoogolplexTheater.class.getPackage();
    LOG.info(
        "Version: {} ({})",
        thePackage.getSpecificationVersion(),
        thePackage.getImplementationVersion());
    for (String property : DIAGNOSTIC_PROPERTIES) {
      LOG.info("Runtime[{}]: {}", property, System.getProperty(property));
    }
  }

  static final String ALL_HOSTS = "*";
  private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");
  private static final String CONFIG_FILE_NAME = "config.yml";

  private final String recieverAppId;
  private final String discoveryNetworkInterface;
  private final InetSocketAddress uiServerAddress;
  private final Path deviceConfigPath;
  private final int baseReconnectSeconds;
  private final int reconnectNoiseSeconds;
  private final int heartbeatIntervalSeconds;
  private final int heartbeatTimeoutSeconds;

  GoogolplexTheaterConfig(Path basePath, ConfigYaml config) {
    this.recieverAppId = config.receiverAppId;
    if (!APP_ID_PATTERN.matcher(recieverAppId).find()) {
      throw new IllegalArgumentException(
          "Invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
    }
    this.discoveryNetworkInterface = config.discoveryNetworkInterface;
    if (ALL_HOSTS.equals(config.uiServerHost)) {
      this.uiServerAddress = new InetSocketAddress(config.uiServerPort);
    } else {
      this.uiServerAddress = new InetSocketAddress(config.uiServerHost, config.uiServerPort);
    }
    this.deviceConfigPath = basePath.resolve(config.deviceConfigFile).toAbsolutePath();
    if (!Files.isRegularFile(deviceConfigPath)) {
      throw new IllegalArgumentException("Devices file does not exist: " + deviceConfigPath);
    }
    this.baseReconnectSeconds = config.baseReconnectSeconds;
    this.reconnectNoiseSeconds = config.reconnectNoiseSeconds;
    this.heartbeatIntervalSeconds = config.heartbeatIntervalSeconds;
    this.heartbeatTimeoutSeconds = config.heartbeatTimeoutSeconds;
  }

  public static GoogolplexTheaterConfig load() throws IOException {
    return load(getConfDirectory());
  }

  public static GoogolplexTheaterConfig load(Path basePath) throws IOException {
    Path path = basePath.resolve(CONFIG_FILE_NAME).toAbsolutePath();
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Config file does not exist: " + path);
    }
    try (InputStream stream = Files.newInputStream(path)) {
      ConfigYaml config = MapperUtil.YAML_MAPPER.readValue(stream, ConfigYaml.class);
      return new GoogolplexTheaterConfig(basePath, config);
    }
  }

  static Path getConfDirectory() {
    try {
      // NOTE: gradle does not expose APP_HOME, so we need to be creative
      Path installedPath =
          Paths.get(
                  GoogolplexTheater.class
                      .getProtectionDomain()
                      .getCodeSource()
                      .getLocation()
                      .toURI())
              .resolve("../../conf")
              .normalize()
              .toAbsolutePath();
      if (installedPath.toFile().exists()) {
        return installedPath;
      }
    } catch (URISyntaxException e) {
      LOG.error("Failed to find default config", e);
    }
    // IDE case
    return Paths.get("src/dist/conf").toAbsolutePath();
  }

  public String getRecieverAppId() {
    return recieverAppId;
  }

  public InetSocketAddress getUiServerAddress() {
    return uiServerAddress;
  }

  public String getDiscoveryNetworkInterface() {
    return discoveryNetworkInterface;
  }

  public Path getDeviceConfigPath() {
    return deviceConfigPath;
  }

  public static final class ConfigYaml {
    private static final int DEFAULT_BASE_RECONNECT_SECONDS = 15;
    private static final int DEFAULT_RECONNECT_NOISE_SECONDS = 5;
    private static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 5;
    private static final int DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 30;

    @JsonProperty public String receiverAppId = GoogolplexClientHandler.DEFAULT_APPLICATION_ID;
    @JsonProperty public String uiServerHost = ALL_HOSTS;
    @JsonProperty public int uiServerPort = 8000;
    @JsonProperty public String discoveryNetworkInterface;
    @JsonProperty public String deviceConfigFile = "devices.yml";

    @JsonProperty public int baseReconnectSeconds = DEFAULT_BASE_RECONNECT_SECONDS;
    @JsonProperty public int reconnectNoiseSeconds = DEFAULT_RECONNECT_NOISE_SECONDS;
    @JsonProperty public int heartbeatIntervalSeconds = DEFAULT_HEARTBEAT_INTERVAL_SECONDS;
    @JsonProperty public int heartbeatTimeoutSeconds = DEFAULT_HEARTBEAT_TIMEOUT_SECONDS;
  }

  public int getBaseReconnectSeconds() {
    return baseReconnectSeconds;
  }

  public int getReconnectNoiseSeconds() {
    return reconnectNoiseSeconds;
  }

  public int getHeartbeatIntervalSeconds() {
    return heartbeatIntervalSeconds;
  }

  public int getHeartbeatTimeoutSeconds() {
    return heartbeatTimeoutSeconds;
  }
}
