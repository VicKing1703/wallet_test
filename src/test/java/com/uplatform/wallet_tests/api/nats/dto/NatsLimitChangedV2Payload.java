package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsLimitChangedV2Payload(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("limits") List<LimitDetail> limits
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LimitDetail(
            @JsonProperty("external_id") String externalId,
            @JsonProperty("limit_type") String limitType,
            @JsonProperty("interval_type") String intervalType,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("started_at") Long startedAt,
            @JsonProperty("expires_at") Long expiresAt,
            @JsonProperty("status") Boolean status
    ) {
    }
}
