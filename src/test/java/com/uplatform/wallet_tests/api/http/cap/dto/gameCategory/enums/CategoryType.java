package com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.enums;

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
public enum CategoryType {
    NAVIGATION_PANEL("navigationPanel"),
    HORIZONTAL("horizontal"),
    VERTICAL("vertical"),
    ALL_GAMES("allGames"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, CategoryType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(CategoryType::getValue, Function.identity()));

    @JsonCreator
    public static CategoryType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize CategoryType from null JSON value");
        }
        CategoryType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for CategoryType: " + value);
        }
        return result;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}