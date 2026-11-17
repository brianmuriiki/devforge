package com.devforge.search;

import com.devforge.core.DevForgeException;

/** Invalid search input or an impossible FindX operation; the message is safe to show to users. */
public class SearchException extends DevForgeException {

    public SearchException(String message) {
        super(message);
    }
}
