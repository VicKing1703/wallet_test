package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.List;

public record KafkaConfig(
        @JsonProperty("bootstrapServer") String bootstrapServers,
        String groupId,
        List<String> listenTopicSuffixes,
        int bufferSize,
        Duration findMessageTimeout,
        Duration findMessageSleepInterval,
        Duration pollDuration,
        boolean seekToEndOnStart,
        Duration shutdownTimeout,
        String autoOffsetReset,
        boolean enableAutoCommit
) {}
