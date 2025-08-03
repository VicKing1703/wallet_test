package com.uplatform.wallet_tests.api.nats.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class NatsMessageNotFoundException extends TestFrameworkException {
    public NatsMessageNotFoundException(String message) {
        super(message);
    }
    public NatsMessageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
