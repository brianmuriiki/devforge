package com.devforge.search.scan;

import com.devforge.core.hash.Sha256;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Reads a file only if it looks like UTF-8 text within the size limit. */
public final class TextReader {

    public record TextFile(String content, String sha256) {}

    private static final int BINARY_SNIFF_BYTES = 8000;

    private TextReader() {}

    /**
     * @return the text and its SHA-256, or empty if the file is too large, has a binary extension,
     *         contains NUL bytes, or is not valid UTF-8
     */
    public static Optional<TextFile> read(Path file, long declaredSize, long maxBytes) throws IOException {
        if (declaredSize > maxBytes
                || FileClassifier.hasBinaryExtension(FileClassifier.extensionOf(file.getFileName().toString()))) {
            return Optional.empty();
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > maxBytes) {
            return Optional.empty();
        }
        int sniff = Math.min(bytes.length, BINARY_SNIFF_BYTES);
        for (int i = 0; i < sniff; i++) {
            if (bytes[i] == 0) {
                return Optional.empty();
            }
        }
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return Optional.of(new TextFile(text, Sha256.bytes(bytes)));
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }
}
