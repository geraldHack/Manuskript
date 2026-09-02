package com.manuskript.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiReviewTextsTest {

    @Test
    void shortTextStaysIntact() {
        assertEquals("Schwächen", NiReviewTexts.previewChange("Schwächen"));
        assertFalse(NiReviewTexts.wasTruncated("Schwächen"));
    }

    @Test
    void collapsesWhitespace() {
        assertEquals("ein Satz", NiReviewTexts.previewChange("ein\n\n  Satz"));
    }

    @Test
    void longDiffIsTruncated() {
        String longText = "A".repeat(120);
        String preview = NiReviewTexts.previewChange(longText);
        assertTrue(preview.endsWith("…"));
        assertEquals(NiReviewTexts.CHANGE_PREVIEW_CHARS, preview.length());
        assertTrue(NiReviewTexts.wasTruncated(longText));
    }
}
