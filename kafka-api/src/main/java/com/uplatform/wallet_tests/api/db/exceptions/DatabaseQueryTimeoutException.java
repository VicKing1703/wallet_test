package com.uplatform.wallet_tests.api.db.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class DatabaseQueryTimeoutException extends TestFrameworkException {
    public DatabaseQueryTimeoutException(String message) {
        super(message);
    }
    public DatabaseQueryTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
