package com.devforge.environment;

import com.devforge.core.DevForgeException;

/** Invalid DevVault input or an impossible backup/restore operation; the message is safe to show to users. */
public class VaultException extends DevForgeException {

    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
