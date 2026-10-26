package com.ashenwarden.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Escape-sequence decoding: arrows, Enter, a lone Escape that must not be
 * mistaken for a sequence, digits and the interrupt/eof keys. Runs on a fake
 * byte source, so no real terminal is involved.
 */
class KeyDecoderTest {

    /** Bytes queued for reading; peek returns the head without consuming. */
    private static final class FakeSource implements KeyDecoder.Source {
        private final Deque<Integer> pending = new ArrayDeque<>();

        FakeSource(String bytes) {
            for (char c : bytes.toCharArray()) {
                pending.add((int) c);
            }
        }

        @Override
        public int read() {
            return pending.isEmpty() ? -1 : pending.poll();
        }

        @Override
        public int peek(int timeoutMs) {
            return pending.isEmpty() ? -1 : pending.peek();
        }
    }

    private static Key decode(String bytes, int first) throws IOException {
        return KeyDecoder.decode(first, new FakeSource(bytes));
    }

    @Test
    void digitsAndCharactersArriveWithTheirValue() throws Exception {
        assertEquals(Key.digit('3'), decode("", '3'));
        assertEquals(Key.character('q'), decode("", 'q'));
        assertEquals(Key.character('?'), decode("", '?'));
    }

    @Test
    void enterTabAndBackspaceAreRecognized() throws Exception {
        assertEquals(Key.of(Key.Kind.ENTER), decode("", '\r'));
        assertEquals(Key.of(Key.Kind.ENTER), decode("", '\n'));
        assertEquals(Key.of(Key.Kind.DOWN), decode("", '\t'));
        assertEquals(Key.of(Key.Kind.BACKSPACE), decode("", 127));
        assertEquals(Key.of(Key.Kind.BACKSPACE), decode("", 8));
    }

    @Test
    void arrowsDecodeFromBothEscapePrefixes() throws Exception {
        assertEquals(Key.of(Key.Kind.UP), decode("[A", 27));
        assertEquals(Key.of(Key.Kind.DOWN), decode("[B", 27));
        assertEquals(Key.of(Key.Kind.UP), decode("OA", 27));
        assertEquals(Key.of(Key.Kind.DOWN), decode("OB", 27));
        assertEquals(Key.of(Key.Kind.UP), decode("[Z", 27)); // Shift+Tab
    }

    @Test
    void pageKeysWalkTheSameDirectionAsTheArrows() throws Exception {
        assertEquals(Key.of(Key.Kind.UP), decode("[5~", 27));
        assertEquals(Key.of(Key.Kind.DOWN), decode("[6~", 27));
    }

    @Test
    void aLoneEscapeIsEscapeNotATruncatedSequence() throws Exception {
        // nothing follows within the peek window: the player pressed Escape
        assertEquals(Key.of(Key.Kind.ESCAPE), decode("", 27));
    }

    @Test
    void controlKeysReportInterruptAndEndOfInput() throws Exception {
        assertEquals(Key.of(Key.Kind.INTERRUPT), decode("", 3)); // Ctrl+C
        assertEquals(Key.of(Key.Kind.EOF), decode("", 4));       // Ctrl+D
        assertEquals(Key.of(Key.Kind.EOF), decode("", -1));      // closed stream
    }

    @Test
    void unusedKeysAreOtherAndNeverThrow() throws Exception {
        assertEquals(Key.of(Key.Kind.OTHER), decode("[15~", 27)); // F5
        assertEquals(Key.of(Key.Kind.OTHER), decode("xD", 27));   // Alt+key
        assertEquals(Key.of(Key.Kind.OTHER), decode("", 7));      // bare bell
    }

    @Test
    void longNumericSequencesAreFullyConsumed() throws Exception {
        // Home (ESC [ 1 H in some terminals): digits are drained, nothing leaks
        assertEquals(Key.of(Key.Kind.OTHER), decode("[1H", 27));
        assertEquals(Key.of(Key.Kind.OTHER), decode("[12;2R", 27));
    }
}
