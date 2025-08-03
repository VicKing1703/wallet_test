package com.uplatform.wallet_tests.api.redis.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class RedisConnectionException extends TestFrameworkException {
    public RedisConnectionException(String message) {
        super(message);
    }
    public RedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
