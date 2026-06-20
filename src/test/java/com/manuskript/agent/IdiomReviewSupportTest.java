package com.manuskript.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdiomReviewSupportTest {

    @Test
    void extractRewriteReturnsInnerText() {
        String raw = """
                <REWRITE>Er stand am Fenster und sah hinaus.</REWRITE>
                <PROBLEM> SCHWEREGRAD: 3 ZITAT: "Er stand am Fenster." PROBLEM: generisch </PROBLEM>
                """;
        assertEquals("Er stand am Fenster und sah hinaus.", IdiomReviewSupport.extractRewrite(raw));
    }

    @Test
    void extractRewriteEmptyWhenMissing() {
        assertEquals("", IdiomReviewSupport.extractRewrite("KEINE_PROBLEME"));
        assertEquals("", IdiomReviewSupport.extractRewrite(null));
    }

    @Test
    void combineContextBlocksMergesChatAndSurrounding() {
        String combined = IdiomReviewSupport.combineContextBlocks(
                "=== KAPITEL ===\nText", "… davor …\nMARKIERT\n… danach …");
        assertTrue(combined.contains("=== KAPITEL ==="));
        assertTrue(combined.contains("=== UMGEBUNG DER MARKIERUNG ==="));
        assertTrue(combined.contains("MARKIERT"));
    }

    @Test
    void combineContextBlocksSkipsBlankParts() {
        assertEquals("nur chat", IdiomReviewSupport.combineContextBlocks("nur chat", ""));
        assertTrue(IdiomReviewSupport.combineContextBlocks("", "").isBlank());
    }

    @Test
    void matchLengthAtIncludesTypographicQuotes() {
        String text = "Vorher. \u201EDie Stimme drang.\u201C Danach.";
        int start = text.indexOf('\u201E');
        assertTrue(start >= 0);
        assertEquals(19, IdiomReviewSupport.matchLengthAt(text, start, "Die Stimme drang."));
    }

    @Test
    void normalizeSuggestionStripsOuterQuotes() {
        assertEquals("Er lehnte am Fenster und starrte hinaus.",
                IdiomReviewSupport.normalizeSuggestion("\"Er lehnte am Fenster und starrte hinaus.\""));
        assertEquals("Er lehnte am Fenster und starrte hinaus.",
                IdiomReviewSupport.normalizeSuggestion("„Er lehnte am Fenster und starrte hinaus.\""));
    }

    @Test
    void normalizeSuggestionStripsEmphasisQuotesButKeepsDirectSpeech() {
        assertEquals("Er meinte, morgen wäre besser, und ging.",
                IdiomReviewSupport.normalizeSuggestion("\"Er meinte, morgen wäre besser, und ging.\""));
        assertEquals("Er sagte ausdrücklich „morgen\" und verließ den Raum.",
                IdiomReviewSupport.normalizeSuggestion(
                        "\"Er sagte ausdrücklich „morgen\" und verließ den Raum.\""));
        assertEquals("Er drehte sich um. „Geh weg\", sagte er leise.",
                IdiomReviewSupport.normalizeSuggestion("„Er drehte sich um. „Geh weg\", sagte er leise.\""));
    }

    @Test
    void normalizeSuggestionStripsQuotedWholeSentenceWithoutSpeechVerb() {
        assertEquals("Die Stimme des Dozenten erreichte mich gedämpft.",
                IdiomReviewSupport.normalizeSuggestion(
                        "„Die Stimme des Dozenten erreichte mich gedämpft.\""));
    }
}
