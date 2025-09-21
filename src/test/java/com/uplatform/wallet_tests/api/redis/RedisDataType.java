package com.uplatform.wallet_tests.api.redis;

/**
 * Logical Redis data domains supported by the test framework.
 */
public enum RedisDataType {

    /**
     * Full wallet aggregate that mirrors the wallet_wallet_redis structure.
     */
    WALLET_AGGREGATE,

    /**
     * Map with all wallets that belong to a particular player.
     */
    PLAYER_WALLETS
}

