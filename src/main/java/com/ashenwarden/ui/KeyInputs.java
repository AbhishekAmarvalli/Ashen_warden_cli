package com.ashenwarden.ui;

import java.io.IOException;

/**
 * Chooses the best available input for this launch. Keyboard navigation
 * needs a real console: without one (piped input, IDE runner, {@code --plain}
 * for legacy consoles) the game silently falls back to typed commands
 * instead of failing or freezing.
 */
public final class KeyInputs {

    private KeyInputs() {
    }

    /** @param plain true when the game was launched with --plain */
    public static KeyInput open(boolean plain) {
        if (plain) {
            return new PlainKeyInput(); // guaranteed-clean ASCII path, no escapes
        }
        if (System.console() == null) {
            // no attached console: piped input (tests, browser front-end)
            return new PlainKeyInput();
        }
        try {
            JLineKeyInput jline = JLineKeyInput.open();
            if (jline != null) {
                return jline;
            }
        } catch (IOException | RuntimeException | LinkageError e) {
            // a terminal JLine cannot drive is not a reason to refuse to start
        }
        return new PlainKeyInput();
    }
}
