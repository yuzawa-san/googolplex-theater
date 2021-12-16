package com.jyuzawa.googolplex_theater.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This is a POJO for YAML deserialization. This class represents a collection of named devices and
 * their settings. The settings is a generic JsonNode to allow for flexibility. The settings will be
 * conveyed to the receiver application verbatim.
 *
 * @author jyuzawa
 */
public final class DeviceConfig {
  public final List<DeviceInfo> devices;

  @JsonCreator
  public DeviceConfig(@JsonProperty("devices") List<DeviceInfo> devices) {
    if (devices == null) {
      this.devices = Collections.emptyList();
    } else {
      this.devices = Collections.unmodifiableList(devices);
    }
  }

  public static final class DeviceInfo {
    public final String name;
    public final JsonNode settings;

    @JsonCreator
    public DeviceInfo(
        @JsonProperty("name") String name, @JsonProperty("settings") JsonNode settings) {
      this.name = name;
      this.settings = settings;
    }

    @Override
    /**
     * This method is used to determine if the device name and settings have changed, which will
     * trigger a reload. It leans heavily on JsonNode.equals() implementation.
     */
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (o == this) {
        return true;
      }
      if (!(o instanceof DeviceInfo)) {
        return false;
      }
      DeviceInfo other = (DeviceInfo) o;
      return Objects.equals(name, other.name) && Objects.equals(settings, other.settings);
    }

    @Override
    public int hashCode() {
      return name.hashCode() + settings.hashCode();
    }
  }
}
