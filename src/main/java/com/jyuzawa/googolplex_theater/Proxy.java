/*
 * Copyright (c) 2024 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.ReferenceCountUtil;
import java.time.Duration;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

@Slf4j
@Component
@ConditionalOnProperty("googolplex-theater.proxy.url")
public class Proxy {

    private static final List<CharSequence> BLOCKED_REQUEST_HEADERS = List.of(
            HttpHeaderNames.CONNECTION,
            HttpHeaderNames.HOST,
            HttpHeaderNames.UPGRADE,
            HttpHeaderNames.ORIGIN,
            HttpHeaderNames.ACCEPT_ENCODING,
            "Sec-WebSocket-Version",
            "Sec-WebSocket-Key",
            "Sec-WebSocket-Extensions");

    private static final List<CharSequence> BLOCKED_RESPONSE_HEADERS = List.of("X-Frame-Options");

    private final ProxyProperties properties;
    private final HttpClient httpClient;
    private final HttpServer httpServer;
    private DisposableServer disposableServer;

    public Proxy(ProxyProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.create().baseUrl(properties.url);
        this.httpServer = HttpServer.create().port(properties.port).handle(this::handle);
    }

    @PostConstruct
    public void start() {
        log.info("Starting proxy on port {}", properties.port);
        disposableServer = httpServer.bindNow();
    }

    private Publisher<Void> handle(HttpServerRequest req, HttpServerResponse res) {
        if (req.requestHeaders().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)) {
            return handleWebsocket(req, res);
        }
        return handleHttp(req, res);
    }

    private HttpClient configureHttpClient(HttpServerRequest req) {
        return httpClient.headers(h -> {
            h.set(req.requestHeaders());
            for (CharSequence name : BLOCKED_REQUEST_HEADERS) {
                h.remove(name);
            }
            properties.addRequestHeaders.forEach((k, v) -> h.set(k, v));
        });
    }

    private Publisher<Void> handleWebsocket(HttpServerRequest req, HttpServerResponse res) {
        return res.sendWebsocket((fromBrowser, toBrowser) -> configureHttpClient(req)
                .websocket()
                .uri(req.uri())
                .handle((fromServer, toServer) -> Mono.when(
                                toBrowser
                                        .sendObject(fromServer.receiveFrames().doOnNext(ReferenceCountUtil::retain))
                                        .then(),
                                toServer.sendObject(fromBrowser.receiveFrames().doOnNext(ReferenceCountUtil::retain)))
                        .then()));
    }

    private Publisher<Void> handleHttp(HttpServerRequest req, HttpServerResponse res) {
        return configureHttpClient(req)
                .request(req.method())
                .uri(req.uri())
                .send(req.receive().doOnNext(ByteBuf::retain))
                .response((r, body) ->
                        res.status(r.status()).headers(stripResponseHeaders(r)).send(body.doOnNext(ByteBuf::retain)));
    }

    private HttpHeaders stripResponseHeaders(HttpClientResponse res) {
        HttpHeaders headers = res.responseHeaders();
        for (CharSequence name : properties.removeResponseHeaders) {
            headers.remove(name);
        }
        for (CharSequence name : BLOCKED_RESPONSE_HEADERS) {
            headers.remove(name);
        }
        return headers;
    }

    @PreDestroy
    public void stop() {
        if (disposableServer != null) {
            disposableServer.disposeNow(Duration.ofSeconds(10));
        }
    }
}
