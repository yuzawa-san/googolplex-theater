/*
 * Copyright (c) 2023 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class DeviceStatus {
    String name;
    String ipAddress;
    JsonNode settings;
    Instant birth;
}
