package com.ashenwarden.persistence;

/** Metadata of one save slot as shown in the Continue / Load menus. */
public record SaveSlotInfo(
        int saveId,
        String slotName,
        String playerName,
        String chapter,
        String locationCode,
        String gameVersion,
        String updatedAt
) {
}
