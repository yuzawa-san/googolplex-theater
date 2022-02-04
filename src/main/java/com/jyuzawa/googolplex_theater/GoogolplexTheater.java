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
import lombok.extern.slf4j.Slf4j;

/**
 * This is the main class for application.
 *
 * @author jyuzawa
 */
@Slf4j
public final class GoogolplexTheater {
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

  public void close() {
    log.info("Shutting down Googolplex Theater!");
    try {
      configLoader.close();
      serviceDiscovery.close();
      vertx.close();
    } catch (Exception e) {
      log.warn("Failed to shut down", e);
    }
  }

  public static void main(String[] args) {
    try {
      GoogolplexTheaterConfig config = GoogolplexTheaterConfig.load();
      GoogolplexTheater googolplexTheater = new GoogolplexTheater(config);
      Runtime.getRuntime().addShutdownHook(new Thread(googolplexTheater::close));
    } catch (Exception e) {
      log.error("Failed to start", e);
      System.exit(1);
    }
  }
}
