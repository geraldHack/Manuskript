package com.manuskript;

import java.util.ArrayList;
import java.util.List;

/**
 * Satzaufteilung für TTS-Batch (Modus „Satz“).
 * Script-Format: direkte Rede oft in eigener Zeile, Attribution in der nächsten (meist ohne Komma).
 */
final class TtsSentenceSplitter {

    private static final String OPENING_QUOTES = "\u201E\u2018";
    private static final String CLOSING_QUOTES = "\u201D\u2019";
    private static final String AMBIGUOUS_QUOTES = "\u00AB\u00BB\u201C";
    private static final char ASCII_QUOTE = '"';

    private TtsSentenceSplitter() {
    }

    static List<int[]> splitIntoSentences(String fullText) {
        List<int[]> ranges = new ArrayList<>();
        if (fullText == null || fullText.isEmpty()) return ranges;
        int len = fullText.length();
        int sentenceStart = 0;
        int quoteLevel = 0;
        int i = 0;
        while (i < len) {
            char c = fullText.charAt(i);
            if (isOpeningQuote(c)) {
                quoteLevel++;
                i++;
                continue;
            }
            if (isClosingQuote(c) || isAmbiguousQuote(c) || c == ASCII_QUOTE) {
                if (quoteLevel > 0) {
                    quoteLevel--;
                    if (quoteLevel == 0 && shouldSplitAfterClosingQuote(fullText, len, i)) {
                        int end = i + 1;
                        if (sentenceStart < end && fullText.substring(sentenceStart, end).trim().length() > 0) {
                            ranges.add(new int[] { sentenceStart, end });
                        }
                        sentenceStart = skipSpaces(fullText, len, end);
                    }
                } else if (isAmbiguousQuote(c) || c == ASCII_QUOTE) {
                    quoteLevel = 1;
                }
                i++;
                continue;
            }
            if (quoteLevel == 0 && (c == '.' || c == '!' || c == '?')) {
                int end = i + 1;
                while (end < len && (fullText.charAt(end) == ' ' || fullText.charAt(end) == '\t')) end++;
                if (sentenceStart < end && fullText.substring(sentenceStart, end).trim().length() > 0) {
                    ranges.add(new int[] { sentenceStart, end });
                }
                sentenceStart = end;
                i = end;
                continue;
            }
            i++;
        }
        if (sentenceStart < len && fullText.substring(sentenceStart).trim().length() > 0) {
            ranges.add(new int[] { sentenceStart, len });
        }
        return ranges;
    }

    /**
     * Satz am schließenden Anführungszeichen nur trennen, wenn danach keine Attribution folgt
     * (z. B. nächste Zeile: „sagte sie …“). Ohne . ! ? in der Rede nie hier trennen.
     */
    private static boolean shouldSplitAfterClosingQuote(String fullText, int len, int closeQuoteIndex) {
        if (!directSpeechEndsWithSentencePunctuation(fullText, closeQuoteIndex)) {
            return false;
        }
        int p = closeQuoteIndex + 1;
        while (p < len && (fullText.charAt(p) == ' ' || fullText.charAt(p) == '\t')) p++;
        if (p < len && fullText.charAt(p) != '\n' && fullText.charAt(p) != '\r') {
            if (isOpeningQuote(fullText.charAt(p))) return true;
            return !Character.isLowerCase(fullText.charAt(p));
        }
        p = skipLineBreaksAndSpaces(fullText, len, p);
        if (p >= len) return true;
        char next = fullText.charAt(p);
        if (isOpeningQuote(next)) return true;
        return !Character.isLowerCase(next);
    }

    private static int skipSpaces(String fullText, int len, int from) {
        while (from < len) {
            char c = fullText.charAt(from);
            if (c == ' ' || c == '\t') from++;
            else break;
        }
        return from;
    }

    private static int skipLineBreaksAndSpaces(String fullText, int len, int from) {
        while (from < len) {
            char c = fullText.charAt(from);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') from++;
            else break;
        }
        return from;
    }

    private static boolean directSpeechEndsWithSentencePunctuation(String fullText, int closeQuoteIndex) {
        int j = closeQuoteIndex - 1;
        while (j >= 0) {
            char c = fullText.charAt(j);
            if (c == ' ' || c == '\t') {
                j--;
                continue;
            }
            return c == '.' || c == '!' || c == '?';
        }
        return false;
    }

    private static boolean isOpeningQuote(char c) {
        return OPENING_QUOTES.indexOf(c) >= 0;
    }

    private static boolean isClosingQuote(char c) {
        return CLOSING_QUOTES.indexOf(c) >= 0;
    }

    private static boolean isAmbiguousQuote(char c) {
        return AMBIGUOUS_QUOTES.indexOf(c) >= 0;
    }
}
