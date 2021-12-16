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
  private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");
  private static final String CONFIG_FILE_NAME = "config.yml";
  static final Path CONF_DIRECTORY = getConfDirectory();

  private final String appId;
  private final String preferredInterface;
  private final InetSocketAddress serverAddress;
  private final Path devicesPath;

  GoogolplexTheaterConfig(ConfigYaml config) {
    this.appId = config.appId;
    if (!APP_ID_PATTERN.matcher(appId).find()) {
      throw new IllegalArgumentException(
          "Invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
    }
    this.preferredInterface = config.preferredInterface;
    if ("*".equals(config.serverHost)) {
      this.serverAddress = new InetSocketAddress(config.serverPort);
    } else {
      this.serverAddress = new InetSocketAddress(config.serverHost, config.serverPort);
    }
    this.devicesPath = CONF_DIRECTORY.resolve(config.devicesPath).toAbsolutePath();
    File devicesFile = devicesPath.toFile();
    if (!devicesFile.exists() || !devicesFile.isFile()) {
      throw new IllegalArgumentException(
          "Devices file does not exist: " + devicesFile.getAbsolutePath());
    }
    printDiagnosticInfo();
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

  public String getAppId() {
    return appId;
  }

  public InetSocketAddress getServerAddress() {
    return serverAddress;
  }

  public String getPreferredInterface() {
    return preferredInterface;
  }

  public Path getDevicesPath() {
    return devicesPath;
  }

  private static void printDiagnosticInfo() {
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

  static class ConfigYaml {
    private String appId = GoogolplexClientHandler.DEFAULT_APPLICATION_ID;
    private String serverHost = "localhost";
    private int serverPort = 8000;
    private String preferredInterface;
    private String devicesPath = "devices.yml";

    @JsonSetter
    public void setAppId(String appId) {
      this.appId = appId;
    }

    @JsonSetter
    public void setServerHost(String serverHost) {
      this.serverHost = serverHost;
    }

    @JsonSetter
    public void setServerPort(int serverPort) {
      this.serverPort = serverPort;
    }

    @JsonSetter
    public void setPreferredInterface(String preferredInterface) {
      this.preferredInterface = preferredInterface;
    }

    @JsonSetter
    public void setDevicesPath(String devicesPath) {
      this.devicesPath = devicesPath;
    }
  }
}
