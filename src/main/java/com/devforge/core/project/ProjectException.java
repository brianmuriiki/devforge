package com.devforge.core.project;

import com.devforge.core.DevForgeException;

/** Invalid project input or a registration conflict. */
public class ProjectException extends DevForgeException {

    public ProjectException(String message) {
        super(message);
    }

    public ProjectException(String message, Throwable cause) {
        super(message, cause);
    }
}
