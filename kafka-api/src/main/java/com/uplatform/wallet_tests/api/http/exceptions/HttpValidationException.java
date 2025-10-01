package com.uplatform.wallet_tests.api.http.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class HttpValidationException extends TestFrameworkException {
    public HttpValidationException(String message) {
        super(message);
    }
    public HttpValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
