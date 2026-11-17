package com.devforge.search.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.hash.Sha256;
import com.devforge.search.scan.TextReader.TextFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextReaderTest {

    @TempDir Path tmp;

    private Optional<TextFile> read(String name, byte[] bytes, long max) throws IOException {
        Path f = tmp.resolve(name);
        Files.write(f, bytes);
        return TextReader.read(f, bytes.length, max);
    }

    @Test
    void readsUtf8TextAndHashesBytes() throws IOException {
        byte[] bytes = "héllo wörld".getBytes(StandardCharsets.UTF_8);
        TextFile t = read("a.txt", bytes, 1000).orElseThrow();
        assertEquals("héllo wörld", t.content());
        assertEquals(Sha256.bytes(bytes), t.sha256());
    }

    @Test
    void rejectsBinaryByNulByte() throws IOException {
        assertTrue(read("a.dat", new byte[] {'a', 0, 'b'}, 1000).isEmpty());
    }

    @Test
    void rejectsInvalidUtf8() throws IOException {
        assertTrue(read("a.txt", new byte[] {(byte) 0xC3, 0x28}, 1000).isEmpty());
    }

    @Test
    void rejectsFilesOverTheSizeLimit() throws IOException {
        assertTrue(read("big.txt", "x".repeat(100).getBytes(StandardCharsets.UTF_8), 50).isEmpty());
    }

    @Test
    void rejectsKnownBinaryExtensions() throws IOException {
        assertTrue(read("pic.png", "plain text".getBytes(StandardCharsets.UTF_8), 1000).isEmpty());
    }

    @Test
    void emptyFileIsText() throws IOException {
        assertEquals("", read("empty.txt", new byte[0], 1000).orElseThrow().content());
    }
}
