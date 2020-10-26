package com.jyuzawa.googolplex_theater.config;

import com.jyuzawa.googolplex_theater.GoogolplexTheater;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents the values of all of the CLI arguments.
 *
 * @author jyuzawa
 */
public final class Config {
  private static final Logger LOG = LoggerFactory.getLogger(Config.class);
  private static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";
  private static final List<String> DIAGNOSTIC_PROPERTIES =
      Collections.unmodifiableList(
          Arrays.asList("os.name", "os.version", "os.arch", "java.vendor", "java.version"));
  public static final Options OPTIONS = generateOptions();
  private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");

  private final String appId;
  private final Path castConfigPath;
  private final String preferredInterface;
  private final int port;

  private static Options generateOptions() {
    Options options = new Options();
    options.addOption(
        Option.builder("a")
            .longOpt("app-id")
            .desc("cast application ID")
            .hasArg()
            .argName("ABCDEFGH")
            .build());
    options.addOption(
        Option.builder("c")
            .longOpt("cast-config")
            .desc("cast config json file")
            .hasArg()
            .argName("FILE")
            .build());
    options.addOption(
        Option.builder("i")
            .longOpt("interface")
            .desc("network interface name or IP address to use for service discovery")
            .hasArg()
            .argName("IFACE_NAME_OR_IP")
            .build());
    options.addOption(
        Option.builder("p")
            .longOpt("port")
            .desc("server port number")
            .hasArg()
            .argName("PORT_NUM")
            .build());
    options.addOption(Option.builder("h").longOpt("help").desc("show usage").build());
    return options;
  }

  /**
   * Parse CLI arguments and run validation.
   *
   * @param args from the main
   * @throws ParseException when validation fails
   */
  public Config(String[] args) throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine line = parser.parse(OPTIONS, args);

    // bail for help
    if (line.hasOption("help")) {
      throw new ParseException("show help");
    }

    // application id: check against pattern.
    if (line.hasOption("app-id")) {
      this.appId = line.getOptionValue("app-id");
    } else {
      this.appId = GoogolplexClientHandler.DEFAULT_APPLICATION_ID;
    }
    if (!APP_ID_PATTERN.matcher(appId).find()) {
      throw new ParseException("invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
    }

    // cast-config: check that file exists.
    if (line.hasOption("cast-config")) {
      this.castConfigPath = Paths.get(line.getOptionValue("cast-config")).toAbsolutePath();
    } else {
      // NOTE: gradle does not expose APP_HOME, but they do expose OLDPWD.
      // Default allows the application to run in IDE
      String appHome = System.getenv().getOrDefault("OLDPWD", "src/dist");
      this.castConfigPath =
          Paths.get(appHome + "/" + CastConfigLoader.DEFAULT_PATH).toAbsolutePath();
    }
    File castConfigFile = getCastConfigPath().toFile();
    if (!castConfigFile.exists() || !castConfigFile.isFile()) {
      throw new ParseException(
          "cast-config file does not exist: " + castConfigFile.getAbsolutePath());
    }

    this.preferredInterface = line.getOptionValue("interface");

    // server port
    if (line.hasOption("port")) {
      this.port = Integer.parseInt(line.getOptionValue("port"));
    } else {
      this.port = GoogolplexServer.DEFAULT_PORT;
    }

    // print some diagnostic information
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

  /** @return the properly registered application ID to use in the receiver. */
  public String getAppId() {
    return appId;
  }

  /** @return the file location of device names and their settings. */
  public Path getCastConfigPath() {
    return castConfigPath;
  }

  /**
   * @return the name, hostname, or IP address for the network interface to use for service
   *     discovery.
   */
  public String getPreferredInterface() {
    return preferredInterface;
  }

  /** @return the port to run the web UI server on */
  public int getServerPort() {
    return port;
  }
}
