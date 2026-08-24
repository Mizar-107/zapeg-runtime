package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class NinthFormCombatGeometryTest {

    private static final Vec3 ORIGIN = new Vec3(0.0D, 64.0D, 0.0D);

    @Test
    void virtualConfinementIsExactlyFortyEightBlocks() {
        assertTrue(NinthFormCombatGeometry.insideConfinement(
                ORIGIN, new Vec3(48.0D, 64.0D, 0.0D)));
        assertFalse(NinthFormCombatGeometry.insideConfinement(
                ORIGIN, new Vec3(48.01D, 64.0D, 0.0D)));
        assertEquals(Vec3.ZERO, NinthFormCombatGeometry.confinementImpulse(
                ORIGIN, new Vec3(20.0D, 64.0D, 0.0D)));
        Vec3 pull = NinthFormCombatGeometry.confinementImpulse(
                ORIGIN, new Vec3(52.0D, 64.0D, 0.0D));
        assertTrue(pull.x < 0.0D);
        assertTrue(pull.length() <= 1.0D);
    }

    @Test
    void authoredAttackShapesRespectShipFacing() {
        assertTrue(NinthFormCombatGeometry.insideKeelSweep(
                ORIGIN, new Vec3(12.0D, 64.0D, 0.0D)));
        assertTrue(NinthFormCombatGeometry.insideAnchorfall(
                new Vec3(5.0D, 64.0D, 5.0D), new Vec3(8.0D, 64.0D, 8.0D)));
        // yaw 0 faces +Z: broadside lies on X and wake/gaze lie on +Z.
        assertTrue(NinthFormCombatGeometry.insideBroadside(
                ORIGIN, 0.0F, new Vec3(20.0D, 64.0D, 3.0D)));
        assertFalse(NinthFormCombatGeometry.insideBroadside(
                ORIGIN, 0.0F, new Vec3(2.0D, 64.0D, 20.0D)));
        assertTrue(NinthFormCombatGeometry.insideWakeCharge(
                ORIGIN, 0.0F, new Vec3(3.0D, 64.0D, 20.0D)));
        assertTrue(NinthFormCombatGeometry.insideNinefoldGaze(
                ORIGIN, 0.0F, new Vec3(1.0D, 64.0D, 30.0D)));
        assertFalse(NinthFormCombatGeometry.insideNinefoldGaze(
                ORIGIN, 0.0F, new Vec3(30.0D, 64.0D, 1.0D)));
    }

    @Test
    void windupYawTracksByABoundedShortestArc() {
        assertEquals(0.0F, NinthFormCombatGeometry.boundedYawToward(
                0.0F, ORIGIN, new Vec3(0.0D, 64.0D, 20.0D), 4.0F));
        assertEquals(-4.0F, NinthFormCombatGeometry.boundedYawToward(
                0.0F, ORIGIN, new Vec3(20.0D, 64.0D, 0.0D), 4.0F));
        assertEquals(-180.0F, NinthFormCombatGeometry.boundedYawToward(
                179.0F, ORIGIN, new Vec3(0.0D, 64.0D, -20.0D), 4.0F));
    }

    @Test
    void anchorfallGeometryRetainsBlockPrecisionAtLargeWorldCoordinates() {
        Vec3 impact = new Vec3(29_999_999.75D, 80.0D, -29_999_999.75D);
        assertTrue(NinthFormCombatGeometry.insideAnchorfall(
                impact, impact.add(4.75D, 0.0D, 0.0D)));
        assertFalse(NinthFormCombatGeometry.insideAnchorfall(
                impact, impact.add(5.25D, 0.0D, 0.0D)));
    }
}
