package com.devforge.security;

import com.devforge.core.DevForgeException;

/** An impossible DepGuard operation; the message is safe to show to users. */
public class SecurityScanException extends DevForgeException {

    public SecurityScanException(String message) {
        super(message);
    }

    public SecurityScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
