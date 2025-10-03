package com.uplatform.wallet_tests.api.kafka.dto;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentTransactionMessage(
        @JsonProperty("playerId") String playerId,
        @JsonProperty("nodeId") String nodeId,
        @JsonProperty("transaction") Transaction transaction
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(
            @JsonProperty("transactionId") String transactionId
    ) {}
}
