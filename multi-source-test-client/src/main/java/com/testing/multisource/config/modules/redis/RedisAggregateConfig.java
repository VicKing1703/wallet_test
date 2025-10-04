package com.testing.multisource.config.modules.redis;

public record RedisAggregateConfig(
        int maxGamblingCount,
        int maxIframeCount,
        int retryAttempts,
        long retryDelayMs
) {}
