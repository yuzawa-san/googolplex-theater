/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;

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
        GoogolplexService controller = Mockito.mock(GoogolplexService.class);
        ServiceDiscovery sd = new ServiceDiscovery(controller, null, true);
        WebServerInitializedEvent event = Mockito.mock(WebServerInitializedEvent.class);
        WebServer webServer = Mockito.mock(WebServer.class);
        Mockito.when(event.getWebServer()).thenReturn(webServer);
        Mockito.when(webServer.getPort()).thenReturn(8080);
        sd.onApplicationEvent(event);
        sd.close();
    }
}
