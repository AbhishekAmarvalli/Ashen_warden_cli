package com.ashenwarden;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ASHEN WARDEN: THE LAST EMBER — text-only CLI entry point.
 * Flags: --plain (ASCII + no colors), --no-color, --width <n>, --help.
 * No AI/LLM/network integration anywhere in this game; offline except local MySQL.
 */
public final class Main {

    public static void main(String[] args) {
        boolean plain = false;
        boolean color = true;
        int width = detectWidth();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--plain" -> {
                    plain = true;
                    color = false;
                }
                case "--no-color" -> color = false;
                case "--width" -> {
                    if (i + 1 < args.length) {
                        try {
                            width = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Ignoring invalid --width value.");
                        }
                    }
                }
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> System.err.println("Unknown flag ignored: " + args[i]);
            }
        }
        if (System.getenv("NO_COLOR") != null) {
            color = false;
        }
        if (plain) {
            color = false;
        }

        Ansi ansi = new Ansi(color);
        boolean unicodeArt = !plain && rendersUnicodeNatively();
        TerminalRenderer renderer = new TerminalRenderer(ansi, unicodeArt, width);

        try {
            BehaviorRegistry behaviors = new BehaviorRegistry();
            ContentRepository content = ContentRepository.loadDefault(behaviors);
            SkillCatalog skills = new SkillCatalog();
            CombatEngine combat = new CombatEngine(content, behaviors, skills);
            ExplorationService exploration = ExplorationService.of(content);
            QuestManager quests = new QuestManager(content);
            DialogueManager dialogues = new DialogueManager(content);
            Database database = Database.fromEnv();
            SaveService saves = new SaveService(database, content);

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            GameEngine engine = new GameEngine(content, renderer, saves, combat,
                    exploration, quests, dialogues, in);
            engine.run();
        } catch (com.ashenwarden.common.ContentException e) {
            renderer.error("Content error: " + e.getMessage());
            renderer.dim("Reinstall/verify the game files; this is a packaging problem, not your save.");
            System.exit(2);
        } catch (RuntimeException e) {
            renderer.error("Startup failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            System.exit(3);
        }
    }

    /** Ornament glyphs the renderer draws; the console must be able to encode them. */
    private static final String ORNAMENT_GLYPHS = "─│╭╮╰╯█░▌❯◆❰❱┄·—…";

    /**
     * Whether this console renders Unicode ornament glyphs (─ │ ╭ █ ▌ ❯ ◆ ❰ ┄)
     * as single-width, presentable text. Windows Terminal and other modern
     * terminals do; the legacy CMD console (conhost) draws many of them
     * double-width or as boxes, so the game falls back to guaranteed-clean
     * ASCII ornaments there. Only the decorations change — every message
     * keeps its meaning in words.
     */
    private static boolean rendersUnicodeNatively() {
        if (!stdoutEncodes(ORNAMENT_GLYPHS)) {
            // console code page (Cp437/Cp850/…) cannot draw the glyphs at all
            return false;
        }
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return true;
        }
        return System.getenv("WT_SESSION") != null
                || System.getenv("TERM_PROGRAM") != null
                || System.getenv("WT_PROFILE_ID") != null;
    }

    /**
     * True when the charset used to write stdout can represent the sample.
     * On Windows the default is the console code page, where many glyphs are
     * missing; play.bat pins stdout to UTF-8, and this check still refuses
     * any console that could not draw them.
     */
    private static boolean stdoutEncodes(String sample) {
        try {
            String name = System.getProperty("stdout.encoding",
                    System.getProperty("file.encoding", "UTF-8"));
            java.nio.charset.Charset charset = java.nio.charset.Charset.forName(name);
            return charset.newEncoder().canEncode(sample);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int detectWidth() {
        try {
            // COLUMNS may be exported by the terminal; fall back to 80
            String cols = System.getenv("COLUMNS");
            if (cols != null && cols.matches("\\d{2,4}")) {
                return Math.max(40, Math.min(120, Integer.parseInt(cols)));
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        // Windows CMD/PowerShell: ask the console itself, so panels fill the
        // window instead of assuming 80 columns.
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            int consoleCols = windowsConsoleColumns();
            if (consoleCols > 0) {
                return consoleCols;
            }
        }
        return 80;
    }

    /**
     * Reads the column count from {@code mode con}. The numeric row order is
     * stable across locales (lines-per-row, columns, keyboard rate, delay),
     * so the second number is the width. Returns -1 when there is no console
     * (piped input, IDE runner) or the command failed.
     */
    private static int windowsConsoleColumns() {
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", "mode", "con")
                    .redirectErrorStream(true)
                    .start();
            // mode con exits immediately; wait first so reading never blocks
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            List<Integer> values = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2,4})\\s*$").matcher("");
                String line;
                while ((line = reader.readLine()) != null) {
                    if (m.reset(line).find()) {
                        values.add(Integer.parseInt(m.group(1)));
                    }
                }
            }
            if (values.size() >= 2) {
                int columns = values.get(1);
                if (columns >= 40) {
                    return Math.min(120, columns);
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // no console (piped/IDE) — caller falls back to 80
        }
        return -1;
    }

    private static void printUsage() {
        // plain ASCII on purpose: this line must survive any console code page
        System.out.println("ASHEN WARDEN: THE LAST EMBER - text-only turn-based RPG");
        System.out.println("Usage: java -jar ashen-warden.jar [--plain] [--no-color] [--width <40-120>]");
        System.out.println("Environment: DB_URL, DB_USER, DB_PASSWORD (local MySQL, no credentials committed)");
    }

    private Main() {
    }
}
