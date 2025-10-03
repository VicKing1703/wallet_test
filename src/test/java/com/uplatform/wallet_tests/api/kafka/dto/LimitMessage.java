package com.uplatform.wallet_tests.api.kafka.dto;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LimitMessage(
        @JsonProperty("limitType") String limitType,
        @JsonProperty("intervalType") String intervalType,
        @JsonProperty("amount") String amount,
        @JsonProperty("currencyCode") String currencyCode,
        @JsonProperty("id") String id,
        @JsonProperty("playerId") String playerId,
        @JsonProperty("status") Boolean status,
        @JsonProperty("startedAt") Long startedAt,
        @JsonProperty("expiresAt") Long expiresAt,
        @JsonProperty("eventType") String eventType
) {}
