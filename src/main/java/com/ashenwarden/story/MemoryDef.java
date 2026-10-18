package com.ashenwarden.story;

/**
 * One of the seven Warden memories (content/memories.json).
 * Collected once per save; shown as N/7 in the journal tracker.
 */
public record MemoryDef(
        String code,
        int order,             // 1..7
        String title,
        String cinematicText,  // short vision shown on acquisition
        String journalText     // expanded journal entry
) {
    public static final int TOTAL = 7;
}
