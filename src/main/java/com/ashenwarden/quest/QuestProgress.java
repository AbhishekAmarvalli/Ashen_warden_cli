package com.ashenwarden.quest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable progress of one quest in the current save slot. Reward claims are
 * tracked explicitly so a reward can never be granted twice (TEST 20).
 */
public final class QuestProgress {
    public enum Status { NOT_STARTED, ACTIVE, COMPLETED }

    private final String questCode;
    private Status status = Status.NOT_STARTED;
    private final Set<String> objectivesDone = new LinkedHashSet<>();
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private boolean rewardClaimed;

    public QuestProgress(String questCode) {
        this.questCode = questCode;
    }

    public String getQuestCode() { return questCode; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isActive() { return status == Status.ACTIVE; }
    public boolean isCompleted() { return status == Status.COMPLETED; }

    public Set<String> getObjectivesDone() { return objectivesDone; }
    public boolean objectiveDone(String objectiveId) { return objectivesDone.contains(objectiveId); }
    public void markObjective(String objectiveId) { objectivesDone.add(objectiveId); }

    public Map<String, Integer> getCounters() { return counters; }
    public int counter(String key) { return counters.getOrDefault(key, 0); }
    public void setCounter(String key, int value) { counters.put(key, Math.max(0, value)); }
    public void incrementCounter(String key) { setCounter(key, counter(key) + 1); }

    public boolean isRewardClaimed() { return rewardClaimed; }
    public void setRewardClaimed(boolean rewardClaimed) { this.rewardClaimed = rewardClaimed; }
}
