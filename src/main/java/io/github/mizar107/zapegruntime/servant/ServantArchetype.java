package io.github.mizar107.zapegruntime.servant;

import java.util.Locale;
import java.util.Optional;

/**
 * The complete, closed set of native Servant combat archetypes.
 *
 * <p>Every value is server-owned combat data. Clients receive the selected
 * archetype through normal entity data synchronization, but never decide an
 * attack, target, range check, effect, or outcome.</p>
 */
public enum ServantArchetype {
    STALKER(
            "stalker", 44.0D, 6.0D, 8.0D, 0.34D, 0.30D,
            5.25D, 5.0F, 14, 72, 12, 4.75D),
    HERALD(
            "herald", 54.0D, 10.0D, 5.0D, 0.26D, 0.45D,
            12.0D, 4.0F, 28, 108, 18, 5.50D),
    BINDER(
            "binder", 64.0D, 12.0D, 4.0D, 0.23D, 0.65D,
            9.0D, 3.0F, 24, 96, 16, 5.00D);

    private final String id;
    private final double maxHealth;
    private final double armor;
    private final double attackDamage;
    private final double movementSpeed;
    private final double knockbackResistance;
    private final double specialRange;
    private final float specialDamage;
    private final int telegraphTicks;
    private final int cooldownTicks;
    private final int cooldownJitterTicks;
    private final double spawnDistance;

    ServantArchetype(
            String id,
            double maxHealth,
            double armor,
            double attackDamage,
            double movementSpeed,
            double knockbackResistance,
            double specialRange,
            float specialDamage,
            int telegraphTicks,
            int cooldownTicks,
            int cooldownJitterTicks,
            double spawnDistance) {
        this.id = id;
        this.maxHealth = maxHealth;
        this.armor = armor;
        this.attackDamage = attackDamage;
        this.movementSpeed = movementSpeed;
        this.knockbackResistance = knockbackResistance;
        this.specialRange = specialRange;
        this.specialDamage = specialDamage;
        this.telegraphTicks = telegraphTicks;
        this.cooldownTicks = cooldownTicks;
        this.cooldownJitterTicks = cooldownJitterTicks;
        this.spawnDistance = spawnDistance;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "entity.zapeg_runtime.servant." + id;
    }

    public String telegraphTranslationKey() {
        return "message.zapeg_runtime.servant.telegraph." + id;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double armor() {
        return armor;
    }

    public double attackDamage() {
        return attackDamage;
    }

    public double movementSpeed() {
        return movementSpeed;
    }

    public double knockbackResistance() {
        return knockbackResistance;
    }

    public double specialRange() {
        return specialRange;
    }

    public float specialDamage() {
        return specialDamage;
    }

    public int telegraphTicks() {
        return telegraphTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public int cooldownJitterTicks() {
        return cooldownJitterTicks;
    }

    public double spawnDistance() {
        return spawnDistance;
    }

    public static Optional<ServantArchetype> fromId(String raw) {
        if (raw == null || raw.length() > 16) {
            return Optional.empty();
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (ServantArchetype archetype : values()) {
            if (archetype.id.equals(normalized)) {
                return Optional.of(archetype);
            }
        }
        return Optional.empty();
    }
}
