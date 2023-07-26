/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class runs the server for the web UI.
 *
 * @author jyuzawa
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public final class GoogolplexServer {

    private final GoogolplexController controller;

    @GetMapping("/devices")
    public List<Map<String, Object>> root() {
        return controller.getDeviceInfo();
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll(@RequestBody RefreshSpec refreshSpec) {
        controller.refresh(refreshSpec.name);
        return Map.of("status", "ok");
    }

    public record RefreshSpec(String name) {}
}
