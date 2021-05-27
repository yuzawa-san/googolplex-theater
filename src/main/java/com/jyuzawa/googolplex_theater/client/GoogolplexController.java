package com.jyuzawa.googolplex_theater.client;

import com.jyuzawa.googolplex_theater.config.DeviceInfo;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.jmdns.ServiceEvent;

/**
 * This class represents the state of the application. All modifications to state occur in the same
 * thread.
 *
 * @author jyuzawa
 */
public interface GoogolplexController {

  /**
   * Load the devices.
   *
   * @param devices the settings loaded from the file
   */
  void processDevices(List<DeviceInfo> devices);

  /**
   * Add a discovered device and initialize a new connection to the device if one does not exist
   * already.
   *
   * @param event mdns info
   * @throws ExecutionException
   * @throws InterruptedException
   */
  void register(ServiceEvent event);

  /** @return a list of device information, address, connection age */
  List<JsonObject> getDeviceInfo();

  /**
   * Trigger a refresh by closing channels which will cause a reconnect.
   *
   * @param name the device to refresh
   */
  void refresh(String name);
}
