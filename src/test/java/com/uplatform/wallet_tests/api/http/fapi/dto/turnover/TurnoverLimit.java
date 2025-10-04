package com.uplatform.wallet_tests.api.http.fapi.dto.turnover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnoverLimit(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("currency") String currency,
        @JsonProperty("status") boolean status,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("spent") BigDecimal spent,
        @JsonProperty("rest") BigDecimal rest,
        @JsonProperty("startedAt") Integer startedAt,
        @JsonProperty("expiresAt") Integer expiresAt,
        @JsonProperty("deactivatedAt") Integer deactivatedAt,
        @JsonProperty("required") boolean required,
        @JsonProperty("upcomingChanges") List<UpcomingChange> upcomingChanges
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpcomingChange(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("effectiveAt") Long effectiveAt
    ) {
    }
}
