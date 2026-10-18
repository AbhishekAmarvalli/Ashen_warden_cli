package com.ashenwarden.player;

import com.ashenwarden.combat.StatusInstance;
import com.ashenwarden.combat.StatusType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Ashen Warden: base (unequipped) stats and progression. Equipment never
 * mutates these fields — derived values are computed by EffectiveStats.
 */
public final class Player {
    public static final int BASE_MAX_HP = 100;
    public static final int BASE_MAX_STAMINA = 50;
    public static final int BASE_MAX_ASH = 30;
    public static final int BASE_ATTACK = 10;
    public static final int BASE_DEFENSE = 5;

    private String name;
    private int level = 1;
    private int xp;
    private int maxHp = BASE_MAX_HP;
    private int hp = BASE_MAX_HP;
    private int maxStamina = BASE_MAX_STAMINA;
    private int stamina = BASE_MAX_STAMINA;
    private int maxAsh = BASE_MAX_ASH;
    private int ash = BASE_MAX_ASH;
    private int baseAttack = BASE_ATTACK;
    private int baseDefense = BASE_DEFENSE;
    private int fragments;
    private int lostFragments;
    private String lostFragmentLocation;
    private final List<StatusInstance> statuses = new ArrayList<>();

    public Player(String name) {
        this.name = (name == null || name.isBlank()) ? "Warden" : name.trim();
    }

    // --- identity / progression -------------------------------------------
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = Math.max(0, xp); }
    public void setLevel(int level) { this.level = Math.max(1, level); }

    /** Predictable, viewable threshold: 100 XP per level above level 1. */
    public static int xpToNext(int level) {
        return level * 100;
    }

    /**
     * Grants XP and applies every level earned (one clear system: level-up fully
     * restores resources and grants fixed stat gains).
     *
     * @return number of levels gained
     */
    public int gainXp(int amount) {
        if (amount <= 0) {
            return 0;
        }
        xp += amount;
        int gained = 0;
        while (xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            maxHp += 10;
            maxStamina += 5;
            maxAsh += 5;
            baseAttack += 2;
            baseDefense += 1;
            hp = maxHp;
            stamina = maxStamina;
            ash = maxAsh;
            gained++;
        }
        return gained;
    }

    // --- resources ---------------------------------------------------------
    public int getMaxHp() { return maxHp; }
    public int getHp() { return hp; }
    public int getMaxStamina() { return maxStamina; }
    public int getStamina() { return stamina; }
    public int getMaxAsh() { return maxAsh; }
    public int getAsh() { return ash; }
    public int getBaseAttack() { return baseAttack; }
    public int getBaseDefense() { return baseDefense; }
    public int getFragments() { return fragments; }

    public void setMaxHp(int v) { maxHp = Math.max(1, v); }
    public void setHp(int v) { hp = clamp(v, 0, maxHp); }
    public void setMaxStamina(int v) { maxStamina = Math.max(1, v); }
    public void setStamina(int v) { stamina = clamp(v, 0, maxStamina); }
    public void setMaxAsh(int v) { maxAsh = Math.max(0, v); }
    public void setAsh(int v) { ash = clamp(v, 0, maxAsh); }
    public void setBaseAttack(int v) { baseAttack = Math.max(0, v); }
    public void setBaseDefense(int v) { baseDefense = Math.max(0, v); }
    public void setFragments(int v) { fragments = Math.max(0, v); }

    public void heal(int amount) { hp = clamp(hp + Math.max(0, amount), 0, maxHp); }
    public int takeDamage(int amount) {
        int applied = clamp(amount, 0, hp);
        hp -= applied;
        return applied;
    }
    public boolean isDead() { return hp <= 0; }

    public boolean spendStamina(int amount) {
        if (stamina < amount) { return false; }
        stamina -= amount;
        return true;
    }
    public boolean spendAsh(int amount) {
        if (ash < amount) { return false; }
        ash -= amount;
        return true;
    }
    public void restoreStamina(int amount) { stamina = clamp(stamina + amount, 0, maxStamina); }
    public void restoreAsh(int amount) { ash = clamp(ash + amount, 0, maxAsh); }

    public boolean spendFragments(int amount) {
        if (fragments < amount) { return false; }
        fragments -= amount;
        return true;
    }
    public void gainFragments(int amount) { fragments = Math.max(0, fragments + amount); }

    public int getLostFragments() { return lostFragments; }
    public String getLostFragmentLocation() { return lostFragmentLocation; }
    public void setLostFragments(int amount, String location) {
        lostFragments = Math.max(0, amount);
        lostFragmentLocation = location;
    }
    public void clearLostFragments() {
        lostFragments = 0;
        lostFragmentLocation = null;
    }

    // --- statuses ----------------------------------------------------------
    public List<StatusInstance> getStatuses() { return statuses; }

    public void addStatus(StatusInstance status) {
        for (StatusInstance existing : statuses) {
            if (existing.type() == status.type()) {
                existing.refresh(status.power(), status.remainingTurns());
                return;
            }
        }
        statuses.add(status);
    }

    public boolean hasStatus(StatusType type) {
        return statuses.stream().anyMatch(s -> s.type() == type);
    }

    public void removeStatus(StatusType type) {
        statuses.removeIf(s -> s.type() == type);
    }

    /**
     * End-of-round status update. A status applied this round is 'fresh' and does
     * not tick yet (so a 1-turn STUN always blocks exactly one action first).
     * Returns the statuses that ticked this round (these deal their DoT).
     */
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

    public void clearStatuses() { statuses.clear(); }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
