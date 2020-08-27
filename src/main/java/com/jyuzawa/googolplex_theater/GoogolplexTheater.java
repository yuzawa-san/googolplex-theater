package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.config.CastConfigLoader;
import com.jyuzawa.googolplex_theater.config.Config;
import com.jyuzawa.googolplex_theater.mdns.ServiceDiscovery;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main class for application.
 *
 * @author jyuzawa
 */
public final class GoogolplexTheater {
  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexTheater.class);
  public static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";

  public static void main(String[] args) throws InterruptedException {
    try {
      Config config = new Config(args);
      LOG.info("Starting up Googolplex Theater!");
      LOG.info("Website: " + PROJECT_WEBSITE);
      GoogolplexController controller = new GoogolplexController(config.getAppId());
      GoogolplexServer server = new GoogolplexServer(controller, config.getServerPort());
      CastConfigLoader configLoader = new CastConfigLoader(controller, config.getCastConfigPath());
      ServiceDiscovery serviceDiscovery =
          new ServiceDiscovery(controller, config.getInterfaceAddress());
      // collect items to close on shutdown
      List<Closeable> tasks = Arrays.asList(configLoader, serviceDiscovery, server, controller);
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    LOG.info("Shutting down Googolplex Theater!");
                    for (Closeable task : tasks) {
                      try {
                        task.close();
                      } catch (Exception e) {
                        LOG.warn("Failed to shut down", e);
                      }
                    }
                  }));
    } catch (ParseException e) {
      System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("googolplex-theater", Config.OPTIONS, true);
      System.exit(1);
    } catch (Exception e) {
      LOG.error("Failed to start", e);
      System.exit(1);
    }
  }
}
