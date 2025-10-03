package com.uplatform.wallet_tests.api.nats.dto.enums;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public enum NatsTransactionDirection {

    WITHDRAW(new String[]{"withdraw", "1"}),
    DEPOSIT(new String[]{"deposit", "2"}),
    UNKNOWN(new String[]{"unknown"});

    private final String[] acceptedValues;

    @JsonValue
    public String getValue() {
        return acceptedValues[0];
    }

    private static final Map<String, NatsTransactionDirection> VALUE_MAP =
            Arrays.stream(values())
                    .flatMap(direction -> Stream.of(direction.acceptedValues)
                            .map(v -> Map.entry(v, direction)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    @JsonCreator
    public static NatsTransactionDirection fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return VALUE_MAP.getOrDefault(value, UNKNOWN);
    }
}