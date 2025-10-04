package com.testing.multisource.config.modules.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/**
 * Kafka configuration record.
 * Used by Kafka API clients through KafkaConfigProvider.
 */
public record KafkaConfig(
        @JsonProperty("bootstrapServer") String bootstrapServers,
        String groupId,
        int bufferSize,
        Duration findMessageTimeout,
        Duration findMessageSleepInterval,
        Duration pollDuration,
        Duration shutdownTimeout,
        String autoOffsetReset,
        boolean enableAutoCommit
) {}
