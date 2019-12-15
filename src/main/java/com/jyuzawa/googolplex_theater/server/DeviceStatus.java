package com.jyuzawa.googolplex_theater.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

public final class DeviceStatus implements Comparable<DeviceStatus> {

  public final String name;
  public final JsonNode settings;
  public final String ipAddress;
  public final String duration;

  public DeviceStatus(DeviceInfo deviceInfo, InetSocketAddress address, Channel channel) {
    this.name = deviceInfo.name;
    this.settings = deviceInfo.settings;
    if (address == null) {
      this.ipAddress = null;
    } else {
      this.ipAddress = address.getAddress().getHostAddress();
    }
    if (channel == null) {
      this.duration = null;
    } else {
      this.duration =
          calculateDuration(channel.attr(GoogolplexClientHandler.CONNECTION_BIRTH_KEY).get());
    }
  }

  private static String calculateDuration(Instant instant) {
    if (instant == null) {
      return null;
    }
    long deltaSeconds = Duration.between(instant, Instant.now()).getSeconds();
    long days = deltaSeconds / 86400L;
    deltaSeconds %= 86400L;
    long hours = deltaSeconds / 3600L;
    deltaSeconds %= 3600L;
    long minutes = deltaSeconds / 60L;
    deltaSeconds %= 60L;
    return String.format("<pre>%02dd%02dh%02dm%02ds</pre>", days, hours, minutes, deltaSeconds);
  }

  public DeviceStatus(String name, InetSocketAddress address) {
    this.name = name;
    this.settings = null;
    this.ipAddress = address.getAddress().getHostAddress();
    this.duration = null;
  }

  @Override
  public int compareTo(DeviceStatus o) {
    return name.compareTo(o.name);
  }
}
