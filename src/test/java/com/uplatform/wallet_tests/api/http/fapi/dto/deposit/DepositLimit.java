package com.uplatform.wallet_tests.api.http.fapi.dto.deposit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DepositLimit(
        @JsonProperty("id") String id,
        @JsonProperty("currency") String currency,
        @JsonProperty("status") boolean status,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("spent") BigDecimal spent,
        @JsonProperty("rest") BigDecimal rest,
        @JsonProperty("type") String type,
        @JsonProperty("startedAt") Long startedAt,
        @JsonProperty("expiresAt") Long expiresAt,
        @JsonProperty("deactivatedAt") Long deactivatedAt,
        @JsonProperty("upcomingChanges") List<UpcomingChange> upcomingChanges,
        @JsonProperty("required") boolean required
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpcomingChange(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("effectiveAt") Long effectiveAt
    ) {
    }
}
