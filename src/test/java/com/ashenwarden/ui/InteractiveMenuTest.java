package com.ashenwarden.ui;

import com.ashenwarden.cli.Ansi;
import com.ashenwarden.cli.TerminalRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Selection, wrapping, digits, Escape, '?' and in-place redraw of the menu. */
class InteractiveMenuTest {

    private static final List<String> OPTIONS = List.of("ALPHA", "BETA", "GAMMA", "DELTA");

    /** KeyInput replaying a fixed script of key presses. */
    private static final class ScriptedInput implements KeyInput {
        private final Deque<Key> keys;

        ScriptedInput(Key... keys) {
            this.keys = new ArrayDeque<>(List.of(keys));
        }

        @Override
        public boolean supportsKeys() {
            return true;
        }

        @Override
        public Key readKey() {
            Key key = keys.poll();
            return key == null ? Key.of(Key.Kind.EOF) : key;
        }

        @Override
        public String readLine() {
            return null;
        }

        @Override
        public void close() {
        }
    }

    /** Outcome plus everything the menu printed, captured from stdout. */
    private record Run(InteractiveMenu.Outcome outcome, String printed) {
    }

    /** Runs one menu against a key script, capturing its output. */
    private static Run run(KeyInput input) throws IOException {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        InteractiveMenu.Outcome outcome;
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            TerminalRenderer renderer = new TerminalRenderer(new Ansi(false), false, 80);
            outcome = new InteractiveMenu(renderer, input, "MAIN MENU", OPTIONS).select(0);
        } finally {
            System.setOut(original);
        }
        return new Run(outcome, buffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void enterConfirmsTheHighlightedOption() throws Exception {
        Run run = run(new ScriptedInput(Key.of(Key.Kind.ENTER)));
        InteractiveMenu.Outcome outcome = run.outcome();
        assertEquals(InteractiveMenu.Outcome.Kind.SELECTED, outcome.kind());
        assertEquals(0, outcome.index());
        for (String option : OPTIONS) {
            assertTrue(run.printed().contains(option), "menu must list " + option);
        }
    }

    @Test
    void downArrowMovesAndEnterPicksTheNewOption() throws Exception {
        Run run = run(new ScriptedInput(Key.of(Key.Kind.DOWN), Key.of(Key.Kind.DOWN),
                Key.of(Key.Kind.ENTER)));
        assertEquals(InteractiveMenu.Outcome.Kind.SELECTED, run.outcome().kind());
        assertEquals(2, run.outcome().index());
    }

    @Test
    void arrowsWrapAroundBothEnds() throws Exception {
        // one step up from the first option lands on the last one
        assertEquals(3, run(new ScriptedInput(Key.of(Key.Kind.UP), Key.of(Key.Kind.ENTER)))
                .outcome().index());
        // three ups then a down: 0 -> 3 -> 2 -> 1 -> 2
        assertEquals(2, run(new ScriptedInput(Key.of(Key.Kind.UP), Key.of(Key.Kind.UP),
                Key.of(Key.Kind.UP), Key.of(Key.Kind.DOWN), Key.of(Key.Kind.ENTER)))
                .outcome().index());
    }

    @Test
    void digitsJumpStraightToAnOption() throws Exception {
        assertEquals(3, run(new ScriptedInput(Key.digit('4'), Key.of(Key.Kind.ENTER)))
                .outcome().index());
    }

    @Test
    void outOfRangeDigitsAreIgnoredAndEscapeCancels() throws Exception {
        Run run = run(new ScriptedInput(Key.digit('9'), Key.of(Key.Kind.ESCAPE)));
        assertEquals(InteractiveMenu.Outcome.Kind.ESCAPE, run.outcome().kind());
        assertEquals(1, run.printed().split("ALPHA", -1).length - 1,
                "a rejected digit must not print a second menu");
    }

    @Test
    void movingTheHighlightRedrawsInPlaceInsteadOfScrolling() throws Exception {
        Run run = run(new ScriptedInput(Key.of(Key.Kind.DOWN), Key.of(Key.Kind.ESCAPE)));
        // panel (edge + 4 options + edge = 6) + air + legend = 8 printed lines
        assertTrue(run.printed().contains("\r\u001b[8A"),
                "redraw must step the cursor back over the whole menu region");
        assertTrue(run.printed().contains("\u001b[2K"), "each line is erased before it is rewritten");
        assertTrue(run.printed().contains("\u001b[0J"), "leftovers below the region are cleared");
    }

    @Test
    void helpKeyAndCtrlCReportBackToTheCaller() throws Exception {
        assertEquals(InteractiveMenu.Outcome.Kind.HELP,
                run(new ScriptedInput(Key.character('?'))).outcome().kind());
        assertEquals(InteractiveMenu.Outcome.Kind.CLOSED,
                run(new ScriptedInput(Key.of(Key.Kind.INTERRUPT))).outcome().kind());
        assertEquals(InteractiveMenu.Outcome.Kind.CLOSED,
                run(new ScriptedInput(Key.of(Key.Kind.EOF))).outcome().kind());
    }
}
