package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsPreventGambleSettedPayload(
        @JsonProperty("is_gambling_active") boolean gamblingActive,
        @JsonProperty("is_betting_active") boolean bettingActive,
        @JsonProperty("created_at") long createdAt
) {
}
