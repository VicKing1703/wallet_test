package com.testing.multisource.api.exceptions;

public abstract class TestFrameworkException extends RuntimeException {
    public TestFrameworkException(String message) {
        super(message);
    }
    public TestFrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
