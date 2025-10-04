package com.uplatform.wallet_tests.tests.default_steps.dto;

import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckResponse;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import org.springframework.http.ResponseEntity;

public record RegisteredPlayerData(
        ResponseEntity<TokenCheckResponse> authorizationResponse,
        WalletFullData walletData
) {
}