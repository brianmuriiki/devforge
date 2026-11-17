package com.devforge.cli;

import java.util.regex.Pattern;

/**
 * Neutralizes control characters in user-controlled text before it reaches the terminal, so a
 * task title cannot inject escape sequences. JSON output is not affected.
 */
public final class TerminalText {

    private static final Pattern INLINE = Pattern.compile("[\\x00-\\x1F\\x7F\\u0080-\\u009F]");
    /** Like INLINE, but keeps tab (0x09) and newline (0x0A). */
    private static final Pattern BLOCK = Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F\\u0080-\\u009F]");

    private TerminalText() {}

    /** Single-line text: every control character, including newlines, becomes a space. */
    public static String inline(String text) {
        return text == null ? "" : INLINE.matcher(text).replaceAll(" ");
    }

    /** Multi-line text: newlines and tabs are kept, other control characters become spaces. */
    public static String block(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return BLOCK.matcher(normalized).replaceAll(" ");
    }
}
