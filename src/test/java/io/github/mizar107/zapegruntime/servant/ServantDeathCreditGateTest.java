package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServantDeathCreditGateTest {

    @Test
    void creditsOnlyTheFirstCommittedDeathByTheDesignatedTarget() {
        UUID target = UUID.randomUUID();

        assertTrue(ServantDeathCreditGate.shouldCredit(false, true, target, target));
        assertFalse(ServantDeathCreditGate.shouldCredit(false, false, target, target),
                "canceled/uncommitted death must not credit");
        assertFalse(ServantDeathCreditGate.shouldCredit(true, true, target, target),
                "a repeated die call must not credit");
        assertFalse(ServantDeathCreditGate.shouldCredit(
                false, true, target, UUID.randomUUID()));
        assertFalse(ServantDeathCreditGate.shouldCredit(false, true, target, null));
    }

    @Test
    void canceledDeathLeavesTheLedgerActiveAndUncredited() {
        UUID event = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        ServantEncounter encounter = new ServantEncounter(
                event,
                target,
                entity,
                "minecraft:overworld",
                false,
                10_000L,
                false);
        ServantEncounterData data = new ServantEncounterData();
        data.begin(encounter);

        boolean shouldCredit =
                ServantDeathCreditGate.shouldCredit(false, false, target, target);
        if (shouldCredit) {
            data.finishVictory(event, entity, target);
        }

        assertTrue(data.activeFor(target).isPresent());
        assertFalse(data.isLiveVictory(event));
    }
}
