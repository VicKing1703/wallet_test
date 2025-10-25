package com.testing.multisource.api.nats.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class NatsDeserializationException extends TestFrameworkException {
    public NatsDeserializationException(String message) {
        super(message);
    }
    public NatsDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
