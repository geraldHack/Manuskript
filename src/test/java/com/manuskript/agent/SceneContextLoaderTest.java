package com.manuskript.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SceneContextLoaderTest {

    @Test
    void truncateRespectsLimit() {
        String text = "a".repeat(100);
        String result = SceneContextLoader.truncate(text, 40, "Test");
        assertTrue(result.startsWith("a".repeat(40)));
        assertTrue(result.contains("gekürzt"));
    }

    @Test
    void truncateUnlimitedWhenNegative() {
        String text = "Kapitelinhalt";
        assertEquals(text, SceneContextLoader.truncate(text, -1, "Kapitel"));
    }

    @Test
    void contextSizeFromName() {
        assertEquals(SceneContextSize.EXTENDED, SceneContextSize.fromName("EXTENDED"));
        assertEquals(SceneContextSize.COMPACT, SceneContextSize.fromName("invalid"));
    }
}
