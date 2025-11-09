package com.manuskript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QuotationMarkConverterTest {
    
    @Test
    public void testConvertToEnglish() {
        // Test: Alle typographischen Anführungszeichen sollten zu englischen werden
        String result1 = QuotationMarkConverter.convertQuotationMarks("\u201AGoliath\u2019", "englisch");
        assertEquals("'Goliath'", result1); // Deutsche einfache → englische
        
        String result2 = QuotationMarkConverter.convertQuotationMarks("\u201ETest\u201C", "englisch");
        assertEquals("\"Test\"", result2); // Deutsche doppelte → englische
        
        String result3 = QuotationMarkConverter.convertQuotationMarks("\u203AGoliath\u2039", "englisch");
        assertEquals("'Goliath'", result3); // Französische einfache → englische
        
        String result4 = QuotationMarkConverter.convertQuotationMarks("\u00BBTest\u00AB", "englisch");
        assertEquals("\"Test\"", result4); // Französische doppelte → englische
        
        String result5 = QuotationMarkConverter.convertQuotationMarks("\u2039Goliath\u203A", "englisch");
        assertEquals("'Goliath'", result5); // Schweizer einfache → englische
        
        String result6 = QuotationMarkConverter.convertQuotationMarks("\u00ABTest\u00BB", "englisch");
        assertEquals("\"Test\"", result6); // Schweizer doppelte → englische
        
        // Test: Gerade Anführungszeichen bleiben unverändert
        String result7 = QuotationMarkConverter.convertQuotationMarks("'Goliath'", "englisch");
        assertEquals("'Goliath'", result7);
        
        String result8 = QuotationMarkConverter.convertQuotationMarks("\"Test\"", "englisch");
        assertEquals("\"Test\"", result8);
    }
    
    @Test
    public void testConvertToGerman() {
        // Test: Gerade Anführungszeichen zu deutschen typographischen Zeichen
        String result1 = QuotationMarkConverter.convertQuotationMarks("'Goliath'", "deutsch");
        // Sollte deutsche einfache Anführungszeichen haben: ‚ und '
        assertTrue(result1.contains("\u201A")); // ‚ (deutsches öffnendes einfaches Anführungszeichen)
        assertTrue(result1.contains("\u2019")); // ' (deutsches schließendes einfaches Anführungszeichen)
        
        // Test: Doppelte Anführungszeichen zu deutschen typographischen Zeichen
        String result2 = QuotationMarkConverter.convertQuotationMarks("\"Test\"", "deutsch");
        // Sollte deutsche doppelte Anführungszeichen haben: „ und "
        assertTrue(result2.contains("\u201E")); // „ (deutsches öffnendes doppeltes Anführungszeichen)
        assertTrue(result2.contains("\"")); // " (U+0022 - gerades schließendes Anführungszeichen, wie im Makro)
    }
    
    @Test
    public void testConvertToFrench() {
        // Test: Gerade Anführungszeichen zu französischen typographischen Zeichen
        String result1 = QuotationMarkConverter.convertQuotationMarks("'Goliath'", "französisch");
        // Sollte französische einfache Anführungszeichen haben: › und ‹
        assertTrue(result1.contains("\u203A")); // › (französisches schließendes einfaches Anführungszeichen)
        assertTrue(result1.contains("\u2039")); // ‹ (französisches öffnendes einfaches Anführungszeichen)
        
        // Test: Doppelte Anführungszeichen zu französischen typographischen Zeichen
        String result2 = QuotationMarkConverter.convertQuotationMarks("\"Test\"", "französisch");
        // Sollte französische doppelte Anführungszeichen haben: » und «
        assertTrue(result2.contains("\u00BB")); // » (französisches schließendes doppeltes Anführungszeichen)
        assertTrue(result2.contains("\u00AB")); // « (französisches öffnendes doppeltes Anführungszeichen)
    }
    
    
    @Test
    public void testConvertToSwiss() {
        // Test: Gerade Anführungszeichen zu schweizer typographischen Zeichen
        String result1 = QuotationMarkConverter.convertQuotationMarks("'Goliath'", "schweizer");
        // Sollte schweizer einfache Anführungszeichen haben: ‹ und ›
        assertTrue(result1.contains("\u2039")); // ‹ (schweizer öffnendes einfaches Anführungszeichen)
        assertTrue(result1.contains("\u203A")); // › (schweizer schließendes einfaches Anführungszeichen)
        
        // Test: Doppelte Anführungszeichen zu schweizer typographischen Zeichen
        String result2 = QuotationMarkConverter.convertQuotationMarks("\"Test\"", "schweizer");
        // Sollte schweizer doppelte Anführungszeichen haben: « und »
        assertTrue(result2.contains("\u00AB")); // « (schweizer öffnendes doppeltes Anführungszeichen)
        assertTrue(result2.contains("\u00BB")); // » (schweizer schließendes doppeltes Anführungszeichen)
    }
    
    @Test
    public void testApostrophesAndQuotes() {
        String input1 = "\"Ich sag' es ja: don't.\"";
        String input2 = "\"Das ist Palues'.\"";
        String input3 = "\"Das ist 'falsch'.\"";

        // Französisch
        String french1 = QuotationMarkConverter.convertQuotationMarks(input1, "französisch");
        String french2 = QuotationMarkConverter.convertQuotationMarks(input2, "französisch");
        String french3 = QuotationMarkConverter.convertQuotationMarks(input3, "französisch");
        
        assertEquals("»Ich sag' es ja: don't.«", french1, "Französisch: Apostrophe müssen bleiben");
        assertEquals("»Das ist Palues'.«", french2, "Französisch: Apostroph am Wortende muss bleiben");
        assertEquals("»Das ist ›falsch‹.«", french3, "Französisch: Anführungszeichen-Paar muss konvertiert werden");

        // Deutsch
        String german1 = QuotationMarkConverter.convertQuotationMarks(input1, "deutsch");
        String german2 = QuotationMarkConverter.convertQuotationMarks(input2, "deutsch");
        String german3 = QuotationMarkConverter.convertQuotationMarks(input3, "deutsch");
        
        assertEquals("„Ich sag' es ja: don't.\"", german1, "Deutsch: Apostrophe müssen bleiben");
        assertEquals("„Das ist Palues'.\"", german2, "Deutsch: Apostroph am Wortende muss bleiben");
        assertEquals("„Das ist \u201Afalsch\u2019.\"", german3, "Deutsch: Anführungszeichen-Paar muss konvertiert werden");

        // Schweizer
        String swiss1 = QuotationMarkConverter.convertQuotationMarks(input1, "schweizer");
        String swiss2 = QuotationMarkConverter.convertQuotationMarks(input2, "schweizer");
        String swiss3 = QuotationMarkConverter.convertQuotationMarks(input3, "schweizer");
        
        assertEquals("«Ich sag' es ja: don't.»", swiss1, "Schweizer: Apostrophe müssen bleiben");
        assertEquals("«Das ist Palues'.»", swiss2, "Schweizer: Apostroph am Wortende muss bleiben");
        assertEquals("«Das ist \u2039falsch\u203A.»", swiss3, "Schweizer: Anführungszeichen-Paar muss konvertiert werden");
    }
}
