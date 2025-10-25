package com.uplatform.wallet_tests.api.http.cap.dto.game_category.enums;

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
public enum CategoryTypeV2 {
    CATEGORY("category"),
    COLLECTION("collection"),
    SUBCATEGORY("subcategory"),
    ALL_GAMES("allGames"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, CategoryTypeV2> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(CategoryTypeV2::getValue, Function.identity()));

    @JsonCreator
    public static CategoryTypeV2 fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize GameCategoryTypeV2 from null JSON value");
        }
        CategoryTypeV2 result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for GameCategoryTypeV2: " + value);
        }
        return result;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
