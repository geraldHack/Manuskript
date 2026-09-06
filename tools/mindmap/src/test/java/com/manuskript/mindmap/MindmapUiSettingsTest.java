package com.manuskript.mindmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MindmapUiSettingsTest {

    @TempDir
    Path temp;

    @Test
    void roundTripWindowSize() throws Exception {
        MindmapUiSettings settings = new MindmapUiSettings();
        settings.width = 1280;
        settings.height = 900;
        settings.x = 120.5;
        settings.y = 80.0;

        settings.save(temp);
        MindmapUiSettings loaded = MindmapUiSettings.load(temp);

        assertEquals(1280, loaded.width, 0.001);
        assertEquals(900, loaded.height, 0.001);
        assertNotNull(loaded.x);
        assertEquals(120.5, loaded.x, 0.001);
        assertEquals(80.0, loaded.y, 0.001);
    }

    @Test
    void clampsInvalidValues() {
        MindmapUiSettings settings = new MindmapUiSettings();
        settings.width = 100;
        settings.height = 99999;
        settings.clamped();

        assertEquals(MindmapUiSettings.MIN_WIDTH, settings.width, 0.001);
        assertEquals(MindmapUiSettings.MAX_HEIGHT, settings.height, 0.001);
    }
}
