package com.uplatform.wallet_tests.api.kafka.dto.player_status.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum PlayerAccountStatus {
    ACTIVE(0),
    INACTIVE(1),
    BLOCKED(2),
    UNKNOWN(-1);

    private final int code;

    PlayerAccountStatus(int code) {
        this.code = code;
    }

    @JsonCreator
    public static PlayerAccountStatus fromValue(Integer value) {
        if (value == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(status -> status.code == value)
                .findFirst()
                .orElse(UNKNOWN);
    }

    @JsonValue
    public int getCode() {
        return code;
    }
}
