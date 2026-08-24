package io.github.mizar107.zapegruntime.director;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/** Read-only UUID command surface for Director state and proof diagnostics. */
public final class DirectorCommands {

    private DirectorCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("director")
                .then(Commands.literal("status")
                        .then(Commands.argument("target_uuid", UuidArgument.uuid())
                                .executes(context -> report(context, false))))
                .then(Commands.literal("diagnose")
                        .then(Commands.argument("target_uuid", UuidArgument.uuid())
                                .executes(context -> report(context, true)))));
    }

    private static int report(CommandContext<CommandSourceStack> context, boolean detailed) {
        UUID targetId = UuidArgument.getUuid(context, "target_uuid");
        String result = detailed
                ? HeraldorDirector.diagnoseFor(context.getSource().getServer(), targetId)
                : HeraldorDirector.statusFor(context.getSource().getServer(), targetId);
        context.getSource().sendSuccess(() -> Component.literal("director " + result), false);
        return 1;
    }
}
