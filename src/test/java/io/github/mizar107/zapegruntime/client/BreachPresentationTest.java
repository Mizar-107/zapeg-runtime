package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BreachPresentationTest {

    @Test
    void exactlyOneSurfaceOwnsEveryScreenAndHudState() {
        for (boolean hasScreen : new boolean[] {false, true}) {
            for (boolean hideGui : new boolean[] {false, true}) {
                int routes = 0;
                for (BreachPresentation.Surface surface :
                        BreachPresentation.Surface.values()) {
                    if (BreachPresentation.routesTo(surface, hasScreen, hideGui)) {
                        routes++;
                    }
                }
                assertEquals(1, routes, "every render state needs exactly one breach surface");
            }
        }
        assertTrue(BreachPresentation.routesTo(
                BreachPresentation.Surface.HUD_POST, false, false));
        assertTrue(BreachPresentation.routesTo(
                BreachPresentation.Surface.SCREEN_POST, true, false));
        assertTrue(BreachPresentation.routesTo(
                BreachPresentation.Surface.SCREEN_POST, true, true));
        assertTrue(BreachPresentation.routesTo(
                BreachPresentation.Surface.HIDDEN_HUD_AFTER_LEVEL, false, true));
    }

    @Test
    void anEventCanRenderAndReportOnlyOncePerFrame() {
        BreachPresentation presentation = new BreachPresentation();
        UUID eventId = UUID.fromString("3adbfcd1-bf45-4f72-ac36-19df1c28780a");
        AtomicInteger renders = new AtomicInteger();
        AtomicInteger proofs = new AtomicInteger();
        presentation.beginFrame();

        assertTrue(presentation.present(
                BreachPresentation.Surface.HUD_POST,
                false,
                false,
                eventId,
                () -> {
                    renders.incrementAndGet();
                    return true;
                },
                () -> {},
                proofs::incrementAndGet));
        assertFalse(presentation.present(
                BreachPresentation.Surface.HUD_POST,
                false,
                false,
                eventId,
                () -> {
                    renders.incrementAndGet();
                    return true;
                },
                () -> {},
                proofs::incrementAndGet));
        assertEquals(1, renders.get());
        assertEquals(1, proofs.get());

        presentation.beginFrame();
        assertTrue(presentation.present(
                BreachPresentation.Surface.HUD_POST,
                false,
                false,
                eventId,
                () -> {
                    renders.incrementAndGet();
                    return true;
                },
                () -> {},
                proofs::incrementAndGet));
        assertEquals(2, renders.get());
        assertEquals(2, proofs.get());
    }

    @Test
    void rejectedRoutesAndTransparentFramesNeverReportProof() {
        BreachPresentation presentation = new BreachPresentation();
        UUID eventId = UUID.fromString("32ccc28a-c429-43c7-911d-cce3b3754084");
        AtomicInteger renders = new AtomicInteger();
        AtomicInteger proofs = new AtomicInteger();
        presentation.beginFrame();

        assertFalse(presentation.present(
                BreachPresentation.Surface.HUD_POST,
                true,
                false,
                eventId,
                () -> {
                    renders.incrementAndGet();
                    return true;
                },
                () -> {},
                proofs::incrementAndGet));
        assertEquals(0, renders.get());

        assertFalse(presentation.present(
                BreachPresentation.Surface.SCREEN_POST,
                true,
                false,
                eventId,
                () -> {
                    renders.incrementAndGet();
                    return false;
                },
                () -> {},
                proofs::incrementAndGet));
        assertEquals(1, renders.get());
        assertEquals(0, proofs.get());

        // The transparent attempt still owns this event/frame; a duplicate
        // callback cannot manufacture a second chance with different truth.
        assertFalse(presentation.present(
                BreachPresentation.Surface.SCREEN_POST,
                true,
                false,
                eventId,
                () -> {
                    renders.incrementAndGet();
                    return true;
                },
                () -> {},
                proofs::incrementAndGet));
        assertEquals(1, renders.get());
        assertEquals(0, proofs.get());
    }

    @Test
    void proofIsReportedOnlyAfterTheRenderedBatchIsSubmitted() {
        BreachPresentation presentation = new BreachPresentation();
        UUID eventId = UUID.fromString("b3473747-59dd-4708-99e3-307029394ec2");
        List<String> calls = new ArrayList<>();
        presentation.beginFrame();

        assertTrue(presentation.present(
                BreachPresentation.Surface.HUD_POST,
                false,
                false,
                eventId,
                () -> {
                    calls.add("render");
                    return true;
                },
                () -> calls.add("submit"),
                () -> calls.add("proof")));
        assertEquals(List.of("render", "submit", "proof"), calls);

        presentation.beginFrame();
        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> presentation.present(
                        BreachPresentation.Surface.HUD_POST,
                        false,
                        false,
                        eventId,
                        () -> true,
                        () -> {
                            throw new RuntimeException("submission failed");
                        },
                        () -> calls.add("invalid proof")));
        assertEquals("submission failed", failure.getMessage());
        assertFalse(calls.contains("invalid proof"));
    }
}
