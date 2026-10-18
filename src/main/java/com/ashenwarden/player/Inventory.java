package com.ashenwarden.player;

import com.ashenwarden.item.ItemDef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stackable container of item codes. Equipment uses the same map (gear stacks at
 * quantity 1..n but is only ever "used" once per slot), which keeps duplicate-equip
 * bugs structurally impossible: bonuses are derived from equipped codes, never from
 * carried quantities.
 */
public final class Inventory {
    private final Map<String, Integer> quantities = new LinkedHashMap<>();
    private int capacity; // max distinct stacks; 0 = unlimited

    public Inventory() {
        this(0);
    }

    public Inventory(int capacity) {
        this.capacity = capacity;
    }

    public int quantityOf(String itemCode) {
        return quantities.getOrDefault(itemCode, 0);
    }

    public boolean has(String itemCode, int amount) {
        return quantityOf(itemCode) >= amount;
    }

    public boolean isEmpty() {
        return quantities.isEmpty();
    }

    /**
     * Adds items. Returns false (and changes nothing) when the stack limit would be
     * exceeded or the amount is invalid — no negative or partial writes.
     */
    public boolean add(String itemCode, int amount) {
        if (itemCode == null || amount <= 0) {
            return false;
        }
        int current = quantityOf(itemCode);
        if (current == 0 && capacity > 0 && quantities.size() >= capacity) {
            return false;
        }
        quantities.put(itemCode, current + amount);
        return true;
    }

    /** Removes items if available. Returns false without mutation when not enough. */
    public boolean remove(String itemCode, int amount) {
        if (itemCode == null || amount <= 0) {
            return false;
        }
        int current = quantityOf(itemCode);
        if (current < amount) {
            return false;
        }
        if (current == amount) {
            quantities.remove(itemCode);
        } else {
            quantities.put(itemCode, current - amount);
        }
        return true;
    }

    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(quantities));
    }

    public void replaceAll(Map<String, Integer> items) {
        quantities.clear();
        if (items != null) {
            items.forEach((code, qty) -> {
                if (code != null && qty != null && qty > 0) {
                    quantities.put(code, qty);
                }
            });
        }
    }

    public boolean canCarry(ItemDef def) {
        if (def == null) {
            return false;
        }
        return quantityOf(def.code()) > 0 || capacity == 0 || quantities.size() < capacity;
    }
}
