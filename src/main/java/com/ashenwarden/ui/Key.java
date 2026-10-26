package com.ashenwarden.ui;

/**
 * One decoded key press from the terminal keyboard. Navigation keys and the
 * confirmation keys are their own kinds; printable keys arrive as
 * {@link Kind#CHARACTER} (or {@link Kind#DIGIT} for 0-9) with the pressed
 * character in {@link #value()}. Raw terminal bytes are decoded once, here,
 * so every menu in the game deals in meaning, never in escape sequences.
 */
public record Key(Kind kind, char value) {

    public enum Kind {
        /** Up arrow, Shift+Tab, Page Up — previous option. */
        UP,
        /** Down arrow, Tab, Page Down — next option. */
        DOWN,
        /** Enter / Return — confirm the highlighted option. */
        ENTER,
        /** Escape — leave the current screen without any effect. */
        ESCAPE,
        /** A digit 0-9, used for direct menu jumps. */
        DIGIT,
        /** Any other printable key (e.g. '?' opens help). */
        CHARACTER,
        /** Backspace / Delete. */
        BACKSPACE,
        /** Ctrl+C — the player asked to stop. */
        INTERRUPT,
        /** Ctrl+D / end of input stream. */
        EOF,
        /** Any key the game does not use (function keys, arrows sideways...). */
        OTHER
    }

    public static Key of(Kind kind) {
        return new Key(kind, '\0');
    }

    public static Key digit(char digit) {
        return new Key(Kind.DIGIT, digit);
    }

    public static Key character(char c) {
        return new Key(Kind.CHARACTER, c);
    }

    public boolean is(Kind expected) {
        return kind == expected;
    }
}
