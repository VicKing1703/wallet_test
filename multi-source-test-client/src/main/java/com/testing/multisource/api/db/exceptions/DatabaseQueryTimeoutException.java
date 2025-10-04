package com.testing.multisource.api.db.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class DatabaseQueryTimeoutException extends TestFrameworkException {
    public DatabaseQueryTimeoutException(String message) {
        super(message);
    }
    public DatabaseQueryTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
