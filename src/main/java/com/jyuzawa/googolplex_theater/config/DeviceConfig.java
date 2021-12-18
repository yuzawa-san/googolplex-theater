package com.jyuzawa.googolplex_theater.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jyuzawa.googolplex_theater.util.MapperUtil;
import java.util.ArrayList;
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

  public DeviceConfig() {
    this(null, null);
  }

  @JsonCreator
  public DeviceConfig(
      @JsonProperty("devices") List<DeviceInfo> devices,
      @JsonProperty("settings") ObjectNode settings) {
    if (devices == null) {
      this.devices = Collections.emptyList();
    } else {
      List<DeviceInfo> newDevices = new ArrayList<>(devices.size());
      for (DeviceInfo device : devices) {
        newDevices.add(device.merge(settings));
      }
      this.devices = Collections.unmodifiableList(newDevices);
    }
  }

  public static final class DeviceInfo {
    public final String name;
    public ObjectNode settings;

    @JsonCreator
    public DeviceInfo(
        @JsonProperty("name") String name, @JsonProperty("settings") ObjectNode settings) {
      this.name = name;
      this.settings = settings;
    }

    public DeviceInfo merge(ObjectNode settings) {
      if (settings == null) {
        return this;
      }
      ObjectNode newSettings = new ObjectNode(MapperUtil.YAML_MAPPER.getNodeFactory());
      newSettings.setAll(settings);
      newSettings.setAll(this.settings);
      return new DeviceInfo(name, newSettings);
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
