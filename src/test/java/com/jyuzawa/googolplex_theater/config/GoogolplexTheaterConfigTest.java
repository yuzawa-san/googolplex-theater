/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater.config;

import static org.junit.jupiter.api.Assertions.*;

import com.jyuzawa.googolplex_theater.GoogolplexClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GoogolplexTheaterConfigTest {

    @Test
    void test() throws IOException {
        GoogolplexTheaterConfig config = GoogolplexTheaterConfig.load();
        assertEquals(GoogolplexClient.DEFAULT_APPLICATION_ID, config.getRecieverAppId());
        assertEquals(8000, config.getUiServerAddress().getPort());
        assertEquals("0.0.0.0", config.getUiServerAddress().getHostName());
        assertNull(config.getDiscoveryNetworkInterface());
        assertTrue(config.getDeviceConfigPath().toFile().exists());
    }

    @Test
    void appIdTest() {
        GoogolplexTheaterConfig.ConfigYaml config = new GoogolplexTheaterConfig.ConfigYaml();
        config.setReceiverAppId("ABCDEFGH");
        new GoogolplexTheaterConfig(GoogolplexTheaterConfig.getConfDirectory(), config);
        GoogolplexTheaterConfig.ConfigYaml config2 = new GoogolplexTheaterConfig.ConfigYaml();
        config2.setReceiverAppId("not-an-app-id");
        assertThrows(IllegalArgumentException.class, () -> {
            new GoogolplexTheaterConfig(GoogolplexTheaterConfig.getConfDirectory(), config2);
        });
    }

    @Test
    void missingDevicesTest() {
        GoogolplexTheaterConfig.ConfigYaml config = new GoogolplexTheaterConfig.ConfigYaml();
        config.setDeviceConfigFile("not/a/real/file.yml");
        assertThrows(IllegalArgumentException.class, () -> {
            new GoogolplexTheaterConfig(GoogolplexTheaterConfig.getConfDirectory(), config);
        });
    }
}
