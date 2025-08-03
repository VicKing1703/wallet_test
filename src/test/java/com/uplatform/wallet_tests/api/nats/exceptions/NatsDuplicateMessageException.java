package com.uplatform.wallet_tests.api.nats.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class NatsDuplicateMessageException extends TestFrameworkException {
    public NatsDuplicateMessageException(String message) {
        super(message);
    }
    public NatsDuplicateMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
