package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PopupOwnershipTest {

    @Test
    void onlyOnePopupLeaseCanExist() {
        PopupOwnership ownership = new PopupOwnership();
        long first = ownership.tryAcquire();

        assertNotEquals(PopupOwnership.NO_OWNER, first);
        assertEquals(PopupOwnership.NO_OWNER, ownership.tryAcquire());
        assertTrue(ownership.owns(first));
    }

    @Test
    void cleanupFailureRetainsLeaseAndFailsClosed() {
        PopupOwnership ownership = new PopupOwnership();
        long owner = ownership.tryAcquire();

        // Physical disposal did not verify, so production intentionally does
        // not call release(owner).
        assertEquals(owner, ownership.ownerToken());
        assertEquals(PopupOwnership.NO_OWNER, ownership.tryAcquire());
    }

    @Test
    void staleCallbackCannotReleaseLaterPopup() {
        PopupOwnership ownership = new PopupOwnership();
        long first = ownership.tryAcquire();
        assertTrue(ownership.release(first));
        long second = ownership.tryAcquire();

        assertFalse(ownership.release(first));
        assertTrue(ownership.owns(second));
        assertTrue(ownership.release(second));
        assertEquals(PopupOwnership.NO_OWNER, ownership.ownerToken());
    }

    @Test
    void releaseIsExactAndIdempotentlyFailClosed() {
        PopupOwnership ownership = new PopupOwnership();
        long owner = ownership.tryAcquire();

        assertFalse(ownership.release(owner + 1L));
        assertTrue(ownership.owns(owner));
        assertTrue(ownership.release(owner));
        assertFalse(ownership.release(owner));
    }
}
