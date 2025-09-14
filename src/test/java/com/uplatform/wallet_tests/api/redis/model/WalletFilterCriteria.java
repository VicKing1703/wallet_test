package com.uplatform.wallet_tests.api.redis.model;

import java.util.Optional;

public record WalletFilterCriteria(
        Optional<String> currency,
        Optional<Integer> type,
        Optional<Integer> status
) {
    public WalletFilterCriteria() {
        this(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Optional<String> getCurrency() { return currency; }

    public Optional<Integer> getType() { return type; }

    public Optional<Integer> getStatus() { return status; }
}

