package com.testing.multisource.config.modules.database;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DatabaseModuleProperties(
        Map<String, DatabaseInstanceConfig> instances
) {
    public DatabaseModuleProperties {
        instances = instances == null ? Map.of() : Map.copyOf(instances);
    }
}
