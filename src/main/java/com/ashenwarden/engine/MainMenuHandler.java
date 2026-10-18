package com.ashenwarden.engine;

import com.ashenwarden.command.ParsedCommand;
import com.ashenwarden.persistence.SaveSlotInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Main-menu commands. Numbers 1..5 mirror the visible menu; every DB failure
 * shows recovery guidance instead of a stack trace.
 */
final class MainMenuHandler {
    private MainMenuHandler() {
    }

    static CommandResult handle(GameEngine engine, ParsedCommand cmd) {
        return switch (cmd.type()) {
            case NEW -> newGame(engine);
            case CONTINUE -> openSlots(engine, "Continue — choose a save slot:");
            case LOAD -> openSlots(engine, "Load slot:");
            case SETTINGS -> openSettings(engine);
            case HELP -> CommandResult.ok().lines(mainHelp());
            case QUIT -> CommandResult.ok().line("The flame dims until you return.").quit();
            case SELECT -> {
                int n = cmd.number();
                if (n == 1) yield newGame(engine);
                if (n == 2) yield openSlots(engine, "Continue — choose a save slot:");
                if (n == 3) yield openSlots(engine, "Load slot:");
                if (n == 4) yield openSettings(engine);
                if (n == 5) yield CommandResult.ok().line("The flame dims until you return.").quit();
                yield CommandResult.rejected("Choose 1-5 from the menu (new | continue | load | settings | exit).");
            }
            default -> CommandResult.rejected(
                    "In the main menu use: new | continue | load | settings | help | quit (or a number 1-5).");
        };
    }

    private static CommandResult newGame(GameEngine engine) {
        if (!engine.isDbAvailable()) {
            return CommandResult.rejected("New Game needs the database to create a save slot.\n"
                    + engine.saves().guidance());
        }
        engine.requestRawPrompt(GameEngine.RawPrompt.SLOT_NAME);
        return CommandResult.ok().line("Name your save slot (1-40 characters, e.g. 'ash-1').");
    }

    static CommandResult openSlots(GameEngine engine, String title) {
        if (!engine.isDbAvailable()) {
            return CommandResult.rejected("Cannot read save slots.\n" + engine.saves().guidance());
        }
        List<SaveSlotInfo> slots;
        try {
            slots = engine.saves().listSlots();
        } catch (RuntimeException e) {
            return CommandResult.rejected("Could not read save slots: " + e.getMessage()
                    + "\n" + engine.saves().guidance());
        }
        if (slots.isEmpty()) {
            return CommandResult.ok().line("No save slots exist yet. Choose [1] New Game first.");
        }
        GameEngine.MenuContext ctx = new GameEngine.MenuContext(GameEngine.MenuContext.Kind.LOAD_LIST);
        ctx.slots.addAll(slots);
        engine.setMenu(ctx);
        CommandResult out = CommandResult.ok().line(title);
        for (int i = 0; i < slots.size(); i++) {
            SaveSlotInfo slot = slots.get(i);
            out.line("  [" + (i + 1) + "] " + slot.slotName() + "  —  " + slot.playerName()
                    + ", " + slot.chapter() + ", " + slot.locationCode()
                    + "  (updated " + slot.updatedAt() + ", v" + slot.gameVersion() + ")");
        }
        return out;
    }

    private static CommandResult openSettings(GameEngine engine) {
        GameEngine.MenuContext ctx = new GameEngine.MenuContext(GameEngine.MenuContext.Kind.SETTINGS);
        engine.setMenu(ctx);
        return CommandResult.ok().lines(settingsLines(engine));
    }

    static List<String> settingsLines(GameEngine engine) {
        List<String> out = new ArrayList<>();
        out.add("SETTINGS");
        out.add("  [1] Toggle color (currently: " + (engine.renderer().ansi().isEnabled() ? "ON" : "OFF") + ")");
        out.add("  [2] Toggle Unicode box drawing (currently: "
                + (engine.renderer().isUnicode() ? "ON" : "ASCII" ) + ")");
        out.add("  [3] Terminal width: " + engine.renderer().width() + "  (type 'width <40-120>')");
        out.add("  [4] Back");
        out.add("  NO_COLOR / --plain are honored at launch; colors are never the only signal.");
        return out;
    }

    static List<String> mainHelp() {
        return List.of(
                "MAIN MENU: [1] New Game  [2] Continue  [3] Load Slot  [4] Settings  [5] Exit",
                "During play you will use:",
                "  Exploration: look | go <way> | map | paths | inspect <thing> | interact <thing>",
                "               search | talk <who> | ask <topic> | rest | journal | quests",
                "               memories | abilities | inventory | equipment | stats | save | load",
                "  Combat:      attack | heavy | defend | parry | skill <name> | item <name>",
                "               inspect enemy | inspect item <name> | status | help | log | flee",
                "  Every menu accepts numbers, 'inspect <n>', 'compare <n>' and 'back'.",
                "  Invalid input is always safe: no turn is ever spent by a mistake."
        );
    }
}
