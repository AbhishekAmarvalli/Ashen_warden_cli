package com.ashenwarden.quest;

import java.util.List;

/**
 * Static quest definition (content/quests.json). Progress per save lives in
 * QuestProgress; rewards are claimed exactly once.
 *
 * Objective types: KILL | TALK | LOCATION | ITEM | FLAG | MEMORY
 * The target is a stable code of the enemy/NPC/location/item/flag/memory.
 */
public record QuestDef(
        String code,
        String name,
        String summary,
        String prerequisiteFlag,   // story flag that must be "true" to start, or null
        List<Objective> objectives,
        List<Reward> rewards
) {
    public Objective objective(String id) {
        if (objectives == null || id == null) {
            return null;
        }
        return objectives.stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
    }

    public record Objective(String id, String text, String type, String target, int count) {
    }

    /** Rewards granted as one atomic bundle when all objectives are complete. */
    public record Reward(String itemCode, int quantity, int xp, int fragments, String abilityCode, String memoryCode) {
    }
}
