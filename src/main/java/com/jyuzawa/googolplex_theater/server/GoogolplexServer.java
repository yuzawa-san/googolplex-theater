/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater.server;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/blah")
    public List<Map<String, Object>> root() {
        return controller.getDeviceInfo();
        //      return "hello";
    }
}
