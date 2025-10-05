package com.testing.multisource.config.modules.nats;

public record NatsSubscriptionConfig(
        int subscriptionRetryCount,
        long subscriptionRetryDelayMs,
        long subscriptionAckWaitSeconds,
        long subscriptionInactiveThresholdSeconds,
        int subscriptionBufferSize
) {}
