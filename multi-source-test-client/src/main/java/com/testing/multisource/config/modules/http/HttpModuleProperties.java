package com.testing.multisource.config.modules.http;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class HttpModuleProperties {
    private HttpDefaultsProperties defaults = new HttpDefaultsProperties();
    private Map<String, Map<String, Object>> services = new LinkedHashMap<>();
}
