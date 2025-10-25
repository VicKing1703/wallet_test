package com.testing.multisource.api.http.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class HttpValidationException extends TestFrameworkException {
    public HttpValidationException(String message) {
        super(message);
    }
    public HttpValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
