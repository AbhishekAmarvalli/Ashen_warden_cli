package com.ashenwarden.world;

import com.ashenwarden.content.ContentRepository;
import com.ashenwarden.engine.GameEvent;
import com.ashenwarden.engine.GameState;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives NPC conversations: talk opens the start node, choose advances with a
 * visible label and records choice flags, ask <topic> matches option labels.
 * Conversations are depth-bounded and never loop back on themselves silently.
 */
public final class DialogueManager {
    private static final int MAX_DEPTH = 40;

    private final ContentRepository content;

    public DialogueManager(ContentRepository content) {
        this.content = content;
    }

    /** An open conversation the engine keeps while the dialogue menu is active. */
    public static final class DialogueView {
        private final String npcCode;
        private final String nodeId;
        private final List<String> lines;
        private final List<Npc.DialogueOption> options;

        DialogueView(String npcCode, String nodeId, List<String> lines, List<Npc.DialogueOption> options) {
            this.npcCode = npcCode;
            this.nodeId = nodeId;
            this.lines = lines;
            this.options = options;
        }

        public String npcCode() {
            return npcCode;
        }

        public String nodeId() {
            return nodeId;
        }

        public List<String> lines() {
            return lines;
        }

        public List<Npc.DialogueOption> options() {
            return options;
        }
    }

    /** Opens the conversation and emits NPC_SPOKEN_TO (quest TALK objectives). */
    public DialogueView open(GameState state, String npcCode) {
        Npc npc = content.npc(npcCode);
        state.addEvent(GameEvent.of(GameEvent.Type.NPC_SPOKEN_TO, npc.code()));
        state.markDirty();
        return viewOf(state, npc, npc.startNode(), true);
    }

    /** Applies the chosen option (flag + next node). Returns null when the chat ends. */
    public DialogueView choose(GameState state, DialogueView view, int choiceNumber) {
        if (view == null) {
            return null;
        }
        Npc npc = content.npc(view.npcCode());
        Npc.DialogueNode node = npc.node(view.nodeId());
        if (node == null || node.options() == null || choiceNumber < 1 || choiceNumber > node.options().size()) {
            return null; // caller reports "no such choice"
        }
        Npc.DialogueOption option = node.options().get(choiceNumber - 1);
        return apply(state, npc, option);
    }

    /** 'ask <topic>' matches an option by label substring; null when unmatched. */
    public DialogueView ask(GameState state, DialogueView view, String topic) {
        if (view == null || topic == null || topic.isBlank()) {
            return null;
        }
        Npc npc = content.npc(view.npcCode());
        Npc.DialogueNode node = npc.node(view.nodeId());
        if (node == null || node.options() == null) {
            return null;
        }
        String want = topic.trim().toLowerCase(java.util.Locale.ROOT);
        for (Npc.DialogueOption option : node.options()) {
            if (option.label() != null && option.label().toLowerCase(java.util.Locale.ROOT).contains(want)) {
                return apply(state, npc, option);
            }
        }
        return null;
    }

    private DialogueView apply(GameState state, Npc npc, Npc.DialogueOption option) {
        if (option.flagToSet() != null && !option.flagToSet().isBlank()) {
            state.setFlag(option.flagToSet(), "true");
        }
        if (option.nextId() == null || option.nextId().isBlank()) {
            return null; // conversation ends
        }
        Npc.DialogueNode next = npc.node(option.nextId());
        if (next == null) {
            return null;
        }
        return viewOf(state, npc, option.nextId(), false);
    }

    private DialogueView viewOf(GameState state, Npc npc, String nodeId, boolean showPrompt) {
        Npc.DialogueNode node = npc.node(nodeId);
        if (node == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add(npc.name().toUpperCase() + ": " + node.text());
        if (node.options() != null && !node.options().isEmpty()) {
            if (showPrompt) {
                lines.add("(choose with a number, 'ask <topic>', or 'leave')");
            }
        }
        List<Npc.DialogueOption> options = node.options() == null
                ? List.of() : List.copyOf(node.options());
        return new DialogueView(npc.code(), nodeId, lines, options);
    }

    public int maxDepth() {
        return MAX_DEPTH;
    }
}
