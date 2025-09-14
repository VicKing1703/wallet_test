package com.uplatform.wallet_tests.api.redis.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class RedisRetryExhaustedException extends TestFrameworkException {
    public RedisRetryExhaustedException(String message) {
        super(message);
    }
    public RedisRetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
