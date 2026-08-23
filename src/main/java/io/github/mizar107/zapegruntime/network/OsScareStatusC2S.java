package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsEffectState;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.server.SceneServerManager;
import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Fixed-size, client-to-server visitation effect report. */
public record OsScareStatusC2S(
        UUID eventId,
        UUID targetId,
        int sequence,
        OsScareReport report) {

    public static final int MAX_SEQUENCE = 64;

    public OsScareStatusC2S {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(report, "report");
        if (sequence < 0 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("OS status sequence out of bounds: " + sequence);
        }
    }

    public static void encode(OsScareStatusC2S message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.eventId);
        buffer.writeUUID(message.targetId);
        buffer.writeVarInt(message.sequence);
        for (OsEffect effect : OsEffect.values()) {
            OsEffectOutcome outcome = message.report.outcome(effect);
            buffer.writeByte(outcome.state().wireId());
            buffer.writeByte(outcome.reason().wireId());
        }
    }

    public static OsScareStatusC2S decode(FriendlyByteBuf buffer) {
        UUID eventId = buffer.readUUID();
        UUID targetId = buffer.readUUID();
        int sequence = buffer.readVarInt();
        EnumMap<OsEffect, OsEffectOutcome> outcomes = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, new OsEffectOutcome(
                    effect,
                    OsEffectState.fromWireId(buffer.readUnsignedByte()),
                    OsEffectReason.fromWireId(buffer.readUnsignedByte())));
        }
        return new OsScareStatusC2S(
                eventId, targetId, sequence, OsScareReport.from(outcomes));
    }

    public static void handle(
            OsScareStatusC2S message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            SceneServerManager.handleOsScareStatus(sender, message);
        }
    }
}
