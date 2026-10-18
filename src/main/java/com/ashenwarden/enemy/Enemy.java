package com.ashenwarden.enemy;

import com.ashenwarden.combat.StatusInstance;
import com.ashenwarden.combat.StatusType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runtime instance of an enemy during an encounter. Behavior state (last defend
 * round, buried flag, boss phase...) lives in the blackboard so behavior classes
 * stay small and data-driven. Randomness only ever comes from the injected seed.
 */
public final class Enemy {
    private final EnemyDef def;
    private int hp;
    private EnemyAction currentIntent;
    private boolean stunned;
    private final List<StatusInstance> statuses = new ArrayList<>();
    private final Map<String, String> blackboard = new HashMap<>();
    private int phase = 1;

    public Enemy(EnemyDef def) {
        if (def == null) {
            throw new IllegalArgumentException("enemy definition required");
        }
        this.def = def;
        this.hp = def.hp();
    }

    public EnemyDef def() { return def; }
    public String code() { return def.code(); }
    public String name() { return def.name(); }
    public int getHp() { return hp; }
    public int getMaxHp() { return def.hp(); }
    public boolean isDead() { return hp <= 0; }
    public double hpFraction() { return (double) hp / Math.max(1, def.hp()); }

    public EnemyAction getCurrentIntent() { return currentIntent; }
    public void setCurrentIntent(EnemyAction action) { this.currentIntent = action; }

    public boolean isStunned() { return stunned; }
    public void setStunned(boolean stunned) { this.stunned = stunned; }

    public int getPhase() { return phase; }
    public void setPhase(int phase) { this.phase = phase; }

    /** @return actual damage applied */
    public int takeDamage(int raw) {
        int applied = Math.max(0, Math.min(hp, raw));
        hp -= applied;
        return applied;
    }

    public void heal(int amount) {
        hp = Math.min(def.hp(), hp + Math.max(0, amount));
    }

    // --- statuses ----------------------------------------------------------
    public List<StatusInstance> getStatuses() { return statuses; }

    public boolean hasStatus(StatusType type) {
        return statuses.stream().anyMatch(s -> s.type() == type);
    }

    public void addStatus(StatusInstance status) {
        for (StatusInstance existing : statuses) {
            if (existing.type() == status.type()) {
                existing.refresh(status.power(), status.remainingTurns());
                return;
            }
        }
        statuses.add(status);
    }

    public void removeStatus(StatusType type) {
        statuses.removeIf(s -> s.type() == type);
    }

    /** Fresh statuses (applied this round) do not tick yet; returns those that do. */
    public List<StatusInstance> tickStatuses() {
        List<StatusInstance> ticked = new ArrayList<>();
        Iterator<StatusInstance> it = statuses.iterator();
        while (it.hasNext()) {
            StatusInstance s = it.next();
            if (s.isFresh()) {
                s.clearFresh();
                continue;
            }
            ticked.add(s);
            s.decrement();
            if (s.remainingTurns() <= 0) {
                it.remove();
            }
        }
        return ticked;
    }

    // --- behavior scratch space -------------------------------------------
    public void putState(String key, String value) { blackboard.put(key, value); }

    public String getState(String key) { return blackboard.get(key); }

    public boolean flag(String key) { return "true".equals(blackboard.get(key)); }

    public void setFlag(String key, boolean value) { blackboard.put(key, Boolean.toString(value)); }
}
