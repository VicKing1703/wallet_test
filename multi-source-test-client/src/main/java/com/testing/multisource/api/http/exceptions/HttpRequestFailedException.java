package com.testing.multisource.api.http.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class HttpRequestFailedException extends TestFrameworkException {
    public HttpRequestFailedException(String message) {
        super(message);
    }
    public HttpRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
