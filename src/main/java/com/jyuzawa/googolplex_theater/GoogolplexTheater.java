/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * All rights reserved. Licensed under the MIT License.
 */
package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.config.GoogolplexTheaterConfig;
import java.io.IOException;
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
    public GoogolplexTheaterConfig config() throws IOException {
        return GoogolplexTheaterConfig.load();
    }

    public static void main(String[] args) {
        SpringApplication.run(GoogolplexTheater.class, args);
    }
}
