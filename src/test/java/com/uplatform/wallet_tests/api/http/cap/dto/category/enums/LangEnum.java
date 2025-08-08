package com.uplatform.wallet_tests.api.http.cap.dto.category.enums;

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
public enum LangEnum {
    RUSSIAN("ru"),
    ENGLISH("en"),
    LATVIAN("lv"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, LangEnum> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(LangEnum::getValue, Function.identity()));

    @JsonCreator
    public static LangEnum fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize DirectionType from null JSON value");
        }
        LangEnum result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for DirectionType: " + value);
        }
        return result;
    }
}
