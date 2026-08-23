package io.github.mizar107.zapegruntime.servant;

import java.util.UUID;
import javax.annotation.Nullable;

/** Pure UUID boundary shared by incoming and outgoing Servant combat checks. */
public final class ServantCombatPolicy {

    public static final boolean DESPAWN_IN_PEACEFUL = false;

    private ServantCombatPolicy() {}

    public static boolean allows(@Nullable UUID designatedTargetId, @Nullable UUID actorId) {
        return designatedTargetId != null && designatedTargetId.equals(actorId);
    }

    public static boolean preventsRest(
            @Nullable UUID designatedTargetId,
            @Nullable UUID playerId) {
        return allows(designatedTargetId, playerId);
    }
}
