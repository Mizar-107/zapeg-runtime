package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Pins the GUI-hold contract: a scene delivered while a screen is open is
 * held (never aborted, never started), starts exactly once when the screen
 * closes, and is cleared by cancel/logout. Newest spawn wins the single
 * slot silently.
 */
class PendingSceneHoldTest {

    @Test
    void anOpenScreenHoldsTheSceneAndAClosedOneStartsIt() {
        PendingSceneHold hold = new PendingSceneHold();
        SceneDescriptor descriptor = descriptor();
        assertTrue(hold.offer(descriptor, false), "no screen: start immediately");
        assertNull(hold.heldScene(), "an immediate start leaves nothing held");

        assertFalse(hold.offer(descriptor, true), "an open screen holds the scene");
        assertEquals(descriptor, hold.heldScene());
        assertNull(hold.promote(true), "the hold continues while the screen is open");
        assertEquals(descriptor, hold.promote(false), "closing the screen releases it");
        assertNull(hold.heldScene(), "promotion empties the slot");
        assertNull(hold.promote(false), "a promoted scene never starts twice");
    }

    @Test
    void aSecondSpawnKeepsTheNewestAndDropsTheOldestSilently() {
        PendingSceneHold hold = new PendingSceneHold();
        SceneDescriptor first = descriptor();
        SceneDescriptor second = descriptor();
        assertFalse(hold.offer(first, true));
        assertFalse(hold.offer(second, true));
        assertEquals(second, hold.heldScene(), "newest wins the single slot");
        assertEquals(second, hold.promote(false));
        assertNull(hold.promote(false), "the dropped scene is gone, not queued");
    }

    @Test
    void aSpawnWithTheScreenClosedDropsAnyStaleHold() {
        PendingSceneHold hold = new PendingSceneHold();
        SceneDescriptor stale = descriptor();
        SceneDescriptor fresh = descriptor();
        assertFalse(hold.offer(stale, true));
        assertTrue(hold.offer(fresh, false), "the fresh scene starts immediately");
        assertNull(hold.heldScene(), "the stale hold was dropped, not resurrected");
    }

    @Test
    void cancelClearsOnlyTheMatchingHeldEvent() {
        PendingSceneHold hold = new PendingSceneHold();
        SceneDescriptor descriptor = descriptor();
        assertFalse(hold.offer(descriptor, true));
        assertFalse(hold.cancel(UUID.randomUUID()), "a foreign cancel is ignored");
        assertEquals(descriptor, hold.heldScene());
        assertTrue(hold.cancel(descriptor.eventId()));
        assertNull(hold.heldScene());
        assertFalse(hold.cancel(descriptor.eventId()), "cancel is idempotent");
    }

    @Test
    void clearEmptiesTheSlotForLogoutAndLevelUnload() {
        PendingSceneHold hold = new PendingSceneHold();
        assertFalse(hold.offer(descriptor(), true));
        hold.clear();
        assertNull(hold.heldScene());
        assertNull(hold.promote(false), "nothing survives a logout");
    }

    private static SceneDescriptor descriptor() {
        return new SceneDescriptor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new Vec3(12.5D, 80.0D, -3.5D),
                0.0F,
                200,
                42L,
                SceneProfile.ECHO_01,
                true,
                0);
    }
}
