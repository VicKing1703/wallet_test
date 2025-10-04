package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountType;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsBlockAmountEventPayload(
        String uuid,
        NatsBlockAmountStatus status,
        BigDecimal amount,
        String reason,
        NatsBlockAmountType type,
        @JsonProperty("expired_at") Long expiredAt,
        @JsonProperty("user_uuid") String userUuid,
        @JsonProperty("user_name") String userName,
        @JsonProperty("created_at") Long createdAt
) {
}
