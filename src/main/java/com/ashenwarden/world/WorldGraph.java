package com.ashenwarden.world;

import com.ashenwarden.common.ContentException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adjacency map of the world. Evaluates traversal requirements and explains
 * exactly which known ability is missing (never silent guessing).
 */
public final class WorldGraph {
    private final Map<String, Location> locations = new LinkedHashMap<>();
    private final String startLocationCode;

    public WorldGraph(Collection<Location> defs, String startLocationCode) {
        for (Location loc : defs) {
            if (loc.code() == null || loc.code().isBlank()) {
                throw new ContentException("Location definition is missing its stable code.");
            }
            if (locations.putIfAbsent(loc.code(), loc) != null) {
                throw new ContentException("Duplicate location code: " + loc.code());
            }
        }
        if (!locations.containsKey(startLocationCode)) {
            throw new ContentException("Start location not found: " + startLocationCode);
        }
        this.startLocationCode = startLocationCode;
        // cross-check exits
        for (Location loc : locations.values()) {
            if (loc.exits() == null) {
                continue;
            }
            for (Location.Exit exit : loc.exits()) {
                if (!locations.containsKey(exit.targetCode())) {
                    throw new ContentException("Exit '" + exit.label() + "' in " + loc.code()
                            + " points to unknown location: " + exit.targetCode());
                }
            }
        }
    }

    public String startLocationCode() {
        return startLocationCode;
    }

    public Location get(String code) {
        Location loc = locations.get(code);
        if (loc == null) {
            throw new ContentException("Unknown location code: " + code);
        }
        return loc;
    }

    public Location find(String code) {
        return locations.get(code);
    }

    public Collection<Location> all() {
        return Collections.unmodifiableCollection(locations.values());
    }

    /**
     * Checks one exit against unlocked abilities.
     *
     * @return null when the exit is passable, otherwise the player-facing reason
     */
    public String blockedReason(Location loc, Location.Exit exit, java.util.Set<String> unlockedAbilities) {
        if (exit == null) {
            return "There is no such way from here.";
        }
        if (exit.isBlockedByAbility(false) && !unlockedAbilities.contains(exit.requiresAbility())) {
            String msg = exit.blockedMessage();
            return (msg == null || msg.isBlank())
                    ? "Blocked. Requires ability: " + exit.requiresAbility() + "."
                    : msg;
        }
        return null;
    }
}
