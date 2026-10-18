package com.ashenwarden.item;

import com.ashenwarden.common.ContentException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable catalogue of item definitions keyed by stable item_code.
 * Duplicate or malformed codes fail fast at startup (never inside a save file).
 */
public final class ItemCatalog {
    private final Map<String, ItemDef> byCode = new LinkedHashMap<>();

    public ItemCatalog(Collection<ItemDef> defs) {
        for (ItemDef def : defs) {
            if (def.code() == null || def.code().isBlank()) {
                throw new ContentException("Item definition missing a stable code: " + def.name());
            }
            if (byCode.putIfAbsent(def.code(), def) != null) {
                throw new ContentException("Duplicate item code: " + def.code());
            }
            if (def.type() == null || def.rarity() == null) {
                throw new ContentException("Item '" + def.code() + "' is missing type or rarity.");
            }
            if (def.type() == ItemType.WEAPON && def.weapon() == null) {
                throw new ContentException("Weapon '" + def.code() + "' has no weapon stats.");
            }
            if (def.type() == ItemType.CONSUMABLE && def.consumable() == null) {
                throw new ContentException("Consumable '" + def.code() + "' has no effect.");
            }
        }
    }

    public ItemDef require(String code) {
        ItemDef def = byCode.get(code);
        if (def == null) {
            throw new ContentException("Unknown item code: " + code);
        }
        return def;
    }

    public ItemDef find(String code) {
        return byCode.get(code);
    }

    public boolean contains(String code) {
        return byCode.containsKey(code);
    }

    public Collection<ItemDef> all() {
        return Collections.unmodifiableCollection(byCode.values());
    }

    public int size() {
        return byCode.size();
    }
}
