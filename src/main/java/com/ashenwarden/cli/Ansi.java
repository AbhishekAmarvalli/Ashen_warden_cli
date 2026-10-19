package com.ashenwarden.cli;

/**
 * ANSI color support with plain-text fallback. Escape sequences are built from
 * the ESC code point (27) so the source file stays clean control-character-free.
 * Colors are always supplemental: every message carries its meaning in words.
 */
public final class Ansi {
    private static final char ESC = 27;
    public static final String RESET = ESC + "[0m";
    public static final String BOLD = ESC + "[1m";
    public static final String DIM = ESC + "[2m";
    public static final String RED = ESC + "[31m";
    public static final String GREEN = ESC + "[32m";
    public static final String YELLOW = ESC + "[33m";
    public static final String BLUE = ESC + "[34m";
    public static final String MAGENTA = ESC + "[35m";
    public static final String CYAN = ESC + "[36m";
    public static final String GRAY = ESC + "[90m";
    /** 256-color ember tones (Windows 10+ terminals render these natively). */
    public static final String EMBER_1 = ESC + "[38;5;214m"; // bright ember
    public static final String EMBER_2 = ESC + "[38;5;208m"; // flame orange
    public static final String EMBER_3 = ESC + "[38;5;166m"; // deep orange
    public static final String EMBER_4 = ESC + "[38;5;130m"; // cooled amber
    public static final String WHITE = ESC + "[38;5;252m";   // ash white

    private boolean enabled;

    public Ansi(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String color(String text, String code) {
        if (!enabled || text == null) {
            return text;
        }
        return code + text + RESET;
    }

    public String red(String t) { return color(t, RED); }
    public String green(String t) { return color(t, GREEN); }
    public String yellow(String t) { return color(t, YELLOW); }
    public String blue(String t) { return color(t, BLUE); }
    public String magenta(String t) { return color(t, MAGENTA); }
    public String cyan(String t) { return color(t, CYAN); }
    public String gray(String t) { return color(t, GRAY); }
    public String white(String t) { return color(t, WHITE); }

    /** Ember accent used for titles, banners and the prompt. */
    public String ember(String t) { return color(t, EMBER_2); }
    public String dim(String t) { return color(t, DIM); }
    public String bold(String t) { return color(t, BOLD); }
}
