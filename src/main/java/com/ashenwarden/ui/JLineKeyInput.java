package com.ashenwarden.ui;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.Locale;

/**
 * JLine 3 backed input: raw single-key reads for the interactive menus and
 * line editing (cursor keys, backspace, history-free) for typed commands,
 * both on the same terminal instance. Raw mode is entered per key read and
 * the previous terminal attributes are restored right after, so a crash or
 * Ctrl+C can never leave the player's console in a broken state.
 */
final class JLineKeyInput implements KeyInput {

    private final Terminal terminal;
    private final NonBlockingReader keys;
    private final org.jline.reader.LineReader lines;
    private boolean closed;

    private JLineKeyInput(Terminal terminal) {
        this.terminal = terminal;
        this.keys = terminal.reader();
        this.lines = org.jline.reader.LineReaderBuilder.builder()
                .terminal(terminal)
                .option(org.jline.reader.LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
    }

    /**
     * Opens the system console as a JLine terminal, or returns null when there
     * is no usable one (piped input, dumb terminal) so callers can fall back
     * to line-by-line input.
     */
    static JLineKeyInput open() throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(false)
                .build();
        String type = terminal.getType();
        if (type != null && type.toLowerCase(Locale.ROOT).contains("dumb")) {
            terminal.close();
            return null;
        }
        return new JLineKeyInput(terminal);
    }

    /** Test seam: the same input driven by any terminal (a scripted pipe). */
    static KeyInput over(Terminal terminal) {
        return new JLineKeyInput(terminal);
    }

    @Override
    public boolean supportsKeys() {
        return true;
    }

    @Override
    public Key readKey() throws IOException {
        Attributes previous = terminal.enterRawMode();
        try {
            return KeyDecoder.decode(keys.read(), new ReaderSource());
        } finally {
            if (previous != null) {
                terminal.setAttributes(previous);
            }
        }
    }

    @Override
    public String readLine() throws IOException {
        try {
            // The renderer prints its own prompt; JLine's is empty on purpose.
            return lines.readLine("");
        } catch (org.jline.reader.UserInterruptException e) {
            return null; // Ctrl+C while typing: treat as end of input, cleanly
        } catch (org.jline.reader.EndOfFileException e) {
            return null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            terminal.close();
        } catch (IOException ignored) {
            // nothing useful to do while shutting down
        }
    }

    /** Adapts JLine's non-blocking terminal reader to the decoder's source. */
    private final class ReaderSource implements KeyDecoder.Source {
        @Override
        public int read() throws IOException {
            return keys.read();
        }

        @Override
        public int peek(int timeoutMs) throws IOException {
            return keys.peek(timeoutMs);
        }
    }
}
