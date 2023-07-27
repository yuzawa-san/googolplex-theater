/*
 * Copyright (c) 2023 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import io.netty.util.CharsetUtil;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@CucumberContextConfiguration
@ActiveProfiles("testing")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class GoogolplexTheaterTesting {

    @Autowired
    public WebTestClient webTestClient;

    @TestConfiguration
    static class MyTestConfiguration {

        @Bean
        @Primary
        public Path deviceConfigPath() throws IOException {
            FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                    .setWatchServiceConfiguration(WatchServiceConfiguration.polling(10, TimeUnit.MILLISECONDS))
                    .build());
            Path confPath = fs.getPath("/conf");
            Files.createDirectory(confPath);
            Path out = confPath.resolve("devices.yml");
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(
                    out, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                bufferedWriter.write("settings:\n  foo: bar");
            }
            return out;
        }
    }

    public void run() {
        webTestClient.get().uri("/devices").exchange().expectStatus().is2xxSuccessful();
    }
}
