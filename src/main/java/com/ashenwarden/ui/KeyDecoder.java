package com.ashenwarden.ui;

import java.io.IOException;

/**
 * Decodes raw terminal bytes into {@link Key} values. Escape sequences
 * (arrows arrive as ESC [ A on essentially every terminal, ESC O A on older
 * ones) are consumed here so no menu ever sees a raw byte. A lone ESC is
 * separated from a sequence by a short peek: real sequences deliver their
 * bytes within milliseconds, a pressed Escape key arrives alone.
 *
 * <p>Input comes through {@link Source}, a two-method reader over the
 * terminal, so decoding is unit-testable without a real console.</p>
 */
final class KeyDecoder {

    /** Blocking byte source with a timed peek (see JLine's NonBlockingReader). */
    interface Source {
        /** Next byte, or -1 at end of input. Blocks until a byte arrives. */
        int read() throws IOException;

        /** Next byte without consuming it, or -1 if none arrives in time. */
        int peek(int timeoutMs) throws IOException;
    }

    /** How long to wait for the rest of an escape sequence before giving up. */
    static final int ESCAPE_PEEK_MS = 50;

    private KeyDecoder() {
    }

    /** Decodes an already-read first byte, consuming any sequence tail. */
    static Key decode(int first, Source source) throws IOException {
        switch (first) {
            case -1 -> {
                return Key.of(Key.Kind.EOF);
            }
            case 3 -> {
                return Key.of(Key.Kind.INTERRUPT); // Ctrl+C
            }
            case 4 -> {
                return Key.of(Key.Kind.EOF);       // Ctrl+D
            }
            case '\r', '\n' -> {
                return Key.of(Key.Kind.ENTER);
            }
            case '\t' -> {
                return Key.of(Key.Kind.DOWN);      // Tab moves forward
            }
            case 127, 8 -> {
                return Key.of(Key.Kind.BACKSPACE);
            }
            case 27 -> {
                return decodeEscape(source);
            }
            default -> {
                if (first >= '0' && first <= '9') {
                    return Key.digit((char) first);
                }
                if (first >= 32 && first < 127) {
                    return Key.character((char) first);
                }
                return Key.of(Key.Kind.OTHER);
            }
        }
    }

    private static Key decodeEscape(Source source) throws IOException {
        int next = source.peek(ESCAPE_PEEK_MS);
        if (next < 0) {
            return Key.of(Key.Kind.ESCAPE); // nothing followed: a plain Escape press
        }
        source.read();
        if (next != '[' && next != 'O') {
            return Key.of(Key.Kind.OTHER);  // Alt+key and friends are not used
        }
        int code = source.read();
        switch (code) {
            case 'A' -> {
                return Key.of(Key.Kind.UP);
            }
            case 'B' -> {
                return Key.of(Key.Kind.DOWN);
            }
            case 'Z' -> {
                return Key.of(Key.Kind.UP); // Shift+Tab walks backwards
            }
            case '[' -> {                   // F-keys: ESC [ [ A ...
                source.read();
                return Key.of(Key.Kind.OTHER);
            }
            default -> {
                return decodeNumeric(code, source);
            }
        }
    }

    /** Consumes ESC [ &lt;digits&gt; &lt;term&gt; sequences (Home/End/PgUp/PgDn...). */
    private static Key decodeNumeric(int firstDigit, Source source) throws IOException {
        int digit = firstDigit - '0';
        if (firstDigit < '0' || firstDigit > '9') {
            return Key.of(Key.Kind.OTHER);
        }
        int c = source.read();
        while (c >= '0' && c <= '9') {
            digit = digit * 10 + (c - '0');
            c = source.read();
        }
        if (c == 'A') {
            return Key.of(Key.Kind.UP);
        }
        if (c == 'B') {
            return Key.of(Key.Kind.DOWN);
        }
        if (c == '~') {
            return switch (digit) {
                case 5 -> Key.of(Key.Kind.UP);    // Page Up
                case 6 -> Key.of(Key.Kind.DOWN);  // Page Down
                default -> Key.of(Key.Kind.OTHER);
            };
        }
        return Key.of(Key.Kind.OTHER);
    }
}
