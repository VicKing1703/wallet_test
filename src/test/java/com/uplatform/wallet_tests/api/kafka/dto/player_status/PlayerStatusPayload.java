package com.uplatform.wallet_tests.api.kafka.dto.player_status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerStatusPayload(
        @JsonProperty("externalId") String externalId,
        @JsonProperty("activeStatus") boolean activeStatus,
        @JsonProperty("status") PlayerAccountStatus status
) {
}
