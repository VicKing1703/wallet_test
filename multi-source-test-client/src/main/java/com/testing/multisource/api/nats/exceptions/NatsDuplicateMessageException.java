package com.testing.multisource.api.nats.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class NatsDuplicateMessageException extends TestFrameworkException {
    public NatsDuplicateMessageException(String message) {
        super(message);
    }
    public NatsDuplicateMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
