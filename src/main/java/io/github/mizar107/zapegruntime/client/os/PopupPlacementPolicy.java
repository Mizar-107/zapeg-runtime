package io.github.mizar107.zapegruntime.client.os;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure monitor selection and popup fitting policy.
 *
 * <p>All coordinates are AWT user-space coordinates. A monitor containing the
 * largest part of the Minecraft window wins; if the window is between or
 * outside monitors, the geometrically nearest monitor wins. Bounds and then
 * the snapshot index break exact ties deterministically.
 */
final class PopupPlacementPolicy {

    private static final double MIN_TRUSTED_SCALE = 0.5D;
    private static final double MAX_TRUSTED_SCALE = 8.0D;

    record Rect(int x, int y, int width, int height) {
        Rect {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("rectangle must have positive area");
            }
        }

        long right() {
            return (long) x + width;
        }

        long bottom() {
            return (long) y + height;
        }

        long intersectionArea(Rect other) {
            long overlapWidth = Math.max(
                    0L, Math.min(right(), other.right()) - Math.max((long) x, other.x));
            long overlapHeight = Math.max(
                    0L, Math.min(bottom(), other.bottom()) - Math.max((long) y, other.y));
            return saturatedMultiply(overlapWidth, overlapHeight);
        }

        double distanceSquared(Rect other) {
            long dx = axisDistance(x, right(), other.x, other.right());
            long dy = axisDistance(y, bottom(), other.y, other.bottom());
            return (double) dx * dx + (double) dy * dy;
        }

        boolean contains(Rect other) {
            return other.x >= x
                    && other.y >= y
                    && other.right() <= right()
                    && other.bottom() <= bottom();
        }

        private static long axisDistance(
                long firstStart, long firstEnd, long secondStart, long secondEnd) {
            if (firstEnd < secondStart) {
                return secondStart - firstEnd;
            }
            if (secondEnd < firstStart) {
                return firstStart - secondEnd;
            }
            return 0L;
        }

        private static long saturatedMultiply(long left, long right) {
            if (left == 0L || right == 0L) {
                return 0L;
            }
            if (left > Long.MAX_VALUE / right) {
                return Long.MAX_VALUE;
            }
            return left * right;
        }
    }

    record Monitor(
            int index,
            Rect bounds,
            Rect usableBounds,
            double scaleX,
            double scaleY,
            boolean metricsDegraded) {
        Monitor {
            if (index < 0) {
                throw new IllegalArgumentException("monitor index must be non-negative");
            }
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(usableBounds, "usableBounds");
            if (!bounds.contains(usableBounds)) {
                throw new IllegalArgumentException("usable bounds must stay inside monitor");
            }
        }
    }

    record Placement(
            int monitorIndex,
            Rect popupBounds,
            boolean scaledToFit,
            boolean metricsDegraded) {}

    private record RankedMonitor(Monitor monitor, long overlap, double distanceSquared) {}

    private PopupPlacementPolicy() {}

    static Optional<Placement> place(
            Rect minecraftWindow,
            List<Monitor> monitors,
            int imagePixelWidth,
            int imagePixelHeight) {
        Objects.requireNonNull(minecraftWindow, "minecraftWindow");
        Objects.requireNonNull(monitors, "monitors");
        if (imagePixelWidth <= 0 || imagePixelHeight <= 0 || monitors.isEmpty()) {
            return Optional.empty();
        }

        Monitor monitor = monitors.stream()
                .map(candidate -> new RankedMonitor(
                        candidate,
                        candidate.bounds().intersectionArea(minecraftWindow),
                        candidate.bounds().distanceSquared(minecraftWindow)))
                .min(Comparator
                        .comparingLong(RankedMonitor::overlap).reversed()
                        .thenComparingDouble(RankedMonitor::distanceSquared)
                        .thenComparingInt(candidate -> candidate.monitor().bounds().x())
                        .thenComparingInt(candidate -> candidate.monitor().bounds().y())
                        .thenComparingInt(candidate -> candidate.monitor().bounds().width())
                        .thenComparingInt(candidate -> candidate.monitor().bounds().height())
                        .thenComparingInt(candidate -> candidate.monitor().index()))
                .map(RankedMonitor::monitor)
                .orElse(null);
        if (monitor == null) {
            return Optional.empty();
        }

        boolean validScaleX = trustedScale(monitor.scaleX());
        boolean validScaleY = trustedScale(monitor.scaleY());
        double scaleX = validScaleX ? monitor.scaleX() : 1.0D;
        double scaleY = validScaleY ? monitor.scaleY() : 1.0D;
        boolean degraded = monitor.metricsDegraded() || !validScaleX || !validScaleY;

        int desiredWidth = logicalSize(imagePixelWidth, scaleX);
        int desiredHeight = logicalSize(imagePixelHeight, scaleY);
        Rect usable = monitor.usableBounds();
        double fit = Math.min(
                1.0D,
                Math.min(
                        usable.width() / (double) desiredWidth,
                        usable.height() / (double) desiredHeight));
        int width = Math.max(1, (int) Math.floor(desiredWidth * fit));
        int height = Math.max(1, (int) Math.floor(desiredHeight * fit));
        int x = centeredCoordinate(usable.x(), usable.width(), width);
        int y = centeredCoordinate(usable.y(), usable.height(), height);

        return Optional.of(new Placement(
                monitor.index(),
                new Rect(x, y, width, height),
                fit < 1.0D,
                degraded));
    }

    private static boolean trustedScale(double scale) {
        return Double.isFinite(scale)
                && scale >= MIN_TRUSTED_SCALE
                && scale <= MAX_TRUSTED_SCALE;
    }

    private static int logicalSize(int imagePixels, double scale) {
        double logical = Math.ceil(imagePixels / scale);
        return (int) Math.max(1.0D, Math.min(Integer.MAX_VALUE, logical));
    }

    private static int centeredCoordinate(int origin, int span, int contentSpan) {
        long centered = (long) origin + ((long) span - contentSpan) / 2L;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, centered));
    }
}
