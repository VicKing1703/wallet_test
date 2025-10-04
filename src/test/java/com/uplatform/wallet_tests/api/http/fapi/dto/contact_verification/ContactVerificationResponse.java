package com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ContactVerificationResponse(@JsonProperty("codeExpire") Long codeExpire) {
}