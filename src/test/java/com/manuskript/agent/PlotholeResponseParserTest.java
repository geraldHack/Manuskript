package com.manuskript.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotholeResponseParserTest {

    @Test
    void parsesMarkedPlaceholderWithMultilineSuggestions() {
        String suggestion1 = "Er sagte hallo und ging.\n\nDas war neu.";
        String suggestion2 = "Sie flüsterte leise.\n\nDas war anders.";
        String response = """
                <PROBLEM> SCHWEREGRAD: 3 ZITAT: (MARKIERT) PROBLEM: Der markierte Text muss überarbeitet werden. Unklare Formulierung. VORSCHLÄGE: "%s", "%s" </PROBLEM>
                """.formatted(suggestion1, suggestion2);

        PlotholeParseResult result = PlotholeResponseParser.parse(response);

        assertEquals(PlotholeParseResult.Outcome.FINDINGS, result.getOutcome());
        List<Finding> findings = result.getFindings();
        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals(SelectionRevisionSupport.MARKED_QUOTE_PLACEHOLDER, f.getQuote());
        assertEquals(2, f.getSuggestions().size());
        assertEquals(suggestion1, f.getSuggestions().get(0));
        assertEquals(suggestion2, f.getSuggestions().get(1));
        assertFalse(f.getProblem().toLowerCase().contains("vorschlag"));
    }

    @Test
    void parsesQuotedSelectionWithInternalDoubleQuotes() {
        String quote = "Er meinte \"vielleicht morgen\" und ging.";
        String response = """
                <PROBLEM> SCHWEREGRAD: 2 ZITAT: "%s" PROBLEM: Der markierte Text muss überarbeitet werden. Zu vage. VORSCHLÄGE: "Er meinte, morgen wäre besser, und ging.", "Er sagte ausdrücklich „morgen“ und verließ den Raum." </PROBLEM>
                """.formatted(quote);

        PlotholeParseResult result = PlotholeResponseParser.parse(response);

        assertEquals(PlotholeParseResult.Outcome.FINDINGS, result.getOutcome());
        Finding f = result.getFindings().get(0);
        assertEquals(quote, f.getQuote());
        assertEquals(2, f.getSuggestions().size());
    }

    @Test
    void estimateMaxOutputTokensScalesWithSelectionLength() {
        int small = SelectionRevisionSupport.estimateMaxOutputTokens(200, 2048);
        int large = SelectionRevisionSupport.estimateMaxOutputTokens(2000, 2048);
        assertTrue(large > small);
        assertTrue(large >= 2048);
    }

    @Test
    void parsesIdiomReviewWithRewriteAndVorschlaege() {
        String response = """
                <REWRITE>Er lehnte am Fenster und starrte hinaus.</REWRITE>

                <PROBLEM> SCHWEREGRAD: 3 ZITAT: "Er stand am Fenster und blickte nach draußen." PROBLEM: Generische KI-Formulierung. VORSCHLÄGE: "Er lehnte am Fenster und starrte hinaus.", "Am Fenster lehnte er und sah auf die Straße." INDEX: 0 </PROBLEM>
                """;

        PlotholeParseResult result = PlotholeResponseParser.parse(response);

        assertEquals(PlotholeParseResult.Outcome.FINDINGS, result.getOutcome());
        Finding f = result.getFindings().get(0);
        assertEquals("Er stand am Fenster und blickte nach draußen.", f.getQuote());
        assertEquals(2, f.getSuggestions().size());
        assertEquals(0, f.getSelectionQuoteIndex());
        assertFalse(f.getProblem().contains("<PROBLEM>"));
        assertFalse(f.getProblem().toLowerCase().contains("vorschlag"));
    }

    @Test
    void parsesIdiomReviewWithRepeatedVorschlagFields() {
        String response = """
                <PROBLEM> SCHWEREGRAD: 2 ZITAT: „Es war ein wichtiger Moment." PROBLEM: Abstrakte Floskel VORSCHLAG: Es war, als würde die Welt stillstehen. VORSCHLAG: In diesem Augenblick zählte nur noch der Atem. INDEX: 12 </PROBLEM>
                """;

        PlotholeParseResult result = PlotholeResponseParser.parse(response);

        assertEquals(PlotholeParseResult.Outcome.FINDINGS, result.getOutcome());
        Finding f = result.getFindings().get(0);
        assertTrue(f.getQuote().contains("wichtiger Moment"));
        assertEquals(2, f.getSuggestions().size());
        assertEquals(12, f.getSelectionQuoteIndex());
    }

    @Test
    void parsesMultilineIdiomReviewBlocksFromModel() {
        String response = """
                <REWRITE>überarbeitet</REWRITE>

                <PROBLEM>
                ZITAT: „Die Stimme des Dozenten drang wie durch Watte zu mir – ein monotones Summen ohne Bedeutung."
                PROBLEM: Metapher („wie durch Watte") – laut Stilregeln verboten.
                VORSCHLÄGE:
                1. „Die Stimme des Dozenten erreichte mich gedämpft, ein gleichmäßiges Brummen, das an mir abprallte."
                2. „Der Dozent redete. Ich hörte die Silben, aber sie ergaben keinen Sinn."
                INDEX: 3
                </PROBLEM>
                """;

        PlotholeParseResult result = PlotholeResponseParser.parse(response);

        assertEquals(PlotholeParseResult.Outcome.FINDINGS, result.getOutcome());
        assertEquals(1, result.getFindings().size());
        Finding f = result.getFindings().get(0);
        assertTrue(f.getQuote().contains("Watte"));
        assertEquals(2, f.getSuggestions().size());
        assertEquals(3, f.getSelectionQuoteIndex());
        assertFalse(f.getProblem().contains("VORSCHL"));
    }

    @Test
    void parsesIdiomReviewWithoutSuggestionsStillReturnsFinding() {
        String response = """
                <PROBLEM> SCHWEREGRAD: 4 ZITAT: "Zu diesem Zeitpunkt begann alles." PROBLEM: Typische KI-Einleitung. </PROBLEM>
                """;

        PlotholeParseResult result = PlotholeResponseParser.parse(response);

        assertEquals(PlotholeParseResult.Outcome.FINDINGS, result.getOutcome());
        Finding f = result.getFindings().get(0);
        assertEquals("Zu diesem Zeitpunkt begann alles.", f.getQuote());
        assertTrue(f.getSuggestions() == null || f.getSuggestions().isEmpty());
    }

    @Test
    void findQuoteOffsetToleratesTypographicQuotes() {
        String selection = "Satz eins. \u201EDie Stimme drang wie durch Watte zu mir.\u201C Satz drei.";
        int idx = IdiomReviewSupport.findQuoteOffsetInSelection(selection,
                "Die Stimme drang wie durch Watte zu mir.");
        assertTrue(idx >= 0);
    }

    @Test
    void charOffsetOfSentenceIsOneBased() {
        String text = "Eins. Zwei. Drei.";
        assertEquals(0, IdiomReviewSupport.charOffsetOfSentence(text, 1));
        assertEquals(6, IdiomReviewSupport.charOffsetOfSentence(text, 2));
        assertEquals(12, IdiomReviewSupport.charOffsetOfSentence(text, 3));
    }
}
