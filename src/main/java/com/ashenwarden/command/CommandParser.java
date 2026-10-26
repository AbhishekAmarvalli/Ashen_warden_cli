package com.ashenwarden.command;

import com.ashenwarden.command.ParsedCommand.Type;
import com.ashenwarden.engine.GamePhase;

import java.util.Locale;
import java.util.Set;

/**
 * Turns raw input into a typed command. No business logic lives here.
 * Rules: trimmed, case-insensitive keyword, bounded input length, documented
 * aliases, phase-aware ('use' is a combat item in combat, interact elsewhere).
 * Invalid input maps to UNKNOWN and never crashes or consumes a turn.
 */
public final class CommandParser {
    public static final int MAX_INPUT = 160;

    private static final Set<String> DIRECTIONS = Set.of(
            "north", "south", "east", "west", "up", "down", "left", "right", "in", "out");

    public ParsedCommand parse(String raw, GamePhase phase) {
        if (raw == null) {
            return new ParsedCommand(Type.UNKNOWN, "");
        }
        String line = raw.strip();
        if (line.length() > MAX_INPUT) {
            line = line.substring(0, MAX_INPUT);
        }
        if (line.isEmpty()) {
            return new ParsedCommand(Type.UNKNOWN, "");
        }
        String lower = line.toLowerCase(Locale.ROOT);

        // pure number = menu selection
        if (lower.matches("\\d{1,4}")) {
            return new ParsedCommand(Type.SELECT, lower);
        }
        // bare direction: "north" == "go north"
        int firstSpace = lower.indexOf(' ');
        String head = firstSpace < 0 ? lower : lower.substring(0, firstSpace);
        String rest = firstSpace < 0 ? "" : line.substring(firstSpace + 1).strip();

        if (DIRECTIONS.contains(head) && firstSpace < 0) {
            return new ParsedCommand(Type.GO, head);
        }

        switch (head) {
            // --- main menu / global ---
            case "new":
                return new ParsedCommand(Type.NEW, rest);
            case "continue":
                return new ParsedCommand(Type.CONTINUE, "");
            case "load":
                return new ParsedCommand(Type.LOAD, rest);
            case "settings":
                return new ParsedCommand(Type.SETTINGS, "");
            case "save":
                return new ParsedCommand(Type.SAVE, rest);
            case "quit":
            case "exit":
                return new ParsedCommand(Type.QUIT, "");
            case "help":
            case "?":
                return new ParsedCommand(Type.HELP, rest);

            // --- exploration ---
            case "look":
            case "l":
                return new ParsedCommand(Type.LOOK, rest);
            case "go":
            case "move":
                return new ParsedCommand(Type.GO, rest);
            case "map":
                return new ParsedCommand(Type.MAP, "");
            case "paths":
                return new ParsedCommand(Type.PATHS, "");
            case "search":
                return new ParsedCommand(Type.SEARCH, "");
            case "interact":
                return new ParsedCommand(Type.INTERACT, rest);
            case "talk":
            case "speak":
                return new ParsedCommand(Type.TALK, rest);
            case "ask":
                return new ParsedCommand(Type.ASK, rest);
            case "choose":
                return new ParsedCommand(Type.CHOOSE, rest);
            case "leave":
                return new ParsedCommand(Type.LEAVE, "");
            case "journal":
                return new ParsedCommand(Type.JOURNAL, "");
            case "quests":
                return new ParsedCommand(Type.QUESTS, "");
            case "memories":
                return new ParsedCommand(Type.MEMORIES, "");
            case "abilities":
                return new ParsedCommand(Type.ABILITIES, "");
            case "inventory":
            case "inv":
            case "i":
                return new ParsedCommand(Type.INVENTORY, "");
            case "equipment":
                return new ParsedCommand(Type.EQUIPMENT, "");
            case "equip": {
                // "equip" opens the equipment screen; "equip 2" equips slot/item 2
                return rest.isBlank()
                        ? new ParsedCommand(Type.EQUIPMENT, "")
                        : new ParsedCommand(Type.EQUIP, rest);
            }
            case "stats":
            case "stat":
                return new ParsedCommand(Type.STATS, "");
            case "rest":
                return new ParsedCommand(Type.REST, "");

            // --- combat ---
            case "attack":
            case "strike":
                return new ParsedCommand(Type.ATTACK, "");
            case "heavy":
                return new ParsedCommand(Type.HEAVY, "");
            case "defend":
            case "block":
            case "guard":
                return new ParsedCommand(Type.DEFEND, "");
            case "parry":
                return new ParsedCommand(Type.PARRY, "");
            case "dodge":
            case "evade":
            case "sidestep":
                return new ParsedCommand(Type.DODGE, "");
            case "skill":
                return new ParsedCommand(Type.SKILL, rest);
            case "flee":
            case "escape":
                return new ParsedCommand(Type.FLEE, "");
            case "status":
                return new ParsedCommand(Type.STATUS, "");
            case "log":
                return new ParsedCommand(Type.LOG, "");

            // --- context-sensitive ---
            case "use": {
                if (phase == GamePhase.COMBAT) {
                    return new ParsedCommand(Type.ITEM, rest);
                }
                return new ParsedCommand(Type.INTERACT, rest);
            }
            case "item":
            case "items": {
                if (phase == GamePhase.COMBAT) {
                    return rest.isBlank()
                            ? new ParsedCommand(Type.STATUS, "")
                            : new ParsedCommand(Type.ITEM, rest);
                }
                return new ParsedCommand(Type.INVENTORY, "");
            }
            case "drink":
            case "heal":
                return new ParsedCommand(Type.ITEM, rest);

            case "inspect":
            case "examine":
            case "x": {
                String what = rest.toLowerCase(Locale.ROOT);
                if (what.startsWith("enemy") || what.startsWith("foe") || what.startsWith("monster")) {
                    String tail = rest.length() > 5 ? rest.substring(5).strip() : "";
                    return new ParsedCommand(Type.INSPECT_ENEMY, tail);
                }
                if (what.startsWith("item")) {
                    String tail = rest.length() > 4 ? rest.substring(4).strip() : "";
                    return new ParsedCommand(Type.INSPECT_ITEM, tail);
                }
                return new ParsedCommand(Type.INSPECT, rest);
            }

            // --- menus ---
            case "take":
                if (rest.equalsIgnoreCase("all")) {
                    return new ParsedCommand(Type.TAKE_ALL, "");
                }
                return new ParsedCommand(Type.TAKE, rest);
            case "back":
            case "cancel":
            case "return":
                return new ParsedCommand(Type.BACK, "");
            case "confirm":
            case "yes":
                return new ParsedCommand(Type.CONFIRM, "");
            case "no":
                return new ParsedCommand(Type.BACK, "");
            case "unequip":
                return new ParsedCommand(Type.UNEQUIP, rest);
            case "compare":
                return new ParsedCommand(Type.COMPARE, rest);
            case "buy":
                return new ParsedCommand(Type.BUY, rest);
            case "sell":
                return new ParsedCommand(Type.SELL, rest);
            case "upgrade":
                return new ParsedCommand(Type.UPGRADE, rest);
            case "discard":
                return new ParsedCommand(Type.DISCARD, rest);
            case "detail":
            case "details":
                return new ParsedCommand(Type.INSPECT, rest);
            default:
                return new ParsedCommand(Type.UNKNOWN, line);
        }
    }
}
