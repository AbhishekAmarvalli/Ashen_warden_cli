package com.ashenwarden.world;

import java.util.List;

/**
 * A node of the world graph (static definition from content/locations.json).
 * Movement is by discrete location transitions; ability gates are checked by
 * WorldGraph, which always names the exact missing requirement.
 */
public record Location(
        String code,
        String name,
        String region,
        String chapter,
        String description,
        String extendedLore,
        boolean sanctuary,
        boolean discoveredByDefault,
        List<Exit> exits,
        List<String> npcCodes,
        List<Interactable> interactables,
        List<Encounter> encounters
) {
    public Exit exitTo(String targetCode) {
        if (exits == null) {
            return null;
        }
        return exits.stream().filter(e -> e.targetCode().equals(targetCode)).findFirst().orElse(null);
    }

    public Exit exitByLabel(String label) {
        if (exits == null || label == null) {
            return null;
        }
        for (Exit e : exits) {
            if (e.label().equalsIgnoreCase(label) || e.targetCode().equalsIgnoreCase(label)) {
                return e;
            }
            for (String alias : e.aliases()) {
                if (alias.equalsIgnoreCase(label)) {
                    return e;
                }
            }
        }
        return null;
    }

    public Interactable interactable(String id) {
        if (interactables == null) {
            return null;
        }
        return interactables.stream()
                .filter(i -> i.code().equalsIgnoreCase(id)
                        || (i.aliases() != null && i.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(id))))
                .findFirst().orElse(null);
    }

    /**
     * @param label        exit label shown to the player ("north", "cracked passage")
     * @param targetCode   destination location code
     * @param aliases      accepted command words for this exit
     * @param requiresAbility ability code needed to pass, or null
     * @param blockedMessage exact requirement shown when blocked
     */
    public record Exit(
            String label,
            String targetCode,
            List<String> aliases,
            String requiresAbility,
            String blockedMessage
    ) {
        public boolean isBlockedByAbility(boolean hasAbility) {
            return requiresAbility != null && !requiresAbility.isBlank() && !hasAbility;
        }
    }

    /**
     * @param kind CHEST | NOTE | WALL | SHRINE | ALTAR | PROP
     * @param loot flat rewards granted by search/interact (item code, quantity)
     */
    public record Interactable(
            String code,
            String name,
            List<String> aliases,
            String kind,
            String description,
            String extendedText,
            String requiresAbility,
            String blockedMessage,
            List<Loot> loot,
            boolean once,
            boolean consumed
    ) {
        public record Loot(String itemCode, int quantity) {
        }
    }

    /**
     * @param trigger  ON_ENTER (fires when arriving) | ON_EXIT (blocks an exit until defeated)
     * @param enemyCode enemy definition code
     * @param exitLabel for ON_EXIT: which exit is blocked (null = all exits)
     */
    public record Encounter(
            String code,
            String trigger,
            String enemyCode,
            String introText,
            String exitLabel,
            boolean once
    ) {
        public static final String TRIGGER_ON_ENTER = "ON_ENTER";
        public static final String TRIGGER_ON_EXIT = "ON_EXIT";
    }
}
