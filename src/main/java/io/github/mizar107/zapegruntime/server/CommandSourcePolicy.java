package io.github.mizar107.zapegruntime.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.rcon.RconConsoleSource;

/** Trust boundary shared by every operator-facing ZapeG command tree. */
public final class CommandSourcePolicy {

    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    private CommandSourcePolicy() {}

    /**
     * Allows a command typed directly by an OP player or delivered through
     * authenticated RCON. Command blocks, functions, local console and
     * {@code execute as <op>} redirects are deliberately rejected.
     */
    public static boolean operatorOrDirector(CommandSourceStack source) {
        boolean effectivePlayer = source.getEntity() instanceof ServerPlayer;
        return allowsTrustedShape(
                source.hasPermission(REQUIRED_PERMISSION_LEVEL),
                effectivePlayer,
                effectivePlayer && source.source == source.getEntity(),
                source.getEntity() == null,
                source.source instanceof RconConsoleSource);
    }

    /** Pure policy seam used to exhaustively test source-shape decisions. */
    static boolean allowsTrustedShape(
            boolean hasPermission,
            boolean effectivePlayer,
            boolean directPlayerSource,
            boolean entityAbsent,
            boolean rconSource) {
        if (!hasPermission) {
            return false;
        }
        if (effectivePlayer) {
            return directPlayerSource;
        }
        return entityAbsent && rconSource;
    }
}
