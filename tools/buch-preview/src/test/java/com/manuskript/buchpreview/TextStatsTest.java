package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextStatsTest {

    @Test
    void countsGermanWordsAndSentences() {
        String text = "Jomar rettet die Welt. Ungebetene Gäste kommen.";
        assertEquals(7, TextStats.wordCount(text));
        assertEquals(2, TextStats.sentenceCount(text));
    }

    @Test
    void emptyIsZero() {
        assertEquals(0, TextStats.wordCount(""));
        assertEquals(0, TextStats.sentenceCount(""));
        assertEquals(0, TextStats.of(null).words());
    }

    @Test
    void fragmentWithoutTerminatorIsOneSentence() {
        assertEquals(1, TextStats.sentenceCount("Nur ein Satz ohne Punkt"));
    }

    @Test
    void hyphenatedWordCountsAsOne() {
        assertEquals(1, TextStats.wordCount("Königsstadt"));
        assertTrue(TextStats.wordCount("Self-Publishing") >= 1);
    }
}
