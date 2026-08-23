package io.github.mizar107.zapegruntime.servant;

import java.util.UUID;
import javax.annotation.Nullable;

/** Pure gate between a committed LivingEntity death and campaign credit. */
public final class ServantDeathCreditGate {

    private ServantDeathCreditGate() {}

    public static boolean shouldCredit(
            boolean deadBeforeDie,
            boolean deadAfterDie,
            @Nullable UUID designatedTargetId,
            @Nullable UUID killerId) {
        return !deadBeforeDie
                && deadAfterDie
                && ServantCombatPolicy.allows(designatedTargetId, killerId);
    }
}
