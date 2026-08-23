package io.github.mizar107.zapegruntime.servant;

import java.util.UUID;
import javax.annotation.Nullable;

/** Pure exact-identity check used by EntityJoinLevelEvent. */
public final class ServantJoinPolicy {

    private ServantJoinPolicy() {}

    public static boolean accepts(
            @Nullable ServantEncounter persisted,
            @Nullable UUID entityEncounterId,
            @Nullable UUID entityTargetId,
            UUID entityId,
            String entityDimension,
            boolean entityRehearsal,
            long entityDeadline) {
        return persisted != null
                && persisted.encounterId().equals(entityEncounterId)
                && persisted.targetId().equals(entityTargetId)
                && persisted.servantId().equals(entityId)
                && persisted.dimension().equals(entityDimension)
                && persisted.rehearsal() == entityRehearsal
                && persisted.deadlineGameTime() == entityDeadline;
    }
}
