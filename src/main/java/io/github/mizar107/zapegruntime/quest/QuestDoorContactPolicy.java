package io.github.mizar107.zapegruntime.quest;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.AABB;

/** Pure narrow-contact test between a colliding player and one door collision shape. */
public final class QuestDoorContactPolicy {

    public static final double HORIZONTAL_CONTACT_EPSILON = 0.04D;
    private static final double VERTICAL_EDGE_EPSILON = 0.01D;

    private QuestDoorContactPolicy() {}

    public static boolean touchesClosedDoor(
            boolean horizontalCollision, AABB playerBox, List<AABB> doorCollisionBoxes) {
        Objects.requireNonNull(playerBox, "playerBox");
        Objects.requireNonNull(doorCollisionBoxes, "doorCollisionBoxes");
        if (!horizontalCollision || doorCollisionBoxes.isEmpty()) {
            return false;
        }
        AABB contactProbe = new AABB(
                playerBox.minX - HORIZONTAL_CONTACT_EPSILON,
                playerBox.minY + VERTICAL_EDGE_EPSILON,
                playerBox.minZ - HORIZONTAL_CONTACT_EPSILON,
                playerBox.maxX + HORIZONTAL_CONTACT_EPSILON,
                playerBox.maxY - VERTICAL_EDGE_EPSILON,
                playerBox.maxZ + HORIZONTAL_CONTACT_EPSILON);
        for (AABB doorBox : doorCollisionBoxes) {
            if (contactProbe.intersects(Objects.requireNonNull(doorBox, "doorBox"))) {
                return true;
            }
        }
        return false;
    }
}
