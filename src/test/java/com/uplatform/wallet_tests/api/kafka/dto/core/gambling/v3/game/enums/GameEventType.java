package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums;

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
public enum GameEventType {
    BRAND("brand"),
    GAME("game"),
    CATEGORY("category"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, GameEventType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(GameEventType::getValue, Function.identity()));

    @JsonCreator
    public static GameEventType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize GameEventType from null JSON value");
        }
        GameEventType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknow value for GameEventType: " + value);
        }
        return result;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
