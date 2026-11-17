package com.devforge.tasks.service;

import com.devforge.core.DevForgeException;

/** Invalid task input or an impossible task operation; the message is safe to show to users. */
public class TaskException extends DevForgeException {

    public TaskException(String message) {
        super(message);
    }
}
