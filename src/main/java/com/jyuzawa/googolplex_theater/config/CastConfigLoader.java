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

/**
 * This class loads the cast config at start and watches the files for subsequent changes. The controller is notified of
 * such changes.
 *
 * @author jyuzawa
 */
public final class CastConfigLoader implements Closeable {
	private static final Logger LOG = LoggerFactory.getLogger(CastConfigLoader.class);

	public static final String DEFAULT_PATH = "conf/cast_config.json";

	private final ExecutorService executor;
	private final Path path;
	private final GoogolplexController controller;

	public CastConfigLoader(GoogolplexController controller, Path castConfigPath) throws IOException {
		this.controller = controller;
		this.executor = Executors.newSingleThreadExecutor();
		this.path = castConfigPath;
		LOG.info("Using cast config: {}", castConfigPath.toAbsolutePath());
		load();
		executor.submit(() -> {
			try {
				/*
				 * the watch operation only works with directories, so we have to get the parent directory of the file.
				 */
				WatchService watchService = FileSystems.getDefault().newWatchService();
				Path directoryPath = path.getParent();
				directoryPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
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
				LOG.error("Failed to watch config file", e);
			}
		});
	}

	/**
	 * Read the file, decode the file content, and inform the controller of the changes.
	 *
	 * @throws IOException
	 *             when JSON deserialization fails
	 */
	private void load() throws IOException {
		LOG.info("Reloading cast config");
		CastConfig out = JsonUtil.MAPPER.readValue(path.toFile(), CastConfig.class);
		controller.accept(out);
	}

	@Override
	public void close() throws IOException {
		executor.shutdownNow();
	}
}
