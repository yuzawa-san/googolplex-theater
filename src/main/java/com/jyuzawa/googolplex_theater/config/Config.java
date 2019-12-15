package com.jyuzawa.googolplex_theater.config;

import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * This class represents the values of all of the CLI arguments.
 *
 * @author jyuzawa
 */
public final class Config {

  public static final Options OPTIONS = generateOptions();
  private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]{8}$");

  private final String appId;
  private final Path castConfigPath;
  private final InetAddress interfaceAddress;
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
            .desc("multicast network interface")
            .hasArg()
            .argName("IFACE")
            .build());
    options.addOption(
        Option.builder("p")
            .longOpt("port")
            .desc("server port number")
            .hasArg()
            .argName("PORTNUM")
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
      this.appId = line.getOptionValue("app_id");
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
      this.castConfigPath = Paths.get(CastConfigLoader.DEFAULT_PATH).toAbsolutePath();
    }
    File castConfigFile = getCastConfigPath().toFile();
    if (!castConfigFile.exists() || !castConfigFile.isFile()) {
      throw new ParseException(
          "cast-config file does not exist: " + castConfigFile.getAbsolutePath());
    }

    // interface: missing will cause the mdns client to autochoose the interface.
    InetAddress theInterfaceAddress = null;
    if (line.hasOption("interface")) {
      String interfaceValue = line.getOptionValue("interface");
      try {
        // this will throw if the interface does not exist
        NetworkInterface iface = NetworkInterface.getByName(interfaceValue);
        // if it does exist, use the first address
        List<InetAddress> addresses = Collections.list(iface.getInetAddresses());
        if (addresses.isEmpty()) {
          throw new IllegalArgumentException("interface " + interfaceValue + " has no addresses");
        }
        theInterfaceAddress = addresses.get(0);
      } catch (Exception e) {
        throw new ParseException(
            "failed to get address for interface "
                + interfaceValue
                + " - "
                + e.getClass().getSimpleName()
                + ": "
                + e.getMessage());
      }
    }
    this.interfaceAddress = theInterfaceAddress;

    // server port
    if (line.hasOption("port")) {
      this.port = Integer.parseInt(line.getOptionValue("port"));
    } else {
      this.port = GoogolplexServer.DEFAULT_PORT;
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

  /** @return the IP address for the network interface to use for service discovery. */
  public InetAddress getInterfaceAddress() {
    return interfaceAddress;
  }

  public int getServerPort() {
    return port;
  }
}
