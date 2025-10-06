package com.uplatform.wallet_tests.api.kafka.dto.player_status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerStatusUpdateMessage(
        @JsonProperty("message") PlayerStatusUpdateEvent message,
        @JsonProperty("player") PlayerStatusPayload player,
        @JsonProperty("context") JsonNode context
) {
}
