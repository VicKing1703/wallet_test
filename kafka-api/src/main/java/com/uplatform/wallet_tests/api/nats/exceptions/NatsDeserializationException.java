package com.uplatform.wallet_tests.api.nats.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class NatsDeserializationException extends TestFrameworkException {
    public NatsDeserializationException(String message) {
        super(message);
    }
    public NatsDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
