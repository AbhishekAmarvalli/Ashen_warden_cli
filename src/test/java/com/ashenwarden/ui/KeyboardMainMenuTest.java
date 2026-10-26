package com.ashenwarden.ui;

import com.ashenwarden.cli.Ansi;
import com.ashenwarden.cli.TerminalRenderer;
import com.ashenwarden.combat.CombatEngine;
import com.ashenwarden.combat.SkillCatalog;
import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameEngine;
import com.ashenwarden.enemy.BehaviorRegistry;
import com.ashenwarden.persistence.Database;
import com.ashenwarden.persistence.SaveService;
import com.ashenwarden.quest.QuestManager;
import com.ashenwarden.world.DialogueManager;
import com.ashenwarden.world.ExplorationService;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end boot-screen tests: a scripted terminal plays the interactive
 * main menu through the real GameEngine, and a plain (non-key) input proves
 * the typed-command menu still works unchanged. The database is pointed at a
 * dead port so the run is hermetic; the engine is expected to degrade to
 * "database unavailable" rather than fail.
 */
class KeyboardMainMenuTest {

    /** Full engine wired exactly like Main, against an unreachable database. */
    private static GameEngine engine(KeyInput input, TerminalRenderer renderer) throws Exception {
        BehaviorRegistry behaviors = new BehaviorRegistry();
        ContentRepository content = ContentRepository.loadDefault(behaviors);
        CombatEngine combat = new CombatEngine(content, behaviors, new SkillCatalog());
        SaveService saves = new SaveService(
                new Database("jdbc:mysql://127.0.0.1:1/none?connectTimeout=200&socketTimeout=500",
                        "none", ""),
                content);
        return new GameEngine(content, renderer, saves, combat,
                ExplorationService.of(content), new QuestManager(content),
                new DialogueManager(content), input);
    }

    @Test
    void arrowKeysDriveTheBootMenu() throws Exception {
        PipedOutputStream script = new PipedOutputStream();
        PipedInputStream pipe = new PipedInputStream(script, 4096);
        ByteArrayOutputStream terminalScreen = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder().streams(pipe, terminalScreen).build();
        KeyInput input = JLineKeyInput.over(terminal);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            GameEngine engine = engine(input, new TerminalRenderer(new Ansi(false), false, 80));
            // Down, Down, Enter -> HOW TO PLAY (prints the command help),
            // then Ctrl+D closes the game cleanly from the menu
            script.write(new byte[]{27, '[', 'B', 27, '[', 'B', '\r', 4});
            script.flush();
            engine.run();
        } finally {
            System.setOut(originalOut);
            terminal.close();
        }

        String out = printed.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("ASHEN WARDEN") && out.contains("A kingdom that could not die"),
                "boot screen must draw the title frame");
        assertTrue(out.contains("BEGIN JOURNEY") && out.contains("EXIT"),
                "every boot option must be listed");
        assertTrue(out.toUpperCase(java.util.Locale.ROOT).contains("MAIN MENU: [1] NEW GAME"),
                "HOW TO PLAY must print the command help");
        assertTrue(out.contains("Farewell, Warden."), "Ctrl+D must end the session cleanly");
    }

    @Test
    void withoutKeySupportTheTypedMenuStillWorks() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            GameEngine engine = engine(new ScriptedLineInput("help", "quit"),
                    new TerminalRenderer(new Ansi(false), false, 80));
            engine.run();
        } finally {
            System.setOut(originalOut);
        }
        String out = printed.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("[1] New Game"), "typed mode must still draw the menu card");
        assertTrue(out.toUpperCase(java.util.Locale.ROOT).contains("MAIN MENU: [1] NEW GAME"),
                "help must still answer");
        assertTrue(out.contains("Farewell, Warden."), "quit must still end the session");
    }

    /** Line-only input replaying typed commands (the no-console path). */
    private static final class ScriptedLineInput implements KeyInput {
        private final String[] lines;
        private int at;

        ScriptedLineInput(String... lines) {
            this.lines = lines;
        }

        @Override
        public boolean supportsKeys() {
            return false;
        }

        @Override
        public Key readKey() {
            return Key.of(Key.Kind.EOF);
        }

        @Override
        public String readLine() {
            return at < lines.length ? lines[at++] : null;
        }

        @Override
        public void close() {
        }
    }
}
