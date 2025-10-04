package com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WithdrawalRedirect {
    FAILED("/account/withdrawal/failed"),
    SUCCESS("/account/withdrawal/success"),
    PENDING("/account/withdrawal/pending");

    private final String path;

    public String url() {
        return EnvironmentHost.current().getBaseUrl() + path;
    }
}
