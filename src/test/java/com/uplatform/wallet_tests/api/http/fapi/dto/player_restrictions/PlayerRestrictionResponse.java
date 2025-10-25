package com.uplatform.wallet_tests.api.http.fapi.dto.player_restrictions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlayerRestrictionResponse(
        String id,
        Long startedAt,
        List<UpcomingChange> upcomingChanges,
        Instant expiresAt,
        Instant deactivatedAt
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpcomingChange(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("effectiveAt") Long effectiveAt
    ) {
    }
}