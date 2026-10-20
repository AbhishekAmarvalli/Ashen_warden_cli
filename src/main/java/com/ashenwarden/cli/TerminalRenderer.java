package com.ashenwarden.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure presentation layer: wraps text to the terminal width, draws title
 * banners, box panels, section rules, status bars and menus. Never mutates
 * game state and never touches SQL.
 *
 * <p>Layout contract — the "clean console" look:</p>
 * <ul>
 *   <li>every reply line is indented two columns below the prompt, so a turn
 *       reads as one block;</li>
 *   <li>no printed line is ever wider than the configured terminal width —
 *       wrap limits always account for indent, hanging prefixes and panel
 *       borders;</li>
 *   <li>blank lines are truly blank (never trailing spaces);</li>
 *   <li>when Unicode ornaments are off (legacy CMD, {@code --plain}) every
 *       typographic character the game emits is folded to plain ASCII, so
 *       nothing can render as a box.</li>
 * </ul>
 * Supports NO_COLOR / --plain (ASCII-only, no escapes) — meaning always
 * travels in words, color only decorates.
 */
public final class TerminalRenderer {

    /** Reply lines start two columns in, below the {@code > } prompt. */
    private static final int INDENT = 2;

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
            {"█   █", " █ █ ", "  █  ", " █ █ ", "  █  "}, // Y
            {"█████", "   █ ", "  █  ", " █   ", "█████"}, // Z
    };

    /** Fading ember gradient applied row-by-row to the banner. */
    private static final String[] GRADIENT = {
            Ansi.EMBER_1, Ansi.EMBER_2, Ansi.EMBER_2, Ansi.EMBER_3, Ansi.EMBER_4
    };

    /** Lines like "[THE HOLLOW NAVE]  ..." are location headers. */
    private static final Pattern HEADER_LINE =
            Pattern.compile("^\\[([A-Z][A-Z0-9 .,'!?\\-]*)\\](.*)$", Pattern.DOTALL);

    private static final char ESC = 27;

    private final Ansi ansi;
    private boolean unicode;
    private int width;

    public TerminalRenderer(Ansi ansi, boolean unicode, int width) {
        this.ansi = ansi;
        this.unicode = unicode;
        this.width = Math.max(40, Math.min(120, width));
    }

    public Ansi ansi() { return ansi; }
    public int width() { return width; }
    public boolean isUnicode() { return unicode; }

    public void setUnicode(boolean unicode) { this.unicode = unicode; }

    public void setWidth(int width) {
        this.width = Math.max(40, Math.min(120, width));
    }

    // --- primitives --------------------------------------------------------

    /** Raw pass-through: prints exactly what it is given (frames only). */
    public void line(String text) {
        System.out.println(text == null ? "" : text);
    }

    public void blank() {
        System.out.println();
    }

    public void info(String text) { body(text, null); }

    public void success(String text) { body(text, Ansi.GREEN); }

    public void warn(String text) { body(text, Ansi.YELLOW); }

    public void error(String text) { body(text, Ansi.RED); }

    public void dim(String text) { body(text, Ansi.GRAY); }

    /** Small print under a screen (commands, tips) — same block as the reply. */
    public void hint(String text) { body(text, Ansi.GRAY); }

    // --- body printing -----------------------------------------------------

    /** One reply line: wrapped to the terminal, indented by {@link #INDENT}. */
    private void body(String text, String color) {
        paint(text, color, INDENT, 0);
    }

    /**
     * Core printer. Wraps so that no line exceeds the configured width,
     * prefixes every line with {@code indent} spaces and continuation lines
     * with {@code hang} extra columns. Blank input prints a blank line.
     */
    private void paint(String text, String color, int indent, int hang) {
        if (text == null || text.isEmpty()) {
            System.out.println();
            return;
        }
        int limit = Math.max(20, width - indent - hang);
        List<String> lines = wrap(prep(text), limit);
        String pad = " ".repeat(indent);
        String hangPad = " ".repeat(indent + hang);
        boolean first = true;
        for (String l : lines) {
            if (l.isEmpty()) {
                System.out.println();
            } else {
                System.out.println((first ? pad : hangPad)
                        + (color == null ? l : ansi.color(l, color)));
            }
            first = false;
        }
    }

    /**
     * Narrative output: [LOCATION] headers get the ember treatment plus a
     * hanging indent, meta notes like [autosaved] fade back, everything else
     * stays calm and unwrapped-by-color.
     */
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
        int headWidth = visible(head);
        if (width - INDENT - headWidth < 24) {
            // terminal too narrow for a hanging header: own line, then normal text
            System.out.println(" ".repeat(INDENT) + head);
            info(m.group(2));
            return;
        }
        // wrap the rest narrow enough that header + text still fits the window
        List<String> lines = wrap(prep(m.group(2)), Math.max(20, width - INDENT - headWidth));
        String pad = " ".repeat(INDENT);
        String hangPad = " ".repeat(INDENT + headWidth);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.isEmpty()) {
                System.out.println();
            } else if (i == 0) {
                System.out.println(pad + head + l);
            } else {
                System.out.println(hangPad + l);
            }
        }
    }

    /**
     * Packed key legend (e.g. the combat actions). Each "[n] Action" pair is
     * kept whole and pairs are packed to the terminal width, so the legend
     * never soft-wraps into a ragged second line.
     */
    public void keyHints(List<String> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return;
        }
        int avail = Math.max(20, width - INDENT);
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        for (String pair : pairs) {
            String prepared = prep(pair);
            int pairWidth = visible(prepared);
            boolean firstOnLine = currentWidth == 0;
            if (!firstOnLine && currentWidth + 2 + pairWidth > avail) {
                System.out.println(" ".repeat(INDENT) + current);
                current = new StringBuilder();
                currentWidth = 0;
                firstOnLine = true;
            }
            if (!firstOnLine) {
                current.append("  ");
                currentWidth += 2;
            }
            current.append(colorPair(prepared));
            currentWidth += pairWidth;
        }
        if (current.length() > 0) {
            System.out.println(" ".repeat(INDENT) + current);
        }
    }

    /** "[1] Attack" -> cyan key, white label (color stays supplemental). */
    private String colorPair(String pair) {
        int close = pair.indexOf(']');
        if (pair.startsWith("[") && close > 0 && close + 1 < pair.length()) {
            return ansi.cyan(pair.substring(0, close + 1))
                    + " " + ansi.white(pair.substring(close + 1).stripLeading());
        }
        return ansi.white(pair);
    }

    // --- brand header -------------------------------------------------------

    /**
     * Logo bar pinned at the very top of the window, the way a site header
     * sits above the page: {@code ◆ ASHEN WARDEN · THE LAST EMBER ───────}.
     * Also sets the window/tab title so the logo shows above the text area.
     */
    public void header() {
        consoleTitle();
        String logo = (unicode ? "◆ " : "* ") + "ASHEN WARDEN";
        String tag = (unicode ? " · " : " - ") + "THE LAST EMBER";
        int used = visible(logo) + visible(tag);
        int rest = width - used - 1;
        String tail = rest > 1 ? " " + ansi.dim(rule(unicode ? "─" : "-", rest)) : "";
        System.out.println(ansi.bold(ansi.ember(logo)) + ansi.gray(tag) + tail);
    }

    /** Sets the terminal window title (real consoles only, never in captures). */
    private void consoleTitle() {
        if (!ansi.isEnabled() || System.console() == null) {
            return;
        }
        System.out.print((char) 27 + "]0;ASHEN WARDEN: THE LAST EMBER" + (char) 7);
    }

    // --- turn framing -------------------------------------------------------

    /**
     * Opens an assistant block, chat-interface style:
     * {@code ◆ ASHEN WARDEN ────────────────────}. The rule fills the window,
     * so every reply reads as one tagged block alternating with the player's
     * own prompt line above it.
     */
    public void beginBlock() {
        String label = (unicode ? "◆ " : "* ") + "ASHEN WARDEN";
        int used = visible(label);
        int rest = width - used - 1;
        String tail = rest > 1 ? " " + ansi.dim(rule(unicode ? "─" : "-", rest)) : "";
        System.out.println(ansi.bold(ansi.ember(label)) + tail);
    }

    // --- section rules -----------------------------------------------------

    /** "── LABEL ────────────" in ember, exactly one terminal wide. */
    public void section(String label) {
        String text = prep(label == null ? "" : label).toUpperCase(java.util.Locale.ROOT);
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

    /** "LABEL ────────" in ember. */
    public void heading(String text) {
        String label = prep(text == null ? "" : text);
        int rule = Math.max(2, width - visible(label) - 1);
        System.out.println(ansi.bold(ansi.ember(label)) + " "
                + ansi.dim(rule(unicode ? "─" : "-", rule)));
    }

    /** Full-width light divider. */
    public void divider() {
        System.out.println(ansi.dim(rule(unicode ? "─" : "-", width)));
    }

    // --- title banner ------------------------------------------------------

    /**
     * Big block-letter banner, vertically tinted like dying embers. Falls back
     * to a centered bold title when the terminal is too narrow for the art
     * (or when Unicode/color is switched off).
     */
    public void banner(String text) {
        String upper = prep(text).toUpperCase(java.util.Locale.ROOT);
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
                sb.append(glyph(c, row)).append(' ');
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
        centered(prep(text).toUpperCase(java.util.Locale.ROOT), t -> ansi.bold(ansi.ember(t)));
    }

    /** Centered dim subtitle; wraps (centered per line) on narrow terminals. */
    public void subLine(String text) {
        centered(prep(text), t -> ansi.gray(t));
    }

    /** Prints a centered line, wrapping to the terminal width when needed. */
    private void centered(String text, java.util.function.Function<String, String> style) {
        for (String l : wrap(text, width)) {
            if (l.isEmpty()) {
                System.out.println();
                continue;
            }
            int pad = Math.max(0, (width - visible(l)) / 2);
            System.out.println(" ".repeat(pad) + style.apply(l));
        }
    }

    // --- word wrapping -----------------------------------------------------

    /**
     * Word-wraps to {@code maxWidth} without destroying indentation and
     * without counting ANSI escape sequences towards the width. Escape
     * sequences are never split across lines; a single word longer than the
     * width is hard-split on visible characters.
     */
    public List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        int limit = Math.max(8, maxWidth);
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            int indent = paragraph.length() - paragraph.stripLeading().length();
            String prefix = " ".repeat(Math.min(indent, 8));
            int avail = Math.max(8, limit - prefix.length());
            String line = "";
            int lineVisible = 0;
            boolean anyWord = false;
            for (String word : paragraph.strip().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                anyWord = true;
                int wordVisible = visible(word);
                if (lineVisible > 0 && lineVisible + 1 + wordVisible <= avail) {
                    line = line + " " + word;
                    lineVisible += 1 + wordVisible;
                    continue;
                }
                if (lineVisible > 0) {
                    out.add(prefix + line);
                    line = "";
                    lineVisible = 0;
                }
                List<String> chunks = wordVisible <= avail
                        ? List.of(word) : hardSplit(word, avail);
                for (int i = 0; i < chunks.size() - 1; i++) {
                    out.add(prefix + chunks.get(i));
                }
                line = chunks.get(chunks.size() - 1);
                lineVisible = visible(line);
            }
            if (lineVisible > 0) {
                out.add(prefix + line);
            } else if (!anyWord) {
                out.add(""); // whitespace-only paragraph stays a blank line
            }
        }
        return out;
    }

    /** Splits an over-long word, never cutting an ANSI escape sequence in half. */
    private static List<String> hardSplit(String word, int avail) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int vis = 0;
        int i = 0;
        while (i < word.length()) {
            char c = word.charAt(i);
            if (c == ESC) {
                int j = i + 1;
                while (j < word.length() && !Character.isLetter(word.charAt(j))) {
                    j++;
                }
                if (j < word.length()) {
                    j++; // keep the terminating byte with the sequence
                }
                current.append(word, i, j);
                i = j;
                continue;
            }
            if (vis == avail) {
                chunks.add(current.toString());
                current = new StringBuilder();
                vis = 0;
            }
            current.append(c);
            vis++;
            i++;
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks.isEmpty() ? List.of(word) : chunks;
    }

    // --- panels ------------------------------------------------------------

    /**
     * Rounded (Unicode) or plain (ASCII) box with a centered, letter-spaced
     * title embedded in the top edge. Titles that do not fit the edge move
     * inside the card as a heading, so a card is never wider than the window
     * and never loses its title.
     */
    public void panel(String title, List<String> body) {
        String unit = unicode ? "─" : "-";
        int inner = Math.max(12, width - 2); // columns between the corners
        String h = rule(unit, inner);
        String tl = unicode ? "╭" : "+";
        String tr = unicode ? "╮" : "+";
        String bl = unicode ? "╰" : "+";
        String br = unicode ? "╯" : "+";
        String v = unicode ? "│" : "|";

        boolean hasTitle = title != null && !title.isEmpty();
        String edgeTitle = null;
        String insideTitle = null;
        if (hasTitle) {
            // engraved labels read as caps, like the section rules
            String plain = prep(title).toUpperCase(java.util.Locale.ROOT);
            String spaced = spaced(plain);
            // the edge needs the label plus room for rules on both sides
            if (visible(spaced) + 6 <= inner) {
                edgeTitle = spaced;
            } else if (visible(plain) + 6 <= inner) {
                edgeTitle = plain;
            } else {
                insideTitle = plain; // narrow window: heading inside the card
            }
        }
        System.out.println(ansi.dim(tl) + topEdge(h, edgeTitle) + ansi.dim(tr));
        if (insideTitle != null) {
            for (String l : wrap(insideTitle, inner - 2)) {
                panelLine(v, ansi.bold(ansi.ember(l)), l, inner);
            }
        }
        for (String raw : body) {
            for (String l : wrap(prep(raw), inner - 2)) {
                panelLine(v, l, l, inner);
            }
        }
        System.out.println(ansi.dim(bl + h + br));
    }

    /** Titled card — the tool-result look for screens (inventory, journal...). */
    public void card(String title, List<String> body) {
        panel(title, body);
    }

    /** One padded row inside a card: {@code | content ......... |}. */
    private void panelLine(String v, String text, String raw, int inner) {
        String visible = stripEscapes(raw);
        int pad = Math.max(0, (inner - 2) - visible.length());
        System.out.println(ansi.dim(v) + " " + text + " ".repeat(pad) + " " + ansi.dim(v));
    }

    /** Builds the top border including the centered title (colorized), or the
     *  plain rule when {@code label} is null. */
    private String topEdge(String h, String label) {
        if (label == null) {
            return ansi.dim(h);
        }
        String framed = " " + label + " ";
        int inner = h.length();
        int visibleLabel = visible(framed);
        int left = (inner - visibleLabel) / 2;
        int right = inner - visibleLabel - left;
        return ansi.dim(rule(unicode ? "─" : "-", left))
                + ansi.bold(ansi.ember(framed))
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
        if (s == null || s.indexOf(ESC) < 0) {
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
            if (c == ESC) {
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

    // --- plain-ASCII fallback ----------------------------------------------

    /** Prepares text for the current ornament mode (folds to ASCII when off). */
    private String prep(String text) {
        if (text == null || unicode) {
            return text;
        }
        return fold(text);
    }

    /**
     * Legacy CMD fallback: the typographic characters used by the game and its
     * content are replaced by ASCII equivalents, so nothing can render as a
     * box or as mojibake in a console with a different code page. Unknown
     * characters (player-typed names) are left alone.
     */
    private static String fold(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 128) {
                sb.append(c);
                continue;
            }
            switch (c) {
                case '—', '–', '―', '−' -> sb.append('-');   // dashes
                case '·', '•' -> sb.append('-');             // separators
                case '…' -> sb.append("...");                // ellipsis
                case '\u2018', '\u2019' -> sb.append('\'');   // curly single quotes
                case '\u201C', '\u201D' -> sb.append('"');    // curly double quotes
                case '×' -> sb.append('x');
                case '❯' -> sb.append('>');
                default -> sb.append(c);                     // never mangle names
            }
        }
        return sb.toString();
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

    /** Numbered option lines — plain glyphs that render cleanly everywhere. */
    public void menu(List<String> options) {
        String pad = " ".repeat(INDENT + 2);
        for (int i = 0; i < options.size(); i++) {
            String label = prep(options.get(i));
            System.out.println(pad + ansi.cyan("[" + (i + 1) + "]") + " "
                    + ansi.bold(label));
        }
    }

    /** Compact status line above the prompt, e.g. '❰ Slot · Tomb · HP 34/40 · EXPLORATION ❱'. */
    public void statusBar(List<String> chips) {
        if (chips == null || chips.isEmpty()) {
            return;
        }
        String joined = String.join(unicode ? " · " : " | ", chips);
        String framed = unicode ? "❰ " + joined + " ❱" : "[ " + joined + " ]";
        int max = width - INDENT;
        if (visible(framed) > max) {
            framed = truncate(framed, max); // drop the tail, keep it one line
        }
        body(framed, Ansi.GRAY);
    }

    /** Cuts a single line to {@code max} visible columns, appending an ellipsis. */
    private String truncate(String text, int max) {
        if (max <= 4) {
            return text.substring(0, Math.min(text.length(), max));
        }
        String suffix = unicode ? "…" : "...";
        int budget = max - suffix.length();
        StringBuilder sb = new StringBuilder();
        int vis = 0;
        int i = 0;
        while (i < text.length() && vis < budget) {
            char c = text.charAt(i);
            if (c == ESC) {
                int j = i + 1;
                while (j < text.length() && !Character.isLetter(text.charAt(j))) {
                    j++;
                }
                if (j < text.length()) {
                    j++;
                }
                sb.append(text, i, j);
                i = j;
                continue;
            }
            sb.append(c);
            vis++;
            i++;
        }
        return sb.append(suffix).toString();
    }

    /** 'Suggested:' footer with recommended commands for the current context. */
    public void suggestFooter(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        String sep = unicode ? "  ·  " : "  |  ";
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < suggestions.size(); i++) {
            if (i > 0) {
                items.append(sep);
            }
            items.append(prep(suggestions.get(i)));
        }
        String marker = unicode ? "┄ " : "~ ";
        body(ansi.dim(marker) + ansi.bold(ansi.gray("Suggested:")) + " "
                + ansi.white(items.toString()), null);
    }

    public void prompt() {
        System.out.print(ansi.ember(unicode ? "❯" : ">") + " ");
        System.out.flush();
    }
}
