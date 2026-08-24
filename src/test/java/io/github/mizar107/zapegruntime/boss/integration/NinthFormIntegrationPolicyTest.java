package io.github.mizar107.zapegruntime.boss.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mizar107.zapegruntime.boss.combat.NinthFormScaling;
import io.github.mizar107.zapegruntime.boss.encounter.NinthFormScalingPolicy;
import org.junit.jupiter.api.Test;

class NinthFormIntegrationPolicyTest {

    @Test
    void encounterAndCombatUseTheSameCappedParticipantScales() {
        for (int participants = 1; participants <= 8; participants++) {
            NinthFormScalingPolicy.Scale encounter =
                    NinthFormScalingPolicy.forParticipants(participants);
            assertEquals(
                    encounter.healthScale(),
                    NinthFormScaling.healthScale(participants),
                    0.0D);
            assertEquals(
                    encounter.damageScale(),
                    NinthFormScaling.damageScale(participants),
                    0.0D);
        }
    }
}
