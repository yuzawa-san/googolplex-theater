/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.jmdns.impl.util.NamedThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This class loads the device config at start and watches the files for subsequent changes. The
 * controller is notified of such changes.
 *
 * @author jyuzawa
 */
@Slf4j
@Component
public final class DeviceConfigLoader implements Closeable {

    private final ExecutorService executor;
    private final Path path;
    private final Path directoryPath;
    private WatchService watchService;
    private final GoogolplexController controller;

    @Autowired
    public DeviceConfigLoader(
            GoogolplexController controller, @Value("${googolplexTheater.devicesPath}") Path deviceConfigPath)
            throws IOException {
        this.controller = controller;
        this.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("deviceConfigLoader"));
        this.path = deviceConfigPath;
        log.info("Using device config: {}", deviceConfigPath.toAbsolutePath());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Config file does not exist: " + path);
        }
        this.directoryPath = path.getParent();
        if (directoryPath == null) {
            throw new IllegalArgumentException("Path has missing parent");
        }
    }

    @PostConstruct
    public void start() throws IOException {
        load();
        this.watchService = path.getFileSystem().newWatchService();
        directoryPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        executor.submit(() -> {
            try {
                WatchKey key;
                // this blocks until the system notifies us of any changes.
                while ((key = watchService.take()) != null) {
                    try {
                        // go thru all changes. sadly this API is not super type safe.
                        for (WatchEvent<?> event : key.pollEvents()) {
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            /*
                             * we could have found out about any file in the same directory, so make sure that it is
                             * indeed our config file.
                             */
                            if (path.endsWith(ev.context())) {
                                load();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to load config", e);
                    } finally {
                        key.reset();
                    }
                }
            } catch (ClosedWatchServiceException | InterruptedException e) {
                log.debug("config watch interrupted");
            } catch (Exception e) {
                log.error("Failed to watch device config file", e);
            }
        });
    }

    /**
     * Read the file, decode the file content, and inform the controller of the changes.
     *
     * @throws IOException when YAML deserialization fails
     */
    private void load() throws IOException {
        log.info("Reloading device config");
        try (InputStream stream = Files.newInputStream(path)) {
            DeviceConfig out = MapperUtil.YAML_MAPPER.readValue(stream, DeviceConfig.class);
            controller.processDeviceConfig(out);
        }
    }

    @Override
    public void close() throws IOException {
    	if (watchService != null) {
            watchService.close();
        }
        executor.close();
    }
}
