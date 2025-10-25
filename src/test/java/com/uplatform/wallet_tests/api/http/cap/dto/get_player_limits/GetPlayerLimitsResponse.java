package com.uplatform.wallet_tests.api.http.cap.dto.get_player_limits;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.List;

public record GetPlayerLimitsResponse(List<PlayerLimit> data, int total) {

    public record PlayerLimit(
            String type,
            boolean status,
            String period,
            String currency,
            BigDecimal amount,
            @Nullable BigDecimal rest,
            @Nullable BigDecimal spent,
            Long createdAt,
            @Nullable Long deactivatedAt,
            Integer startedAt,
            @Nullable Integer expiresAt
    ) {
    }
}
