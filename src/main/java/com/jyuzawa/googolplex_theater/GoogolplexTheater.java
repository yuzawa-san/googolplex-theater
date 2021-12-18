package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.client.GoogolplexControllerImpl;
import com.jyuzawa.googolplex_theater.config.DeviceConfigLoader;
import com.jyuzawa.googolplex_theater.config.GoogolplexTheaterConfig;
import com.jyuzawa.googolplex_theater.mdns.ServiceDiscovery;
import com.jyuzawa.googolplex_theater.server.GoogolplexServer;
import io.netty.channel.EventLoopGroup;
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

  public static void main(String[] args) {
    try {
      GoogolplexTheaterConfig config = GoogolplexTheaterConfig.load();
      Vertx vertx = Vertx.vertx();
      EventLoopGroup eventLoopGroup = vertx.nettyEventLoopGroup();
      GoogolplexController controller =
          new GoogolplexControllerImpl(eventLoopGroup, config.getRecieverAppId());
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
      DeviceConfigLoader configLoader =
          new DeviceConfigLoader(controller, config.getDeviceConfigPath());
      ServiceDiscovery serviceDiscovery =
          new ServiceDiscovery(controller, config.getDiscoveryNetworkInterface());
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
    } catch (Exception e) {
      LOG.error("Failed to start", e);
      System.exit(1);
    }
  }
}
