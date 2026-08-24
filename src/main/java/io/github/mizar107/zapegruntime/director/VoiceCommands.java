package io.github.mizar107.zapegruntime.director;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Native compatibility surface for the retired external Voice rehearsal. */
public final class VoiceCommands {

    private VoiceCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("voice")
                .then(Commands.literal("rehearse")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> rehearse(
                                        context, VoiceRehearsalPlan.VOICE_01))
                                .then(Commands.literal("voice_01")
                                        .executes(context -> rehearse(
                                                context, VoiceRehearsalPlan.VOICE_01)))
                                .then(Commands.literal("voice_02")
                                        .executes(context -> rehearse(
                                                context, VoiceRehearsalPlan.VOICE_02)))))
                .then(Commands.literal("status")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(VoiceCommands::status))));
    }

    private static int rehearse(
            CommandContext<CommandSourceStack> context,
            net.minecraft.resources.ResourceLocation subject)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        VoiceRehearsalManager.StartResult result =
                VoiceRehearsalManager.rehearse(target, subject);
        if (result.success()) {
            context.getSource().sendSuccess(() -> Component.literal(result.message()), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        context.getSource().sendSuccess(
                () -> Component.literal(VoiceRehearsalManager.statusFor(target.getUUID())),
                false);
        return 1;
    }
}
