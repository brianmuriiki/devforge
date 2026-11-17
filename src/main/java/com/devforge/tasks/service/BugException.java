package com.devforge.tasks.service;

import com.devforge.core.DevForgeException;

/** Invalid bug input or an impossible bug operation; the message is safe to show to users. */
public class BugException extends DevForgeException {

    public BugException(String message) {
        super(message);
    }
}
