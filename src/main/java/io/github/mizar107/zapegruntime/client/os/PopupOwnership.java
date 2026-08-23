package io.github.mizar107.zapegruntime.client.os;

/**
 * Small synchronized lease for the single external popup.
 *
 * <p>A lease is released only after the owning window is verified disposed (or
 * before any window is materialised). A stale callback can neither release nor
 * mutate a later lease. Failed cleanup deliberately retains the lease, making
 * all future popup preflights fail closed.
 */
final class PopupOwnership {

    static final long NO_OWNER = 0L;

    private long nextToken;
    private long ownerToken;

    synchronized long tryAcquire() {
        if (ownerToken != NO_OWNER) {
            return NO_OWNER;
        }
        nextToken++;
        if (nextToken == NO_OWNER) {
            nextToken++;
        }
        ownerToken = nextToken;
        return ownerToken;
    }

    synchronized long ownerToken() {
        return ownerToken;
    }

    synchronized boolean owns(long token) {
        return token != NO_OWNER && ownerToken == token;
    }

    synchronized boolean release(long token) {
        if (!owns(token)) {
            return false;
        }
        ownerToken = NO_OWNER;
        return true;
    }
}
