package com.testing.multisource.config.modules.kafka;

import java.time.Duration;

/**
 * Kafka client behavior configuration
 */
public record KafkaClientConfig(
        int bufferSize,
        Duration findMessageTimeout,
        Duration findMessageSleepInterval,
        Duration pollDuration,
        Duration shutdownTimeout
) {}
