package io.github.mizar107.zapegruntime.timeline;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Typed operator surface for idempotent timeline sessions. */
public final class TimelineCommands {

    private TimelineCommands() {}

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("timeline")
                .then(Commands.literal("start")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("session_id", UuidArgument.uuid())
                                        .then(Commands.argument(
                                                        "timeline_id",
                                                        ResourceLocationArgument.id())
                                                .executes(TimelineCommands::start)))))
                .then(Commands.literal("status")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(TimelineCommands::status)))
                .then(Commands.literal("result")
                        .then(Commands.argument("session_id", UuidArgument.uuid())
                                .executes(TimelineCommands::result)))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(TimelineCommands::cancel))));
    }

    private static int start(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        UUID sessionId = UuidArgument.getUuid(context, "session_id");
        ResourceLocation timelineId =
                ResourceLocationArgument.getId(context, "timeline_id");
        TimelineServerManager.StartResult result = TimelineServerManager.start(
                context.getSource().getServer(), target, sessionId, timelineId);
        Component message = Component.literal(
                result.message() + " session=" + result.sessionId());
        if (result.success()) {
            context.getSource().sendSuccess(() -> message, false);
        } else {
            context.getSource().sendFailure(message);
        }
        audit(context, "start", target, sessionId, timelineId);
        return result.success() ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        String status = TimelineServerManager.statusFor(
                context.getSource().getServer(), target.getUUID());
        context.getSource().sendSuccess(() -> Component.literal(status), false);
        audit(context, "status", target, null, null);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        boolean cancelled = TimelineServerManager.cancel(
                context.getSource().getServer(), target.getUUID());
        Component message = Component.literal(
                cancelled ? "timeline cancelled" : "active=0");
        context.getSource().sendSuccess(() -> message, false);
        audit(context, "cancel", target, null, null);
        return cancelled ? 1 : 0;
    }

    private static int result(CommandContext<CommandSourceStack> context) {
        UUID sessionId = UuidArgument.getUuid(context, "session_id");
        String result = TimelineServerManager.resultFor(
                context.getSource().getServer(), sessionId);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        ZapeGRuntime.LOGGER.info(
                "Timeline operator action=result source={} session={}",
                context.getSource().getTextName(),
                sessionId);
        return 1;
    }

    private static void audit(
            CommandContext<CommandSourceStack> context,
            String action,
            ServerPlayer target,
            UUID sessionId,
            ResourceLocation timelineId) {
        ZapeGRuntime.LOGGER.info(
                "Timeline operator action={} source={} target={} session={} timeline={}",
                action,
                context.getSource().getTextName(),
                target.getUUID(),
                sessionId,
                timelineId);
    }
}
