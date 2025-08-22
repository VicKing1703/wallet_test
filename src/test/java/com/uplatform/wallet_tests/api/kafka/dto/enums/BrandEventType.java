package com.uplatform.wallet_tests.api.kafka.dto.enums;

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
public enum BrandEventType {
    BRAND_CREATED("gambling.gameBrandCreated"),
    BRAND_UPDATED("gambling.gameBrandUpdated"),
    BRAND_DELETED("gambling.gameBrandDeleted"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, BrandEventType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(BrandEventType::getValue, Function.identity()));

    @JsonCreator
    public static BrandEventType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize BrandEventType from null JSON value");
        }
        BrandEventType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknow value for BrandEventType: " + value);
        }
        return result;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
