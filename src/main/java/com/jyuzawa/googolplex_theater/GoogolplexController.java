/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * This class handles for the web UI.
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
    public String refresh(@ModelAttribute RefreshSpec spec, Model model) {
        String name = spec.name;
        service.refresh(name);
        model.addAttribute("name", name == null ? "All Devices" : name);
        return "main";
    }

    public record RefreshSpec(String name) {}
}
