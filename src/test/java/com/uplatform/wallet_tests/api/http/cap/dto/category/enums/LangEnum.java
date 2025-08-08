package com.uplatform.wallet_tests.api.http.cap.dto.enums;

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
    RUSSIAN("en"),
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

    /*    public enum CategoryNames {

    CAT_VERTICAL(Map.of(
            "ru", "Вертикальная",
            "en", "Vertical",
            "lv", "Vertikāli"
    )),
    CAT_HORIZONTAL(Map.of(
            "ru", "Горизонтальный",
            "en", "Horizontal",
            "lv", "Horizontāli"
    )),
    CAT_NAV(Map.of(
            "ru", "Навигационная панель",
            "en", "Navigation panel",
            "lv", "Navigācijas josla"
    )),
    EMPTY(Map.of(
            "", ""
    ));


    private final Map<String, String> names;

    @JsonValue
    public Map<String, String> getValue() {
        return names;
    }

    @JsonCreator
    public static CategoryNames fromValue(Map<String, String> map) {
        for (CategoryNames value : CategoryNames.values()) {
            if (value.names.equals(map)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + map);
    }
}*/
