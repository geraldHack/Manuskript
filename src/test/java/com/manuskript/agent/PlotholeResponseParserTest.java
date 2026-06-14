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
}
