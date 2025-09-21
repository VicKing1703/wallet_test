package com.uplatform.wallet_tests.api.redis.config;

import java.time.Duration;

/**
 * Holder for default Awaitility settings used by the fluent Redis client.
 */
public record RedisAwaitilityProperties(
        Duration defaultTimeout,
        Duration pollInterval
) {
}

