package com.testing.multisource.api.redis.config;

import java.time.Duration;

public record RedisAwaitilityProperties(
        Duration defaultTimeout,
        Duration pollInterval
) {}

