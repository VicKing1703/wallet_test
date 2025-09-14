package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Context(
        @JsonProperty("confirmationCode") String confirmationCode,
        @JsonProperty("regType") String regType
) {}