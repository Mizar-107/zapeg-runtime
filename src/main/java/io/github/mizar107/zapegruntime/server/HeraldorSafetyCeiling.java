package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.Map;

/** Deployment-owned hard ceiling. Missing or malformed configuration never enables live work. */
public final class HeraldorSafetyCeiling {

    public static final String ENVIRONMENT_VARIABLE = "HERALDOR_MAX_MODE";
    public static final String LEGACY_ENVIRONMENT_VARIABLE = "HERALDOR_SAFETY_CEILING";
    public static final HeraldorSafetyMode DEFAULT = HeraldorSafetyMode.MANUAL;

    private HeraldorSafetyCeiling() {}

    public static HeraldorSafetyMode current() {
        return fromEnvironment(System.getenv());
    }

    static HeraldorSafetyMode fromEnvironment(Map<String, String> environment) {
        if (environment == null) {
            return DEFAULT;
        }
        String configured = environment.get(ENVIRONMENT_VARIABLE);
        if (configured == null || configured.isBlank()) {
            configured = environment.get(LEGACY_ENVIRONMENT_VARIABLE);
        }
        return parse(configured);
    }

    public static HeraldorSafetyMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        return HeraldorSafetyMode.parse(raw).orElseGet(() -> {
            ZapeGRuntime.LOGGER.error(
                    "Invalid {}={}; safety ceiling is {}",
                    ENVIRONMENT_VARIABLE,
                    raw,
                    HeraldorSafetyMode.QUARANTINED.serializedName());
            return HeraldorSafetyMode.QUARANTINED;
        });
    }
}
