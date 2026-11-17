package com.devforge.core.db;

import com.devforge.core.DevForgeException;

/** Database failure with a message that is safe to show to end users. */
public class DatabaseException extends DevForgeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
