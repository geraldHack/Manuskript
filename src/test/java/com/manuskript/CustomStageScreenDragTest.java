package com.manuskript;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomStageScreenDragTest {

    private static final Rectangle2D EXTERNAL = new Rectangle2D(0, 0, 2560, 1440);
    private static final Rectangle2D MACBOOK = new Rectangle2D(560, 1440, 1440, 900);

    @Test
    void keepsWindowFullyOnExternalWhenItWouldPeekOntoMacBook() {
        double w = 800;
        double h = 600;
        double[] pos = CustomStage.keepOffOtherScreens(
                EXTERNAL, List.of(MACBOOK), 700, 1000, w, h);

        assertEquals(700, pos[0], 0.01);
        assertEquals(1440 - h, pos[1], 0.01);
        assertTrue(pos[1] + h <= MACBOOK.getMinY() + 0.01);
    }

    @Test
    void placesWindowFullyOnMacBookWhenLeavingExternal() {
        double w = 800;
        double h = 600;
        double[] pos = CustomStage.keepOffOtherScreens(
                MACBOOK, List.of(EXTERNAL), 700, 1300, w, h);

        assertEquals(700, pos[0], 0.01);
        assertEquals(EXTERNAL.getMaxY(), pos[1], 0.01);
        assertTrue(pos[1] >= EXTERNAL.getMaxY() - 0.01);
    }
}
