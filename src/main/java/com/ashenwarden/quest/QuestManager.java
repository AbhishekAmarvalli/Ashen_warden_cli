package com.ashenwarden.quest;

import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameEvent;
import com.ashenwarden.engine.GameState;
import com.ashenwarden.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes domain events and advances quest progress. Rewards are granted at
 * most once per save (rewardClaimed is persisted). Quest code strings are
 * validated against content at startup, so no event can corrupt a save.
 */
public final class QuestManager {
    private final ContentRepository content;

    public QuestManager(ContentRepository content) {
        this.content = content;
    }

    /** Starts a quest if it has not been started; returns player-facing lines. */
    public List<String> startQuest(GameState state, String questCode) {
        QuestDef def = content.quest(questCode);
        QuestProgress progress = state.ensureQuest(questCode);
        if (progress.getStatus() != QuestProgress.Status.NOT_STARTED) {
            return List.of();
        }
        progress.setStatus(QuestProgress.Status.ACTIVE);
        state.markDirty();
        List<String> out = new ArrayList<>();
        out.add("NEW QUEST: " + def.name());
        out.add("  " + def.summary());
        return out;
    }

    /**
     * Advances quests from a batch of events; emits reward events (ability,
     * memory) back into the state for the dispatcher to route.
     * @return player-facing quest update lines
     */
    public List<String> process(GameState state, List<GameEvent> events) {
        List<String> out = new ArrayList<>();
        if (events == null || events.isEmpty()) {
            return out;
        }
        for (GameEvent event : events) {
            for (QuestDef quest : content.quests()) {
                QuestProgress progress = state.quest(quest.code());
                if (progress == null || !progress.isActive() || quest.objectives() == null) {
                    continue;
                }
                for (QuestDef.Objective objective : quest.objectives()) {
                    if (progress.objectiveDone(objective.id())) {
                        continue;
                    }
                    if (!matches(objective, event, state)) {
                        continue;
                    }
                    if (objective.count() > 1) {
                        progress.incrementCounter(objective.id());
                        if (progress.counter(objective.id()) < objective.count()) {
                            state.markDirty();
                            out.add(quest.name() + ": " + objective.text()
                                    + " (" + progress.counter(objective.id()) + "/" + objective.count() + ")");
                            continue;
                        }
                    }
                    progress.markObjective(objective.id());
                    state.markDirty();
                    out.add("Quest updated — " + quest.name() + ": " + objective.text() + " [done]");
                }
                if (allObjectivesDone(quest, progress)) {
                    out.addAll(completeQuest(state, quest, progress));
                }
            }
        }
        return out;
    }

    /**
     * Re-evaluates FLAG/MEMORY objectives (flags change outside the event flow).
     * Call after dialogue choices and manual flag updates.
     */
    public List<String> evaluatePassiveObjectives(GameState state) {
        List<String> out = new ArrayList<>();
        for (QuestDef quest : content.quests()) {
            QuestProgress progress = state.quest(quest.code());
            if (progress == null || !progress.isActive() || quest.objectives() == null) {
                continue;
            }
            boolean changed = true;
            while (changed) { // an objective may unlock another via rewards
                changed = false;
                for (QuestDef.Objective objective : quest.objectives()) {
                    if (progress.objectiveDone(objective.id())) {
                        continue;
                    }
                    if (!passivelySatisfied(objective, state)) {
                        continue;
                    }
                    progress.markObjective(objective.id());
                    state.markDirty();
                    changed = true;
                    out.add("Quest updated — " + quest.name() + ": " + objective.text() + " [done]");
                }
                if (changed && allObjectivesDone(quest, progress)) {
                    out.addAll(completeQuest(state, quest, progress));
                    changed = false;
                }
            }
        }
        return out;
    }

    private boolean matches(QuestDef.Objective objective, GameEvent event, GameState state) {
        String type = objective.type() == null ? "" : objective.type();
        String target = objective.target();
        return switch (type) {
            case "KILL" -> event.type() == GameEvent.Type.ENEMY_DEFEATED
                    && event.code().equals(target);
            case "TALK" -> event.type() == GameEvent.Type.NPC_SPOKEN_TO
                    && event.code().equals(target);
            case "LOCATION" -> event.type() == GameEvent.Type.LOCATION_VISITED
                    && event.code().equals(target);
            case "ITEM" -> event.type() == GameEvent.Type.ITEM_OBTAINED
                    && event.code().equals(target);
            case "MEMORY" -> event.type() == GameEvent.Type.MEMORY_ACQUIRED
                    && event.code().equals(target);
            case "FLAG" -> false; // evaluated by evaluatePassiveObjectives
            default -> false;
        };
    }

    private boolean passivelySatisfied(QuestDef.Objective objective, GameState state) {
        String type = objective.type() == null ? "" : objective.type();
        String target = objective.target();
        return switch (type) {
            case "FLAG" -> target != null && "true".equals(state.getFlag(target));
            case "MEMORY" -> target != null && state.getMemories().contains(target);
            case "LOCATION" -> target != null && state.getDiscoveredLocations().contains(target);
            default -> false;
        };
    }

    private boolean allObjectivesDone(QuestDef quest, QuestProgress progress) {
        if (quest.objectives() == null || quest.objectives().isEmpty()) {
            return false;
        }
        return quest.objectives().stream().allMatch(o -> progress.objectiveDone(o.id()));
    }

    /** Claims the reward bundle exactly once; returns player-facing lines. */
    private List<String> completeQuest(GameState state, QuestDef quest, QuestProgress progress) {
        if (progress.isRewardClaimed()) {
            return List.of();
        }
        progress.setRewardClaimed(true);
        progress.setStatus(QuestProgress.Status.COMPLETED);
        state.markDirty();

        List<String> out = new ArrayList<>();
        out.add("QUEST COMPLETED: " + quest.name());
        if (quest.rewards() == null) {
            state.addEvent(GameEvent.of(GameEvent.Type.QUEST_COMPLETED, quest.code()));
            return out;
        }
        for (QuestDef.Reward reward : quest.rewards()) {
            if (reward.itemCode() != null && reward.quantity() > 0) {
                if (state.getInventory().add(reward.itemCode(), reward.quantity())) {
                    out.add("  Received: " + content.item(reward.itemCode()).name() + " x" + reward.quantity());
                    state.addEvent(GameEvent.withAmount(GameEvent.Type.ITEM_OBTAINED,
                            reward.itemCode(), reward.quantity()));
                } else {
                    out.add("  (Inventory full — " + content.item(reward.itemCode()).name()
                            + " could not be taken; see shop/space limits.)");
                }
            }
            if (reward.fragments() > 0) {
                state.getPlayer().gainFragments(reward.fragments());
                out.add("  Ash Fragments +" + reward.fragments());
            }
            if (reward.xp() > 0) {
                Player player = state.getPlayer();
                int levels = player.gainXp(reward.xp());
                out.add("  XP +" + reward.xp() + (levels > 0 ? " — LEVEL UP to Lv. " + player.getLevel() : ""));
            }
            if (reward.abilityCode() != null && !reward.abilityCode().isBlank()) {
                if (state.unlockAbility(reward.abilityCode())) {
                    out.add("  ABILITY UNLOCKED: " + reward.abilityCode().replace('_', ' '));
                }
            }
            if (reward.memoryCode() != null && !reward.memoryCode().isBlank()) {
                if (state.acquireMemory(reward.memoryCode())) {
                    out.add("  MEMORY RECOVERED: " + content.memory(reward.memoryCode()).title());
                }
            }
        }
        state.addEvent(GameEvent.of(GameEvent.Type.QUEST_COMPLETED, quest.code()));
        return out;
    }

    // --- command text -------------------------------------------------------
    public List<String> questsText(GameState state) {
        List<String> out = new ArrayList<>();
        boolean any = false;
        for (QuestDef quest : content.quests()) {
            QuestProgress progress = state.quest(quest.code());
            if (progress == null || progress.getStatus() == QuestProgress.Status.NOT_STARTED) {
                continue;
            }
            any = true;
            out.add(quest.name() + "  [" + (progress.isCompleted() ? "COMPLETED" : "ACTIVE") + "]");
            out.add("  " + quest.summary());
            if (quest.objectives() != null) {
                for (QuestDef.Objective objective : quest.objectives()) {
                    boolean done = progress.objectiveDone(objective.id());
                    String count = objective.count() > 1
                            ? " (" + Math.min(progress.counter(objective.id()), objective.count())
                            + "/" + objective.count() + ")"
                            : "";
                    out.add("    [" + (done ? "x" : " ") + "] " + objective.text() + count);
                }
            }
        }
        if (!any) {
            out.add("No quests yet. Speak to those who still draw breath — and some who do not.");
        }
        return out;
    }

    public List<String> journalText(GameState state) {
        List<String> out = new ArrayList<>();
        out.add("JOURNAL — " + state.getPlayer().getName() + ", " + state.getChapter());
        out.add("Memories recovered: " + state.getMemories().size() + "/" + com.ashenwarden.story.MemoryDef.TOTAL);
        out.add("");
        out.addAll(questsText(state));
        return out;
    }
}
