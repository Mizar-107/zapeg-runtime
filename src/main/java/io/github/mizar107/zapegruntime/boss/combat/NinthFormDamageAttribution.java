package io.github.mizar107.zapegruntime.boss.combat;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/** Resolves direct player hits and projectile owners without trusting display names. */
public final class NinthFormDamageAttribution {

    private NinthFormDamageAttribution() {}

    public static Optional<ServerPlayer> creditedPlayer(DamageSource source) {
        if (source == null) {
            return Optional.empty();
        }
        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer owner) {
            return Optional.of(owner);
        }
        return Optional.empty();
    }
}
