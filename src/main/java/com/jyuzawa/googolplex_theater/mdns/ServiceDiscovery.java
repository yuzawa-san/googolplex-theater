package com.jyuzawa.googolplex_theater.mdns;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import lombok.extern.slf4j.Slf4j;

/**
 * This class starts a listener for nearby devices and informs the controller of any changes.
 *
 * @author jyuzawa
 */
@Slf4j
public final class ServiceDiscovery implements Closeable {
  public static final String MDNS_SERVICE_NAME = "_googlecast._tcp.local.";

  private final GoogolplexController controller;
  private final JmDNS mdns;

  public ServiceDiscovery(GoogolplexController controller, String preferredInterface)
      throws IOException {
    this.controller = controller;
    InetAddress inetAddress = getInterfaceAddress(preferredInterface);
    if (inetAddress == null) {
      log.warn("No IP address for service discovery found. Falling back to JmDNS library default.");
    }
    this.mdns = JmDNS.create(inetAddress);
    log.info("Search for casts using {}", mdns.getInetAddress());
    this.mdns.addServiceListener(MDNS_SERVICE_NAME, new ServiceDiscoveryListener());
  }

  public static InetAddress getInterfaceAddress(String preferredInterface)
      throws SocketException, UnknownHostException {
    if (preferredInterface != null) {
      return getPreferredInterfaceAddress(preferredInterface);
    }
    List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
    InetAddress bestIpAddress = null;
    for (NetworkInterface iface : interfaces) {
      InetAddress ipAddress = getBestInetAddress(iface);
      if (bestIpAddress == null && ipAddress != null) {
        bestIpAddress = ipAddress;
      }
    }
    return bestIpAddress;
  }

  private static InetAddress getBestInetAddress(NetworkInterface iface) throws SocketException {
    List<InetAddress> ipAddresses = Collections.list(iface.getInetAddresses());
    if (!iface.isUp()
        || !iface.supportsMulticast()
        || iface.isLoopback()
        || iface.isPointToPoint()) {
      return null;
    }
    log.info("Found network interface {} - {}", iface, ipAddresses);
    for (InetAddress ipAddress : ipAddresses) {
      if (!ipAddress.isLoopbackAddress()
          && !ipAddress.isLinkLocalAddress()
          && ipAddress.isSiteLocalAddress()) {
        return ipAddress;
      }
    }
    return null;
  }

  private static InetAddress getPreferredInterfaceAddress(String preferredInterface)
      throws SocketException, UnknownHostException {
    // try by name
    NetworkInterface iface = NetworkInterface.getByName(preferredInterface);
    if (iface != null) {
      return getBestInetAddress(iface);
    }
    // try by ip address / hostname
    return InetAddress.getByName(preferredInterface);
  }

  @Override
  public void close() throws IOException {
    mdns.close();
  }

  private class ServiceDiscoveryListener implements ServiceListener {

    @Override
    public void serviceAdded(ServiceEvent event) {
      // pass
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
      /*
       * NOTE: it is hard to determine if something is permanently disconnected. empirically, we do get a lot of
       * these events when the device is not actually disconnected.
       */
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      controller.register(event);
    }
  }
}
