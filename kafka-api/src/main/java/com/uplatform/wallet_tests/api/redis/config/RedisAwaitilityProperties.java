package com.uplatform.wallet_tests.api.redis.config;

import java.time.Duration;

public record RedisAwaitilityProperties(
        Duration defaultTimeout,
        Duration pollInterval
) {
}

