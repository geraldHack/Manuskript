package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TtsSentenceSplitterTest {

    private static String slice(String text, int[] range) {
        return text.substring(range[0], range[1]);
    }

    @Test
    void scriptFormatSpeechLineThenAttributionOnNextLine() {
        String text = "„Das ist toll“\nsagte sie.";
        List<int[]> ranges = TtsSentenceSplitter.splitIntoSentences(text);
        assertEquals(1, ranges.size());
        assertEquals("„Das ist toll“\nsagte sie.", slice(text, ranges.get(0)));
    }

    @Test
    void scriptFormatWithExclamationOnSpeechLine() {
        String text = "„Hallo!“\nsagte sie.";
        List<int[]> ranges = TtsSentenceSplitter.splitIntoSentences(text);
        assertEquals(1, ranges.size());
        assertEquals("„Hallo!“\nsagte sie.", slice(text, ranges.get(0)));
    }

    @Test
    void twoDialogueLinesWithoutAttribution() {
        String text = "„Hallo!“ „Welt!“";
        List<int[]> ranges = TtsSentenceSplitter.splitIntoSentences(text);
        assertEquals(2, ranges.size());
        assertEquals("„Hallo!“", slice(text, ranges.get(0)));
        assertEquals("„Welt!“", slice(text, ranges.get(1)));
    }

    @Test
    void twoDialogueLinesOnSeparateLines() {
        String text = "„Hallo!“\n„Welt!“";
        List<int[]> ranges = TtsSentenceSplitter.splitIntoSentences(text);
        assertEquals(2, ranges.size());
        assertEquals("„Hallo!“", slice(text, ranges.get(0)).trim());
        assertEquals("„Welt!“", slice(text, ranges.get(1)).trim());
    }

    @Test
    void normalSentencesUnchanged() {
        String text = "Er ging. Sie blieb.";
        List<int[]> ranges = TtsSentenceSplitter.splitIntoSentences(text);
        assertEquals(2, ranges.size());
        assertEquals("Er ging.", slice(text, ranges.get(0)).trim());
        assertEquals("Sie blieb.", slice(text, ranges.get(1)).trim());
    }

    @Test
    void questionInsideQuoteWithAttributionOnSameLine() {
        String text = "„Was?“ fragte er.";
        List<int[]> ranges = TtsSentenceSplitter.splitIntoSentences(text);
        assertEquals(1, ranges.size());
        assertEquals("„Was?“ fragte er.", slice(text, ranges.get(0)));
    }

    @Test
    void speechLineThenNarrativeOnNextLine() {
        String text = "„Hallo!“\nEr ging weiter.";
        List<int[]> ranges = TtsSentenceSplitter.splitIntoSentences(text);
        assertEquals(2, ranges.size());
        assertEquals("„Hallo!“", slice(text, ranges.get(0)).trim());
        assertEquals("Er ging weiter.", slice(text, ranges.get(1)).trim());
    }
}
