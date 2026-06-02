package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteNavigationTest {

    @Test
    void findQuoteRange_includesLeadingAndTrailingDialogQuotes() {
        String doc = "Er sagte: „Das ist ein Test.\u201C und ging.";
        String quote = "Das ist ein Test";

        QuoteNavigation.QuoteRange range = QuoteNavigation.findQuoteRange(doc, quote).orElseThrow();

        assertEquals('„', doc.charAt(range.start()));
        assertEquals('.', doc.charAt(range.end() - 2));
        assertEquals('\u201C', doc.charAt(range.end() - 1));
    }

    @Test
    void findQuoteRange_includesAsciiQuotesWhenAgentOmitsThem() {
        String doc = "\"Hallo Welt\", sagte sie.";
        String quote = "Hallo Welt";

        QuoteNavigation.QuoteRange range = QuoteNavigation.findQuoteRange(doc, quote).orElseThrow();

        assertEquals('"', doc.charAt(range.start()));
        assertEquals('"', doc.charAt(range.end() - 1));
    }
}
