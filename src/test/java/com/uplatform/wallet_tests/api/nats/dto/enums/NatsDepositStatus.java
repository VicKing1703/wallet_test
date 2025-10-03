package com.uplatform.wallet_tests.api.nats.dto.enums;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

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
public enum NatsDepositStatus {
    SUCCESS(4),
    UNKNOWN(0);

    private final int value;

    @JsonValue
    public int getValue() {
        return value;
    }

    private static final Map<Integer, NatsDepositStatus> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsDepositStatus::getValue, Function.identity()));

    @JsonCreator
    public static NatsDepositStatus fromValue(int value) {
        NatsDepositStatus result = valueMap.get(value);
        return result == null ? UNKNOWN : result;
    }
}
