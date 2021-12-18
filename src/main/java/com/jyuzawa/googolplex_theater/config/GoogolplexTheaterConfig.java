package com.jyuzawa.googolplex_theater.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.jyuzawa.googolplex_theater.GoogolplexTheater;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.util.MapperUtil;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
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
  private static final Path CONF_DIRECTORY = getConfDirectory();

  private final String recieverAppId;
  private final String discoveryNetworkInterface;
  private final InetSocketAddress uiServerAddress;
  private final Path deviceConfigPath;

  GoogolplexTheaterConfig(ConfigYaml config) {
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
    this.deviceConfigPath = CONF_DIRECTORY.resolve(config.deviceConfigFile).toAbsolutePath();
    File devicesFile = deviceConfigPath.toFile();
    if (!devicesFile.exists() || !devicesFile.isFile()) {
      throw new IllegalArgumentException(
          "Devices file does not exist: " + devicesFile.getAbsolutePath());
    }
  }

  public static GoogolplexTheaterConfig load() throws IOException {
    File file = CONF_DIRECTORY.resolve(CONFIG_FILE_NAME).toFile();
    if (!file.exists() || !file.isFile()) {
      throw new IllegalArgumentException("Config file does not exist: " + file.getAbsolutePath());
    }
    ConfigYaml config = MapperUtil.YAML_MAPPER.readValue(file, ConfigYaml.class);
    return new GoogolplexTheaterConfig(config);
  }

  private static Path getConfDirectory() {
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

  static final class ConfigYaml {
    private String receiverAppId = GoogolplexClientHandler.DEFAULT_APPLICATION_ID;
    private String uiServerHost = ALL_HOSTS;
    private int uiServerPort = 8000;
    private String discoveryNetworkInterface;
    private String deviceConfigFile = "devices.yml";

    @JsonSetter
    public void setReceiverAppId(String receiverAppId) {
      this.receiverAppId = receiverAppId;
    }

    @JsonSetter
    public void setUiServerHost(String uiServerHost) {
      this.uiServerHost = uiServerHost;
    }

    @JsonSetter
    public void setUiServerPort(int uiServerPort) {
      this.uiServerPort = uiServerPort;
    }

    @JsonSetter
    public void setDiscoveryNetworkInterface(String discoveryNetworkInterface) {
      this.discoveryNetworkInterface = discoveryNetworkInterface;
    }

    @JsonSetter
    public void setDeviceConfigFile(String deviceConfigFile) {
      this.deviceConfigFile = deviceConfigFile;
    }
  }
}
