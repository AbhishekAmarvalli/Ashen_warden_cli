package com.ashenwarden.engine;

import com.ashenwarden.command.ParsedCommand;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.item.ItemType;
import com.ashenwarden.player.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu-phase commands: numbers, inspect/compare/equip, take/leave, dialogue
 * choices, slot selection, settings. Inspection always precedes commitment and
 * 'back' never costs anything. Non-menu commands fall through to PlayHandler
 * so the player can, for example, type 'stats' while browsing.
 */
final class MenuHandler {
    private MenuHandler() {
    }

    static CommandResult handle(GameEngine e, ParsedCommand cmd) {
        GameEngine.MenuContext menu = e.menu();
        if (menu == null) {
            return PlayHandler.handle(e, cmd);
        }
        // Global verbs work on every screen: 'help' always answers, and 'quit'
        // always leaves the game (except inside the quit menu itself, which
        // owns that decision). No screen may ever trap the player.
        if (cmd.type() == ParsedCommand.Type.HELP) {
            CommandResult out = CommandResult.ok();
            out.lines(menuHelp(menu.kind));
            out.line("Any time: 'back' closes this screen, 'quit' saves and exits.");
            return out;
        }
        if (cmd.type() == ParsedCommand.Type.QUIT
                && menu.kind != GameEngine.MenuContext.Kind.QUIT) {
            e.setMenu(null);
            return CommandResult.ok()
                    .line("Quitting without saving. The ash remembers regardless.").quit();
        }
        return switch (menu.kind) {
            case INVENTORY -> inventory(e, cmd, menu);
            case EQUIP_CONFIRM -> equipConfirm(e, cmd, menu);
            case LOOT -> loot(e, cmd, menu);
            case DIALOGUE -> dialogue(e, cmd, menu);
            case LOAD_LIST -> loadList(e, cmd, menu);
            case QUIT -> quit(e, cmd, menu);
            case SETTINGS -> settings(e, cmd, menu);
        };
    }

    // =================== inventory ==========================================
    /** Context-sensitive help lines mirroring the menu hints, so 'help' never lies. */
    private static List<String> menuHelp(GameEngine.MenuContext.Kind kind) {
        return switch (kind) {
            case INVENTORY -> List.of(
                    "Inventory screen:",
                    "  inspect <n>   full details of item n",
                    "  compare <n>   precise deltas vs. what is equipped",
                    "  equip <n>     equip item n (asks for confirmation)",
                    "  unequip <slot> weapon | armor | accessory1 | accessory2",
                    "  back          close the inventory");
            case LOOT -> List.of(
                    "Loot screen:",
                    "  inspect <n>   full details before deciding",
                    "  take <n>      take item n     ·  take all  take everything",
                    "  leave         walk away (loot stays where it fell)");
            case DIALOGUE -> List.of(
                    "Dialogue:",
                    "  choose <n>    pick option n",
                    "  ask <topic>   ask about a topic (look at the listed topics)",
                    "  leave         end the conversation");
            case EQUIP_CONFIRM -> List.of(
                    "Confirmation screen:",
                    "  1             confirm and equip",
                    "  back          cancel — nothing changes, nothing is lost");
            case LOAD_LIST -> List.of(
                    "Load a slot:",
                    "  <n>           load save slot number n",
                    "  back          return without loading");
            case QUIT -> List.of(
                    "Quit menu:",
                    "  1             save, then exit",
                    "  2             exit without saving",
                    "  3 / back      stay in the game");
            case SETTINGS -> List.of(
                    "Settings:",
                    "  choose a number to change it, or 'back' to return");
        };
    }

    private static CommandResult inventory(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        GameState s = e.state();
        switch (cmd.type()) {
            case BACK -> {
                e.setMenu(null);
                return CommandResult.ok().line("Closed.");
            }
            case SELECT, INSPECT_ITEM, INSPECT -> {
                int index = resolveIndex(e, cmd, menu);
                if (index < 0) {
                    return CommandResult.rejected("No item by that name/number here. Use 'inspect <n>' or 'back'.");
                }
                ItemDef def = e.content().item(menu.codes.get(index));
                return CommandResult.ok().lines(com.ashenwarden.cli.ItemText.describe(
                        def, s.getInventory(), s.getEquipment(), e.content().items()));
            }
            case COMPARE -> {
                int index = resolveIndex(e, cmd, menu);
                if (index < 0) {
                    return CommandResult.rejected("Usage: compare <n> — compare an item with what you have equipped.");
                }
                ItemDef candidate = e.content().item(menu.codes.get(index));
                String equippedCode = equippedCounterpart(e, candidate);
                if (equippedCode == null) {
                    return CommandResult.ok().line(candidate.name()
                            + " has nothing equipped to compare against (that slot is empty).");
                }
                return CommandResult.ok().lines(com.ashenwarden.cli.ItemText.compare(
                        candidate, e.content().item(equippedCode)));
            }
            case EQUIP -> {
                int index = resolveIndex(e, cmd, menu);
                if (index < 0) {
                    return CommandResult.rejected("Usage: equip <n> — equip an item from your inventory.");
                }
                ItemDef def = e.content().item(menu.codes.get(index));
                if (!def.isEquipable()) {
                    return CommandResult.rejected(def.name() + " cannot be equipped. Inspect it for its use.");
                }
                menu.kind = GameEngine.MenuContext.Kind.EQUIP_CONFIRM;
                menu.pendingEquipCode = def.code();
                CommandResult out = CommandResult.ok();
                out.lines(com.ashenwarden.cli.ItemText.describe(
                        def, s.getInventory(), s.getEquipment(), e.content().items()));
                out.line("");
                out.line("Equip " + def.name() + " now?");
                out.line("    ◇ [1] Yes, equip it");
                out.line("    ◇ [2] Back (nothing changes)");
                return out;
            }
            case UNEQUIP -> {
                EquipmentSlot slot = slotFrom(cmd.arg());
                if (slot == null) {
                    return CommandResult.rejected("Usage: unequip weapon|armor|accessory1|accessory2");
                }
                if (slot == EquipmentSlot.ACCESSORY_2 && !s.getEquipment().isAccessory2Unlocked()) {
                    return CommandResult.rejected("Accessory slot 2 is locked.");
                }
                String reason = s.getEquipment().unequip(slot, s.getInventory());
                if (reason != null) {
                    return CommandResult.rejected(reason);
                }
                s.markDirty();
                e.clampVitals(s);
                CommandResult out = CommandResult.ok().line("Unequipped and returned to your bag.");
                out.lines(e.buildInventoryLines());
                return out;
            }
            default -> {
                // any other valid command (stats, journal, go north, attack...) closes the
                // menu first so no stale menu hint leaks into another phase
                e.setMenu(null);
                return PlayHandler.handle(e, cmd);
            }
        }
    }

    private static String equippedCounterpart(GameEngine e, ItemDef candidate) {
        EquipmentSlot slot = com.ashenwarden.player.EquipmentManager.slotFor(candidate.type());
        if (slot == null) {
            return null;
        }
        if (candidate.type() == ItemType.ACCESSORY) {
            String a1 = e.state().getEquipment().equippedCode(EquipmentSlot.ACCESSORY_1);
            if (a1 != null) {
                return a1;
            }
            return e.state().getEquipment().equippedCode(EquipmentSlot.ACCESSORY_2);
        }
        return e.state().getEquipment().equippedCode(slot);
    }

    private static CommandResult equipConfirm(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        GameState s = e.state();
        boolean yes = (cmd.type() == ParsedCommand.Type.SELECT && cmd.number() == 1)
                || cmd.type() == ParsedCommand.Type.CONFIRM;
        boolean no = cmd.type() == ParsedCommand.Type.BACK
                || (cmd.type() == ParsedCommand.Type.SELECT && cmd.number() == 2);
        if (no) {
            menu.kind = GameEngine.MenuContext.Kind.INVENTORY;
            menu.pendingEquipCode = null;
            CommandResult out = CommandResult.ok().line("Nothing was changed.");
            out.lines(e.buildInventoryLines());
            return out;
        }
        if (!yes) {
            return CommandResult.rejected("Type [1] to confirm, or 'back' to cancel without changes.");
        }
        ItemDef def = e.content().item(menu.pendingEquipCode);
        EquipmentSlot slot = com.ashenwarden.player.EquipmentManager.slotFor(def.type());
        if (def.type() == ItemType.ACCESSORY) {
            if (s.getEquipment().equippedCode(EquipmentSlot.ACCESSORY_1) == null) {
                slot = EquipmentSlot.ACCESSORY_1;
            } else if (s.getEquipment().isAccessory2Unlocked()
                    && s.getEquipment().equippedCode(EquipmentSlot.ACCESSORY_2) == null) {
                slot = EquipmentSlot.ACCESSORY_2;
            } else {
                slot = EquipmentSlot.ACCESSORY_1; // swaps; previous item returns to the bag
            }
        }
        String reason = s.getEquipment().equip(slot, def.code(), s.getInventory(),
                e.content().items(), s.getPlayer().getLevel());
        if (reason != null) {
            return CommandResult.rejected(reason);
        }
        if (def.type() == ItemType.WEAPON) {
            s.setFlag("equipped_weapon", "true");
        }
        s.markDirty();
        e.clampVitals(s);

        menu.kind = GameEngine.MenuContext.Kind.INVENTORY;
        menu.pendingEquipCode = null;
        menu.showEquipment = true;
        CommandResult out = CommandResult.ok().line("Equipped: " + def.name()
                + " in the " + slot.name().toLowerCase(java.util.Locale.ROOT) + " slot.");
        out.lines(e.processEvents());
        out.lines(e.buildInventoryLines());
        return out;
    }

    // =================== loot ===============================================
    private static CommandResult loot(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        GameState s = e.state();
        switch (cmd.type()) {
            case BACK, LEAVE -> {
                e.setMenu(null);
                int left = menu.codes.size();
                CommandResult out = CommandResult.ok();
                if (s.getCombat() != null) {
                    s.getCombat().clearLoot();
                    closePostCombat(e);
                    out.phase(GamePhase.EXPLORATION);
                } else {
                    s.setPhase(GamePhase.EXPLORATION);
                }
                out.line(left == 0 ? "The way is clear." : "You leave the remaining loot where it lies.");
                return out;
            }
            case SELECT, INSPECT_ITEM, INSPECT -> {
                int index = resolveIndex(e, cmd, menu);
                if (index < 0) {
                    return CommandResult.rejected("No such loot here. Use 'inspect <n>' or 'take all'.");
                }
                ItemDef def = e.content().item(menu.codes.get(index));
                return CommandResult.ok().lines(com.ashenwarden.cli.ItemText.describe(
                        def, s.getInventory(), s.getEquipment(), e.content().items()));
            }
            case TAKE -> {
                int index = resolveIndex(e, cmd, menu);
                if (index < 0) {
                    return CommandResult.rejected("Usage: take <n> or 'take all'.");
                }
                return takeOne(e, menu, index);
            }
            case TAKE_ALL -> {
                CommandResult out = CommandResult.ok();
                while (!menu.codes.isEmpty()) {
                    CommandResult one = takeOne(e, menu, 0);
                    out.lines(one.lines());
                    if (one.nextPhase() != null) {
                        out.phase(one.nextPhase());
                    }
                    if (one.status() == CommandResult.Status.REJECTED) {
                        return one;
                    }
                }
                out.line("All loot taken.");
                return out;
            }
            case INVENTORY -> {
                return CommandResult.ok().lines(inventoryPeek(e));
            }
            default -> {
                return CommandResult.rejected("While looting use: inspect <n> | take <n> | take all | leave.");
            }
        }
    }

    private static CommandResult takeOne(GameEngine e, GameEngine.MenuContext menu, int index) {
        GameState s = e.state();
        String code = menu.codes.get(index);
        int qty = menu.quantities.get(index);
        ItemDef def = e.content().item(code);
        if (!s.getInventory().add(code, qty)) {
            return CommandResult.rejected("Your inventory cannot hold more of " + def.name() + ".");
        }
        s.addEvent(com.ashenwarden.engine.GameEvent.withAmount(
                com.ashenwarden.engine.GameEvent.Type.ITEM_OBTAINED, code, qty));
        s.markDirty();
        menu.codes.remove(index);
        menu.quantities.remove(index);
        CommandResult out = CommandResult.ok().line("Taken: " + def.name() + " x" + qty + ".");
        out.lines(e.processEvents());
        if (menu.codes.isEmpty()) {
            finishLoot(e, out);
        } else {
            out.lines(lootRowsRefresh(e, menu));
        }
        return out;
    }

    private static List<String> lootRowsRefresh(GameEngine e, GameEngine.MenuContext menu) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < menu.codes.size(); i++) {
            ItemDef def = e.content().item(menu.codes.get(i));
            rows.add("  [" + (i + 1) + "] " + def.name() + " x" + menu.quantities.get(i) + "  (still here)");
        }
        return rows;
    }

    private static void finishLoot(GameEngine e, CommandResult out) {
        GameState s = e.state();
        e.setMenu(null);
        if (s.getCombat() != null) {
            s.getCombat().clearLoot();
            closePostCombat(e);
        } else {
            s.setPhase(GamePhase.EXPLORATION);
        }
        out.phase(GamePhase.EXPLORATION);
    }

    private static void closePostCombat(GameEngine e) {
        e.closeCombatState();
    }

    private static List<String> inventoryPeek(GameEngine e) {
        List<String> out = new ArrayList<>();
        out.add("INVENTORY (read-only while looting):");
        var snapshot = e.state().getInventory().snapshot();
        if (snapshot.isEmpty()) {
            out.add("  (empty)");
            return out;
        }
        int i = 1;
        for (String code : snapshot.keySet()) {
            ItemDef def = e.content().item(code);
            out.add("  [" + (i++) + "] " + com.ashenwarden.cli.ItemText.row(def, e.state().getInventory()));
        }
        return out;
    }

    // =================== dialogue ===========================================
    private static CommandResult dialogue(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        GameState s = e.state();
        switch (cmd.type()) {
            case SELECT, CHOOSE -> {
                int choice = cmd.type() == ParsedCommand.Type.SELECT ? cmd.number() : numberFrom(cmd.arg());
                if (choice < 1) {
                    return CommandResult.rejected("Choose a number from the list, or 'leave'.");
                }
                var next = e.dialogues().choose(s, menu.dialogue, choice);
                return afterDialogueChoice(e, menu, next, "They have nothing more to say on that.");
            }
            case ASK -> {
                if (!cmd.hasArg()) {
                    return CommandResult.rejected("Usage: ask <topic> — topics match the visible choice labels.");
                }
                var next = e.dialogues().ask(s, menu.dialogue, cmd.arg());
                return afterDialogueChoice(e, menu, next, "They will not speak of that. Choose from the list.");
            }
            case LEAVE, BACK -> {
                e.setMenu(null);
                s.setPhase(GamePhase.EXPLORATION);
                CommandResult out = CommandResult.ok().line("You step back.");
                out.lines(e.processEvents());
                return out;
            }
            default -> {
                return CommandResult.rejected("In conversation use: a number | ask <topic> | leave.");
            }
        }
    }

    private static CommandResult afterDialogueChoice(GameEngine e, GameEngine.MenuContext menu,
                                                     com.ashenwarden.world.DialogueManager.DialogueView next,
                                                     String emptyMessage) {
        GameState s = e.state();
        if (next == null) {
            e.setMenu(null);
            s.setPhase(GamePhase.EXPLORATION);
            CommandResult out = CommandResult.ok().line("The conversation ends.");
            out.lines(e.processEvents());
            return out;
        }
        menu.dialogue = next;
        s.setPhase(GamePhase.DIALOGUE);
        CommandResult out = CommandResult.ok().lines(next.lines());
        out.lines(PlayHandler.optionLines(next));
        if (next.options().isEmpty()) {
            out.line("  (type 'leave' to end the conversation)");
        }
        return out;
    }

    // =================== load list ==========================================
    private static CommandResult loadList(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        switch (cmd.type()) {
            case SELECT -> {
                int index = cmd.number();
                if (index < 1 || index > menu.slots.size()) {
                    return CommandResult.rejected("Choose a slot from 1-" + menu.slots.size() + ", or 'back'.");
                }
                int saveId = menu.slots.get(index - 1).saveId();
                e.setMenu(null);
                return e.doLoad(saveId);
            }
            case BACK -> {
                e.setMenu(null);
                if (e.currentState() == null) {
                    e.renderMainMenu();
                    return CommandResult.ok();
                }
                return CommandResult.ok().line("Load cancelled.");
            }
            default -> {
                return CommandResult.rejected("Choose a slot number, or 'back'.");
            }
        }
    }

    // =================== quit ===============================================
    private static CommandResult quit(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        GameState s = e.state();
        int choice = cmd.type() == ParsedCommand.Type.SELECT ? cmd.number() : -1;
        if (choice == 3 || cmd.type() == ParsedCommand.Type.BACK) {
            e.setMenu(null);
            return CommandResult.ok().line("You stay a while longer in Veyrath.");
        }
        if (choice == 2) {
            e.setMenu(null);
            return CommandResult.ok().line("Quitting without saving. The ash remembers regardless.").quit();
        }
        if (choice == 1) {
            if (!s.isPersisted()) {
                CommandResult out = e.doSave(); // asks for a slot name first
                if (out.status() == CommandResult.Status.REJECTED) {
                    return out;
                }
                e.setMenu(new GameEngine.MenuContext(GameEngine.MenuContext.Kind.QUIT));
                out.line("Name the slot, then choose [1] again to save & quit.");
                return out;
            }
            CommandResult saveResult = e.saveNowForQuit();
            if (saveResult.status() == CommandResult.Status.REJECTED) {
                return saveResult;
            }
            e.setMenu(null);
            return saveResult.line("Quitting. Farewell, Warden.").quit();
        }
        return CommandResult.rejected("Choose [1] save & quit, [2] quit without saving, or [3] stay.");
    }

    // =================== settings ===========================================
    private static CommandResult settings(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        int choice = cmd.type() == ParsedCommand.Type.SELECT ? cmd.number() : -1;
        if (choice == 1) {
            e.renderer().ansi().setEnabled(!e.renderer().ansi().isEnabled());
            CommandResult out = CommandResult.ok().line("Colors "
                    + (e.renderer().ansi().isEnabled() ? "enabled (still supplemental — text carries meaning)"
                    : "disabled (plain mode)"));
            out.lines(MainMenuHandler.settingsLines(e));
            return out;
        }
        if (choice == 2) {
            e.renderer().setUnicode(!e.renderer().isUnicode());
            CommandResult out = CommandResult.ok().line("Box drawing: "
                    + (e.renderer().isUnicode() ? "Unicode" : "ASCII"));
            out.lines(MainMenuHandler.settingsLines(e));
            return out;
        }
        if (cmd.type() == ParsedCommand.Type.UNKNOWN && cmd.arg() != null
                && cmd.arg().toLowerCase(java.util.Locale.ROOT).startsWith("width")) {
            String[] parts = cmd.arg().trim().split("\\s+");
            if (parts.length == 2 && parts[1].matches("\\d{1,3}")) {
                int width = Integer.parseInt(parts[1]);
                if (width < 40 || width > 120) {
                    return CommandResult.rejected("Width must be between 40 and 120.");
                }
                e.renderer().setWidth(width);
                CommandResult out = CommandResult.ok().line("Terminal width set to " + width + ".");
                out.lines(MainMenuHandler.settingsLines(e));
                return out;
            }
            return CommandResult.rejected("Usage: width <40-120>");
        }
        if (choice == 3 || choice == 4 || cmd.type() == ParsedCommand.Type.BACK) {
            e.setMenu(null);
            if (e.currentState() == null) {
                e.renderMainMenu();
                return CommandResult.ok();
            }
            return CommandResult.ok().line("Settings closed.");
        }
        return CommandResult.rejected("Choose 1-4, 'width <n>', or 'back'.");
    }

    // =================== helpers ============================================
    /** Resolves a 1-based menu index from a number or an item name. */
    private static int resolveIndex(GameEngine e, ParsedCommand cmd, GameEngine.MenuContext menu) {
        int number = cmd.type() == ParsedCommand.Type.SELECT || cmd.type() == ParsedCommand.Type.CONFIRM
                ? cmd.number() : numberFrom(cmd.arg());
        if (number >= 1 && number <= menu.codes.size()) {
            return number - 1;
        }
        if (number == -1 && cmd.hasArg()) {
            String want = cmd.arg().trim().toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < menu.codes.size(); i++) {
                ItemDef def = e.content().item(menu.codes.get(i));
                if (def.name().toLowerCase(java.util.Locale.ROOT).contains(want)
                        || def.code().equalsIgnoreCase(want)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int numberFrom(String arg) {
        if (arg == null || !arg.matches("\\d{1,4}")) {
            return -1;
        }
        return Integer.parseInt(arg);
    }

    private static EquipmentSlot slotFrom(String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        return switch (arg.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "weapon", "w", "1" -> EquipmentSlot.WEAPON;
            case "armor", "a", "2" -> EquipmentSlot.ARMOR;
            case "accessory1", "acc1", "3" -> EquipmentSlot.ACCESSORY_1;
            case "accessory2", "acc2", "4" -> EquipmentSlot.ACCESSORY_2;
            default -> null;
        };
    }
}
