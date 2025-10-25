package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerEventMessage(
        @JsonProperty("eventType") String eventType,
        @JsonProperty("eventCreatedAt") Long eventCreatedAt
) {}
