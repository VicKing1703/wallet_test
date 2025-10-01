package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.uplatform.wallet_tests.api.http.config.HttpModuleProperties;
import com.uplatform.wallet_tests.api.redis.config.RedisModuleProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentConfig {
    private String name;
    private ApiConfig api;
    private HttpModuleProperties http;
    private PlatformConfig platform;
    private Map<String, DatabaseInstanceConfig> databases;
    private RedisModuleProperties redis;
    private KafkaConfig kafka;
    private NatsConfig nats;

    public String getTopicPrefix() {
        return name + "_";
    }

    public String getNatsStreamPrefix() {
        return name + "_";
    }

    public void normalize() {
        normalizeHttpConfig();
    }

    private void normalizeHttpConfig() {
        if (http == null && api != null) {
            http = HttpModuleProperties.fromLegacy(api);
        }
        if (http != null) {
            api = http.toLegacyApiConfig();
        }
    }
}

