package com.uplatform.wallet_tests.api.http.cap.dto.check;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CapTokenCheckResponse(
        @JsonProperty("token") String token,
        @JsonProperty("refreshToken") String refreshToken
) {
}