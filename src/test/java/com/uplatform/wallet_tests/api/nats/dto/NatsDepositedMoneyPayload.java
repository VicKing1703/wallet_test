package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsDepositedMoneyPayload(
        String uuid,
        @JsonProperty("currency_code") String currencyCode,
        BigDecimal amount,
        NatsDepositStatus status,
        @JsonProperty("node_uuid") String nodeUuid,
        @JsonProperty("bonus_id") String bonusId
) {
}
