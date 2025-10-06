package com.uplatform.wallet_tests.api.kafka.dto.player_status.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum PlayerAccountEventType {
    PLAYER_STATUS_UPDATE("player.statusUpdate"),
    UNKNOWN("unknown");

    private final String value;

    PlayerAccountEventType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static PlayerAccountEventType fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(value))
                .findFirst()
                .orElse(UNKNOWN);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
