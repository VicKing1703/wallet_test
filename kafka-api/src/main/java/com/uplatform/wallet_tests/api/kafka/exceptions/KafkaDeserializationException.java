package com.uplatform.wallet_tests.api.kafka.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class KafkaDeserializationException extends TestFrameworkException {
    public KafkaDeserializationException(String message) {
        super(message);
    }
    public KafkaDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
