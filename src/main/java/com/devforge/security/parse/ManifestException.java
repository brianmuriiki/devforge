package com.devforge.security.parse;

import com.devforge.core.DevForgeException;

/** A manifest could not be read or parsed. */
public class ManifestException extends DevForgeException {

    public ManifestException(String message) {
        super(message);
    }

    public ManifestException(String message, Throwable cause) {
        super(message, cause);
    }
}
