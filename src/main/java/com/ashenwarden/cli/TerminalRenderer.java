package com.ashenwarden.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure presentation layer: wraps text to the terminal width, draws title
 * banners, box panels, section rules, status bars and menus. Never mutates
 * game state and never touches SQL. Supports NO_COLOR / --plain (ASCII-only,
 * no escapes) — meaning always travels in words, color only decorates.
 */
public final class TerminalRenderer {

    /** 5x5 block-letter font for the title banner (space + A..Z). */
    private static final String[][] FONT = {
            {"     "}, // ' ' fallback handled separately
            {"█████", "█   █", "█████", "█   █", "█   █"}, // A
            {"████ ", "█   █", "████ ", "█   █", "████ "}, // B
            {"█████", "█    ", "█    ", "█    ", "█████"}, // C
            {"████ ", "█   █", "█   █", "█   █", "████ "}, // D
            {"█████", "█    ", "████ ", "█    ", "█████"}, // E
            {"█████", "█    ", "████ ", "█    ", "█    "}, // F
            {"█████", "█    ", "█ ██ ", "█   █", "█████"}, // G
            {"█   █", "█   █", "█████", "█   █", "█   █"}, // H
            {"█████", "  █  ", "  █  ", "  █  ", "█████"}, // I
            {"█████", "    █", "    █", "█   █", "█████"}, // J
            {"█  █ ", "█ █  ", "██   ", "█ █  ", "█  █ "}, // K
            {"█    ", "█    ", "█    ", "█    ", "█████"}, // L
            {"█   █", "██ ██", "█ █ █", "█   █", "█   █"}, // M
            {"█   █", "██  █", "█ █ █", "█  ██", "█   █"}, // N
            {"█████", "█   █", "█   █", "█   █", "█████"}, // O
            {"█████", "█   █", "█████", "█    ", "█    "}, // P
            {"█████", "█   █", "█ █ █", "█  ██", "█████"}, // Q
            {"████ ", "█   █", "████ ", "█ ██ ", "█  ██"}, // R
            {"█████", "█    ", "█████", "    █", "█████"}, // S
            {"█████", "  █  ", "  █  ", "  █  ", "  █  "}, // T
            {"█   █", "█   █", "█   █", "█   █", "█████"}, // U
            {"█   █", "█   █", "█   █", " █ █ ", "  █  "}, // V
            {"█   █", "█   █", "█ █ █", "██ ██", "█   █"}, // W
            {"█   █", " █ █ ", "  █  ", " █ █ ", "█   █"}, // X
            {"█   █", " █ █ ", "  █  ", "  █  ", "  █  "}, // Y
            {"█████", "   █ ", "  █  ", " █   ", "█████"}, // Z
    };

    /** Fading ember gradient applied row-by-row to the banner. */
    private static final String[] GRADIENT = {
            Ansi.EMBER_1, Ansi.EMBER_2, Ansi.EMBER_2, Ansi.EMBER_3, Ansi.EMBER_4
    };

    /** Lines like "[THE HOLLOW NAVE]  ..." are location headers. */
    private static final Pattern HEADER_LINE =
            Pattern.compile("^\\[([A-Z][A-Z0-9 .,'!?\\-]*)\\](.*)$", Pattern.DOTALL);

    /** Frames visible while the loader spinner runs (skipped when piped). */
    private static final String[] SPINNER_UNICODE = {
            "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
    };
    private static final String[] SPINNER_ASCII = {"|", "/", "-", "\\"};

    private final Ansi ansi;
    private final boolean interactive;
    private boolean unicode;
    private int width;
    /** True while the current turn's reply is drawn with a chat-style gutter. */
    private boolean gutter;

    public TerminalRenderer(Ansi ansi, boolean unicode, int width) {
        this.ansi = ansi;
        this.unicode = unicode;
        this.width = Math.max(40, Math.min(120, width));
        // A real console (typing human) gets the animated loader; piped input
        // (tests, scripts) gets none — output must stay deterministic.
        this.interactive = System.console() != null;
    }

    public Ansi ansi() { return ansi; }
    public int width() { return width; }
    public boolean isUnicode() { return unicode; }

    public void setUnicode(boolean unicode) { this.unicode = unicode; }

    public void setWidth(int width) {
        this.width = Math.max(40, Math.min(120, width));
    }

    // --- primitives --------------------------------------------------------

    public void line(String text) {
        System.out.println(text == null ? "" : text);
    }

    public void blank() {
        System.out.println();
    }

    public void info(String text) { printWrapped(text, null); }

    // --- chat-style turn framing --------------------------------------------

    /** Echoes what the player typed, like a chat transcript: 'You ❯ look'. */
    public void userEcho(String input) {
        System.out.println("  " + ansi.cyan("You") + " "
                + ansi.ember(unicode ? "❯" : ">") + " " + (input == null ? "" : input));
    }

    /** Opens the assistant side of the turn: tag line, then replies get a gutter bar. */
    public void beginReply() {
        System.out.println();
        System.out.println("  " + ansi.ember(unicode ? "◆" : "*") + " "
                + ansi.bold(ansi.ember("ASHEN WARDEN")));
        gutter = true;
    }

    /** Closes the assistant side of the turn: gutter off, one blank line of air. */
    public void endReply() {
        gutter = false;
        System.out.println();
    }

    /** Adds the reply gutter bar under turn framing; plain otherwise. */
    private void emit(String text) {
        if (!gutter) {
            System.out.println(text);
            return;
        }
        System.out.println(ansi.dim(unicode ? "▌" : "|") + " " + text);
    }

    /**
     * Brief animated status before a reply (agent-CLI style loader). Skipped
     * entirely when input is not a typing console — piped output must be
     * deterministic and tests must never sleep.
     */
    public void loader(String message) {
        if (!interactive || message == null || message.isEmpty()) {
            return;
        }
        String[] frames = unicode ? SPINNER_UNICODE : SPINNER_ASCII;
        try {
            long deadline = System.currentTimeMillis() + 380;
            int i = 0;
            while (System.currentTimeMillis() < deadline) {
                System.out.print("\r  " + frames[i % frames.length] + " " + message + "   ");
                System.out.flush();
                i++;
                Thread.sleep(40);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.print("\r                                      \r");
        System.out.flush();
    }

    /** Narrative output: [LOCATION] headers get the ember treatment, meta
     *  notes like [autosaved] fade back, everything else stays calm white. */
    public void narrative(String text) {
        if (text == null || text.isEmpty()) {
            System.out.println();
            return;
        }
        if (text.startsWith("[autosave")) {
            dim(text);
            return;
        }
        Matcher m = HEADER_LINE.matcher(text);
        if (!m.matches()) {
            info(text);
            return;
        }
        String head = ansi.bold(ansi.ember("[" + m.group(1) + "]"));
        List<String> lines = wrap(m.group(2), width);
        emit(head + lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            emit("  " + lines.get(i));
        }
    }

    public void success(String text) { printWrapped(ansi.green(text), null); }

    public void warn(String text) { printWrapped(ansi.yellow(text), null); }

    public void error(String text) { printWrapped(ansi.red(text), null); }

    public void dim(String text) { printWrapped(ansi.gray(text), null); }

    public void heading(String text) {
        String label = text == null ? "" : text;
        int rule = Math.max(2, width - visible(label) - 1);
        System.out.println(ansi.bold(ansi.ember(label)) + " "
                + ansi.dim(rule(unicode ? "─" : "-", rule)));
    }

    /** Section rule: "── LABEL ────────────" in ember. */
    public void section(String label) {
        String text = label.toUpperCase(java.util.Locale.ROOT);
        String spaced = spaced(text);
        if (visible(spaced) + 6 > width) {
            // too long for flanking rules — print the label alone, never wrap mid-word
            System.out.println(ansi.bold(ansi.ember(text)));
            return;
        }
        int rule = Math.max(2, width - visible(spaced) - 3);
        System.out.println(ansi.dim(rule(unicode ? "─" : "-", 1)) + " "
                + ansi.bold(ansi.ember(spaced)) + " "
                + ansi.dim(rule(unicode ? "─" : "-", rule)));
    }

    /** Full-width light divider. */
    public void divider() {
        System.out.println(ansi.dim(rule(unicode ? "─" : "-", width)));
    }

    private void printWrapped(String text, String ignored) {
        if (text == null) {
            System.out.println();
            return;
        }
        for (String l : wrap(text, width)) {
            emit(l);
        }
    }

    // --- title banner ------------------------------------------------------

    /**
     * Big block-letter banner, vertically tinted like dying embers. Falls back
     * to a centered bold title when the terminal is too narrow for the art
     * (or when Unicode/color is switched off).
     */
    public void banner(String text) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        int artWidth = artWidth(upper);
        if (!unicode || artWidth + 4 > width) {
            int pad = Math.max(0, (width - visible(upper)) / 2);
            System.out.println(" ".repeat(pad) + ansi.bold(ansi.ember(upper)));
            return;
        }
        int left = Math.max(0, (width - artWidth) / 2);
        String indent = " ".repeat(left);
        for (int row = 0; row < 5; row++) {
            StringBuilder sb = new StringBuilder(indent);
            for (int i = 0; i < upper.length(); i++) {
                char c = upper.charAt(i);
                if (c == ' ') {
                    sb.append("   ");
                    continue;
                }
                String glyph = glyph(c, row);
                sb.append(glyph).append(' ');
            }
            String art = sb.toString().stripTrailing();
            System.out.println(ansi.color(art, GRADIENT[row]));
        }
    }

    private int artWidth(String upper) {
        int w = 0;
        for (int i = 0; i < upper.length(); i++) {
            w += (upper.charAt(i) == ' ') ? 3 : 6;
        }
        return Math.max(0, w - 1);
    }

    private String glyph(char c, int row) {
        if (c < 'A' || c > 'Z') {
            return "     ";
        }
        String[] glyph = FONT[c - 'A' + 1];
        return glyph[row];
    }

    /** Centered amber title line (for screens without banner art). */
    public void titleLine(String text) {
        String t = text.toUpperCase(java.util.Locale.ROOT);
        int pad = Math.max(0, (width - visible(t)) / 2);
        System.out.println(" ".repeat(pad) + ansi.bold(ansi.ember(t)));
    }

    /** Centered dim subtitle. */
    public void subLine(String text) {
        int pad = Math.max(0, (width - visible(text)) / 2);
        System.out.println(" ".repeat(pad) + ansi.gray(text));
    }

    // --- word wrapping -----------------------------------------------------

    /** Word-wraps to the terminal width without destroying indentation. */
    public List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            int indent = paragraph.length() - paragraph.stripLeading().length();
            String prefix = " ".repeat(Math.min(indent, 8));
            int limit = Math.max(20, maxWidth - prefix.length());
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.strip().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (line.length() == 0) {
                    line.append(word);
                } else if (line.length() + 1 + word.length() <= limit) {
                    line.append(' ').append(word);
                } else {
                    out.add(prefix + line);
                    line = new StringBuilder(word);
                }
                // very long single word: hard-split
                while (line.length() > limit) {
                    out.add(prefix + line.substring(0, limit));
                    line = new StringBuilder(line.substring(limit));
                }
            }
            if (line.length() > 0) {
                out.add(prefix + line);
            }
        }
        return out;
    }

    // --- panels ------------------------------------------------------------

    /** Rounded (Unicode) or plain (ASCII) box with a centered, letter-spaced
     *  title embedded in the top edge. */
    public void panel(String title, List<String> body) {
        String h = rule(unicode ? "─" : "-", Math.max(0, width - 2));
        String tl = unicode ? "╭" : "+";
        String tr = unicode ? "╮" : "+";
        String bl = unicode ? "╰" : "+";
        String br = unicode ? "╯" : "+";
        String v = unicode ? "│" : "|";

        boolean hasTitle = title != null && !title.isEmpty();
        System.out.println(ansi.dim(tl) + topEdge(h, title, hasTitle) + ansi.dim(tr));
        for (String raw : body) {
            for (String l : wrap(raw, width - 4)) {
                String visible = stripEscapes(l);
                int pad = Math.max(0, (width - 2) - visible.length());
                System.out.println(ansi.dim(v) + " " + l + " ".repeat(pad) + " " + ansi.dim(v));
            }
        }
        System.out.println(ansi.dim(bl + h + br));
    }

    /** Builds the top border including the centered title (colorized). */
    private String topEdge(String h, String title, boolean hasTitle) {
        if (!hasTitle) {
            return ansi.dim(h);
        }
        String label = " " + spaced(title) + " ";
        int inner = h.length();
        int visibleLabel = visible(label);
        if (visibleLabel >= inner - 2) {
            return ansi.bold(ansi.ember(label.substring(0, inner)));
        }
        int left = (inner - visibleLabel) / 2;
        int right = inner - visibleLabel - left;
        return ansi.dim(rule(unicode ? "─" : "-", left))
                + ansi.bold(ansi.ember(label))
                + ansi.dim(rule(unicode ? "─" : "-", right));
    }

    private String rule(String unit, int count) {
        return count <= 0 ? "" : unit.repeat(count);
    }

    /** "ENCOUNTER" -> "E N C O U N T E R" so titles read as engraved labels. */
    private String spaced(String title) {
        StringBuilder sb = new StringBuilder(title.length() * 2);
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (i > 0 && c != ' ') {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String stripEscapes(String s) {
        if (s == null || s.indexOf((char) 27) < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inEsc) {
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                    inEsc = false;
                }
                continue;
            }
            if (c == (char) 27) {
                inEsc = true;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static int visible(String s) {
        return stripEscapes(s == null ? "" : s).length();
    }

    // --- bars --------------------------------------------------------------

    /**
     * Tinted segmented bar, e.g. [██████░░░░] 72/100 — green when healthy,
     * amber when thinning, red when critical. Meaning never relies on color:
     * the bar always carries its numbers alongside.
     */
    public String bar(int current, int max, int segments) {
        int safeMax = Math.max(1, max);
        double ratio = (double) Math.max(0, Math.min(current, safeMax)) / safeMax;
        int filled = (int) Math.round(ratio * segments);
        filled = Math.max(current > 0 ? 1 : 0, Math.min(segments, filled));
        StringBuilder sb = new StringBuilder("[");
        sb.append(unicode ? "█".repeat(filled) : "#".repeat(filled));
        sb.append(unicode ? "░".repeat(segments - filled) : "-".repeat(segments - filled));
        sb.append("]");
        String bar = sb.toString();
        if (ratio > 0.5) {
            return ansi.green(bar);
        }
        if (ratio > 0.25) {
            return ansi.yellow(bar);
        }
        return ansi.red(bar);
    }

    public String hpLine(int current, int max) {
        return bar(current, max, 10) + " " + current + "/" + max;
    }

    // --- menus -------------------------------------------------------------

    /** Option cards — numbered, diamond-marked choices (agent-CLI style). */
    public void menu(List<String> options) {
        int pad = 0;
        for (String option : options) {
            pad = Math.max(pad, visible(option));
        }
        for (int i = 0; i < options.size(); i++) {
            String label = options.get(i);
            String gap = " ".repeat(Math.max(0, pad - visible(label)));
            System.out.println("    " + ansi.dim(unicode ? "◇" : "o") + " "
                    + ansi.cyan("[" + (i + 1) + "]") + " "
                    + ansi.bold(label) + gap);
        }
    }

    /** Compact status line above the prompt: e.g. ' ❰ The Hollow Nave · HP 34/40 · EXPLORATION ❱ '. */
    public void statusBar(List<String> chips) {
        if (chips == null || chips.isEmpty()) {
            return;
        }
        String joined = String.join(" · ", chips);
        String framed = unicode ? "❰ " + joined + " ❱" : "[ " + joined + " ]";
        System.out.println("  " + ansi.gray(framed));
    }

    /** 'Suggested next:' footer with recommended commands for the current context. */
    public void suggestFooter(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < suggestions.size(); i++) {
            if (i > 0) {
                sb.append(ansi.gray("  ·  "));
            }
            sb.append(ansi.white(suggestions.get(i)));
        }
        System.out.println("  " + ansi.gray(unicode ? "┄" : "~") + " "
                + ansi.bold(ansi.gray("Suggested next:")) + " " + sb);
    }

    public void hint(String text) {
        System.out.println(ansi.gray(text));
    }

    public void prompt() {
        System.out.print(ansi.ember(unicode ? "❯" : ">") + " ");
        System.out.flush();
    }
}
