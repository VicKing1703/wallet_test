package com.uplatform.wallet_tests.api.db.entity.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameCategoryStatus {
    ACTIVE((short) 2);

    private final short id;
}
