package io.github.mizar107.zapegruntime.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/** Minimal operator surface: inspect, arm with a one-time nonce, or stop without one. */
public final class HeraldorSafetyCommands {

    private HeraldorSafetyCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("safety")
                .then(Commands.literal("status")
                        .executes(HeraldorSafetyCommands::status))
                .then(Commands.literal("stop")
                        .executes(HeraldorSafetyCommands::stop))
                .then(Commands.literal("cleanup")
                        .executes(HeraldorSafetyCommands::cleanup))
                .then(Commands.literal("arm")
                        .then(armMode("manual", HeraldorSafetyMode.MANUAL))
                        .then(armMode("live", HeraldorSafetyMode.LIVE))
                        .then(armMode("auto", HeraldorSafetyMode.AUTO))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> armMode(
            String literal, HeraldorSafetyMode mode) {
        return Commands.literal(literal)
                .then(Commands.argument("nonce", UuidArgument.uuid())
                        .executes(context -> arm(context, mode)));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal(HeraldorSafetyController.statusLine(
                        context.getSource().getServer())),
                false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        HeraldorSafetyController.StopOutcome result =
                HeraldorSafetyController.emergencyStop(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(result.machineLine()), false);
        return 1;
    }

    private static int cleanup(CommandContext<CommandSourceStack> context) {
        HeraldorSafetyController.CleanupOutcome result =
                HeraldorSafetyController.cleanup(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(result.machineLine()), false);
        return 1;
    }

    private static int arm(
            CommandContext<CommandSourceStack> context, HeraldorSafetyMode requested) {
        HeraldorSafetyController.ActionOutcome result = HeraldorSafetyController.arm(
                context.getSource().getServer(),
                requested,
                UuidArgument.getUuid(context, "nonce"));
        Component message = Component.literal(result.machineLine());
        if (!result.success()) {
            context.getSource().sendFailure(message);
            return 0;
        }
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }
}
