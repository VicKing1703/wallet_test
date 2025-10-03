package com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PaymentMethodId {
    FAKE(1001),
    MOCK(3819);

    private final int value;

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentMethodId fromValue(int value) {
        return Arrays.stream(values())
                .filter(m -> m.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method id: " + value));
    }
}
