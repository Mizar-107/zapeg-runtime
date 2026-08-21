package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import org.junit.jupiter.api.Test;

/**
 * Pins the TTL contract behind R-04: no requested scene length may ever
 * fail descriptor validation (and thereby burn a consumed event id), and
 * the command tree's floor matches the wire descriptor's.
 */
class SceneTtlContractTest {

    @Test
    void resolvedTtlAlwaysLandsInsideTheDescriptorBounds() {
        // Non-positive overrides fall back to the profile default...
        assertEquals(
                SceneProfile.ECHO_01.defaultTtlTicks(),
                SceneServerManager.resolveTtlTicks(0, SceneProfile.ECHO_01));
        assertEquals(
                SceneProfile.COLOSSUS_01.defaultTtlTicks(),
                SceneServerManager.resolveTtlTicks(-5, SceneProfile.COLOSSUS_01));
        // ...and everything else clamps into the wire bounds, never throws.
        assertEquals(
                SceneDescriptor.MIN_TTL_TICKS,
                SceneServerManager.resolveTtlTicks(1, SceneProfile.ECHO_01));
        assertEquals(
                SceneDescriptor.MIN_TTL_TICKS,
                SceneServerManager.resolveTtlTicks(19, SceneProfile.ECHO_01));
        assertEquals(20, SceneServerManager.resolveTtlTicks(20, SceneProfile.ECHO_01));
        assertEquals(600, SceneServerManager.resolveTtlTicks(600, SceneProfile.ECHO_01));
        assertEquals(
                SceneDescriptor.MAX_TTL_TICKS,
                SceneServerManager.resolveTtlTicks(5_000, SceneProfile.ECHO_01));
    }

    @Test
    void theBrigadierFloorMatchesTheWireDescriptorFloor() {
        // An operator typo below the wire minimum must be refused by the
        // command tree itself, long before the ledger is in reach.
        assertEquals(SceneDescriptor.MIN_TTL_TICKS, SceneCommands.MIN_TTL_TICKS);
        assertEquals(SceneDescriptor.MAX_TTL_TICKS, SceneServerManager.MAX_TTL_TICKS);
    }
}
