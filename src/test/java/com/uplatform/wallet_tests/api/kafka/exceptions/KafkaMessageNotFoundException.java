package com.uplatform.wallet_tests.api.kafka.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class KafkaMessageNotFoundException extends TestFrameworkException {
    public KafkaMessageNotFoundException(String message) {
        super(message);
    }
    public KafkaMessageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
