package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class QuestDoorContactPolicyTest {

    private static final AABB PLAYER = new AABB(0.20D, 0.0D, 0.20D, 0.80D, 1.80D, 0.80D);

    @Test
    void narrowInflationRecognizesActualClosedDoorContact() {
        AABB touchingDoor = new AABB(0.82D, 0.0D, 0.0D, 1.00D, 2.00D, 1.00D);
        assertTrue(QuestDoorContactPolicy.touchesClosedDoor(
                true, PLAYER, List.of(touchingDoor)));
    }

    @Test
    void wallCollisionWithMerelyNearbyDoorIsRejected() {
        AABB nearbyButNotTouchingDoor =
                new AABB(1.05D, 0.0D, 0.0D, 1.20D, 2.00D, 1.00D);
        assertFalse(QuestDoorContactPolicy.touchesClosedDoor(
                true, PLAYER, List.of(nearbyButNotTouchingDoor)));
    }

    @Test
    void DoorProximityWithoutHorizontalCollisionIsRejected() {
        AABB intersectingDoor = new AABB(0.79D, 0.0D, 0.0D, 1.00D, 2.00D, 1.00D);
        assertFalse(QuestDoorContactPolicy.touchesClosedDoor(
                false, PLAYER, List.of(intersectingDoor)));
    }
}
