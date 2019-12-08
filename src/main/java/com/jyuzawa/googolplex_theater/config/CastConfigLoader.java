package com.jyuzawa.googolplex_theater.config;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.util.JsonUtil;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CastConfigLoader implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(CastConfigLoader.class);

  public static final String DEFAULT_PATH = "cast_config.json";

  private final ExecutorService executor;
  private final Path path;
  private final GoogolplexController state;

  public CastConfigLoader(GoogolplexController state, Path castConfigPath) throws IOException {
    this.state = state;
    this.executor = Executors.newSingleThreadExecutor();
    this.path = castConfigPath;
    LOG.info("Using cast config: {}", castConfigPath.toAbsolutePath());
    load();
    executor.submit(
        () -> {
          try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path directoryPath = path.getParent();
            directoryPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            WatchKey key;
            while ((key = watchService.take()) != null) {
              try {
                for (WatchEvent<?> event : key.pollEvents()) {
                  @SuppressWarnings("unchecked")
                  WatchEvent<Path> ev = (WatchEvent<Path>) event;
                  if (path.endsWith(ev.context())) {
                    load();
                  }
                }
              } catch (Exception e) {
                LOG.error("Failed to load config", e);
              } finally {
                key.reset();
              }
            }
          } catch (InterruptedException e) {
            LOG.debug("config watch interrupted");
          } catch (Exception e) {
            LOG.error("Failed to watch config file", e);
          }
        });
  }

  private void load() throws IOException {
    LOG.info("Reloading cast config");
    CastConfig out = JsonUtil.MAPPER.readValue(path.toFile(), CastConfig.class);
    state.loadConfig(out);
  }

  @Override
  public void close() throws IOException {
    executor.shutdownNow();
  }
}
