package com.jyuzawa.googolplex_theater.server;

import com.jyuzawa.googolplex_theater.GoogolplexTheater;
import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import java.net.InetSocketAddress;
import lombok.extern.slf4j.Slf4j;

/**
 * This class runs the server for the web UI.
 *
 * @author jyuzawa
 */
@Slf4j
public final class GoogolplexServer extends AbstractVerticle {

  private final GoogolplexController controller;
  private final SocketAddress address;

  public GoogolplexServer(GoogolplexController controller, InetSocketAddress address) {
    this.controller = controller;
    log.info("Running web-ui server on " + address);
    this.address = SocketAddress.inetSocketAddress(address);
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Router router = Router.router(vertx);
    HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create(vertx);
    Package thePackage = GoogolplexTheater.class.getPackage();
    router
        .get("/")
        .handler(
            ctx -> {
              JsonObject data = new JsonObject();
              data.put("devices", controller.getDeviceInfo());
              data.put("tag", thePackage.getSpecificationVersion());
              engine.render(
                  data,
                  "templates/overview.hbs",
                  res -> {
                    if (res.succeeded()) {
                      ctx.response().end(res.result());
                    } else {
                      ctx.fail(res.cause());
                    }
                  });
            });
    router
        .post("/refresh")
        .handler(
            ctx -> {
              HttpServerRequest request = ctx.request();
              request
                  .setExpectMultipart(true)
                  .endHandler(
                      req -> {
                        String name = request.formAttributes().get("name");
                        controller.refresh(name);
                        JsonObject data = new JsonObject();
                        if (name == null) {
                          name = "All Devices";
                        }
                        data.put("name", name);
                        data.put("tag", thePackage.getSpecificationVersion());
                        engine.render(
                            data,
                            "templates/refresh.hbs",
                            res -> {
                              if (res.succeeded()) {
                                ctx.response().end(res.result());
                              } else {
                                ctx.fail(res.cause());
                              }
                            });
                      });
            });
    router
        .get("/favicon.png")
        .handler(
            ctx -> {
              ctx.response().putHeader("content-type", "image/png").sendFile("favicon.png");
            });
    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(
            address,
            res -> {
              if (res.succeeded()) {
                startPromise.complete();
              } else {
                startPromise.fail(res.cause());
              }
            });
  }
}
