package com.ashenwarden.combat;

/**
 * One active status on a combatant. Refresh rule: re-applying keeps the longer
 * duration and higher power (never grows without bound past MAX_DURATION).
 */
public final class StatusInstance {
    private final StatusType type;
    private int power;
    private int remainingTurns;
    private boolean fresh = true; // applied this round: first tick happens next round

    public StatusInstance(StatusType type, int power, int remainingTurns) {
        if (type == null) {
            throw new IllegalArgumentException("status type required");
        }
        this.type = type;
        this.power = Math.max(0, power);
        this.remainingTurns = clampTurns(remainingTurns);
    }

    public StatusType type() { return type; }
    public int power() { return power; }
    public int remainingTurns() { return remainingTurns; }

    public void refresh(int newPower, int newTurns) {
        this.power = Math.max(this.power, Math.max(0, newPower));
        this.remainingTurns = Math.max(this.remainingTurns, clampTurns(newTurns));
        this.fresh = true;
    }

    /** Fresh statuses do not tick in the round they were applied. */
    public boolean isFresh() {
        return fresh;
    }

    public void clearFresh() {
        fresh = false;
    }

    public void decrement() {
        remainingTurns--;
    }

    private static int clampTurns(int turns) {
        return Math.max(1, Math.min(StatusType.MAX_DURATION, turns));
    }

    @Override
    public String toString() {
        return type.label() + " (" + remainingTurns + " turn" + (remainingTurns == 1 ? "" : "s") + ")";
    }
}
