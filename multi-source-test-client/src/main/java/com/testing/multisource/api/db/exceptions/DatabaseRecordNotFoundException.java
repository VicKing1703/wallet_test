package com.testing.multisource.api.db.exceptions;

import com.testing.multisource.api.exceptions.TestFrameworkException;

public class DatabaseRecordNotFoundException extends TestFrameworkException {
    public DatabaseRecordNotFoundException(String message) {
        super(message);
    }
    public DatabaseRecordNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
