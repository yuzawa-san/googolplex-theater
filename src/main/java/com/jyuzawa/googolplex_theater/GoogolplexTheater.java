package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.client.GoogolplexControllerImpl;
import com.jyuzawa.googolplex_theater.config.CastConfigLoader;
import com.jyuzawa.googolplex_theater.mdns.ServiceDiscovery;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import io.vertx.core.Vertx;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * This is the main class for application.
 *
 * @author jyuzawa
 */
@Command(
    name = "googolplex-theater",
    mixinStandardHelpOptions = true,
    versionProvider = GoogolplexTheater.class)
public final class GoogolplexTheater implements Callable<Integer>, IVersionProvider {
  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexTheater.class);

  private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");
  private static final File DEFAULT_CAST_CONFIG = getDefaultCastConfig();
  private static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";
  private static final List<String> DIAGNOSTIC_PROPERTIES =
      Collections.unmodifiableList(
          Arrays.asList("os.name", "os.version", "os.arch", "java.vendor", "java.version"));

  @Spec CommandSpec spec;

  @Option(
      names = {"-a", "--app-id"},
      description = "cast application ID",
      paramLabel = "ABCDEFGH")
  String appId = GoogolplexClientHandler.DEFAULT_APPLICATION_ID;

  @Option(
      names = {"-c", "--cast-config"},
      description = "cast config JSON file",
      paramLabel = "FILE")
  File castConfigFile = DEFAULT_CAST_CONFIG;

  @Option(
      names = {"-i", "--interface"},
      description = "network interface name or IP address to use for service discovery",
      paramLabel = "IFACE_NAME_OR_IP")
  String preferredInterface;

  @Option(
      names = {"-p", "--port"},
      description = "server port number",
      paramLabel = "PORT_NUM")
  int serverPort = GoogolplexServer.DEFAULT_PORT;

  @Override
  public Integer call() throws Exception {
    validate();
    Vertx vertx = Vertx.vertx();
    GoogolplexController controller =
        new GoogolplexControllerImpl(vertx.nettyEventLoopGroup(), appId);
    vertx.deployVerticle(new GoogolplexServer(controller, serverPort));
    CastConfigLoader configLoader = new CastConfigLoader(controller, castConfigFile.toPath());
    ServiceDiscovery serviceDiscovery = new ServiceDiscovery(controller, preferredInterface);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Shutting down Googolplex Theater!");
                  try {
                    configLoader.close();
                    serviceDiscovery.close();
                    vertx.close();
                  } catch (Exception e) {
                    LOG.warn("Failed to shut down", e);
                  }
                }));
    return 0;
  }

  void validate() {
    if (!APP_ID_PATTERN.matcher(appId).find()) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
    }
    if (!castConfigFile.exists() || !castConfigFile.isFile()) {
      throw new CommandLine.ParameterException(
          spec.commandLine(),
          "cast-config file does not exist: " + castConfigFile.getAbsolutePath());
    }
    // print some diagnostic information
    LOG.info("Starting up Googolplex Theater!");
    LOG.info("Website: " + PROJECT_WEBSITE);
    String[] version = getVersion();
    LOG.info("Version: {} ({})", version[0], version[1]);
    for (String property : DIAGNOSTIC_PROPERTIES) {
      LOG.info("Runtime[{}]: {}", property, System.getProperty(property));
    }
  }

  static File getDefaultCastConfig() {
    // NOTE: gradle does not expose APP_HOME, but they do expose OLDPWD.
    // Default allows the application to run in IDE
    String appHome = System.getenv().getOrDefault("OLDPWD", "src/dist");
    return Paths.get(appHome + "/" + CastConfigLoader.DEFAULT_PATH).toAbsolutePath().toFile();
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new GoogolplexTheater()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public String[] getVersion() {
    Package thePackage = GoogolplexTheater.class.getPackage();
    return new String[] {
      thePackage.getSpecificationVersion(), thePackage.getImplementationVersion()
    };
  }
}
