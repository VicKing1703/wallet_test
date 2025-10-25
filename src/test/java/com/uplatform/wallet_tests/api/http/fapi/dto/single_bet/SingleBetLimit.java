package com.uplatform.wallet_tests.api.http.fapi.dto.single_bet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SingleBetLimit(
        @JsonProperty("id") String id,
        @JsonProperty("currency") String currency,
        @JsonProperty("status") boolean status,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("upcomingChanges") List<UpcomingChange> upcomingChanges,
        @JsonProperty("deactivatedAt") Integer deactivatedAt,
        @JsonProperty("required") boolean required
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpcomingChange(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("effectiveAt") Long effectiveAt
    ) {
    }
}
