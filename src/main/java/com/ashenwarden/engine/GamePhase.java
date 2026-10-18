package com.ashenwarden.engine;

/**
 * Phases of the game loop. The dispatcher only accepts commands that are legal
 * for the current phase, so "attack" in exploration is rejected without side effects.
 */
public enum GamePhase {
    MAIN_MENU,
    EXPLORATION,
    DIALOGUE,
    COMBAT,
    LOOT,
    GAME_OVER,
    ENDING
}
