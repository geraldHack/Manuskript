package com.manuskript;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Satz- und Wortgrenzen für Kontextmenü-Aktionen im Kapitel-Editor. */
final class ChapterRewriteSentenceUtils {

    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\"\\u201E\\u201C\\u201D\\u00BB\\u00AB]");

    private ChapterRewriteSentenceUtils() {
    }

    static int[] findCurrentSentenceBounds(String text, int caretPosition) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int caret = Math.max(0, Math.min(text.length() - 1, caretPosition));
        if (caretPosition >= text.length()) {
            caret = text.length() - 1;
        }

        Matcher quoteMatcher = QUOTE_PATTERN.matcher(text);
        List<Integer> quotePositions = new ArrayList<>();
        while (quoteMatcher.find()) {
            quotePositions.add(quoteMatcher.start());
        }

        int sentenceStart = caret;
        while (sentenceStart > 0) {
            char c = text.charAt(sentenceStart - 1);
            if (isInsideQuotePair(quotePositions, sentenceStart - 1)) {
                sentenceStart--;
                continue;
            }
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                if (sentenceStart > 1 && c == '.') {
                    char prev = text.charAt(sentenceStart - 2);
                    if (Character.isLetter(prev)) {
                        sentenceStart--;
                        continue;
                    }
                }
                break;
            }
            sentenceStart--;
        }

        int sentenceEnd = caret;
        while (sentenceEnd < text.length()) {
            char c = text.charAt(sentenceEnd);
            if (isInsideQuotePair(quotePositions, sentenceEnd)) {
                sentenceEnd++;
                continue;
            }
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                if (c != '\n') {
                    sentenceEnd++;
                }
                break;
            }
            sentenceEnd++;
        }
        while (sentenceEnd < text.length() && Character.isWhitespace(text.charAt(sentenceEnd))) {
            sentenceEnd++;
        }
        if (sentenceStart >= sentenceEnd) {
            return null;
        }
        return new int[]{sentenceStart, sentenceEnd};
    }

    private static boolean isInsideQuotePair(List<Integer> quotePositions, int offset) {
        for (int i = 0; i + 1 < quotePositions.size(); i += 2) {
            int startQuote = quotePositions.get(i);
            int endQuote = quotePositions.get(i + 1);
            if (offset > startQuote && offset <= endQuote) {
                return true;
            }
        }
        return false;
    }

    static boolean containsSpeechTag(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("sagte") || lower.contains("fragte") || lower.contains("flüsterte")
                || lower.contains("rief") || lower.contains("antwortete")
                || lower.contains("erwiderte") || lower.contains("meinte");
    }
}
