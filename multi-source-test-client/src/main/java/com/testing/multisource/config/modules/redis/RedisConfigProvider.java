package com.testing.multisource.config.modules.redis;

import com.testing.multisource.config.modules.redis.RedisAggregateConfig;

public interface RedisConfigProvider {
    RedisAggregateConfig getRedisAggregateConfig();
}
