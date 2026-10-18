package com.ashenwarden.common;

/**
 * Raised when shipped content files (items, enemies, locations, quests) are missing,
 * duplicated or malformed. Checked at startup so broken IDs never reach a save file.
 */
public class ContentException extends GameException {
    public ContentException(String message) {
        super(message);
    }

    public ContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
