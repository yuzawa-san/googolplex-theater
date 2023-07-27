/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * This class runs the server for the web UI.
 *
 * @author jyuzawa
 */
@RequiredArgsConstructor
@Controller
public final class GoogolplexController {

    private final GoogolplexService service;

    @GetMapping("/")
    public String root(Model model) {
        model.addAttribute("devices", service.getDeviceInfo());
        return "index";
    }

    @PostMapping("/refresh")
    public String refresh(Model model, String name) {
        // service.refresh(name);
        service.getDeviceInfo();
        return "index";
    }
}
