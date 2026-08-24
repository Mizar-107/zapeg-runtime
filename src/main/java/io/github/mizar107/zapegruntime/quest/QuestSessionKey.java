package io.github.mizar107.zapegruntime.quest;

import java.util.Objects;

/** Canonical reset boundary for every partial multi-tick or bell sequence. */
record QuestSessionKey(
        QuestAction action, String dimension, String nodeId, long recoveryEpoch) {

    QuestSessionKey {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(nodeId, "nodeId");
        if (dimension.isBlank() || nodeId.isBlank() || recoveryEpoch < 0L) {
            throw new IllegalArgumentException("quest session key is invalid");
        }
    }

    static QuestSessionKey from(
            QuestStoryAccess.ExpectedAction expected, String dimension) {
        Objects.requireNonNull(expected, "expected");
        return new QuestSessionKey(
                expected.action(), dimension, expected.nodeId(), expected.recoveryEpoch());
    }
}
