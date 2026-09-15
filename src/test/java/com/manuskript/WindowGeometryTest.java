package com.manuskript;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowGeometryTest {

    private static final Rectangle2D LAPTOP = new Rectangle2D(0, 0, 1512, 982);
    private static final Rectangle2D LEFT_MONITOR = new Rectangle2D(-1920, 0, 1920, 1080);

    @Test
    void centerOnPrimaryCountsAsVisible() {
        assertTrue(WindowGeometry.isSubstantiallyVisible(200, 100, 1400, 900, List.of(LAPTOP)));
    }

    @Test
    void windowOnUnpluggedLeftMonitorIsNotVisible() {
        assertFalse(WindowGeometry.isSubstantiallyVisible(-1800, 80, 1400, 900, List.of(LAPTOP)));
    }

    @Test
    void fewPixelsOnTheEdgeAreNotEnough() {
        assertFalse(WindowGeometry.isSubstantiallyVisible(1400, 100, 1400, 900, List.of(LAPTOP)));
    }

    @Test
    void windowOnLeftMonitorStaysWhenThatScreenExists() {
        assertTrue(WindowGeometry.isSubstantiallyVisible(-1800, 80, 1400, 900, List.of(LEFT_MONITOR, LAPTOP)));
    }

    @Test
    void snapMovesOffscreenWindowOntoFallback() {
        Rectangle2D snapped = WindowGeometry.snapIfNeeded(-1800, 80, 1400, 900, List.of(LAPTOP), LAPTOP);
        assertEquals(56, snapped.getMinX(), 0.5);
        assertEquals(41, snapped.getMinY(), 0.5);
        assertEquals(1400, snapped.getWidth(), 0.5);
        assertEquals(900, snapped.getHeight(), 0.5);
    }

    @Test
    void snapKeepsVisibleWindow() {
        Rectangle2D original = WindowGeometry.snapIfNeeded(200, 100, 1400, 900, List.of(LAPTOP), LAPTOP);
        assertEquals(200, original.getMinX(), 0.01);
        assertEquals(100, original.getMinY(), 0.01);
    }
}
