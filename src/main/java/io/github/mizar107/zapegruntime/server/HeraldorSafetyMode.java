package io.github.mizar107.zapegruntime.server;

import java.util.Locale;
import java.util.Optional;

/**
 * Persistent authority floor for every Heraldor mutation.
 *
 * <p>The declaration order is intentional: a mode may perform work at its own level and every
 * lower level. QUARANTINED is diagnostics and cleanup only, MANUAL admits rehearsals, LIVE admits
 * explicit live operator actions, and AUTO admits story and autonomous producers.</p>
 */
public enum HeraldorSafetyMode {
    QUARANTINED,
    MANUAL,
    LIVE,
    AUTO;

    public boolean allows(HeraldorSafetyMode required) {
        return required != null && ordinal() >= required.ordinal();
    }

    public HeraldorSafetyMode clampTo(HeraldorSafetyMode ceiling) {
        if (ceiling == null) {
            return QUARANTINED;
        }
        return ordinal() <= ceiling.ordinal() ? this : ceiling;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<HeraldorSafetyMode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
