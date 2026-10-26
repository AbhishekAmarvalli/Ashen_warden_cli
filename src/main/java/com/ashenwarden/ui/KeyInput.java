package com.ashenwarden.ui;

import java.io.IOException;

/**
 * Where the game reads player input from. Two modes on one interface:
 * single-key reads (arrow-key menus) and whole-line reads (typed commands,
 * names, free text). Implementations must never throw on ordinary player
 * input and must answer {@code false} from {@link #supportsKeys()} when the
 * terminal cannot deliver individual keys (piped input, legacy console,
 * {@code --plain}), so every screen keeps a typed-command path that works.
 */
public interface KeyInput {

    /** True when {@link #readKey()} delivers real key presses. */
    boolean supportsKeys();

    /**
     * Blocks until the player presses a key and returns it decoded.
     * Only valid when {@link #supportsKeys()} is true.
     */
    Key readKey() throws IOException;

    /**
     * Blocks until the player submits a whole line (Enter) and returns it
     * without the trailing newline; {@code null} means end of input (Ctrl+D,
     * a closed pipe) — callers stop cleanly, never spin.
     */
    String readLine() throws IOException;

    /** Releases the terminal. Safe to call more than once. */
    void close();
}
