/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * All rights reserved. Licensed under the MIT License.
 */
package com.jyuzawa.googolplex_theater.config;

import com.jyuzawa.googolplex_theater.GoogolplexTheater;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.util.MapperUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * A representation of the application config.
 *
 * @author jtyuzawa
 */
@Slf4j
@Data
public final class GoogolplexTheaterConfig {
    private static final String PROJECT_WEBSITE = "https://github.com/yuzawa-san/googolplex-theater";
    private static final List<String> DIAGNOSTIC_PROPERTIES = Collections.unmodifiableList(
            Arrays.asList("os.name", "os.version", "os.arch", "java.vendor", "java.version"));

    static {
        log.info("Starting up Googolplex Theater!");
        log.info("Website: " + PROJECT_WEBSITE);
        Package thePackage = GoogolplexTheater.class.getPackage();
        log.info("Version: {} ({})", thePackage.getSpecificationVersion(), thePackage.getImplementationVersion());
        for (String property : DIAGNOSTIC_PROPERTIES) {
            log.info("Runtime[{}]: {}", property, System.getProperty(property));
        }
    }

    static final String ALL_HOSTS = "*";
    private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");
    private static final String CONFIG_FILE_NAME = "config.yml";

    private final String recieverAppId;
    private final String discoveryNetworkInterface;
    private final InetSocketAddress uiServerAddress;
    private final Path deviceConfigPath;
    private final int baseReconnectSeconds;
    private final int reconnectNoiseSeconds;
    private final int heartbeatIntervalSeconds;
    private final int heartbeatTimeoutSeconds;

    GoogolplexTheaterConfig(Path basePath, ConfigYaml config) {
        this.recieverAppId = config.receiverAppId;
        if (!APP_ID_PATTERN.matcher(recieverAppId).find()) {
            throw new IllegalArgumentException("Invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
        }
        this.discoveryNetworkInterface = config.discoveryNetworkInterface;
        if (ALL_HOSTS.equals(config.uiServerHost)) {
            this.uiServerAddress = new InetSocketAddress(config.uiServerPort);
        } else {
            this.uiServerAddress = new InetSocketAddress(config.uiServerHost, config.uiServerPort);
        }
        this.deviceConfigPath = basePath.resolve(config.deviceConfigFile).toAbsolutePath();
        if (!Files.isRegularFile(deviceConfigPath)) {
            throw new IllegalArgumentException("Devices file does not exist: " + deviceConfigPath);
        }
        this.baseReconnectSeconds = config.baseReconnectSeconds;
        this.reconnectNoiseSeconds = config.reconnectNoiseSeconds;
        this.heartbeatIntervalSeconds = config.heartbeatIntervalSeconds;
        this.heartbeatTimeoutSeconds = config.heartbeatTimeoutSeconds;
    }

    public static GoogolplexTheaterConfig load() throws IOException {
        return load(getConfDirectory());
    }

    public static GoogolplexTheaterConfig load(Path basePath) throws IOException {
        Path path = basePath.resolve(CONFIG_FILE_NAME).toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Config file does not exist: " + path);
        }
        try (InputStream stream = Files.newInputStream(path)) {
            ConfigYaml config = MapperUtil.YAML_MAPPER.readValue(stream, ConfigYaml.class);
            return new GoogolplexTheaterConfig(basePath, config);
        }
    }

    static Path getConfDirectory() {
        try {
            // NOTE: gradle does not expose APP_HOME, so we need to be creative
            Path installedPath = Paths.get(GoogolplexTheater.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .resolve("../../conf")
                    .normalize()
                    .toAbsolutePath();
            if (installedPath.toFile().exists()) {
                return installedPath;
            }
        } catch (URISyntaxException e) {
            log.error("Failed to find default config", e);
        }
        // IDE case
        return Paths.get("src/dist/conf").toAbsolutePath();
    }

    @Data
    public static final class ConfigYaml {
        private static final int DEFAULT_BASE_RECONNECT_SECONDS = 15;
        private static final int DEFAULT_RECONNECT_NOISE_SECONDS = 5;
        private static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 5;
        private static final int DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 30;

        private String receiverAppId = GoogolplexClientHandler.DEFAULT_APPLICATION_ID;
        private String uiServerHost = ALL_HOSTS;
        private int uiServerPort = 8000;
        private String discoveryNetworkInterface;
        private String deviceConfigFile = "devices.yml";
        private int baseReconnectSeconds = DEFAULT_BASE_RECONNECT_SECONDS;
        private int reconnectNoiseSeconds = DEFAULT_RECONNECT_NOISE_SECONDS;
        private int heartbeatIntervalSeconds = DEFAULT_HEARTBEAT_INTERVAL_SECONDS;
        private int heartbeatTimeoutSeconds = DEFAULT_HEARTBEAT_TIMEOUT_SECONDS;
    }
}
