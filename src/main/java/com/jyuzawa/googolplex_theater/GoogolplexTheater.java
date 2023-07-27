/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * This is the main class for application.
 *
 * @author jyuzawa
 */
@SpringBootApplication
public class GoogolplexTheater {

    @Bean
    public Path deviceConfigPath(@Value("${googolplex-theater.devices-path}") Path deviceConfigPath) {
        return deviceConfigPath;
    }

    public static void main(String[] args) {
        SpringApplication.run(GoogolplexTheater.class, args);
    }
}
