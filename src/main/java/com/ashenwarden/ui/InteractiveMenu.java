package com.ashenwarden.ui;

import com.ashenwarden.cli.Ansi;
import com.ashenwarden.cli.TerminalRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A selectable panel drawn inside the terminal and navigated with the
 * keyboard: arrows move the highlight, digits jump straight to an option,
 * Enter confirms, Escape leaves the screen untouched, '?' asks the caller
 * for the help screen. While it waits for a key it redraws itself in place
 * (cursor up + rewrite), so browsing options never scrolls the window.
 *
 * <p>Contract with the caller: the menu must be the last thing printed on
 * screen while {@link #select(int)} runs — anything printed after it belongs
 * below the menu region and the next draw must start a fresh frame.</p>
 */
public final class InteractiveMenu {

    private static final char ESC = 27;

    /** What the player asked for; {@code index} is only set for SELECTED. */
    public record Outcome(Kind kind, int index) {
        public enum Kind {
            /** Enter (or a digit): the option at {@code index} was chosen. */
            SELECTED,
            /** Escape: close the screen without any effect. */
            ESCAPE,
            /** '?': the caller should show the help screen. */
            HELP,
            /** Ctrl+C / Ctrl+D / closed input: stop cleanly. */
            CLOSED
        }

        static Outcome of(Kind kind) {
            return new Outcome(kind, -1);
        }

        static Outcome selected(int index) {
            return new Outcome(Kind.SELECTED, index);
        }
    }

    private final TerminalRenderer renderer;
    private final KeyInput input;
    private final String title;
    private final List<String> options;

    /** Lines printed by the last draw, so a redraw can cursor back over them. */
    private int regionLines;

    public InteractiveMenu(TerminalRenderer renderer, KeyInput input,
                           String title, List<String> options) {
        this.renderer = renderer;
        this.input = input;
        this.title = title;
        this.options = List.copyOf(options);
    }

    /**
     * Draws the menu, then reads keys until the player decides. Returns the
     * decision; never returns null. {@code initial} is the option highlighted
     * on the first draw (out-of-range values select the first option).
     */
    public Outcome select(int initial) throws IOException {
        int selected = clamp(initial);
        draw(selected);
        while (true) {
            Key key = input.readKey();
            switch (key.kind()) {
                case UP -> {
                    selected = wrap(selected - 1);
                    redraw(selected);
                }
                case DOWN -> {
                    selected = wrap(selected + 1);
                    redraw(selected);
                }
                case DIGIT -> {
                    int number = key.value() - '0';
                    if (number >= 1 && number <= options.size()) {
                        return Outcome.selected(number - 1);
                    }
                }
                case ENTER -> {
                    return Outcome.selected(selected);
                }
                case ESCAPE -> {
                    return Outcome.of(Outcome.Kind.ESCAPE);
                }
                case CHARACTER -> {
                    if (key.value() == '?') {
                        return Outcome.of(Outcome.Kind.HELP);
                    }
                }
                case EOF, INTERRUPT -> {
                    return Outcome.of(Outcome.Kind.CLOSED);
                }
                default -> {
                    // any other key (function keys, sideways arrows...) changes nothing
                }
            }
        }
    }

    /** Prints the menu fresh, as the last lines on screen. */
    public void draw(int selected) {
        List<String> lines = render(clamp(selected));
        for (String line : lines) {
            System.out.println(line);
        }
        regionLines = lines.size();
    }

    /** Rewrites the menu region in place: no scroll, no flicker. */
    private void redraw(int selected) {
        List<String> lines = render(clamp(selected));
        StringBuilder out = new StringBuilder();
        if (regionLines > 0) {
            // the cursor sits one line below the region: step back over it
            out.append('\r').append(ESC).append('[').append(regionLines).append('A');
        }
        for (String line : lines) {
            out.append('\r').append(ESC).append("[2K").append(line).append('\n');
        }
        out.append(ESC).append("[0J"); // wipe anything left over below the region
        System.out.print(out);
        System.out.flush();
        regionLines = lines.size();
    }

    /** The full region: bordered option panel, air, control legend. */
    private List<String> render(int selected) {
        List<String> body = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            body.add(optionLine(i, i == selected));
        }
        List<String> lines = new ArrayList<>(renderer.panelLines(title, body));
        lines.add("");
        lines.add(legend());
        return lines;
    }

    /** {@code ❯ [1] BEGIN JOURNEY} selected, {@code   [2] CONTINUE} otherwise. */
    private String optionLine(int index, boolean selected) {
        Ansi ansi = renderer.ansi();
        String key = "[" + (index + 1) + "]";
        if (!selected) {
            return "  " + ansi.cyan(key) + " " + options.get(index);
        }
        String marker = renderer.isUnicode() ? "❯ " : "> ";
        return ansi.bold(ansi.ember(marker + key + " " + options.get(index)));
    }

    /** One-line key legend, always short enough not to soft-wrap. */
    private String legend() {
        String arrows = renderer.isUnicode() ? "↑ ↓" : "Up/Down";
        String text = renderer.width() < 56
                ? "  " + arrows + "   ENTER select   ? help"
                : "  " + arrows + " move   ENTER select   1-" + options.size() + " jump   ? help";
        return renderer.ansi().dim(text);
    }

    private int clamp(int index) {
        if (index < 0 || index >= options.size()) {
            return 0;
        }
        return index;
    }

    private int wrap(int index) {
        int size = options.size();
        return ((index % size) + size) % size;
    }
}
