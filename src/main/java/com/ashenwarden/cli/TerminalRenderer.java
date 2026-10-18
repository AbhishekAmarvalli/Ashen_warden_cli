package com.ashenwarden.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure presentation layer: wraps text to the terminal width, draws lightweight
 * box panels, status bars and menus. Never mutates game state and never touches
 * SQL. Supports NO_COLOR / --plain (ASCII-only, no escapes).
 */
public final class TerminalRenderer {
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

    public void line(String text) {
        System.out.println(text == null ? "" : text);
    }

    public void blank() {
        System.out.println();
    }

    public void info(String text) { printWrapped(text, null); }

    public void success(String text) { printWrapped(ansi.green(text), null); }

    public void warn(String text) { printWrapped(ansi.yellow(text), null); }

    public void error(String text) { printWrapped(ansi.red(text), null); }

    public void dim(String text) { printWrapped(ansi.gray(text), null); }

    public void heading(String text) {
        System.out.println(ansi.bold(text));
    }

    private void printWrapped(String text, String ignored) {
        if (text == null) {
            System.out.println();
            return;
        }
        for (String l : wrap(text, width)) {
            System.out.println(l);
        }
    }

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

    public void panel(String title, List<String> body) {
        String h = horizontal();
        String open = unicode ? "+" + h + "+" : "+" + h + "+";
        System.out.println(ansi.dim(open));
        if (title != null && !title.isEmpty()) {
            System.out.println(ansi.dim(v()) + " " + ansi.bold(title) + paddingFor(title) + ansi.dim(v()));
        }
        for (String raw : body) {
            for (String l : wrap(raw, width - 4)) {
                String visible = stripEscapes(l);
                int pad = Math.max(0, (width - 2) - visible.length());
                System.out.println(ansi.dim(v()) + " " + l + " ".repeat(pad) + ansi.dim(v()));
            }
        }
        System.out.println(ansi.dim(open));
    }

    private String paddingFor(String title) {
        int visible = stripEscapes(title).length();
        int pad = Math.max(0, (width - 2) - visible - 2);
        return " ".repeat(pad);
    }

    private String horizontal() {
        return "=".repeat(Math.max(0, width - 2));
    }

    private String v() {
        return unicode ? "|" : "|";
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

    // --- bars --------------------------------------------------------------

    /** Segmented bar, e.g. [#######---] 72/100. Meaning never relies on color. */
    public String bar(int current, int max, int segments) {
        int safeMax = Math.max(1, max);
        int filled = (int) Math.round((double) Math.max(0, Math.min(current, safeMax)) / safeMax * segments);
        filled = Math.max(current > 0 ? 1 : 0, Math.min(segments, filled));
        StringBuilder sb = new StringBuilder("[");
        sb.append(unicode ? "█".repeat(filled) : "#".repeat(filled));
        sb.append("-".repeat(segments - filled));
        sb.append("]");
        return sb.toString();
    }

    public String hpLine(int current, int max) {
        return ansi.red(bar(current, max, 10)) + " " + current + "/" + max;
    }

    // --- menus -------------------------------------------------------------

    /** Renders a numbered menu: lines like "  [1] New Game". */
    public void menu(List<String> options) {
        for (int i = 0; i < options.size(); i++) {
            System.out.println("  " + ansi.cyan("[" + (i + 1) + "]") + " " + options.get(i));
        }
    }

    public void hint(String text) {
        System.out.println(ansi.gray(text));
    }

    public void prompt() {
        System.out.print(ansi.green(">") + " ");
        System.out.flush();
    }
}
