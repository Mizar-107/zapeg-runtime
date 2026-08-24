package io.github.mizar107.zapegruntime.boss.encounter;

import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Idempotent toast-only narrative reward; no item, XP, or loot table is involved. */
public final class NinthFormRewardService {

    public static final ResourceLocation ADVANCEMENT_ID = Objects.requireNonNull(
            ResourceLocation.tryBuild("zapeg_runtime", "heraldor/banish_ninth_form"));
    public static final String CRITERION = "banished";

    private NinthFormRewardService() {}

    public static AwardResult award(ServerPlayer player, NinthFormBarrier defeatBarrier) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(defeatBarrier, "defeatBarrier");
        if (defeatBarrier.kind() != NinthFormBarrier.Kind.DEFEATED
                || !defeatBarrier.targetId().equals(player.getUUID())) {
            return AwardResult.IDENTITY_MISMATCH;
        }
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(ADVANCEMENT_ID);
        if (advancement == null) {
            return AwardResult.DEFINITION_NOT_LOADED;
        }
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return AwardResult.ALREADY_AWARDED;
        }
        return player.getAdvancements().award(advancement, CRITERION)
                ? AwardResult.AWARDED
                : AwardResult.REFUSED;
    }

    public enum AwardResult {
        AWARDED,
        ALREADY_AWARDED,
        IDENTITY_MISMATCH,
        DEFINITION_NOT_LOADED,
        REFUSED
    }
}
