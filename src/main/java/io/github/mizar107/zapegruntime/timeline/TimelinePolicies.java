package io.github.mizar107.zapegruntime.timeline;

import java.util.Locale;
import java.util.Objects;

/** Explicit lifecycle choices carried by every authored timeline. */
public record TimelinePolicies(
        Disconnect disconnect,
        Restart restart,
        DimensionChange dimensionChange,
        Death death) {

    public TimelinePolicies {
        Objects.requireNonNull(disconnect, "disconnect");
        Objects.requireNonNull(restart, "restart");
        Objects.requireNonNull(dimensionChange, "dimensionChange");
        Objects.requireNonNull(death, "death");
    }

    public enum Disconnect implements ParsedPolicy {
        PAUSE,
        FAIL
    }

    public enum Restart implements ParsedPolicy {
        PAUSE,
        FAIL
    }

    public enum DimensionChange implements ParsedPolicy {
        PAUSE,
        FAIL
    }

    public enum Death implements ParsedPolicy {
        FAIL,
        CANCEL
    }

    interface ParsedPolicy {
        static <T extends Enum<T>> T parse(Class<T> type, String raw, String field) {
            Objects.requireNonNull(raw, field);
            try {
                return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(
                        "unknown " + field + " policy: " + raw, invalid);
            }
        }
    }
}
