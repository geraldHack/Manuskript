package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterTtsFootnoteTest {

    @Test
    void segmentationMaskRemovesFootnotePunctuationAndPreservesOffsets() {
        String text = "Ein Satz^[Quelle. Noch ein Satz?] endet hier.";

        String masked = ChapterTtsEditorWindow.maskInlineFootnotesForSegmentation(text);

        assertEquals(text.length(), masked.length());
        assertFalse(masked.contains("Quelle"));
        assertEquals(text.indexOf(" endet"), masked.indexOf(" endet"));
    }

    @Test
    void inlineFootnoteIsNotRecognizedAsAudioTag() {
        assertFalse(ChapterTtsEditorWindow.BRACKET_TAG_PATTERN.matcher("Text^[Quelle]").find());
        assertTrue(ChapterTtsEditorWindow.BRACKET_TAG_PATTERN.matcher("Text [flüstert]").find());
    }
}
