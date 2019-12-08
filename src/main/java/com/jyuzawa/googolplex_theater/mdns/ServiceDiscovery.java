package com.jyuzawa.googolplex_theater.mdns;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServiceDiscovery implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(ServiceDiscovery.class);

  public static final String MDNS_SERVICE_NAME = "_googlecast._tcp.local.";

  private final GoogolplexController controller;
  private final JmDNS mdns;

  public ServiceDiscovery(GoogolplexController controller, InetAddress interfaceAddress)
      throws IOException {
    this.controller = controller;
    this.mdns = JmDNS.create(interfaceAddress);
    LOG.info("Search for casts using {}", mdns.getInetAddress());
    this.mdns.addServiceListener(MDNS_SERVICE_NAME, new ServiceDiscoveryListener());
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
      // controller.unregister(event);
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      controller.register(event);
    }
  }
}
