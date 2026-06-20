package com.manuskript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.manuskript.agent.Finding;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ChapterAgentQuoteActionsTest {

    @Test
    void resolveReplacementRangeIgnoresStaleOffsetsAfterPriorEdit() {
        String document = "Er stand am Fenster. Die Stimme drang wie durch Watte zu mir.";
        Finding finding = new Finding(3,
                "Die Stimme drang wie durch Watte zu mir.",
                "Metapher",
                "Die Stimme erreichte mich gedämpft.");
        // Veraltete Offsets aus der Analyse (zeigen noch auf den ersten Satz)
        finding.setReplaceRangeStart(0);
        finding.setReplaceRangeEnd(22);

        Optional<QuoteNavigation.QuoteRange> range =
                ChapterAgentQuoteActions.resolveReplacementRange(document, finding);

        assertTrue(range.isPresent());
        assertEquals("Die Stimme drang wie durch Watte zu mir.",
                document.substring(range.get().start(), range.get().end()));
    }

    @Test
    void resolveReplacementRangeUsesValidStoredRangeWhenQuoteMatches() {
        String document = "Er stand am Fenster. Die Stimme drang wie durch Watte zu mir.";
        int start = document.indexOf("Die Stimme");
        int end = document.length();
        Finding finding = new Finding(3,
                "Die Stimme drang wie durch Watte zu mir.",
                "Metapher",
                "Die Stimme erreichte mich gedämpft.");
        finding.setReplaceRangeStart(start);
        finding.setReplaceRangeEnd(end);

        Optional<QuoteNavigation.QuoteRange> range =
                ChapterAgentQuoteActions.resolveReplacementRange(document, finding);

        assertTrue(range.isPresent());
        assertEquals(start, range.get().start());
        assertEquals(end, range.get().end());
    }

    @Test
    void prepareReplacementTextConvertsDialogueQuotesToGermanStyle() {
        String suggestion = "Er drehte sich um. „Geh weg\", sagte er leise.";
        String prepared = ChapterAgentQuoteActions.prepareReplacementText(suggestion, 0);
        assertTrue(prepared.contains("\u201EGeh weg\u201C"), prepared);
    }

    @Test
    void resolveReplacementRangeExtendsIdiomQuoteToFullSentence() {
        String document = "Er stand am Fenster. Die Stimme drang wie durch Watte zu mir und verhallte.";
        String truncatedQuote = "Die Stimme drang wie durch Watte";
        Finding finding = new Finding(3, truncatedQuote, "Metapher", "Die Stimme erreichte mich gedämpft.");
        finding.setSelectionQuoteIndex(2);

        Optional<QuoteNavigation.QuoteRange> range =
                ChapterAgentQuoteActions.resolveReplacementRange(document, finding);

        assertTrue(range.isPresent());
        assertEquals("Die Stimme drang wie durch Watte zu mir und verhallte.",
                document.substring(range.get().start(), range.get().end()));
    }

    @Test
    void findQuoteRangeStrictSkipsPrefixFallback() {
        String document = "Er stand am Fenster. Die Stimme drang wie durch Watte zu mir.";
        String quoteLongerThanDocument = "Die Stimme drang wie durch Watte zu mir und noch mehr Text";

        assertTrue(QuoteNavigation.findQuoteRange(document, quoteLongerThanDocument).isPresent());
        assertTrue(QuoteNavigation.findQuoteRangeStrict(document, quoteLongerThanDocument).isEmpty());
    }
}
