package io.github.mizar107.zapegruntime.journal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** UUID-only journal recovery attached beneath the already trusted Heraldor root. */
public final class JournalCommands {

    private JournalCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("journal")
                .then(Commands.literal("restore")
                        .then(Commands.argument("target_uuid", UuidArgument.uuid())
                                .executes(JournalCommands::restore))));
    }

    private static int restore(CommandContext<CommandSourceStack> context) {
        UUID playerId = UuidArgument.getUuid(context, "target_uuid");
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayer(playerId);
        if (target == null) {
            context.getSource().sendFailure(Component.literal(
                    "journal restore refused: target_uuid is not online"));
            return 0;
        }
        JournalService.GrantResult result = JournalService.restore(target);
        boolean success = result == JournalService.GrantResult.ISSUED
                || result == JournalService.GrantResult.RESTORED
                || result == JournalService.GrantResult.PRESENT;
        Component reply = Component.literal(
                "journal restore=" + result + " target_uuid=" + playerId);
        if (success) {
            context.getSource().sendSuccess(() -> reply, false);
        } else {
            context.getSource().sendFailure(reply);
        }
        return success ? 1 : 0;
    }
}
