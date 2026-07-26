package com.manuskript.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SelectionRevisionSupportTest {

    @Test
    void isLikelyTruncatedRewriteDetectsShortSuggestion() {
        String original = "A".repeat(400);
        String suggestion = "B".repeat(100);
        assertTrue(SelectionRevisionSupport.isLikelyTruncatedRewrite(original, suggestion));
    }

    @Test
    void isLikelyTruncatedRewriteAllowsSimilarLength() {
        String original = "Der Morgen war kühl. ".repeat(20);
        String suggestion = "Der Tag begann frisch. ".repeat(18);
        assertFalse(SelectionRevisionSupport.isLikelyTruncatedRewrite(original, suggestion));
    }

    @Test
    void mergeTruncatedRewriteKeepsTail() {
        String original = ("Erster Satz hier mit etwas mehr Inhalt für die Länge. "
                + "Zweiter Satz folgt mit zusätzlichen Worten. "
                + "Dritter Satz bleibt stehen und beschreibt die Szene. "
                + "Vierter Satz ebenfalls mit Handlung und Dialog. "
                + "Fünfter Satz zum Schluss der Passage. ").repeat(3);
        // Nur Anfang umgeschrieben, Rest fehlt
        String suggestion = "Neuer erster Satz mit frischer Formulierung. Neuer zweiter Satz ebenfalls.";
        assertTrue(SelectionRevisionSupport.isLikelyTruncatedRewrite(original, suggestion));

        String merged = SelectionRevisionSupport.mergeTruncatedRewrite(original, suggestion);
        assertNotNull(merged);
        assertTrue(merged.startsWith(suggestion), merged);
        assertTrue(merged.contains("Dritter Satz") || merged.contains("Vierter") || merged.contains("Fünfter"),
                merged);
        assertTrue(merged.length() > suggestion.length(), merged);
    }

    @Test
    void findQuoteSpanInSelectionFindsSubstring() {
        String selection = "Alpha. Beta gamma. Delta.";
        int[] span = SelectionRevisionSupport.findQuoteSpanInSelection(selection, "Beta gamma.");
        assertNotNull(span);
        assertEquals("Beta gamma.", selection.substring(span[0], span[1]));
    }

    @Test
    void findQuoteSpanInSelectionIgnoresPlaceholder() {
        assertEquals(null, SelectionRevisionSupport.findQuoteSpanInSelection(
                "Irgendein Text", SelectionRevisionSupport.MARKED_QUOTE_PLACEHOLDER));
    }
}
