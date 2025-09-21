package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
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
