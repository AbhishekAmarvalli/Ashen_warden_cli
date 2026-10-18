package com.ashenwarden.common;

/**
 * Raised when a database operation fails. The engine catches this, rolls back any
 * open transaction and shows recovery guidance without printing a raw stack trace.
 */
public class PersistenceException extends GameException {
    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
