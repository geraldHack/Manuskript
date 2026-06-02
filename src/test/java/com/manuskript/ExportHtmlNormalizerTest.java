package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportHtmlNormalizerTest {

    @Test
    void standaloneBrBecomesEmptyLine() {
        String input = "Zeile eins.\n<br>\nZeile zwei.";
        String result = ExportHtmlNormalizer.normalizeBrTagsForEpubHtml(input);
        assertFalse(result.contains("<br>"));
        assertTrue(result.contains("Zeile eins.\n\nZeile zwei."));
    }

    @Test
    void inlineBrBecomesSelfClosing() {
        String input = "Zeile eins<br>Zeile zwei";
        String result = ExportHtmlNormalizer.normalizeBrTagsForEpubHtml(input);
        assertEquals("Zeile eins<br />Zeile zwei", result);
    }

    @Test
    void fixBrTagsInXhtmlRemovesEmptyParagraphs() {
        String input = "<p>Text</p><p><br></p><p>Mehr</p>";
        String result = ExportHtmlNormalizer.fixBrTagsInXhtml(input);
        assertFalse(result.contains("<br>"));
        assertFalse(result.contains("<p><br />"));
        assertTrue(result.contains("<p>Text</p>"));
        assertTrue(result.contains("<p>Mehr</p>"));
    }
}
