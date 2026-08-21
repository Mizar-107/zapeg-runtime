package io.github.mizar107.zapegruntime.scene;

import java.util.UUID;

/**
 * The client-side holding slot for a scene that arrived while a screen was
 * open (chat, an inventory, a modpack terminal). Aborting there would
 * silently burn a ledger-consumed Director beat, so the descriptor waits
 * here instead: acknowledged as delivered on receipt, started — and only
 * then aged, so the presented TTL begins at the actual visual start — once
 * the screen closes.
 *
 * <p>Bounds: a single slot; a newer spawn silently replaces an older held
 * one (the server's one-active-scene occupancy makes that pathological); a
 * cancel for the held event clears it; logout and level unload clear it via
 * {@link #clear()}. The server-side occupancy expiry keeps even a
 * never-closed screen bounded — the hold cannot outlive it. Pure state
 * machine so the whole policy is unit-testable without a Minecraft client.
 */
public final class PendingSceneHold {

    private SceneDescriptor held;

    /**
     * Offers a freshly delivered descriptor. Returns {@code true} when the
     * scene should start immediately; {@code false} when it was taken into
     * the hold instead. Either way any previously held scene is dropped —
     * newest wins.
     */
    public boolean offer(SceneDescriptor descriptor, boolean screenOpen) {
        if (!screenOpen) {
            held = null;
            return true;
        }
        held = descriptor;
        return false;
    }

    /**
     * Releases the held scene for its visual start once no screen is open;
     * {@code null} while the hold must continue (or nothing is held).
     */
    public SceneDescriptor promote(boolean screenOpen) {
        if (screenOpen || held == null) {
            return null;
        }
        SceneDescriptor promoted = held;
        held = null;
        return promoted;
    }

    /** Clears the hold when the server cancels the held event. */
    public boolean cancel(UUID eventId) {
        if (held != null && held.eventId().equals(eventId)) {
            held = null;
            return true;
        }
        return false;
    }

    /** Logout, level unload, account switch: nothing survives. */
    public void clear() {
        held = null;
    }

    public SceneDescriptor heldScene() {
        return held;
    }
}
