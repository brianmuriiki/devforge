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
                widths[i] = Math.max(widths[i], TerminalText.inline(row.get(i)).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (List<String> row : all) {
            for (int i = 0; i < columns; i++) {
                String cell = TerminalText.inline(row.get(i));
                sb.append(cell);
                if (i < columns - 1) {
                    sb.append(" ".repeat(widths[i] - cell.length() + 2));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
