package io.github.mizar107.zapegruntime.story;

import java.util.Objects;

/** One immutable entry in the hidden journal's authoritative campaign line. */
public record StoryNode(
        String id,
        int ordinal,
        int chapter,
        String journalKey,
        boolean terminal,
        StoryTrigger advanceOn,
        String nextNodeId) {

    public StoryNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(journalKey, "journalKey");
    }
}
