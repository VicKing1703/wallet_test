package com.uplatform.wallet_tests.api.nats.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum NatsBlockAmountType {
    PAYMENT(1),
    MANUAL(3),
    UNKNOWN(0);

    private final int value;

    @JsonValue
    public int getValue() {
        return value;
    }

    private static final Map<Integer, NatsBlockAmountType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsBlockAmountType::getValue, Function.identity()));

    @JsonCreator
    public static NatsBlockAmountType fromValue(int value) {
        NatsBlockAmountType result = valueMap.get(value);
        return result == null ? UNKNOWN : result;
    }
}
