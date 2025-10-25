package com.testing.multisource.config.modules.nats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.testing.multisource.config.modules.nats.NatsConfig;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsModuleProperties(
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
    public NatsModuleProperties {
        if (hosts == null) {
            hosts = List.of();
        }
        if (uniqueDuplicateWindowMs == 0) {
            uniqueDuplicateWindowMs = 400;
        }
    }

    

    public NatsConfig toLegacyNatsConfig() {
        return new NatsConfig(
                hosts,
                streamName,
                subscriptionRetryCount,
                subscriptionRetryDelayMs,
                connectReconnectWaitSeconds,
                connectMaxReconnects,
                searchTimeoutSeconds,
                subscriptionAckWaitSeconds,
                subscriptionInactiveThresholdSeconds,
                subscriptionBufferSize,
                uniqueDuplicateWindowMs,
                failOnDeserialization
        );
    }

    

    public NatsConnectionConfig connection() {
        return new NatsConnectionConfig(
                hosts,
                connectReconnectWaitSeconds,
                connectMaxReconnects
        );
    }

    

    public NatsStreamConfig stream() {
        return new NatsStreamConfig(streamName, uniqueDuplicateWindowMs);
    }

    

    public NatsSubscriptionConfig subscription() {
        return new NatsSubscriptionConfig(
                subscriptionRetryCount,
                subscriptionRetryDelayMs,
                subscriptionAckWaitSeconds,
                subscriptionInactiveThresholdSeconds,
                subscriptionBufferSize
        );
    }

    

    public NatsBehaviorConfig behavior() {
        return new NatsBehaviorConfig(searchTimeoutSeconds, failOnDeserialization);
    }
}
