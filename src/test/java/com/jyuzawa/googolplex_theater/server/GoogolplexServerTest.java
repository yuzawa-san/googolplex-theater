package com.jyuzawa.googolplex_theater.server;

import static org.junit.jupiter.api.Assertions.*;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
class GoogolplexServerTest {
  static final GoogolplexController controller = Mockito.mock(GoogolplexController.class);

  @BeforeEach
  void setup(Vertx vertx, VertxTestContext testContext) {
    JsonObject device = new JsonObject();
    device.put("name", "my device");
    List<JsonObject> devices = Collections.singletonList(device);
    Mockito.when(controller.getDeviceInfo()).thenReturn(devices);
    GoogolplexServer server = new GoogolplexServer(controller, 9001);
    vertx.deployVerticle(server, testContext.completing());
  }

  @Test
  void indexTest(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);
    client
        .get(9001, "localhost", "/")
        .as(BodyCodec.string())
        .send(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          Mockito.verify(controller).getDeviceInfo();
                          assertTrue(response.body().contains("my device"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void refreshTest(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    form.add("name", "my device");
    client
        .post(9001, "localhost", "/refresh")
        .as(BodyCodec.string())
        .sendForm(
            form,
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          Mockito.verify(controller).refresh("my device");
                          assertTrue(response.body().contains("my device refreshing..."));
                          testContext.completeNow();
                        })));
  }

  @Test
  void refreshAllTest(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    client
        .post(9001, "localhost", "/refresh")
        .as(BodyCodec.string())
        .sendForm(
            form,
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          Mockito.verify(controller).refresh(null);
                          assertTrue(response.body().contains("All Devices refreshing..."));
                          testContext.completeNow();
                        })));
  }
}
