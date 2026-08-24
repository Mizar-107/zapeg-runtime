package io.github.mizar107.zapegruntime.boss.presentation;

import java.util.List;
import java.util.Objects;

/** Exact, non-overlapping cube unwraps shared by the model and asset contract. */
public final class NinthFormUvLayout {

    public static final int WIDTH = 512;
    public static final int HEIGHT = 512;

    public static final UvBox PARENT_HULL = new UvBox("parent_hull", 0, 0, 112, 52, 128);
    public static final UvBox ARMORED_HULL_AFT =
            new UvBox("armored_hull_aft", 0, 180, 144, 72, 80);
    public static final UvBox PROW_LANTERN =
            new UvBox("prow_lantern", 0, 332, 24, 28, 24);
    public static final UvBox PORT_MOORING =
            new UvBox("port_mooring", 96, 332, 32, 32, 32);
    public static final UvBox STARBOARD_MOORING =
            new UvBox("starboard_mooring", 224, 332, 32, 32, 32);
    public static final UvBox KEEL_HEART =
            new UvBox("keel_heart", 352, 332, 40, 40, 32);
    public static final UvBox CROWN = new UvBox("crown", 0, 400, 32, 80, 32);
    public static final UvBox MAST_RIB = new UvBox("mast_rib", 128, 400, 16, 64, 16);
    public static final UvBox PORT_FIN = new UvBox("port_fin", 192, 404, 8, 44, 64);
    public static final UvBox STARBOARD_FIN =
            new UvBox("starboard_fin", 336, 404, 8, 44, 64);

    public static final List<UvBox> BOXES = List.of(
            PARENT_HULL,
            ARMORED_HULL_AFT,
            PROW_LANTERN,
            PORT_MOORING,
            STARBOARD_MOORING,
            KEEL_HEART,
            CROWN,
            MAST_RIB,
            PORT_FIN,
            STARBOARD_FIN);

    static {
        for (int index = 0; index < BOXES.size(); index++) {
            UvBox box = BOXES.get(index);
            if (!box.insideAtlas()) {
                throw new IllegalStateException(box.name() + " exceeds the Ninth Form atlas");
            }
            for (int other = index + 1; other < BOXES.size(); other++) {
                if (box.overlaps(BOXES.get(other))) {
                    throw new IllegalStateException(
                            box.name() + " overlaps " + BOXES.get(other).name());
                }
            }
        }
    }

    private NinthFormUvLayout() {}

    /** Minecraft's standard cuboid unwrap is 2(x+z) wide and y+z high. */
    public record UvBox(String name, int u, int v, int sizeX, int sizeY, int sizeZ) {

        public UvBox {
            Objects.requireNonNull(name, "name");
            if (!name.matches("[a-z0-9_]+")
                    || u < 0
                    || v < 0
                    || sizeX < 1
                    || sizeY < 1
                    || sizeZ < 1) {
                throw new IllegalArgumentException("invalid Ninth Form UV box");
            }
        }

        public int pixelWidth() {
            return 2 * (sizeX + sizeZ);
        }

        public int pixelHeight() {
            return sizeY + sizeZ;
        }

        public int right() {
            return u + pixelWidth();
        }

        public int bottom() {
            return v + pixelHeight();
        }

        public boolean insideAtlas() {
            return right() <= WIDTH && bottom() <= HEIGHT;
        }

        public boolean overlaps(UvBox other) {
            Objects.requireNonNull(other, "other");
            return u < other.right()
                    && right() > other.u
                    && v < other.bottom()
                    && bottom() > other.v;
        }
    }
}
