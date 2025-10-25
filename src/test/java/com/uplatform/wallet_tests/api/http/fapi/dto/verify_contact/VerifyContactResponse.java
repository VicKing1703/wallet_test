package com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifyContactResponse(@JsonProperty("hash") String hash) {
}