package com.uplatform.wallet_tests.api.http.cap.dto.categories;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CategoryType {
    CATEGORY("category"),
    SUBCATEGORY("subcategory"),
    COLLECTION("collection"),
    LOBBY("allGames");

    private final String value;

    CategoryType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
