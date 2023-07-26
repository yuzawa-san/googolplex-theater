/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.*;

import com.jyuzawa.googolplex_theater.GoogolplexController;
import com.jyuzawa.googolplex_theater.GoogolplexService;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
class GoogolplexServerTest {
    static final GoogolplexService controller = Mockito.mock(GoogolplexService.class);
    static final int port = 9101;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        JsonObject device = new JsonObject();
        device.put("name", "my device");
        List<JsonObject> devices = Collections.singletonList(device);
        Mockito.when(controller.getDeviceInfo()).thenReturn(devices);
        GoogolplexController server = new GoogolplexController(controller, new InetSocketAddress(port));
        vertx.deployVerticle(server, testContext.succeedingThenComplete());
    }

    @Test
    void indexTest(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", "/")
                .as(BodyCodec.string())
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                    Mockito.verify(controller).getDeviceInfo();
                    assertEquals(200, response.statusCode());
                    assertTrue(response.body().contains("my device"));
                    testContext.completeNow();
                })));
    }

    @Test
    void refreshTest(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("name", "my device");
        client.post(port, "localhost", "/refresh")
                .as(BodyCodec.string())
                .sendForm(
                        form,
                        testContext.succeeding(response -> testContext.verify(() -> {
                            Mockito.verify(controller).refresh("my device");
                            assertEquals(200, response.statusCode());
                            assertTrue(response.body().contains("my device refreshing..."));
                            testContext.completeNow();
                        })));
    }

    @Test
    void refreshAllTest(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        client.post(port, "localhost", "/refresh")
                .as(BodyCodec.string())
                .sendForm(
                        form,
                        testContext.succeeding(response -> testContext.verify(() -> {
                            Mockito.verify(controller).refresh(null);
                            assertEquals(200, response.statusCode());
                            assertTrue(response.body().contains("All Devices refreshing..."));
                            testContext.completeNow();
                        })));
    }

    @Test
    void faviconTest(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", "/favicon.png")
                .as(BodyCodec.buffer())
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                    assertEquals(200, response.statusCode());
                    assertTrue(response.body().length() > 0);
                    testContext.completeNow();
                })));
    }
}
