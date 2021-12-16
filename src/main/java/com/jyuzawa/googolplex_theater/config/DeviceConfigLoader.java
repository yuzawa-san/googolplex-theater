package com.jyuzawa.googolplex_theater.config;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.util.MapperUtil;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class loads the device config at start and watches the files for subsequent changes. The
 * controller is notified of such changes.
 *
 * @author jyuzawa
 */
public final class DeviceConfigLoader implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(DeviceConfigLoader.class);

  private final ExecutorService executor;
  private final Path path;
  private final GoogolplexController controller;
  private final WatchService watchService;

  public DeviceConfigLoader(GoogolplexController controller, Path deviceConfigPath)
      throws IOException {
    this.controller = controller;
    this.executor = Executors.newSingleThreadExecutor();
    this.path = deviceConfigPath;
    LOG.info("Using device config: {}", deviceConfigPath.toAbsolutePath());
    load();
    this.watchService = path.getFileSystem().newWatchService();
    /*
     * the watch operation only works with directories, so we have to get the parent directory of the file.
     */
    Path directoryPath = path.getParent();
    if (directoryPath == null) {
      throw new IllegalArgumentException("Path has missing parent");
    }
    directoryPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
    executor.submit(
        () -> {
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
                LOG.error("Failed to load config", e);
              } finally {
                key.reset();
              }
            }
          } catch (InterruptedException e) {
            LOG.debug("config watch interrupted");
          } catch (Exception e) {
            LOG.error("Failed to watch device config file", e);
          }
        });
  }

  /**
   * Read the file, decode the file content, and inform the controller of the changes.
   *
   * @throws IOException when YAML deserialization fails
   */
  private void load() throws IOException {
    LOG.info("Reloading device config");
    try (InputStream stream = Files.newInputStream(path)) {
      DeviceConfig out = MapperUtil.YAML_MAPPER.readValue(stream, DeviceConfig.class);
      controller.processDeviceConfig(out);
    }
  }

  @Override
  public void close() throws IOException {
    controller.processDeviceConfig(new DeviceConfig());
    executor.shutdownNow();
    watchService.close();
  }
}
