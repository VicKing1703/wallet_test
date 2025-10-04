package com.testing.multisource.api.redis.config;

import com.testing.multisource.config.modules.redis.RedisAggregateConfig;

public interface RedisConfigProvider {
    RedisAggregateConfig getRedisAggregateConfig();
}
