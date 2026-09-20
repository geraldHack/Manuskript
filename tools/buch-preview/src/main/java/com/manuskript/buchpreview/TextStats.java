package com.manuskript.buchpreview;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wörter und Sätze aus Klartext (HTML-Tags bereits entfernt).
 */
public final class TextStats {

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’-]*", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SENTENCE_END = Pattern.compile(
            "(?<=[\\p{L}\\p{N}\"»”'])[.!?…](?:['\"»”')\\]]*+)(?=\\s+|\\z)",
            Pattern.UNICODE_CHARACTER_CLASS);

    private TextStats() {
    }

    public static int wordCount(String plain) {
        if (plain == null || plain.isBlank()) {
            return 0;
        }
        Matcher matcher = WORD.matcher(plain);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static int sentenceCount(String plain) {
        if (plain == null || plain.isBlank()) {
            return 0;
        }
        String trimmed = plain.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        Matcher matcher = SENTENCE_END.matcher(trimmed);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        if (count == 0 && WORD.matcher(trimmed).find()) {
            return 1;
        }
        return count;
    }

    public record Counts(int words, int sentences) {
    }

    public static Counts of(String plain) {
        return new Counts(wordCount(plain), sentenceCount(plain));
    }
}
