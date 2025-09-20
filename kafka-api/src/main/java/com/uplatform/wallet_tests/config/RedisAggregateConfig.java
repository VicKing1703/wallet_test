package com.uplatform.wallet_tests.config;

public record RedisAggregateConfig(
        int maxGamblingCount,
        int maxIframeCount,
        int retryAttempts,
        long retryDelayMs
) {}
