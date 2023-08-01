/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public Path appHome(@Value("${googolplex-theater.app-home}") Path appHome) {
        return appHome;
    }

    public static void main(String[] args) throws Exception {
        Path appHome = Paths.get("src/dist").toAbsolutePath();
        try {
            Path jarPath = Paths.get(GoogolplexTheater.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (Files.isRegularFile(jarPath)) {
                appHome = jarPath.resolve("../../").normalize().toAbsolutePath();
                System.setProperty(
                        "spring.config.import",
                        appHome.resolve("./conf/config.yml").toString());
            }
        } catch (Exception e) {
            // pass
        }
        System.setProperty("googolplex-theater.app-home", appHome.toString());
        SpringApplication.run(GoogolplexTheater.class, args);
    }
}
