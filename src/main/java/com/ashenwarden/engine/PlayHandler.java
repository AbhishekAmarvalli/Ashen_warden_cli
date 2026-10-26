package com.ashenwarden.engine;

import com.ashenwarden.command.ParsedCommand;
import com.ashenwarden.player.EffectiveStats;
import com.ashenwarden.player.EquipmentSlot;
import com.ashenwarden.world.ExplorationService;
import com.ashenwarden.world.Location;
import com.ashenwarden.world.Npc;

import java.util.ArrayList;
import java.util.List;

/**
 * Commands available while playing: exploration verbs and combat verbs.
 * Menus are delegated to MenuHandler; this class never draws screens directly —
 * it returns CommandResult lines that the renderer prints.
 */
final class PlayHandler {
    private PlayHandler() {
    }

    static CommandResult handle(GameEngine e, ParsedCommand cmd) {
        if (e.state().getPhase() == GamePhase.COMBAT) {
            return e.combatAction(cmd);
        }
        return explore(e, cmd);
    }

    private static CommandResult explore(GameEngine e, ParsedCommand cmd) {
        GameState s = e.state();
        ExplorationService exploration = e.exploration();
        return switch (cmd.type()) {
            case LOOK -> CommandResult.ok().lines(exploration.look(s));
            case GO -> go(e, cmd);
            case MAP -> CommandResult.ok().cardLines(exploration.mapText(s));
            case PATHS -> CommandResult.ok().cardLines(exploration.pathsText(s));
            case INSPECT -> CommandResult.ok().cardLines(exploration.inspect(s, cmd.arg()));
            case INTERACT -> interact(e, cmd);
            case SEARCH -> withQuestEvents(e, exploration.search(s));
            case TALK -> talk(e, cmd);
            case REST -> rest(e, exploration);
            case JOURNAL -> CommandResult.ok().cardLines(e.quests().journalText(s));
            case QUESTS -> CommandResult.ok().cardLines(e.quests().questsText(s));
            case MEMORIES -> CommandResult.ok().cardLines(memories(e));
            case ABILITIES -> CommandResult.ok().cardLines(abilities(e));
            case STATS, STATUS -> CommandResult.ok().cardLines(stats(e));
            case INVENTORY -> e.openInventoryMenu(false);
            case EQUIPMENT -> e.openInventoryMenu(true);
            case SAVE -> e.doSave();
            case LOAD -> MainMenuHandler.openSlots(e, "Load a slot (progress since your last save is lost):");
            case HELP -> CommandResult.ok().cardLines(explorationHelp());
            case QUIT -> openQuit(e);
            case LOG -> CommandResult.ok().line("The combat log only exists during battle; your story is in 'journal'.");
            case DODGE -> CommandResult.rejected("You can only dodge while an enemy is upon you. Nothing changed.");
            case INSPECT_ITEM -> inspectCarriedItem(e, cmd);
            case UPGRADE -> CommandResult.rejected("Tempering happens from your inventory at a sanctuary:"
                    + " type 'inventory', then 'upgrade <n>'. Nothing changed.");
            case CHOOSE, LEAVE -> CommandResult.rejected("You are not in a conversation right now.");
            default -> CommandResult.rejected("That command does not apply here. Type 'help'.");
        };
    }

    private static CommandResult go(GameEngine e, ParsedCommand cmd) {
        GameState s = e.state();
        ExplorationService.MoveOutcome outcome = e.exploration().go(s, cmd.arg());
        CommandResult out = CommandResult.ok().lines(outcome.lines());
        if (outcome.pendingEncounter() != null) {
            out.lines(e.beginEncounter(outcome.pendingEncounter()));
            out.phase(GamePhase.COMBAT);
            return out;
        }
        if (outcome.moved()) {
            e.setMenu(null);
            out.lines(e.processEvents());
            out.lines(e.exploration().look(s));
            // ON_ENTER ambush arriving with the movement itself
            Location.Encounter enc = e.exploration().enterEncounterAtCurrent(s);
            if (enc != null) {
                out.lines(e.beginEncounter(enc));
                out.phase(GamePhase.COMBAT);
            }
        }
        return out;
    }

    private static CommandResult interact(GameEngine e, ParsedCommand cmd) {
        GameState s = e.state();
        ExplorationService.InteractOutcome outcome = e.exploration().interact(s, cmd.arg());
        CommandResult out = CommandResult.ok().lines(outcome.lines());
        if (outcome.questFlagSet()) {
            out.lines(e.processEvents());
        }
        if (outcome.target() != null && outcome.target().loot() != null && !outcome.target().loot().isEmpty()) {
            out.cardLines(e.openLootFromInteractable(outcome.target().loot()));
            out.phase(GamePhase.LOOT);
        }
        return out;
    }

    private static CommandResult talk(GameEngine e, ParsedCommand cmd) {
        GameState s = e.state();
        Location loc = e.exploration().current(s);
        String want = cmd.arg() == null ? "" : cmd.arg().trim().toLowerCase(java.util.Locale.ROOT);
        if (want.isBlank() || loc.npcCodes() == null || loc.npcCodes().isEmpty()) {
            return CommandResult.rejected("There is no one here to talk to. Try 'search' or 'look'.");
        }
        String npcCode = null;
        for (String code : loc.npcCodes()) {
            Npc npc = e.content().npc(code);
            if (code.toLowerCase(java.util.Locale.ROOT).contains(want)
                    || npc.name().toLowerCase(java.util.Locale.ROOT).contains(want)
                    || want.contains(code.toLowerCase(java.util.Locale.ROOT))) {
                npcCode = code;
                break;
            }
        }
        if (npcCode == null) {
            List<String> names = new ArrayList<>();
            for (String code : loc.npcCodes()) {
                names.add(e.content().npc(code).name());
            }
            return CommandResult.rejected("No one by that name is here. Present: " + String.join(", ", names));
        }
        var view = e.dialogues().open(s, npcCode);
        if (view == null) {
            return CommandResult.rejected("They have nothing to say to you.");
        }
        e.setMenu(new GameEngine.MenuContext(GameEngine.MenuContext.Kind.DIALOGUE));
        e.menu().dialogue = view;
        s.setPhase(GamePhase.DIALOGUE);
        List<String> body = new ArrayList<>(view.lines());
        body.addAll(optionLines(view));
        return CommandResult.ok().card("DIALOGUE", body);
    }

    static List<String> optionLines(com.ashenwarden.world.DialogueManager.DialogueView view) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < view.options().size(); i++) {
            out.add("    [" + (i + 1) + "] " + view.options().get(i).label());
        }
        if (view.options().isEmpty()) {
            out.add("    leave  —  end the conversation");
        }
        return out;
    }

    private static CommandResult rest(GameEngine e, ExplorationService exploration) {
        GameState s = e.state();
        List<String> lines = new ArrayList<>(exploration.rest(s));
        lines.addAll(e.processEvents());
        CommandResult out = CommandResult.ok().lines(lines);
        e.autoSaveAfterMilestone(out);
        return out;
    }

    private static CommandResult withQuestEvents(GameEngine e, List<String> base) {
        CommandResult out = CommandResult.ok().lines(base);
        out.lines(e.processEvents());
        return out;
    }

    private static CommandResult inspectCarriedItem(GameEngine e, ParsedCommand cmd) {
        if (!cmd.hasArg()) {
            return CommandResult.rejected("Usage: inspect item <name> (free — never spends a turn).");
        }
        String want = cmd.arg().trim().toLowerCase(java.util.Locale.ROOT);
        for (var entry : e.state().getInventory().snapshot().keySet()) {
            var def = e.content().items().find(entry);
            if (def == null) {
                continue;
            }
            if (def.code().equalsIgnoreCase(want) || def.name().toLowerCase(java.util.Locale.ROOT).contains(want)) {
                return CommandResult.ok().cardLines(com.ashenwarden.cli.ItemText.describe(
                        def, e.state().getInventory(), e.state().getEquipment(), e.content().items()));
            }
        }
        return CommandResult.rejected("You carry no item by that name.");
    }

    private static CommandResult openQuit(GameEngine e) {
        e.setMenu(new GameEngine.MenuContext(GameEngine.MenuContext.Kind.QUIT));
        CommandResult out = CommandResult.ok().line("Leave Veyrath?");
        out.line("    [1] Save, then quit");
        out.line("    [2] Quit without saving");
        out.line("    [3] Stay");
        return out;
    }

    // --- display texts ------------------------------------------------------
    private static List<String> memories(GameEngine e) {
        GameState s = e.state();
        List<String> out = new ArrayList<>();
        out.add("WARDEN MEMORIES: " + s.getMemories().size() + "/" + com.ashenwarden.story.MemoryDef.TOTAL);
        for (var memory : e.content().memories()) {
            boolean owned = s.getMemories().contains(memory.code());
            out.add("  [" + (memory.order()) + "] " + (owned ? memory.title() : "???") + (owned ? "" : "  (not yet recovered)"));
            if (owned) {
                out.add("      " + memory.journalText());
            }
        }
        return out;
    }

    private static List<String> abilities(GameEngine e) {
        GameState s = e.state();
        List<String> out = new ArrayList<>();
        out.add("UNLOCKED ABILITIES:");
        if (s.getUnlockedAbilities().isEmpty()) {
            out.add("  None yet. Bosses guard the tools that open Veyrath's hidden ways.");
        }
        for (String ability : s.getUnlockedAbilities()) {
            out.add("  - " + abilityLabel(ability));
        }
        return out;
    }

    static String abilityLabel(String ability) {
        return switch (ability) {
            case "bellbreaker" -> "Bellbreaker — shatters marked cracked walls and opens shortcuts.";
            case "wraith_step" -> "Wraith Step — cross spectral barriers and phantom routes.";
            case "ashen_flame" -> "Ashen Flame — ignite ritual braziers and melt cursed seals.";
            case "gravebound_leap" -> "Gravebound Leap — reach high ledges as a single travel option.";
            case "palace_sigil" -> "Palace Sigil — authority to enter the royal palace.";
            case "accessory2" -> "Accessory Slot II — a second accessory may be worn.";
            default -> ability.replace('_', ' ');
        };
    }

    private static List<String> stats(GameEngine e) {
        GameState s = e.state();
        var p = s.getPlayer();
        EffectiveStats stats = EffectiveStats.compute(p, s.getEquipment(), e.content().items());
        List<String> out = new ArrayList<>();
        out.add("THE ASHEN WARDEN — " + p.getName() + "  (Level " + p.getLevel() + ")");
        out.add("HP " + p.getHp() + "/" + stats.maxHp()
                + " | Stamina " + p.getStamina() + "/" + stats.maxStamina()
                + " | Ash " + p.getAsh() + "/" + stats.maxAsh());
        out.add("Attack " + stats.attack() + " (base " + p.getBaseAttack() + " + equipment)"
                + " | Defense " + stats.defense() + " (base " + p.getBaseDefense() + " + equipment)");
        out.add("XP: " + p.getXp() + "/" + com.ashenwarden.player.Player.xpToNext(p.getLevel()) + " to next level"
                + " | Ash Fragments: " + p.getFragments());
        out.add("Statuses: " + (p.getStatuses().isEmpty() ? "none" : p.getStatuses()));
        String weapon = s.getEquipment().equippedCode(EquipmentSlot.WEAPON);
        String armor = s.getEquipment().equippedCode(EquipmentSlot.ARMOR);
        String acc1 = s.getEquipment().equippedCode(EquipmentSlot.ACCESSORY_1);
        String acc2 = s.getEquipment().equippedCode(EquipmentSlot.ACCESSORY_2);
        out.add("Equipped: weapon=" + nameOf(e, weapon) + " | armor=" + nameOf(e, armor)
                + " | acc1=" + nameOf(e, acc1)
                + " | acc2=" + (s.getEquipment().isAccessory2Unlocked() ? nameOf(e, acc2) : "locked"));
        out.add("Quests: 'journal'  |  Memories: " + s.getMemories().size() + "/7  |  Place: "
                + e.content().world().get(s.getCurrentLocationId()).name());
        return out;
    }

    static String nameOf(GameEngine e, String code) {
        return code == null ? "-" : e.content().items().find(code).name();
    }

    private static List<String> explorationHelp() {
        return List.of(
                "EXPLORATION",
                "  look                 describe this place",
                "  go <way> | 1..9      take an exit (numbers match the exit list)",
                "  map | paths          discovered places | exits with lock reasons",
                "  inspect <thing>      details before you commit (free)",
                "  interact <thing>     open chests, read notes, break walls",
                "  search               look for loose items (and dropped fragments)",
                "  talk <who> | ask <t> | choose <n> | leave    conversations",
                "  rest                 heal at a SANCTUARY and set respawn",
                "  inventory | equipment | stats | abilities      your character",
                "  journal | quests | memories | help             information",
                "  save | load | quit    persistence and exit",
                "Blocked? The game tells you exactly which ability is missing."
        );
    }
}
