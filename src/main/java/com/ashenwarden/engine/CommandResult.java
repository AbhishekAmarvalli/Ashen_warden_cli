package com.ashenwarden.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of dispatching one parsed command: messages for the renderer, emitted
 * domain events, the next phase, and an optional prompt override. The renderer is
 * pure presentation and never mutates GameState from a CommandResult.
 */
public final class CommandResult {
    public enum Status { OK, REJECTED, FATAL }

    private final Status status;
    private final List<String> lines;
    private final List<GameEvent> events;
    private final GamePhase nextPhase;
    private final boolean quitRequested;

    private CommandResult(Status status, List<String> lines, List<GameEvent> events,
                          GamePhase nextPhase, boolean quitRequested) {
        this.status = status;
        this.lines = lines;
        this.events = events;
        this.nextPhase = nextPhase;
        this.quitRequested = quitRequested;
    }

    public static CommandResult ok() {
        return new CommandResult(Status.OK, new ArrayList<>(), new ArrayList<>(), null, false);
    }

    public static CommandResult rejected(String message) {
        CommandResult r = new CommandResult(Status.REJECTED, new ArrayList<>(), new ArrayList<>(), null, false);
        r.lines.add(message);
        return r;
    }

    public CommandResult line(String text) {
        lines.add(text);
        return this;
    }

    public CommandResult lines(List<String> texts) {
        lines.addAll(texts);
        return this;
    }

    public CommandResult event(GameEvent e) {
        events.add(e);
        return this;
    }

    public CommandResult phase(GamePhase phase) {
        return new CommandResult(status, lines, events, phase, quitRequested);
    }

    public CommandResult quit() {
        return new CommandResult(status, lines, events, nextPhase, true);
    }

    public Status status() {
        return status;
    }

    public List<String> lines() {
        return Collections.unmodifiableList(lines);
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
