package com.devforge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextTableTest {

    @Test
    void measuresCombiningAndWideCharactersByTerminalWidth() {
        assertEquals(1, TextTable.displayWidth("e\u0301"));
        assertEquals(2, TextTable.displayWidth("界"));
        assertEquals(2, TextTable.displayWidth("🔒"));
    }

    @Test
    void padsColumnsUsingDisplayWidth() {
        String table = TextTable.render(List.of("Name", "State"), List.of(
                List.of("界", "ready"),
                List.of("ab", "waiting")));

        assertEquals("Name  State\n界    ready\nab    waiting\n", table);
    }
}
