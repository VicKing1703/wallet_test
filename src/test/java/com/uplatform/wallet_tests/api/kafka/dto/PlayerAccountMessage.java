package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerAccountMessage(
        @JsonProperty("message") PlayerEventMessage message,
        @JsonProperty("player") PlayerInfo player,
        @JsonProperty("context") Context context
) {}