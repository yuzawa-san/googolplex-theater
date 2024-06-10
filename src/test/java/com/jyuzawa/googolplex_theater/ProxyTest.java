/*
 * Copyright (c) 2024 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

class ProxyTest {

    private static DisposableServer server;
    private static Proxy proxy;

    @BeforeAll
    static void start() {
        server = HttpServer.create()
                .port(8082)
                .route(routes -> routes.get(
                                "/hello", (request, response) -> response.sendString(Mono.just("Hello World!")))
                        .post(
                                "/echo",
                                (request, response) ->
                                        response.send(request.receive().retain()))
                        .ws(
                                "/ws",
                                (wsInbound, wsOutbound) -> wsOutbound.sendObject(
                                        wsInbound.receiveFrames().doOnNext(ReferenceCountUtil::retain))))
                .bindNow();
        ProxyProperties proxyProperties = new ProxyProperties();
        proxyProperties.url = "http://localhost:8082/";
        proxyProperties.port = 8081;
        proxy = new Proxy(proxyProperties);
        proxy.start();
    }

    @AfterAll
    static void stop() {
        proxy.stop();
        server.disposeNow(Duration.ofSeconds(10));
    }

    @Test
    void test() {
        HttpClient httpClient = HttpClient.create().baseUrl("http://localhost:8081/");
        assertEquals(
                "Hello World!",
                httpClient
                        .get()
                        .uri("/hello")
                        .responseContent()
                        .aggregate()
                        .asString()
                        .block(Duration.ofSeconds(10)));
        assertEquals(
                "payload",
                httpClient
                        .post()
                        .uri("/echo")
                        .send(Mono.just(Unpooled.copiedBuffer("payload", StandardCharsets.UTF_8)))
                        .responseContent()
                        .aggregate()
                        .asString()
                        .block(Duration.ofSeconds(10)));
        List<String> payloads = List.of("foo", "bar", "baz");
        assertEquals(
                payloads,
                httpClient
                        .websocket()
                        .uri("/ws")
                        .handle((in, out) -> out.sendObject(Flux.fromIterable(payloads)
                                        .map(payload -> new TextWebSocketFrame(
                                                Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8))))
                                .then()
                                .thenMany(in.receiveFrames()
                                        .cast(TextWebSocketFrame.class)
                                        .flatMap(f -> {
                                            String value = f.text();
                                            if (value.equals(payloads.get(payloads.size() - 1))) {
                                                return out.sendClose().thenReturn(value);
                                            }
                                            return Mono.just(value);
                                        })
                                        .doOnNext(System.out::println)))
                        .collectList()
                        .block(Duration.ofSeconds(10)));
    }
}
