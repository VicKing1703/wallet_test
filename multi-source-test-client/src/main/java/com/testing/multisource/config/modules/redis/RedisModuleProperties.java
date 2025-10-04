package com.testing.multisource.config.modules.redis;

import com.testing.multisource.config.modules.redis.RedisAggregateConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RedisModuleProperties {
    private RedisAggregateConfig aggregate;
    private Map<String, RedisClientProperties> clients = new LinkedHashMap<>();

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RedisClientProperties extends RedisInstanceProperties {
    }
}
