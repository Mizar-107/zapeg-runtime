package io.github.mizar107.zapegruntime.journal;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Closed journal interactions. The wire carries only the explicit one-byte id. */
public enum JournalAction {
    REVEAL_PALIMPSEST(
            0,
            4,
            "ink_beneath_ink",
            ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "palimpsest_01")),
    COUNT_ABSENCES(
            1,
            18,
            "ledger_of_absence",
            ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "absence_ledger"));

    private final int wireId;
    private final int entryOrdinal;
    private final String expectedNodeId;
    private final ResourceLocation subject;

    JournalAction(int wireId, int entryOrdinal, String expectedNodeId, ResourceLocation subject) {
        this.wireId = wireId;
        this.entryOrdinal = entryOrdinal;
        this.expectedNodeId = expectedNodeId;
        this.subject = subject;
    }

    public int wireId() {
        return wireId;
    }

    public int entryOrdinal() {
        return entryOrdinal;
    }

    public String expectedNodeId() {
        return expectedNodeId;
    }

    public ResourceLocation subject() {
        return subject;
    }

    public static JournalAction fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown journal action wire id: " + wireId));
    }

    public static Optional<JournalAction> forOrdinal(int ordinal) {
        return Arrays.stream(values())
                .filter(value -> value.entryOrdinal == ordinal)
                .findFirst();
    }
}
