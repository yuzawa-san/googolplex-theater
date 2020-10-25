package com.jyuzawa.googolplex_theater.mdns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.NetworkInterface;
import org.junit.jupiter.api.Test;

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
    NetworkInterface loopbackInterface =
        NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));
    assertNull(ServiceDiscovery.getInterfaceAddress(loopbackInterface.getName()));
    assertNull(ServiceDiscovery.getInterfaceAddress("localhost"));
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          ServiceDiscovery.getInterfaceAddress("jyuzawa.com");
        });
  }
}
