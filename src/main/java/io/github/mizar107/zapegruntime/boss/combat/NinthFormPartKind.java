package io.github.mizar107.zapegruntime.boss.combat;

import java.util.Locale;

/** Stable multipart vocabulary and target-relative ship geometry. */
public enum NinthFormPartKind {
    PROW_LANTERN(0b001, 0.0D, 8.5D, 3.8D, 2.2F, 2.2F),
    PORT_MOORING(0b010, -5.4D, 1.8D, 2.8D, 2.8F, 3.2F),
    STARBOARD_MOORING(0b100, 5.4D, 1.8D, 2.8D, 2.8F, 3.2F),
    // The heart projects beyond the prow-side parent pick box while remaining
    // above ordinary terrain, so aimed rays reach its phase-two route first.
    KEEL_HEART(0, 0.0D, 5.8D, 1.2D, 3.8F, 2.6F),
    ARMORED_HULL_AFT(0, 0.0D, -5.2D, 2.7D, 9.5F, 5.8F);

    private final int weakPointBit;
    private final double lateralOffset;
    private final double forwardOffset;
    private final double verticalOffset;
    private final float width;
    private final float height;

    NinthFormPartKind(
            int weakPointBit,
            double lateralOffset,
            double forwardOffset,
            double verticalOffset,
            float width,
            float height) {
        this.weakPointBit = weakPointBit;
        this.lateralOffset = lateralOffset;
        this.forwardOffset = forwardOffset;
        this.verticalOffset = verticalOffset;
        this.width = width;
        this.height = height;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int weakPointBit() {
        return weakPointBit;
    }

    public boolean weakPoint() {
        return weakPointBit != 0;
    }

    public double lateralOffset() {
        return lateralOffset;
    }

    public double forwardOffset() {
        return forwardOffset;
    }

    public double verticalOffset() {
        return verticalOffset;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }
}
