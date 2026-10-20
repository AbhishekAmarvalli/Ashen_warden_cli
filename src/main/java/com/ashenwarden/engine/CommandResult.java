package com.ashenwarden.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of dispatching one parsed command: an ordered list of blocks for the
 * renderer, emitted domain events, the next phase, and an optional prompt
 * override. A block is either plain text (narrative lines) or a titled card
 * (a screen: inventory, journal, loot...). The renderer is pure presentation
 * and never mutates GameState from a CommandResult.
 */
public final class CommandResult {
    public enum Status { OK, REJECTED, FATAL }

    /**
     * One chunk of a reply. {@code cardTitle == null} means ordinary text;
     * otherwise the lines are drawn inside a bordered card titled
     * {@code cardTitle}.
     */
    public static final class Block {
        private final String cardTitle;
        private final List<String> lines;

        Block(String cardTitle, List<String> lines) {
            this.cardTitle = cardTitle;
            this.lines = lines;
        }

        public String cardTitle() {
            return cardTitle;
        }

        public boolean isCard() {
            return cardTitle != null;
        }

        public List<String> lines() {
            return Collections.unmodifiableList(lines);
        }
    }

    private final Status status;
    private final List<Block> blocks;
    private final List<GameEvent> events;
    private final GamePhase nextPhase;
    private final boolean quitRequested;

    private CommandResult(Status status, List<Block> blocks, List<GameEvent> events,
                          GamePhase nextPhase, boolean quitRequested) {
        this.status = status;
        this.blocks = blocks;
        this.events = events;
        this.nextPhase = nextPhase;
        this.quitRequested = quitRequested;
    }

    public static CommandResult ok() {
        return new CommandResult(Status.OK, new ArrayList<>(), new ArrayList<>(), null, false);
    }

    public static CommandResult rejected(String message) {
        CommandResult r = new CommandResult(Status.REJECTED, new ArrayList<>(), new ArrayList<>(), null, false);
        r.line(message);
        return r;
    }

    // --- building ----------------------------------------------------------

    public CommandResult line(String text) {
        plainBlock().lines.add(text);
        return this;
    }

    public CommandResult lines(List<String> texts) {
        plainBlock().lines.addAll(texts);
        return this;
    }

    /** Appends a titled card (a screen) to the reply. */
    public CommandResult card(String title, List<String> body) {
        if (body != null && !body.isEmpty()) {
            blocks.add(new Block(title, new ArrayList<>(body)));
        }
        return this;
    }

    /**
     * Appends a card whose first line is the screen header, e.g.
     * "INVENTORY - inspect an item before choosing".
     */
    public CommandResult cardLines(List<String> body) {
        if (body == null || body.isEmpty()) {
            return this;
        }
        return card(body.get(0), body.subList(1, body.size()));
    }

    /** Merges another result's blocks, keeping card boundaries. */
    public CommandResult blocks(List<Block> more) {
        blocks.addAll(more);
        return this;
    }

    private Block plainBlock() {
        if (!blocks.isEmpty()) {
            Block last = blocks.get(blocks.size() - 1);
            if (!last.isCard()) {
                return last;
            }
        }
        Block created = new Block(null, new ArrayList<>());
        blocks.add(created);
        return created;
    }

    // --- chaining ----------------------------------------------------------

    public CommandResult event(GameEvent e) {
        events.add(e);
        return this;
    }

    public CommandResult phase(GamePhase phase) {
        return new CommandResult(status, blocks, events, phase, quitRequested);
    }

    public CommandResult quit() {
        return new CommandResult(status, blocks, events, nextPhase, true);
    }

    // --- reading -----------------------------------------------------------

    public Status status() {
        return status;
    }

    public List<Block> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    /** All text lines, cards included — for callers that only need content. */
    public List<String> lines() {
        List<String> all = new ArrayList<>();
        for (Block block : blocks) {
            all.addAll(block.lines);
        }
        return all;
    }

    public List<GameEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public GamePhase nextPhase() {
        return nextPhase;
    }

    public boolean isQuitRequested() {
        return quitRequested;
    }
}
