package com.jyuzawa.googolplex_theater.config;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import java.io.Closeable;
import java.io.IOException;
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
 * This class loads the cast config at start and watches the files for subsequent changes. The
 * controller is notified of such changes.
 *
 * @author jyuzawa
 */
public final class CastConfigLoader implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(CastConfigLoader.class);

  public static final String DEFAULT_PATH = "conf/cast_config.json";

  private final ExecutorService executor;
  private final Path path;
  private final CastConfig initialConfig;
  private final WatchService watchService;

  public CastConfigLoader(Path path) throws IOException {
    this.path = path;
    this.executor = Executors.newSingleThreadExecutor();
    LOG.info("Using cast config: {}", path);
    this.initialConfig = CastConfig.fromPath(path);
    this.watchService = path.getFileSystem().newWatchService();
  }

  public CastConfig getInitialConfig() {
    return initialConfig;
  }

  public void watch(GoogolplexController controller) throws IOException {
    controller.processDevices(initialConfig.devices);
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
                    LOG.info("Reloading cast config");
                    controller.processDevices(CastConfig.fromPath(path).devices);
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

  @Override
  public void close() throws IOException {
    executor.shutdownNow();
    watchService.close();
  }
}
