package com.testing.multisource.api.redis.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class RedisRetryExhaustedException extends TestFrameworkException {
    public RedisRetryExhaustedException(String message) {
        super(message);
    }
    public RedisRetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
