package io.github.mizar107.zapegruntime.boss.api;

/** Closed, monotonic combat phase set for the Ninth Form. */
public enum NinthFormPhase {
    PRELUDE(0, false),
    FIRST(1, false),
    INTERLUDE(2, false),
    FINAL(3, false),
    BANISHED(4, true);

    private final int sequence;
    private final boolean terminal;

    NinthFormPhase(int sequence, boolean terminal) {
        this.sequence = sequence;
        this.terminal = terminal;
    }

    public int sequence() {
        return sequence;
    }

    public boolean terminal() {
        return terminal;
    }

    /** Only the designed forward edge is legal; repeats are handled as replay. */
    public boolean canAdvanceTo(NinthFormPhase next) {
        return next != null && !terminal && next.sequence == sequence + 1;
    }
}
