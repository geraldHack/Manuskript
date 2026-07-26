package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationMarkSupportTest {

    private static final int FRENCH = 1;
    private static final int GERMAN = 0;

    @Test
    void doubleQuoteAtEndOfFrenchDialog_closesWithGuillemetNotApostrophe() {
        String text = "»Dann muss er heute länger warten";
        String result = QuotationMarkSupport.resolveTypedQuote(text, text.length(), "\"", FRENCH);
        assertEquals("\u00AB", result); // «
    }

    @Test
    void doubleQuoteAtEndOfGermanDialog_closesWithTypographicQuote() {
        String text = "„Dann muss er heute länger warten";
        String result = QuotationMarkSupport.resolveTypedQuote(text, text.length(), "\"", GERMAN);
        assertEquals("\u201C", result); // “
    }

    @Test
    void singleQuoteMidWord_staysApostrophe() {
        String text = "dont";
        // caret between n and t
        String result = QuotationMarkSupport.resolveTypedQuote(text, 3, "'", GERMAN);
        assertEquals("'", result);
    }

    @Test
    void singleQuoteAtWordEnd_withoutOpenSingle_isApostrophe() {
        String text = "warten";
        String result = QuotationMarkSupport.resolveTypedQuote(text, text.length(), "'", GERMAN);
        assertEquals("'", result);
    }

    @Test
    void doubleQuoteAtLineStart_opensEvenIfEarlierSpeechUnclosed() {
        String text = "»Frühere Rede ohne Ende\n\n";
        QuotationMarkSupport.TypedQuoteResult result = QuotationMarkSupport.resolveTypedQuoteDetailed(
                text, text.length(), "\"", FRENCH);
        assertEquals("\u00BB", result.text()); // »
        assertTrue(result.warnUnbalancedQuotes());
    }

    @Test
    void doubleQuoteMidLine_stillClosesWhenOpen() {
        String text = "»Dann muss er heute länger warten";
        String result = QuotationMarkSupport.resolveTypedQuote(text, text.length(), "\"", FRENCH);
        assertEquals("\u00AB", result); // «
    }

    @Test
    void isEffectiveLineStart_afterNewlineAndSpaces() {
        assertTrue(QuotationMarkSupport.isEffectiveLineStart("Hallo\n  ", 8));
        assertFalse(QuotationMarkSupport.isEffectiveLineStart("Hallo ", 6));
    }
}
