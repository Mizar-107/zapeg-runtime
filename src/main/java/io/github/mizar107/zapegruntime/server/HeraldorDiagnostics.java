package io.github.mizar107.zapegruntime.server;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

/** Pure formatter for operator diagnostics and deterministic tests. */
public final class HeraldorDiagnostics {

    private HeraldorDiagnostics() {}

    public static String format(PlayerDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        HeraldorWorldData.PlayerSnapshot state = diagnostic.state();
        StringJoiner milestones = new StringJoiner(",", "[", "]");
        state.milestones().stream().sorted().forEach(milestones::add);
        return "heraldor target=" + diagnostic.playerName()
                + " uuid=" + diagnostic.playerId()
                + " runtime=" + diagnostic.runtimeVersion()
                + " protocol=" + diagnostic.protocolVersion()
                + " dimension=" + diagnostic.dimension()
                + " scene={" + diagnostic.activeScene() + "}"
                + " state={schema=" + HeraldorWorldData.CURRENT_SCHEMA_VERSION
                + " victories=" + state.victories()
                + " milestones=" + milestones
                + " consumed_events=" + state.consumedEventCount()
                + "}";
    }

    public record PlayerDiagnostic(
            String playerName,
            UUID playerId,
            String runtimeVersion,
            String protocolVersion,
            String dimension,
            String activeScene,
            HeraldorWorldData.PlayerSnapshot state) {

        public PlayerDiagnostic {
            Objects.requireNonNull(playerName, "playerName");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(runtimeVersion, "runtimeVersion");
            Objects.requireNonNull(protocolVersion, "protocolVersion");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(activeScene, "activeScene");
            Objects.requireNonNull(state, "state");
        }
    }
}
