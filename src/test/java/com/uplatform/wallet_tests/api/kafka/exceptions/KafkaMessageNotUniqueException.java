package com.uplatform.wallet_tests.api.kafka.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class KafkaMessageNotUniqueException extends TestFrameworkException {
    public KafkaMessageNotUniqueException(String message) {
        super(message);
    }
    public KafkaMessageNotUniqueException(String message, Throwable cause) {
        super(message, cause);
    }
}
