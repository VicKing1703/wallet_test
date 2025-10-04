package com.testing.multisource.config.modules.nats;

import java.util.List;

/**
 * NATS configuration record.
 * Used by NATS API clients through NatsConfigProvider.
 */
public record NatsConfig(
        List<String> hosts,
        String streamName,
        int subscriptionRetryCount,
        long subscriptionRetryDelayMs,
        long connectReconnectWaitSeconds,
        int connectMaxReconnects,
        long searchTimeoutSeconds,
        long subscriptionAckWaitSeconds,
        long subscriptionInactiveThresholdSeconds,
        int subscriptionBufferSize,
        long uniqueDuplicateWindowMs,
        boolean failOnDeserialization
) {
    public NatsConfig {
        if (hosts == null) {
            hosts = List.of();
        }
        if (uniqueDuplicateWindowMs == 0) {
            uniqueDuplicateWindowMs = 400;
        }
    }
}
