package io.github.mizar107.zapegruntime.server;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

/** Pure formatter for operator diagnostics and deterministic tests. */
public final class HeraldorDiagnostics {

    private HeraldorDiagnostics() {}

    public static String format(PlayerDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        HeraldorWorldData.SchemaStatus schema = diagnostic.schemaStatus();
        Optional<HeraldorWorldData.PlayerSnapshot> state = diagnostic.state();
        String prefix = "heraldor target=" + diagnostic.playerName()
                + " uuid=" + diagnostic.playerId()
                + " runtime=" + diagnostic.runtimeVersion()
                + " protocol=" + diagnostic.protocolVersion()
                + " dimension=" + diagnostic.dimension()
                + " scene={" + diagnostic.activeScene() + "}"
                + " state={loaded_schema=" + schema.loadedVersion()
                + " current_schema=" + schema.currentVersion()
                + " migrated=" + schema.migratedFromLegacy()
                + " writable=" + schema.writable();
        if (state.isEmpty()) {
            return prefix + " progress=unavailable}";
        }
        HeraldorWorldData.PlayerSnapshot progress = state.orElseThrow();
        StringJoiner milestones = new StringJoiner(",", "[", "]");
        progress.milestones().stream().sorted().forEach(milestones::add);
        return prefix
                + " victories=" + progress.victories()
                + " milestones=" + milestones
                + " consumed_events=" + progress.consumedEventCount()
                + "}";
    }

    public record PlayerDiagnostic(
            String playerName,
            UUID playerId,
            String runtimeVersion,
            String protocolVersion,
            String dimension,
            String activeScene,
            HeraldorWorldData.SchemaStatus schemaStatus,
            Optional<HeraldorWorldData.PlayerSnapshot> state) {

        public PlayerDiagnostic {
            Objects.requireNonNull(playerName, "playerName");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(runtimeVersion, "runtimeVersion");
            Objects.requireNonNull(protocolVersion, "protocolVersion");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(activeScene, "activeScene");
            Objects.requireNonNull(schemaStatus, "schemaStatus");
            Objects.requireNonNull(state, "state");
        }
    }
}
