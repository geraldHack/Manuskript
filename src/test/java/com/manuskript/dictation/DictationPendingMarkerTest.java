package com.manuskript.dictation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictationPendingMarkerTest {

    @Test
    void pendingMarkerIsJobUniqueAndFindable() {
        String first = DictationSupport.buildPendingMarker(0);
        String second = DictationSupport.buildPendingMarker(1);

        assertEquals("⟦d:0⟧", first);
        assertEquals("⟦d:1⟧", second);
        assertFalse(first.equals(second));

        String document = "Vorher " + first + " danach " + second + " Ende";
        assertEquals(7, document.indexOf(first));
        assertTrue(document.indexOf(second) > document.indexOf(first));
    }

    @Test
    void resultTracksWhetherLlmFormattedTheText() {
        DictationResult fromLlm = new DictationResult("roh", "sauber", DictationMode.TRANSCRIPTION, true);
        DictationResult rawOnly = new DictationResult("roh", "roh.", DictationMode.TRANSCRIPTION, false);

        assertTrue(fromLlm.llmFormatted());
        assertFalse(rawOnly.llmFormatted());
        assertEquals("roh.", rawOnly.processedText());
    }
}
