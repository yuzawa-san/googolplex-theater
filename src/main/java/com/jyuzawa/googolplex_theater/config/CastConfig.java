package com.jyuzawa.googolplex_theater.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This is a POJO for JSON deserialization. This class represents a collection of named devices and
 * their settings. The settings is a generic JsonNode to allow for flexibility. The settings will be
 * conveyed to the receiver application verbatim.
 *
 * @author jyuzawa
 */
public final class CastConfig {
  public List<DeviceInfo> devices = Collections.emptyList();

  public static final class DeviceInfo {
    public String name;
    public JsonNode settings;

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
  }
}
