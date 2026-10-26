package com.ashenwarden.command;

import com.ashenwarden.command.ParsedCommand.Type;
import com.ashenwarden.engine.CommandResult;
import com.ashenwarden.engine.GameEngine;
import com.ashenwarden.engine.GamePhase;
import com.ashenwarden.engine.GameState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks the current phase/menu and routes commands. Illegal commands are
 * rejected with a one-line correction and spend nothing (no turn, no state).
 */
public final class CommandDispatcher {
    private final GameEngine engine;

    private static final Map<GamePhase, Set<Type>> ALLOWED = new EnumMap<>(GamePhase.class);

    static {
        ALLOWED.put(GamePhase.MAIN_MENU, EnumSet.of(
                Type.NEW, Type.CONTINUE, Type.LOAD, Type.SETTINGS, Type.HELP, Type.QUIT, Type.SELECT));
        ALLOWED.put(GamePhase.EXPLORATION, EnumSet.of(
                Type.LOOK, Type.GO, Type.MAP, Type.PATHS, Type.INSPECT, Type.INTERACT, Type.SEARCH,
                Type.TALK, Type.ASK, Type.CHOOSE, Type.LEAVE, Type.JOURNAL, Type.QUESTS, Type.MEMORIES,
                Type.ABILITIES, Type.INVENTORY, Type.EQUIPMENT, Type.STATS, Type.STATUS, Type.REST,
                Type.SAVE, Type.LOAD, Type.HELP, Type.QUIT, Type.INSPECT_ITEM, Type.LOG,
                Type.UPGRADE));
        ALLOWED.put(GamePhase.COMBAT, EnumSet.of(
                Type.ATTACK, Type.HEAVY, Type.DEFEND, Type.DODGE, Type.PARRY, Type.SKILL, Type.ITEM,
                Type.FLEE, Type.INSPECT_ENEMY, Type.INSPECT_ITEM, Type.STATUS, Type.HELP, Type.LOG,
                Type.INVENTORY, Type.JOURNAL, Type.QUIT, Type.STATS, Type.SELECT));
        ALLOWED.put(GamePhase.LOOT, EnumSet.of(
                Type.TAKE, Type.TAKE_ALL, Type.INSPECT_ITEM, Type.BACK, Type.LEAVE, Type.INVENTORY,
                Type.HELP, Type.SELECT));
        ALLOWED.put(GamePhase.DIALOGUE, EnumSet.of(
                Type.SELECT, Type.CHOOSE, Type.ASK, Type.LEAVE, Type.BACK, Type.HELP));
        ALLOWED.put(GamePhase.GAME_OVER, EnumSet.of(Type.HELP, Type.QUIT, Type.LOAD));
        ALLOWED.put(GamePhase.ENDING, EnumSet.of(Type.SELECT, Type.CHOOSE, Type.HELP, Type.QUIT));
    }

    public CommandDispatcher(GameEngine engine) {
        this.engine = engine;
    }

    public CommandResult dispatch(GameState state, ParsedCommand cmd) {
        if (cmd.isUnknown()) {
            return CommandResult.rejected(unknownMessage(state, cmd));
        }
        // An open screen (settings, load list, ...) owns its numbers even on
        // the main menu — before any state-based routing.
        if (engine.isMenuActive()) {
            return engine.handleMenuCommand(cmd);
        }
        if (state == null) {
            return engine.handleMainMenu(cmd);
        }
        GamePhase phase = state.getPhase();
        Set<Type> allowed = ALLOWED.get(phase);
        if (allowed == null || !allowed.contains(cmd.type())) {
            return CommandResult.rejected(illegal(phase, cmd));
        }
        return engine.handlePlay(cmd);
    }

    private static String unknownMessage(GameState state, ParsedCommand cmd) {
        String input = cmd.arg() == null || cmd.arg().isBlank() ? "(empty)" : cmd.arg();
        if (state == null) {
            return "Unknown command '" + input + "'. Try: new | continue | load | settings | help | quit";
        }
        return switch (state.getPhase()) {
            case COMBAT -> "Unknown command '" + input
                    + "'. Combat verbs: attack | heavy | defend | parry | skill <name> | item <name>"
                    + " | inspect enemy | status | help | flee";
            case EXPLORATION -> "Unknown command '" + input
                    + "'. Exploration verbs: look | go <way> | inspect <thing> | interact <thing>"
                    + " | search | talk <who> | inventory | journal | help";
            default -> "Unknown command '" + input + "'. Type 'help' for the command list.";
        };
    }

    private static String illegal(GamePhase phase, ParsedCommand cmd) {
        if (phase == GamePhase.EXPLORATION && isCombatVerb(cmd.type())) {
            return "'" + headOf(cmd) + "' is a combat command. You are exploring — nothing changed."
                    + " Try: look | go <way> | inventory | help";
        }
        if (phase == GamePhase.COMBAT && isExplorationVerb(cmd.type())) {
            return "'" + headOf(cmd) + "' is not available during combat. Nothing changed."
                    + " Try: attack | defend | parry | item <name> | status | help";
        }
        if (phase == GamePhase.COMBAT && cmd.type() == Type.SAVE) {
            return "You cannot save during combat. Finish or flee the encounter first.";
        }
        if (phase == GamePhase.LOOT) {
            return "'" + headOf(cmd) + "' is not available while looting. Use: inspect <n> | take <n>"
                    + " | take all | leave";
        }
        return "'" + headOf(cmd) + "' is not available right now (phase: " + phase + "). Type 'help'.";
    }

    private static boolean isCombatVerb(Type type) {
        return type == Type.ATTACK || type == Type.HEAVY || type == Type.DEFEND || type == Type.PARRY
                || type == Type.SKILL || type == Type.FLEE;
    }

    private static boolean isExplorationVerb(Type type) {
        return type == Type.GO || type == Type.LOOK || type == Type.INTERACT || type == Type.SEARCH
                || type == Type.TALK || type == Type.REST || type == Type.MAP || type == Type.PATHS;
    }

    private static String headOf(ParsedCommand cmd) {
        return switch (cmd.type()) {
            case GO -> "go";
            case LOOK -> "look";
            case INTERACT -> "interact";
            case SEARCH -> "search";
            case TALK -> "talk";
            case ASK -> "ask";
            case REST -> "rest";
            case MAP -> "map";
            case PATHS -> "paths";
            case SAVE -> "save";
            case LOAD -> "load";
            case INVENTORY -> "inventory";
            case EQUIPMENT -> "equipment";
            case SKILL -> "skill";
            case ITEM -> "item";
            case FLEE -> "flee";
            case PARRY -> "parry";
            case DEFEND -> "defend";
            case ATTACK -> "attack";
            case HEAVY -> "heavy";
            case INSPECT_ENEMY -> "inspect enemy";
            case INSPECT_ITEM -> "inspect item";
            default -> cmd.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        };
    }
}
