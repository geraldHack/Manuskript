package com.manuskript;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComfyUIClientTest {

    @AfterEach
    void resetDedupCache() {
        ComfyUIClient.resetComfyUiTextDedupCacheForTests();
    }

    @Test
    void textDedupSuffixOnlyOnRepeatedText() {
        assertEquals("Hallo.", ComfyUIClient.applyComfyUiTextDedupSuffix("Hallo."));
        assertEquals("Hallo." + "\u200B", ComfyUIClient.applyComfyUiTextDedupSuffix("Hallo."));
        assertEquals("Hallo." + "\u200B\u200B", ComfyUIClient.applyComfyUiTextDedupSuffix("Hallo."));
        assertEquals("Neu.", ComfyUIClient.applyComfyUiTextDedupSuffix("Neu."));
        assertEquals("Neu." + "\u200B", ComfyUIClient.applyComfyUiTextDedupSuffix("Neu."));
    }

    @Test
    void capsTopPAndTopKForConsistencyMode() {
        assertEquals(0.8, ComfyUIClient.capTopPForConsistency(1.0, true), 0.001);
        assertEquals(30, ComfyUIClient.capTopKForConsistency(80, true));
        assertEquals(1.0, ComfyUIClient.capTopPForConsistency(1.0, false), 0.001);
        assertEquals(80, ComfyUIClient.capTopKForConsistency(80, false));
    }
}
