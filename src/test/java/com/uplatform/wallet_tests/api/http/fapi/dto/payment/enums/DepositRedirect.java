package com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumerates redirect endpoints for deposit operations.
 */
@Getter
@RequiredArgsConstructor
public enum DepositRedirect {
    FAILED("/account/deposit/failed"),
    SUCCESS("/account/deposit/success"),
    PENDING("/account/deposit/pending");

    private final String path;

    public String url() {
        return EnvironmentHost.current().getBaseUrl() + path;
    }
}
