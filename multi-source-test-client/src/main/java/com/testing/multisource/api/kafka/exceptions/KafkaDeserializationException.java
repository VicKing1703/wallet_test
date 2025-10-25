package com.testing.multisource.api.kafka.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class KafkaDeserializationException extends TestFrameworkException {
    public KafkaDeserializationException(String message) {
        super(message);
    }
    public KafkaDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
