package io.github.mizar107.zapegruntime.boss.encounter;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Standalone typed subtree. Integration explicitly decides whether to attach it. */
public final class NinthFormCommands {

    private NinthFormCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("ninth_form")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rehearse")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(NinthFormCommands::rehearse)))
                .then(Commands.literal("reconcile")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(NinthFormCommands::reconcile)))
                .then(Commands.literal("status")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(NinthFormCommands::status))));
    }

    private static int rehearse(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        NinthFormEncounterManager.StartResult result = NinthFormEncounterManager.rehearse(target);
        Component message = Component.literal(result.detail()
                + (result.encounterId() == null ? "" : " encounter=" + result.encounterId())
                + (result.entityId() == null ? "" : " entity=" + result.entityId()));
        if (result.success()) {
            context.getSource().sendSuccess(() -> message, false);
        } else {
            context.getSource().sendFailure(message);
        }
        audit(context, "rehearse", target, result.status().name());
        return result.success() ? 1 : 0;
    }

    private static int reconcile(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        NinthFormEncounterManager.queueStoryAdvance(
                context.getSource().getServer(), target.getUUID());
        context.getSource().sendSuccess(
                () -> Component.literal("Ninth Form reconciliation queued for UUID "
                        + target.getUUID()),
                false);
        audit(context, "reconcile", target, "QUEUED");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        Optional<NinthFormEncounter> active = NinthFormEncounterManager.activeFor(
                context.getSource().getServer(), target.getUUID());
        int barriers = NinthFormProgressionSync.barrierCountForTarget(
                context.getSource().getServer(), target.getUUID());
        String detail = active
                .map(encounter -> "active=1 encounter=" + encounter.encounterId()
                        + " entity=" + encounter.entityId()
                        + " generation=" + encounter.generation()
                        + " phase=" + encounter.phase()
                        + " lifecycle=" + encounter.lifecycle()
                        + " rehearsal=" + encounter.rehearsal()
                        + " participants=" + encounter.participantCount()
                        + " barriers=" + barriers)
                .orElse("active=0 barriers=" + barriers);
        context.getSource().sendSuccess(() -> Component.literal(detail), false);
        audit(context, "status", target, "OK");
        return 1;
    }

    private static void audit(
            CommandContext<CommandSourceStack> context,
            String action,
            ServerPlayer target,
            String result) {
        ZapeGRuntime.LOGGER.info(
                "Ninth Form operator action={} source={} target={} target_uuid={} result={}",
                action,
                context.getSource().getTextName(),
                target.getGameProfile().getName(),
                target.getUUID(),
                result);
    }
}
