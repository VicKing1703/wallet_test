package com.uplatform.wallet_tests.api.redis.config;

import com.uplatform.wallet_tests.config.modules.redis.RedisAggregateConfig;

public interface RedisConfigProvider {
    RedisAggregateConfig getRedisAggregateConfig();
}
