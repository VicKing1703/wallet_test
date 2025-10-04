package com.testing.multisource.api.nats.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class NatsMessageNotFoundException extends TestFrameworkException {
    public NatsMessageNotFoundException(String message) {
        super(message);
    }
    public NatsMessageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
