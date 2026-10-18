package com.ashenwarden.world;

import java.util.List;

/**
 * NPC definition with its dialogue tree (from content/npcs.json).
 * Dialogue options may set story flags so choices persist across saves.
 * Trees are acyclic by convention; the engine bounds conversation depth.
 */
public record Npc(
        String code,
        String name,
        String role,
        String description,
        String startNode,
        List<DialogueNode> nodes
) {
    public DialogueNode node(String id) {
        if (nodes == null || id == null) {
            return null;
        }
        return nodes.stream().filter(n -> id.equals(n.id())).findFirst().orElse(null);
    }

    /**
     * @param flagToSet optional story flag written when the option is chosen
     * @param nextId    next node id, or null to end the conversation
     */
    public record DialogueNode(String id, String text, List<DialogueOption> options) {
    }

    public record DialogueOption(String label, String nextId, String flagToSet) {
    }
}
