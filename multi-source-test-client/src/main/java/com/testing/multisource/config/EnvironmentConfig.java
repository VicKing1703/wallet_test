package com.testing.multisource.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.testing.multisource.config.modules.database.DatabaseInstanceConfig;
import com.testing.multisource.config.modules.http.HttpModuleProperties;
import com.testing.multisource.config.modules.kafka.KafkaModuleProperties;
import com.testing.multisource.config.modules.nats.NatsModuleProperties;
import com.testing.multisource.config.modules.redis.RedisModuleProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentConfig {
    private String name;
    private HttpModuleProperties http;
    private PlatformConfig platform;
    private Map<String, DatabaseInstanceConfig> databases;
    private RedisModuleProperties redis;
    private KafkaModuleProperties kafka;
    private NatsModuleProperties nats;

    public String getTopicPrefix() {
        return name + "_";
    }

    public String getNatsStreamPrefix() {
        return name + "_";
    }

    @Data
    public static class PlatformConfig {
        private String currency;
        private String country;
        private String nodeId;
        private String groupId;
    }
}
