package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.uplatform.wallet_tests.config.modules.database.DatabaseInstanceConfig;
import com.uplatform.wallet_tests.config.modules.http.HttpModuleProperties;
import com.uplatform.wallet_tests.config.modules.kafka.KafkaModuleProperties;
import com.uplatform.wallet_tests.config.modules.nats.NatsModuleProperties;
import com.uplatform.wallet_tests.config.modules.redis.RedisModuleProperties;
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
