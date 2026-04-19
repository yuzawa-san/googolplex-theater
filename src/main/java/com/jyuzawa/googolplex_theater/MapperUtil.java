/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Provides a singleton JSON mapper.
 *
 * @author jyuzawa
 */
public final class MapperUtil {

    public static final ObjectMapper MAPPER = JsonMapper.shared();

    public static final ObjectMapper YAML_MAPPER =
            YAMLMapper.builder(new YAMLFactory()).build();
}
