package com.uplatform.wallet_tests.api.http.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class HttpRequestFailedException extends TestFrameworkException {
    public HttpRequestFailedException(String message) {
        super(message);
    }
    public HttpRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
