package com.ashenwarden.common;

/**
 * Raised for domain-level failures that must be shown to the player as a friendly
 * one-line message instead of a stack trace (invalid action, blocked route, etc.).
 */
public class GameException extends RuntimeException {
    public GameException(String message) {
        super(message);
    }

    public GameException(String message, Throwable cause) {
        super(message, cause);
    }
}
