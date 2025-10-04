package com.uplatform.wallet_tests.api.http.fapi.dto.check;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenCheckResponse(
        @JsonProperty("token") String token,
        @JsonProperty("refreshToken") String refreshToken
) {

    public String getToken() {
        return "Bearer " + token;
    }
}