package com.ashenwarden.combat;

/**
 * Documented status rules (duration, tick behavior, counterplay). Durations are
 * capped so no effect can persist forever.
 */
public enum StatusType {
    BLEED("Bleed", "Takes physical damage each round. Removed by Sacred Bandages or rest.", false),
    BURN("Burn", "Takes flame damage each round. Reduces flame resistance while active.", false),
    POISON("Poison", "Takes damage each round; healing received is reduced by 25%.", false),
    STUN("Stun", "Skips one action. Single stack only.", false),
    WEAKNESS("Weakness", "Deals 25% less outgoing damage while active.", false),
    GUARD_BREAK("Guard Break", "Defense is halved while active.", false),
    CURSE("Curse", "Special boss curse: reduces healing and max HP while active.", false);

    public static final int MAX_DURATION = 8;

    private final String label;
    private final String rules;
    private final boolean stacks;

    StatusType(String label, String rules, boolean stacks) {
        this.label = label;
        this.rules = rules;
        this.stacks = stacks;
    }

    public String label() { return label; }
    public String rules() { return rules; }
    public boolean stacks() { return stacks; }
}
