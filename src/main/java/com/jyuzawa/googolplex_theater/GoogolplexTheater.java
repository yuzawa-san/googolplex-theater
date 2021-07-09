package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.client.GoogolplexControllerImpl;
import com.jyuzawa.googolplex_theater.config.CastConfig;
import com.jyuzawa.googolplex_theater.config.CastConfigLoader;
import com.jyuzawa.googolplex_theater.mdns.ServiceDiscovery;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import io.netty.channel.EventLoopGroup;
import io.vertx.core.Vertx;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;

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

  private static final File DEFAULT_CAST_CONFIG = getDefaultCastConfig();
  private static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";
  private static final List<String> DIAGNOSTIC_PROPERTIES =
      Collections.unmodifiableList(
          Arrays.asList("os.name", "os.version", "os.arch", "java.vendor", "java.version"));

  @Option(
      names = {"-c", "--cast-config"},
      description = "cast config JSON file",
      paramLabel = "FILE")
  File castConfigFile = DEFAULT_CAST_CONFIG;

  @Override
  public Integer call() throws Exception {
    printBanner();
    Vertx vertx = Vertx.vertx();
    EventLoopGroup eventLoopGroup = vertx.nettyEventLoopGroup();
    CastConfigLoader configLoader = new CastConfigLoader(castConfigFile.toPath().toAbsolutePath());
    CastConfig config = configLoader.getInitialConfig();
    GoogolplexController controller = new GoogolplexControllerImpl(eventLoopGroup, config.appId);
    configLoader.watch(controller);
    vertx.deployVerticle(new GoogolplexServer(controller, config.port));
    ServiceDiscovery serviceDiscovery = new ServiceDiscovery(controller, config.preferredInterface);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Shutting down Googolplex Theater!");
                  try {
                    controller.processDevices(Collections.emptyList());
                    configLoader.close();
                    serviceDiscovery.close();
                    vertx.close();
                  } catch (Exception e) {
                    LOG.warn("Failed to shut down", e);
                  }
                }));
    eventLoopGroup.terminationFuture().awaitUninterruptibly();
    return 0;
  }

  void printBanner() {
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
