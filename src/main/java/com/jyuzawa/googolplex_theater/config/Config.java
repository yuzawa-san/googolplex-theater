package com.jyuzawa.googolplex_theater.config;

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

public final class Config {

  private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]{8}$");

  private final String appId;
  private final Path castConfigPath;
  private final InetAddress interfaceAddress;

  public static Options generateOptions() {
    Options options = new Options();
    options.addOption(
        Option.builder("a")
            .longOpt("app-id")
            .desc("cast application ID")
            .hasArg()
            .argName("ABCDEFGH")
            .required()
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
    return options;
  }

  public Config(Options options, String[] args) throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine line = parser.parse(options, args);

    // application id
    this.appId = line.getOptionValue("app-id");
    if (!APP_ID_PATTERN.matcher(appId).find()) {
      throw new ParseException("invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
    }

    // cast-config
    if (line.hasOption("cast-config")) {
      this.castConfigPath = Paths.get(line.getOptionValue("cast-config"));
    } else {
      this.castConfigPath = Paths.get(CastConfigLoader.DEFAULT_PATH);
    }
    File castConfigFile = getCastConfigPath().toFile();
    if (!castConfigFile.exists() || !castConfigFile.isFile()) {
      throw new ParseException(
          "cast-config file does not exist: " + castConfigFile.getAbsolutePath());
    }

    // interface
    InetAddress theInterfaceAddress = null;
    if (line.hasOption("interface")) {
      String interfaceValue = line.getOptionValue("interface");
      try {
        NetworkInterface iface = NetworkInterface.getByName(interfaceValue);
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
  }

  public String getAppId() {
    return appId;
  }

  public Path getCastConfigPath() {
    return castConfigPath;
  }

  public InetAddress getInterfaceAddress() {
    return interfaceAddress;
  }
}
