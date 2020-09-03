package com.jyuzawa.googolplex_theater.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

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
    StringBuilder out = new StringBuilder();
    long deltaSeconds = Duration.between(instant, Instant.now()).getSeconds();
    long seconds = deltaSeconds;
    long days = seconds / 86400L;
    if (deltaSeconds > 86400L) {
      out.append(days).append("d");
    }
    seconds %= 86400L;

    long hours = seconds / 3600L;
    if (deltaSeconds > 3600L) {
      out.append(hours).append("h");
    }
    seconds %= 3600L;

    long minutes = seconds / 60L;
    if (deltaSeconds > 60L) {
      out.append(minutes).append("m");
    }
    seconds %= 60L;

    out.append(seconds).append("s");
    return out.toString();
  }

  @Override
  public int compareTo(DeviceStatus o) {
    return name.compareTo(o.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (o == this) {
      return true;
    }
    if (!(o instanceof DeviceStatus)) {
      return false;
    }
    DeviceStatus other = (DeviceStatus) o;
    return Objects.equals(name, other.name);
  }
}
