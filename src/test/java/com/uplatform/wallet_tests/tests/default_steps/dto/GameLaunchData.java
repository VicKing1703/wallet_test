package com.uplatform.wallet_tests.tests.default_steps.dto;

import com.uplatform.wallet_tests.api.db.entity.wallet.WalletGameSession;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameResponseBody;
import org.springframework.http.ResponseEntity;

public record GameLaunchData(
        WalletGameSession dbGameSession,
        ResponseEntity<LaunchGameResponseBody> launchGameResponse
) {
}