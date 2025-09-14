package com.uplatform.wallet_tests.api.redis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletData(
        @JsonProperty("wallet_uuid") String walletUUID,
        @JsonProperty("currency") String currency,
        @JsonProperty("type") int type,
        @JsonProperty("status") int status
) {
    public WalletData() {
        this(null, null, 0, 0);
    }

    public String getWalletUUID() { return walletUUID; }

    public String getCurrency() { return currency; }

    public int getType() { return type; }

    public int getStatus() { return status; }
}