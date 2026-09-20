package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookFormatTest {

    @Test
    void spineIsPagesOverTwoTimesPaper() {
        assertEquals(16.4, BookFormat.spineMm(328, 0.10, false, 0), 0.01);
    }

    @Test
    void hardcoverAddsCoverExtra() {
        assertEquals(19.4, BookFormat.spineMm(328, 0.10, true, 3.0), 0.01);
    }

    @Test
    void presetsIncludePocketAndHardcover() {
        assertTrue(BookFormat.presets().stream().anyMatch(f -> "pocket".equals(f.id)));
        BookFormat hc = BookFormat.byId("hardcover");
        assertTrue(hc.hardcover);
        assertEquals(160, hc.widthMm, 0.01);
        assertFalse(BookFormat.byId("pocket").hardcover);
    }

    @Test
    void unknownIdFallsBackToFirstPreset() {
        assertEquals(BookFormat.presets().get(0).id, BookFormat.byId("nope").id);
    }
}
