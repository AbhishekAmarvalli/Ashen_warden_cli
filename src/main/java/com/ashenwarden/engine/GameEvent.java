package com.ashenwarden.engine;

/**
 * Domain events emitted by services. The quest/story managers consume these so
 * game systems stay decoupled (QuestManager never calls CombatEngine directly).
 */
public final class GameEvent {
    public enum Type {
        LOCATION_VISITED,
        ENEMY_DEFEATED,
        ITEM_OBTAINED,
        NPC_SPOKEN_TO,
        QUEST_STARTED,
        QUEST_OBJECTIVE_DONE,
        QUEST_COMPLETED,
        MEMORY_ACQUIRED,
        BOSS_DEFEATED,
        ABILITY_UNLOCKED,
        PLAYER_DIED,
        GAME_SAVED,
        GAME_LOADED
    }

    private final Type type;
    private final String code;
    private final int amount;

    private GameEvent(Type type, String code, int amount) {
        this.type = type;
        this.code = code;
        this.amount = amount;
    }

    public static GameEvent of(Type type, String code) {
        return new GameEvent(type, code, 1);
    }

    public static GameEvent withAmount(Type type, String code, int amount) {
        return new GameEvent(type, code, amount);
    }

    public Type type() {
        return type;
    }

    public String code() {
        return code;
    }

    public int amount() {
        return amount;
    }

    @Override
    public String toString() {
        return type + "(" + code + (amount != 1 ? ", " + amount : "") + ")";
    }
}
