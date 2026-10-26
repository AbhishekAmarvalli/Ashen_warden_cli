package com.ashenwarden.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Line-by-line input for terminals that cannot deliver individual keys:
 * piped input, the browser front-end's child process, {@code --plain} mode,
 * and any console where JLine found no usable terminal. Every keyboard
 * feature degrades to typed commands on this path, so the whole game stays
 * playable with no keys at all.
 */
final class PlainKeyInput implements KeyInput {

    private final BufferedReader in;

    PlainKeyInput() {
        this.in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    @Override
    public boolean supportsKeys() {
        return false;
    }

    @Override
    public Key readKey() {
        // Callers check supportsKeys() first; answering EOF keeps a stray
        // call harmless (a menu would just close) instead of crashing.
        return Key.of(Key.Kind.EOF);
    }

    @Override
    public String readLine() throws IOException {
        return in.readLine();
    }

    @Override
    public void close() {
        // System.in is never closed here: the process may still want it
        // (browser front-end keeps its pipe open between turns).
    }
}
