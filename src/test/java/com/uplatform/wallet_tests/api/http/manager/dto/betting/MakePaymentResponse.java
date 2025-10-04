package com.uplatform.wallet_tests.api.http.manager.dto.betting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode;

public record MakePaymentResponse(boolean success, String description, int errorCode) {

    @JsonIgnore
    public BettingErrorCode getErrorCodeAsEnum() {
        return BettingErrorCode.findByCode(this.errorCode);
    }
}