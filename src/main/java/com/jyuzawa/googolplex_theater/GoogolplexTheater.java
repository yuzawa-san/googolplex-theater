package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.client.GoogolplexControllerImpl;
import com.jyuzawa.googolplex_theater.config.DeviceConfigLoader;
import com.jyuzawa.googolplex_theater.config.GoogolplexTheaterConfig;
import com.jyuzawa.googolplex_theater.mdns.ServiceDiscovery;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main class for application.
 *
 * @author jyuzawa
 */
public final class GoogolplexTheater {
  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexTheater.class);

  private final Vertx vertx;
  private final DeviceConfigLoader configLoader;
  private final ServiceDiscovery serviceDiscovery;

  public GoogolplexTheater(GoogolplexTheaterConfig config) throws Exception {
    this.vertx = Vertx.vertx();
    GoogolplexController controller =
        new GoogolplexControllerImpl(vertx.nettyEventLoopGroup(), config);
    CompletableFuture<Boolean> serverFuture = new CompletableFuture<>();
    vertx.deployVerticle(
        new GoogolplexServer(controller, config.getUiServerAddress()),
        result -> {
          if (result.succeeded()) {
            serverFuture.complete(Boolean.TRUE);
          } else {
            serverFuture.completeExceptionally(result.cause());
          }
        });
    serverFuture.get(10, TimeUnit.SECONDS);
    this.configLoader = new DeviceConfigLoader(controller, config.getDeviceConfigPath());
    this.serviceDiscovery = new ServiceDiscovery(controller, config.getDiscoveryNetworkInterface());
  }

  ServiceDiscovery getServiceDiscovery() {
    return serviceDiscovery;
  }

  public void close() {
    LOG.info("Shutting down Googolplex Theater!");
    try {
      configLoader.close();
      serviceDiscovery.close();
      vertx.close();
    } catch (Exception e) {
      LOG.warn("Failed to shut down", e);
    }
  }

  public static void main(String[] args) {
    try {
      GoogolplexTheaterConfig config = GoogolplexTheaterConfig.load();
      GoogolplexTheater googolplexTheater = new GoogolplexTheater(config);
      Runtime.getRuntime().addShutdownHook(new Thread(googolplexTheater::close));
    } catch (Exception e) {
      LOG.error("Failed to start", e);
      System.exit(1);
    }
  }
}
