package com.devforge.cli;

import java.util.ArrayList;
import java.util.List;

/** Renders left-aligned plain-text tables. Cell text is sanitized; the last column is not padded. */
public final class TextTable {

    private TextTable() {}

    public static String render(List<String> headers, List<List<String>> rows) {
        int columns = headers.size();
        List<List<String>> all = new ArrayList<>();
        all.add(headers);
        all.addAll(rows);

        int[] widths = new int[columns];
        for (List<String> row : all) {
            for (int i = 0; i < columns; i++) {
                widths[i] = Math.max(widths[i], displayWidth(TerminalText.inline(row.get(i))));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (List<String> row : all) {
            for (int i = 0; i < columns; i++) {
                String cell = TerminalText.inline(row.get(i));
                sb.append(cell);
                if (i < columns - 1) {
                    sb.append(" ".repeat(widths[i] - displayWidth(cell) + 2));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Approximate the cell width used by modern monospace terminals. */
    static int displayWidth(String text) {
        int width = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isZeroWidth(codePoint)) {
                continue;
            }
            width += isWide(codePoint) ? 2 : 1;
        }
        return width;
    }

    private static boolean isZeroWidth(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK || codePoint == 0x200D || codePoint == 0xFE0E
                || codePoint == 0xFE0F;
    }

    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2329 && codePoint <= 0x232A)
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE10 && codePoint <= 0xFE19)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD);
    }
}
