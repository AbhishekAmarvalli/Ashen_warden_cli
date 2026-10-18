package com.ashenwarden.command;

import com.ashenwarden.command.ParsedCommand.Type;
import com.ashenwarden.engine.GamePhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test
    void numbersBecomeMenuSelections() {
        assertEquals(Type.SELECT, parser.parse("3", GamePhase.EXPLORATION).type());
        assertEquals(3, parser.parse("3", GamePhase.EXPLORATION).number());
    }

    @Test
    void combatVerbsParseOnlyAsCombatTypes() {
        assertEquals(Type.ATTACK, parser.parse("attack", GamePhase.COMBAT).type());
        assertEquals(Type.DEFEND, parser.parse("Guard", GamePhase.COMBAT).type());
        assertEquals(Type.PARRY, parser.parse(" parry ", GamePhase.COMBAT).type());
        assertEquals(Type.HEAVY, parser.parse("heavy", GamePhase.COMBAT).type());
    }

    @Test
    void useIsPhaseAware() {
        assertEquals(Type.ITEM, parser.parse("use crimson vial", GamePhase.COMBAT).type());
        assertEquals("crimson vial", parser.parse("use crimson vial", GamePhase.COMBAT).arg());
        assertEquals(Type.INTERACT, parser.parse("use chest", GamePhase.EXPLORATION).type());
    }

    @Test
    void inspectVariants() {
        assertEquals(Type.INSPECT_ENEMY, parser.parse("inspect enemy", GamePhase.COMBAT).type());
        assertEquals(Type.INSPECT_ITEM, parser.parse("inspect item vial", GamePhase.COMBAT).type());
        assertEquals("vial", parser.parse("inspect item vial", GamePhase.COMBAT).arg());
        assertEquals(Type.INSPECT, parser.parse("inspect journal", GamePhase.EXPLORATION).type());
    }

    @Test
    void bareDirectionActsAsGo() {
        assertEquals(Type.GO, parser.parse("north", GamePhase.EXPLORATION).type());
        assertEquals("north", parser.parse("go north", GamePhase.EXPLORATION).arg());
    }

    @Test
    void takeAllAndBackAndCompare() {
        assertEquals(Type.TAKE_ALL, parser.parse("take all", GamePhase.LOOT).type());
        assertEquals(Type.BACK, parser.parse("cancel", GamePhase.EXPLORATION).type());
        assertEquals(Type.COMPARE, parser.parse("compare 2", GamePhase.EXPLORATION).type());
    }

    @Test
    void unknownInputIsFlaggedButSafe() {
        ParsedCommand cmd = parser.parse("frobnicate the widget", GamePhase.EXPLORATION);
        assertTrue(cmd.isUnknown());
        assertEquals("frobnicate the widget", cmd.arg());
        assertTrue(parser.parse("", GamePhase.COMBAT).isUnknown());
        assertTrue(parser.parse(null, GamePhase.COMBAT).isUnknown());
    }

    @Test
    void itemOutsideCombatOpensInventory() {
        assertEquals(Type.INVENTORY, parser.parse("item", GamePhase.EXPLORATION).type());
        assertEquals(Type.ITEM, parser.parse("item crimson vial", GamePhase.COMBAT).type());
    }

    @Test
    void oversizedInputIsBounded() {
        String huge = "x".repeat(5000);
        ParsedCommand cmd = parser.parse(huge, GamePhase.EXPLORATION);
        assertTrue(cmd.arg().length() <= CommandParser.MAX_INPUT);
    }
}
