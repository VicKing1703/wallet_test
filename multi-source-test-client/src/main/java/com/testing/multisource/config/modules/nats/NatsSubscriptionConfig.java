package com.testing.multisource.config.modules.nats;

/**
 * NATS subscription configuration
 */
public record NatsSubscriptionConfig(
        int subscriptionRetryCount,
        long subscriptionRetryDelayMs,
        long subscriptionAckWaitSeconds,
        long subscriptionInactiveThresholdSeconds,
        int subscriptionBufferSize
) {}
