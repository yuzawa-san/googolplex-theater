package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.client.GoogolplexControllerImpl;
import com.jyuzawa.googolplex_theater.config.CastConfigLoader;
import com.jyuzawa.googolplex_theater.config.Config;
import com.jyuzawa.googolplex_theater.mdns.ServiceDiscovery;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import io.vertx.core.Vertx;
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

  public static void main(String[] args) {
    try {
      Config config = new Config(args);
      LOG.info("Starting up Googolplex Theater!");
      LOG.info("Website: " + PROJECT_WEBSITE);
      Vertx vertx = Vertx.vertx();
      GoogolplexController controller =
          new GoogolplexControllerImpl(vertx.nettyEventLoopGroup(), config.getAppId());
      vertx.deployVerticle(new GoogolplexServer(controller, config.getServerPort()));
      CastConfigLoader configLoader = new CastConfigLoader(controller, config.getCastConfigPath());
      ServiceDiscovery serviceDiscovery =
          new ServiceDiscovery(controller, config.getInterfaceAddress());
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
