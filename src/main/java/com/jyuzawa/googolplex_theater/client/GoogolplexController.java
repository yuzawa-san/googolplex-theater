/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * All rights reserved. Licensed under the MIT License.
 */
package com.jyuzawa.googolplex_theater.client;

import com.jyuzawa.googolplex_theater.config.DeviceConfig;
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
     * Load the device config and propagate the changes to the any currently connected devices.
     *
     * @param config the settings loaded from the file
     */
    void processDeviceConfig(DeviceConfig config);

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
