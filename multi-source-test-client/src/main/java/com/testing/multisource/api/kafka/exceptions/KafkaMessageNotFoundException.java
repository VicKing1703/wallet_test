package com.testing.multisource.api.kafka.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class KafkaMessageNotFoundException extends TestFrameworkException {
    public KafkaMessageNotFoundException(String message) {
        super(message);
    }
    public KafkaMessageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
