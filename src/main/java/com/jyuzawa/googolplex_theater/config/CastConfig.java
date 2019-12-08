package com.jyuzawa.googolplex_theater.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CastConfig {
  public List<DeviceInfo> devices = Collections.emptyList();

  public static class DeviceInfo {
    public String name;
    public JsonNode settings;

    @Override
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
