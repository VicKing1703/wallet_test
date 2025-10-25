package com.testing.multisource.api.kafka.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class KafkaMessageNotUniqueException extends TestFrameworkException {
    public KafkaMessageNotUniqueException(String message) {
        super(message);
    }
    public KafkaMessageNotUniqueException(String message, Throwable cause) {
        super(message, cause);
    }
}
