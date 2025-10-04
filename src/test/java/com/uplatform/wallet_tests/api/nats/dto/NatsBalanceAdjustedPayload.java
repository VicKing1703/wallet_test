package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsBalanceAdjustedPayload(
        String uuid,
        @JsonProperty("currenc") String currency,
        BigDecimal amount,
        @JsonProperty("operation_type") int operationType,
        int direction,
        int reason,
        String comment,
        @JsonProperty("user_uuid") String userUuid,
        @JsonProperty("user_name") String userName
) {
}
