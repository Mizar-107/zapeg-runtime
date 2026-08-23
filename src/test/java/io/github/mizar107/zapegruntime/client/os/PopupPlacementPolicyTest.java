package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PopupPlacementPolicyTest {

    @Test
    void monitorContainingMostOfMinecraftWinsInsteadOfDefaultDevice() {
        List<PopupPlacementPolicy.Monitor> monitors = List.of(
                monitor(0, rect(0, 0, 1920, 1080), rect(0, 0, 1920, 1040), 1.0D, 1.0D),
                monitor(1, rect(1920, 0, 2560, 1440), rect(1920, 0, 2560, 1400), 1.5D, 1.5D));

        PopupPlacementPolicy.Placement placement = PopupPlacementPolicy.place(
                rect(1800, 120, 1400, 900), monitors, 600, 450).orElseThrow();

        assertEquals(1, placement.monitorIndex());
        assertTrue(monitors.get(1).usableBounds().contains(placement.popupBounds()));
        assertEquals(400, placement.popupBounds().width(),
                "image pixels are converted through the selected monitor DPI scale");
        assertEquals(300, placement.popupBounds().height());
    }

    @Test
    void nearestMonitorWinsWhenWindowDoesNotIntersectAnyMonitor() {
        List<PopupPlacementPolicy.Monitor> monitors = List.of(
                monitor(0, rect(-1920, 0, 1920, 1080), rect(-1920, 0, 1920, 1040),
                        1.0D, 1.0D),
                monitor(1, rect(0, 0, 1920, 1080), rect(0, 0, 1920, 1040), 1.0D, 1.0D));

        PopupPlacementPolicy.Placement placement = PopupPlacementPolicy.place(
                rect(-2500, 200, 300, 300), monitors, 280, 360).orElseThrow();

        assertEquals(0, placement.monitorIndex());
        assertEquals(-1100, placement.popupBounds().x());
        assertEquals(340, placement.popupBounds().y());
    }

    @Test
    void overlapBeatsDistanceAndStableIndexBreaksExactTies() {
        PopupPlacementPolicy.Monitor first = monitor(
                0, rect(0, 0, 1000, 1000), rect(0, 0, 1000, 1000), 1.0D, 1.0D);
        PopupPlacementPolicy.Monitor second = monitor(
                1, rect(1000, 0, 1000, 1000), rect(1000, 0, 1000, 1000), 1.0D, 1.0D);

        assertEquals(0, PopupPlacementPolicy.place(
                rect(900, 100, 200, 400), List.of(first, second), 100, 100)
                .orElseThrow()
                .monitorIndex());

        assertEquals(1, PopupPlacementPolicy.place(
                rect(950, 100, 500, 400), List.of(first, second), 100, 100)
                .orElseThrow()
                .monitorIndex());
    }

    @Test
    void oversizedImageIsAspectFittedAndCenteredInsideUsableBounds() {
        PopupPlacementPolicy.Monitor monitor = monitor(
                0,
                rect(100, 200, 800, 600),
                rect(120, 230, 760, 520),
                1.0D,
                1.0D);

        PopupPlacementPolicy.Placement placement = PopupPlacementPolicy.place(
                rect(200, 300, 400, 300), List.of(monitor), 2000, 1000)
                .orElseThrow();

        assertTrue(placement.scaledToFit());
        assertEquals(rect(120, 300, 760, 380), placement.popupBounds());
        assertTrue(monitor.usableBounds().contains(placement.popupBounds()));
    }

    @Test
    void invalidDpiMetricsDegradeToOneWithoutEscapingSafeBounds() {
        PopupPlacementPolicy.Monitor monitor = new PopupPlacementPolicy.Monitor(
                0,
                rect(-1280, -720, 1280, 720),
                rect(-1280, -700, 1280, 680),
                Double.NaN,
                99.0D,
                true);

        PopupPlacementPolicy.Placement placement = PopupPlacementPolicy.place(
                rect(-1000, -600, 800, 500), List.of(monitor), 280, 360)
                .orElseThrow();

        assertTrue(placement.metricsDegraded());
        assertFalse(placement.scaledToFit());
        assertEquals(280, placement.popupBounds().width());
        assertEquals(360, placement.popupBounds().height());
        assertTrue(monitor.usableBounds().contains(placement.popupBounds()));
    }

    @Test
    void emptyOrInvalidInputsHaveNoPlacement() {
        PopupPlacementPolicy.Rect window = rect(0, 0, 800, 600);
        assertTrue(PopupPlacementPolicy.place(window, List.of(), 280, 360).isEmpty());
        assertTrue(PopupPlacementPolicy.place(
                window,
                List.of(monitor(0, window, window, 1.0D, 1.0D)),
                0,
                360).isEmpty());
    }

    private static PopupPlacementPolicy.Monitor monitor(
            int index,
            PopupPlacementPolicy.Rect bounds,
            PopupPlacementPolicy.Rect usable,
            double scaleX,
            double scaleY) {
        return new PopupPlacementPolicy.Monitor(
                index, bounds, usable, scaleX, scaleY, false);
    }

    private static PopupPlacementPolicy.Rect rect(
            int x, int y, int width, int height) {
        return new PopupPlacementPolicy.Rect(x, y, width, height);
    }
}
