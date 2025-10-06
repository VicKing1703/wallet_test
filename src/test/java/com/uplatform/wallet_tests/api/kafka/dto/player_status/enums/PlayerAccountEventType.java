package com.uplatform.wallet_tests.api.kafka.dto.player_status.enums;

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
public enum PlayerAccountEventType {
    PLAYER_STATUS_UPDATE("player.statusUpdate"),
    UNKNOWN("unknown");

    @JsonValue
    private final String value;

    private static final Map<String, PlayerAccountEventType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(PlayerAccountEventType::getValue, Function.identity()));

    @JsonCreator
    public static PlayerAccountEventType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize PlayerAccountEventType from null JSON value");
        }
        PlayerAccountEventType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for PlayerAccountEventType: " + value);
        }
        return result;
    }
}
