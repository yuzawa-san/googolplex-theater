/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater.mdns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ServiceDiscoveryTest {

    @Test
    void preferredInterfaceTest() throws Exception {
        InetAddress auto = ServiceDiscovery.getInterfaceAddress(null);
        assertNotNull(auto);
        NetworkInterface iface = NetworkInterface.getByInetAddress(auto);
        assertNotNull(iface);
        String name = iface.getName();
        assertEquals(auto, ServiceDiscovery.getInterfaceAddress(name));
        String ipAddress = auto.getHostAddress();
        assertEquals(auto, ServiceDiscovery.getInterfaceAddress(ipAddress));
    }

    @Test
    void instantiationTest() throws IOException {
        GoogolplexController controller = Mockito.mock(GoogolplexController.class);
        ServiceDiscovery sd = new ServiceDiscovery(controller, null);
        sd.close();
    }
}
