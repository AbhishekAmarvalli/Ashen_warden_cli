package com.ashenwarden.item;

/** Rarity language matches the game bible: common -> legendary. */
public enum Rarity {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    LEGENDARY("Legendary");

    private final String label;

    Rarity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
