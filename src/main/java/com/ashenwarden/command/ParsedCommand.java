package com.ashenwarden.command;

/**
 * Typed command produced by the parser. The arg is the remainder of the line
 * (direction, item name, topic, or a menu number) — never SQL-interpreted.
 */
public record ParsedCommand(Type type, String arg) {

    public enum Type {
        // main menu
        NEW, CONTINUE, LOAD, SETTINGS, SAVE, QUIT, HELP,
        // exploration
        LOOK, GO, MAP, PATHS, INSPECT, INTERACT, SEARCH, TALK, ASK, CHOOSE, LEAVE,
        JOURNAL, QUESTS, MEMORIES, ABILITIES, INVENTORY, EQUIPMENT, STATS, REST,
        // combat
        ATTACK, HEAVY, DEFEND, DODGE, PARRY, SKILL, ITEM, INSPECT_ENEMY, INSPECT_ITEM,
        STATUS, FLEE, LOG,
        // menus
        SELECT, TAKE, TAKE_ALL, BACK, CONFIRM, EQUIP, UNEQUIP, COMPARE, BUY, SELL, UPGRADE, DISCARD,
        // fallback
        UNKNOWN
    }

    public static final ParsedCommand UNKNOWN = new ParsedCommand(Type.UNKNOWN, "");

    public boolean isUnknown() {
        return type == Type.UNKNOWN;
    }

    /** Numeric menu argument, or -1 when absent/not a number. */
    public int number() {
        if (arg == null || !arg.matches("\\d{1,4}")) {
            return -1;
        }
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public boolean hasArg() {
        return arg != null && !arg.isBlank();
    }
}
