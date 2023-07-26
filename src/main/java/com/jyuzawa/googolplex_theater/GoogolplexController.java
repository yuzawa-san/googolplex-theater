/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class runs the server for the web UI.
 *
 * @author jyuzawa
 */
@RequiredArgsConstructor
@RestController
public final class GoogolplexController {

    private final GoogolplexService service;

    @GetMapping("/devices")
    public List<Map<String, Object>> root() {
        return service.getDeviceInfo();
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll(@RequestBody RefreshSpec refreshSpec) {
        service.refresh(refreshSpec.name);
        return Map.of("status", "ok");
    }

    public record RefreshSpec(String name) {}
}
