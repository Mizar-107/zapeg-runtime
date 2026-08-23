package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HeraldorDiagnosticsTest {

    @Test
    void formatsStableOperatorDiagnosticWithSortedMilestones() {
        UUID playerId = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        HeraldorWorldData.PlayerSnapshot state = new HeraldorWorldData.PlayerSnapshot(
                2,
                new LinkedHashSet<>(java.util.List.of("chapter.02", "chapter.01")),
                7);

        String actual = HeraldorDiagnostics.format(
                new HeraldorDiagnostics.PlayerDiagnostic(
                        "Mizar__107",
                        playerId,
                        "0.4.0",
                        "7",
                        "minecraft:overworld",
                        "active=0",
                        new HeraldorWorldData.SchemaStatus(1, 1, false, true),
                        Optional.of(state)));

        assertEquals(
                "heraldor target=Mizar__107"
                        + " uuid=12345678-1234-5678-9abc-def012345678"
                        + " runtime=0.4.0 protocol=7"
                        + " dimension=minecraft:overworld"
                        + " scene={active=0}"
                        + " state={loaded_schema=1 current_schema=1"
                        + " migrated=false writable=true"
                        + " victories=2 milestones=[chapter.01,chapter.02]"
                        + " consumed_events=7}",
                actual);
    }

    @Test
    void reportsUnsupportedFutureSchemaWithoutInventingProgress() {
        String actual = HeraldorDiagnostics.format(
                new HeraldorDiagnostics.PlayerDiagnostic(
                        "Mizar__107",
                        UUID.fromString("12345678-1234-5678-9abc-def012345678"),
                        "0.4.0",
                        "7",
                        "minecraft:overworld",
                        "active=0",
                        new HeraldorWorldData.SchemaStatus(4, 1, false, false),
                        Optional.empty()));

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.endsWith(
                        "state={loaded_schema=4 current_schema=1"
                                + " migrated=false writable=false progress=unavailable}"));
    }
}
