package com.uplatform.wallet_tests.api.db.exceptions;

import com.uplatform.wallet_tests.api.exceptions.TestFrameworkException;

public class DatabaseRecordNotFoundException extends TestFrameworkException {
    public DatabaseRecordNotFoundException(String message) {
        super(message);
    }
    public DatabaseRecordNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
