package com.uplatform.wallet_tests.api.db.entity.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum GameCategoryStatus {

    DISABLED((short) 2, "disabled"),
    UNKNOWN((short) -1, "unknown");

    public final short statusId;
    public final String status;

    GameCategoryStatus(short statusId, String status) {
        this.statusId = statusId;
        this.status = status;
    }

    private static final Map<Short, GameCategoryStatus> BY_ID = Collections.unmodifiableMap(
            Arrays.stream(values())
                    .collect(Collectors.toMap(s -> s.statusId, Function.identity(), (existing, replacement) -> existing))
    );

    private static final Map<String, GameCategoryStatus> BY_STATUS = Collections.unmodifiableMap(
            Arrays.stream(values())
                    .collect(Collectors.toMap(s -> normalizeStatus(s.status), Function.identity(), (existing, replacement) -> existing))
    );

    @JsonValue
    public String getStatus() {
        return status;
    }

    public static GameCategoryStatus fromStatusId(Number statusId) {
        if (statusId == null) {
            return UNKNOWN;
        }
        return fromStatusId(statusId.shortValue());
    }

    public static GameCategoryStatus fromStatusId(short statusId) {
        return BY_ID.getOrDefault(statusId, UNKNOWN);
    }

    @JsonCreator
    public static GameCategoryStatus fromStatus(String status) {
        if (status == null) {
            return UNKNOWN;
        }
        return BY_STATUS.getOrDefault(normalizeStatus(status), UNKNOWN);
    }

    private static String normalizeStatus(String status) {
        return status.trim().toLowerCase(Locale.ROOT);
    }
}
