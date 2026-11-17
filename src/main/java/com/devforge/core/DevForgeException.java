package com.devforge.core;

/**
 * Base class for expected failures whose message is safe to show to end users.
 * Anything else that escapes a command is treated as an unexpected bug.
 */
public class DevForgeException extends RuntimeException {

    public DevForgeException(String message) {
        super(message);
    }

    public DevForgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
