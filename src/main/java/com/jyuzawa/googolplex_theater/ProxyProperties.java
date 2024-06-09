/*
 * Copyright (c) 2024 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import java.util.List;
import java.util.Map;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Configuration
@ConfigurationProperties(prefix = "googolplex-theater.proxy")
public class ProxyProperties {
    public String url;
    public int port = 8081;
    public Map<String, String> addRequestHeaders = Map.of();
    public List<String> removeResponseHeaders = List.of();
}
