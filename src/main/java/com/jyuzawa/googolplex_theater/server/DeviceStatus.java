package com.jyuzawa.googolplex_theater.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

/**
 * A representation of a device's name, settings, current address, and connection age.
 *
 * @author jyuzawa
 */
public final class DeviceStatus implements Comparable<DeviceStatus> {

  public final String name;
  public final JsonNode settings;
  public final String ipAddress;
  public final String duration;

  /**
   * Constructor for configured devices
   *
   * @param deviceInfo
   * @param address
   * @param channel
   */
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

  /**
   * Constructor for unconfigured devices
   *
   * @param instant
   * @return
   */
  public DeviceStatus(String name, InetSocketAddress address) {
    this.name = name;
    this.settings = null;
    this.ipAddress = address.getAddress().getHostAddress();
    this.duration = null;
  }

  /**
   * Generate a human readable connection age string.
   *
   * @param instant
   * @return
   */
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

  @Override
  public int compareTo(DeviceStatus o) {
    return name.compareTo(o.name);
  }
}
