package com.uplatform.wallet_tests.api.kafka.dto.player_status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountEventType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerStatusUpdateEvent(
        @JsonProperty("eventType") PlayerAccountEventType eventType,
        @JsonProperty("eventCreatedAt") Long eventCreatedAt
) {
}
