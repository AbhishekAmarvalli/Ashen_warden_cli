package com.ashenwarden.world;

import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameEvent;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.item.ItemDef;
import com.ashenwarden.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Exploration rules: discrete location transitions, ability gates that always
 * name the missing requirement, sanctuary rests, and encounter triggers.
 * Combat itself is started by the engine from the pendingEncounter result.
 */
public final class ExplorationService {
    public static final String ENCOUNTER_FLAG_PREFIX = "encounter_done:";

    /** Small facade so world code does not depend on the whole content package. */
    public interface ContentAccess {
        com.ashenwarden.item.ItemCatalog items();

        Location location(String code);
    }

    private final ContentAccess content;

    public ExplorationService(ContentAccess content) {
        this.content = content;
    }

    public static ExplorationService of(ContentRepository repo) {
        return new ExplorationService(new ContentAccess() {
            @Override
            public com.ashenwarden.item.ItemCatalog items() {
                return repo.items();
            }

            @Override
            public Location location(String code) {
                return repo.world().get(code);
            }
        });
    }

    /** Result of a movement attempt: lines, whether we arrived, and a pending encounter. */
    public record MoveOutcome(List<String> lines, boolean moved, Location.Encounter pendingEncounter) {
    }

    public Location current(GameState state) {
        return content.location(state.getCurrentLocationId());
    }

    // --- look ---------------------------------------------------------------
    public List<String> look(GameState state) {
        Location loc = current(state);
        List<String> out = new ArrayList<>();
        out.add("[" + loc.name().toUpperCase() + "]  " + loc.chapter()
                + "  |  Region: " + loc.region() + (loc.sanctuary() ? "  |  SANCTUARY" : ""));
        out.add(loc.description());
        out.add("Exits: " + exitsLine(state, loc));
        List<String> nearby = new ArrayList<>();
        if (loc.npcCodes() != null) {
            nearby.addAll(loc.npcCodes());
        }
        if (loc.interactables() != null) {
            for (Location.Interactable item : loc.interactables()) {
                if (alreadyConsumed(state, item)) {
                    continue;
                }
                nearby.add(item.name());
            }
        }
        out.add(nearby.isEmpty() ? "Nearby: nothing of note." : "Nearby: " + String.join(", ", nearby) + ".");
        out.add("Commands: look | go <way> | inspect <thing> | interact <thing> | search | talk <who> | rest");
        return out;
    }

    private String exitsLine(GameState state, Location loc) {
        if (loc.exits() == null || loc.exits().isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < loc.exits().size(); i++) {
            Location.Exit exit = loc.exits().get(i);
            boolean locked = exit.isBlockedByAbility(false) && !state.hasAbility(exit.requiresAbility());
            parts.add("[" + (i + 1) + "] " + exit.label() + (locked ? " [LOCKED: " + abilityName(exit.requiresAbility()) + "]" : ""));
        }
        return String.join("  ", parts);
    }

    private String abilityName(String abilityCode) {
        if (abilityCode == null) {
            return "?";
        }
        return switch (abilityCode) {
            case "bellbreaker" -> "Bellbreaker";
            case "wraith_step" -> "Wraith Step";
            case "ashen_flame" -> "Ashen Flame";
            case "gravebound_leap" -> "Gravebound Leap";
            case "palace_sigil" -> "Palace Sigil";
            default -> abilityCode.replace('_', ' ');
        };
    }

    // --- go -----------------------------------------------------------------
    public MoveOutcome go(GameState state, String direction) {
        Location loc = current(state);
        Location.Exit exit = resolveExit(state, loc, direction);
        if (exit == null) {
            return new MoveOutcome(List.of("No such way from here. Known exits: " + exitsLine(state, loc)),
                    false, null);
        }
        boolean locked = exit.isBlockedByAbility(false) && !state.hasAbility(exit.requiresAbility());
        if (locked) {
            return new MoveOutcome(List.of(exit.blockedMessage() != null && !exit.blockedMessage().isBlank()
                    ? exit.blockedMessage()
                    : "Blocked. You still lack the ability: " + abilityName(exit.requiresAbility()) + "."),
                    false, null);
        }

        // blocking encounter on this exit?
        Location.Encounter blocker = pendingExitEncounter(state, loc, exit);
        if (blocker != null) {
            return new MoveOutcome(List.of(blocker.introText()), false, blocker);
        }

        Location target = content.location(exit.targetCode());
        state.setCurrentLocationId(target.code());
        boolean firstVisit = state.discover(target.code());
        state.addEvent(GameEvent.of(GameEvent.Type.LOCATION_VISITED, target.code()));
        state.markDirty();

        List<String> out = new ArrayList<>();
        out.add("You go " + exit.label() + ".");
        if (firstVisit) {
            out.add("DISCOVERED: " + target.name());
        }

        Location.Encounter ambush = pendingEnterEncounter(state, target);
        if (ambush != null) {
            out.add(ambush.introText());
            return new MoveOutcome(out, true, ambush);
        }
        return new MoveOutcome(out, true, null);
    }

    private Location.Exit resolveExit(GameState state, Location loc, String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        String want = direction.trim();
        // numeric selection: "1" picks the first exit (documented alias)
        if (want.matches("\\d+")) {
            int index = Integer.parseInt(want);
            if (loc.exits() != null && index >= 1 && index <= loc.exits().size()) {
                return loc.exits().get(index - 1);
            }
            return null;
        }
        Location.Exit byLabel = loc.exitByLabel(want);
        if (byLabel != null) {
            return byLabel;
        }
        // fuzzy: allow partial matches like "go north" vs label "the north road"
        if (loc.exits() != null) {
            for (Location.Exit exit : loc.exits()) {
                if (exit.label().toLowerCase(java.util.Locale.ROOT).contains(want.toLowerCase(java.util.Locale.ROOT))) {
                    return exit;
                }
            }
        }
        return null;
    }

    private Location.Encounter pendingEnterEncounter(GameState state, Location loc) {
        if (loc.encounters() == null) {
            return null;
        }
        for (Location.Encounter enc : loc.encounters()) {
            if (Location.Encounter.TRIGGER_ON_ENTER.equals(enc.trigger())
                    && !encounterDone(state, enc)) {
                return enc;
            }
        }
        return null;
    }

    private Location.Encounter pendingExitEncounter(GameState state, Location loc, Location.Exit exit) {
        if (loc.encounters() == null) {
            return null;
        }
        for (Location.Encounter enc : loc.encounters()) {
            if (!Location.Encounter.TRIGGER_ON_EXIT.equals(enc.trigger()) || encounterDone(state, enc)) {
                continue;
            }
            if (enc.exitLabel() == null || enc.exitLabel().equalsIgnoreCase(exit.label())) {
                return enc;
            }
        }
        return null;
    }

    public boolean encounterDone(GameState state, Location.Encounter enc) {
        return "true".equals(state.getFlag(ENCOUNTER_FLAG_PREFIX + enc.code()));
    }

    public void markEncounterDone(GameState state, Location.Encounter enc) {
        state.setFlag(ENCOUNTER_FLAG_PREFIX + enc.code(), "true");
    }

    /** The encounter the player would trigger right now by arriving (startup). */
    public Location.Encounter enterEncounterAtCurrent(GameState state) {
        return pendingEnterEncounter(state, current(state));
    }

    // --- inspect / interact -------------------------------------------------
    public List<String> inspect(GameState state, String what) {
        Location loc = current(state);
        if (what == null || what.isBlank()) {
            return List.of("Inspect what? Things nearby: " + nearbyNames(state, loc));
        }
        String want = what.trim().toLowerCase(java.util.Locale.ROOT);
        if (want.equals("here") || want.equals("location") || want.equals("room")) {
            List<String> out = new ArrayList<>();
            out.add(loc.name().toUpperCase() + " — " + loc.description());
            out.add("Lore: " + (loc.extendedLore() == null ? "Nothing more is written here." : loc.extendedLore()));
            return out;
        }
        if (loc.interactables() != null) {
            for (Location.Interactable item : loc.interactables()) {
                if (matches(item.name(), item.aliases(), want)) {
                    List<String> out = new ArrayList<>();
                    out.add(item.name().toUpperCase());
                    out.add(item.description());
                    if (item.requiresAbility() != null && !state.hasAbility(item.requiresAbility())) {
                        out.add(item.blockedMessage() != null ? item.blockedMessage()
                                : "It resists you. Requires: " + abilityName(item.requiresAbility()) + ".");
                    }
                    if (item.extendedText() != null && !item.extendedText().isBlank()) {
                        out.add("Read more: " + item.extendedText());
                    }
                    if (alreadyConsumed(state, item)) {
                        out.add("(Already used.)");
                    }
                    return out;
                }
            }
        }
        if (loc.npcCodes() != null) {
            for (String npcCode : loc.npcCodes()) {
                if (want.contains(npcCode.replace('_', ' ')) || npcCode.contains(want)) {
                    return List.of("A figure wrapped in ash-grey cloth. Use 'talk " + npcCode + "' to speak.");
                }
            }
        }
        return List.of("You find nothing by that name here. Nearby: " + nearbyNames(state, loc));
    }

    /** Interact opens/uses things; loot appears in a menu (inspect before take). */
    public record InteractOutcome(List<String> lines, Location.Interactable target, boolean questFlagSet) {
    }

    public InteractOutcome interact(GameState state, String what) {
        Location loc = current(state);
        List<String> out = new ArrayList<>();
        if (loc.interactables() == null || loc.interactables().isEmpty()) {
            return new InteractOutcome(List.of("There is nothing here to interact with."), null, false);
        }
        Location.Interactable target = null;
        if (what == null || what.isBlank()) {
            target = loc.interactables().stream()
                    .filter(i -> !alreadyConsumed(state, i)).findFirst().orElse(null);
        } else {
            String want = what.trim().toLowerCase(java.util.Locale.ROOT);
            for (Location.Interactable item : loc.interactables()) {
                if (matches(item.name(), item.aliases(), want)) {
                    target = item;
                    break;
                }
            }
        }
        if (target == null) {
            return new InteractOutcome(List.of("Nothing by that name responds. Nearby: "
                    + nearbyNames(state, loc)), null, false);
        }
        if (alreadyConsumed(state, target)) {
            return new InteractOutcome(List.of(target.name() + " holds nothing more for you."), null, false);
        }
        if (target.requiresAbility() != null && !state.hasAbility(target.requiresAbility())) {
            String blocked = target.blockedMessage() != null && !target.blockedMessage().isBlank()
                    ? target.blockedMessage()
                    : "Blocked. Requires: " + abilityName(target.requiresAbility()) + ".";
            return new InteractOutcome(List.of(blocked), null, false);
        }

        boolean questFlagSet = false;
        String flag = state.getFlag("interact_done:" + target.code());
        out.add("You interact with " + target.name() + ".");
        if (target.kind() != null && target.kind().endsWith("NOTE")) {
            out.add(target.extendedText() != null ? target.extendedText() : target.description());
        }
        if (target.loot() != null && !target.loot().isEmpty()) {
            out.add("Inside you find:");
            for (Location.Interactable.Loot loot : target.loot()) {
                ItemDef def = content.items().find(loot.itemCode());
                String name = def == null ? loot.itemCode() : def.name();
                out.add("  - " + name + " x" + loot.quantity() + "  (inspect it before you take it)");
            }
        }
        if (target.once()) {
            state.setFlag("interact_done:" + target.code(), "true");
        }
        if (target.kind() != null && target.kind().equals("NOTE")) {
            state.setFlag("read:" + target.code(), "true");
            questFlagSet = true;
        }
        state.markDirty();
        return new InteractOutcome(out, target, questFlagSet);
    }

    private boolean alreadyConsumed(GameState state, Location.Interactable item) {
        return item.once() && "true".equals(state.getFlag("interact_done:" + item.code()));
    }

    private boolean matches(String name, List<String> aliases, String want) {
        if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(want)) {
            return true;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias.toLowerCase(java.util.Locale.ROOT).equals(want)
                        || want.contains(alias.toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String nearbyNames(GameState state, Location loc) {
        List<String> names = new ArrayList<>();
        if (loc.interactables() != null) {
            for (Location.Interactable item : loc.interactables()) {
                if (!alreadyConsumed(state, item)) {
                    names.add(item.name());
                }
            }
        }
        return names.isEmpty() ? "nothing" : String.join(", ", names);
    }

    // --- search / rest / map / paths ---------------------------------------
    public List<String> search(GameState state) {
        Location loc = current(state);
        List<String> out = new ArrayList<>();
        Player player = state.getPlayer();
        out.add("You search the ash and the dust.");
        // recoverable death-loss fragments
        if (player.getLostFragments() > 0
                && loc.code().equals(player.getLostFragmentLocation())) {
            int recovered = player.getLostFragments();
            player.gainFragments(reduced(recovered));
            player.clearLostFragments();
            out.add("Your dropped Ash Fragments glint in the ash — recovered " + recovered + ".");
            state.markDirty();
        } else if (player.getLostFragments() > 0 && player.getLostFragmentLocation() != null) {
            out.add("Something of yours lies waiting at " + player.getLostFragmentLocation() + ".");
        }
        List<String> found = new ArrayList<>();
        if (loc.interactables() != null) {
            for (Location.Interactable item : loc.interactables()) {
                if (!alreadyConsumed(state, item)) {
                    found.add(item.name());
                }
            }
        }
        out.add(found.isEmpty() ? "You turn over old ash and find nothing more."
                : "You notice: " + String.join(", ", found) + ".");
        out.add("Exits: " + exitsLine(state, loc));
        return out;
    }

    private int reduced(int amount) {
        return amount; // the full drop is recoverable once, exactly as documented
    }

    /** Rest at a sanctuary: full restore, clears statuses, sets respawn, autosave hint. */
    public List<String> rest(GameState state) {
        Location loc = current(state);
        List<String> out = new ArrayList<>();
        if (!loc.sanctuary()) {
            out.add("There is no sanctuary here — rest is only safe at a shrine, brazier or haven"
                    + " (look for 'SANCTUARY'). The restless dead would not allow it.");
            return out;
        }
        Player player = state.getPlayer();
        int hpBefore = player.getHp();
        player.setHp(Integer.MAX_VALUE); // clamps to max
        player.setStamina(Integer.MAX_VALUE);
        player.setAsh(Integer.MAX_VALUE);
        player.clearStatuses();
        state.setSanctuaryId(loc.code());
        state.markDirty();
        out.add("You plant your sword and kneel at the ember. The ash stills.");
        out.add("HP " + hpBefore + " -> " + player.getHp() + " | stamina and Ash restored | statuses cleared.");
        out.add("Respawn point set: " + loc.name() + ". Type 'save' to write your progress to MySQL.");
        state.addEvent(GameEvent.of(GameEvent.Type.LOCATION_VISITED, loc.code()));
        return out;
    }

    public List<String> mapText(GameState state) {
        List<String> out = new ArrayList<>();
        out.add("WORLD MAP — discovered places");
        for (String code : state.locationIds()) {
            Location loc = content.location(code);
            if (loc == null) {
                continue;
            }
            String marker = code.equals(state.getCurrentLocationId()) ? " <== YOU ARE HERE"
                    : (loc.sanctuary() ? " (sanctuary)" : "");
            out.add("  " + loc.region() + " — " + loc.name() + marker);
        }
        out.add("Abilities: " + (state.getUnlockedAbilities().isEmpty()
                ? "none yet"
                : String.join(", ", state.getUnlockedAbilities())));
        return out;
    }

    public List<String> pathsText(GameState state) {
        Location loc = current(state);
        List<String> out = new ArrayList<>();
        out.add("KNOWN PATHS from " + loc.name() + ":");
        if (loc.exits() == null || loc.exits().isEmpty()) {
            out.add("  No exits.");
            return out;
        }
        for (Location.Exit exit : loc.exits()) {
            boolean locked = exit.isBlockedByAbility(false) && !state.hasAbility(exit.requiresAbility());
            String target = content.location(exit.targetCode()).name();
            out.add("  -> " + exit.label() + " leads to " + target
                    + (locked ? "  [LOCKED: " + (exit.blockedMessage() != null ? exit.blockedMessage()
                    : abilityName(exit.requiresAbility())) + "]" : "  [open]"));
        }
        return out;
    }
}
