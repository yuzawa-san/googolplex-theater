/*
 * Copyright (c) 2024 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class ProxyController {

    private static final List<String> BANNED_REQUEST_HEADERS = List.of(
            HttpHeaders.HOST,
            HttpHeaders.ACCEPT_ENCODING,
            HttpHeaders.CONNECTION,
            HttpHeaders.REFERER,
            HttpHeaders.ORIGIN,
            "Upgrade-Insecure-Requests",
            "CAST-DEVICE-CAPABILITIES");
    private static final Set<String> BANNED_RESPONSE_HEADERS = Set.of(
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.CONNECTION,
            HttpHeaders.TRANSFER_ENCODING,
            "Content-Security-Policy",
            "X-Frame-Options",
            "X-Xss-Protection",
            "X-Content-Type-Options",
            "Strict-Transport-Security");

    private final WebClient httpClient;

    @Autowired
    public ProxyController() {
        this.httpClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build();
    }

    private static final Pattern PATTERN = Pattern.compile("^/(.*)$");

    private static final String requestUri(ServerHttpRequest request) {
        return request.getURI().getRawPath() + "?"
                + defaultIfEmpty(request.getURI().getRawQuery(), "");
    }

    public static <T extends CharSequence> T defaultIfEmpty(final T str, final T defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }

    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    @RequestMapping("/**")
    public Mono<ResponseEntity<ByteBuf>> viewer(ServerHttpRequest request, @RequestBody(required = false) byte[] body) {
        String uri = requestUri(request);
        Matcher matcher = PATTERN.matcher(uri);
        if (!matcher.find()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, uri);
        }
        return run("", matcher.group(1), body, request);
    }

    public Mono<ResponseEntity<ByteBuf>> run(String ref, String path, byte[] body, ServerHttpRequest request) {
        URI uri = URI.create(String.format("https://example.com/%s", path));
        HttpHeaders originalHeaders = request.getHeaders();
        if (originalHeaders.containsKey(HttpHeaders.UPGRADE)) {
            return Mono.just(ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(Unpooled.copiedBuffer("upgrade not supported", StandardCharsets.UTF_8)));
        }
        HttpHeaders requestHeaders = new org.springframework.http.HttpHeaders();
        originalHeaders.forEach((k, v) -> {
            if (!BANNED_REQUEST_HEADERS.contains(k)) {
                requestHeaders.put(k, v);
            }
        });
        requestHeaders.set("Authorization", "Bearer blah");
        if (body == null) {
            body = Unpooled.EMPTY_BUFFER.array();
        }
        log.info("REQ " + uri + " " + requestHeaders);
        return httpClient
                .method(HttpMethod.valueOf(request.getMethod().name()))
                .uri(uri)
                .headers(h -> h.putAll(requestHeaders))
                .bodyValue(body)
                .retrieve()
                .toEntity(ByteBuf.class)
                .map(response -> ResponseEntity.status(response.getStatusCode())
                        .headers(httpHeaders -> {
                            HttpHeaders responseHeaders = response.getHeaders();
                            responseHeaders.forEach((k, v) -> {
                                if (!BANNED_RESPONSE_HEADERS.contains(k)) {
                                    httpHeaders.put(k, v);
                                }
                            });
                            log.info("RESP " + response.getStatusCode() + " " + uri + " " + responseHeaders);
                        })
                        .body(response.getBody()))
                .onErrorResume(e -> {
                    log.error(e.toString(), e);
                    return Mono.just(ResponseEntity.badRequest()
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(Unpooled.copiedBuffer(e.toString(), StandardCharsets.UTF_8)));
                });
    }
}
