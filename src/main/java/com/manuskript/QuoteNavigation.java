package com.manuskript;

import java.util.Optional;

/**
 * Robuste Zitat-Suche (Anführungszeichen/Whitespace-normalisiert) für Agenten-Sprünge.
 */
public final class QuoteNavigation {

    private QuoteNavigation() {
    }

    public record QuoteRange(int start, int end) {
    }

    /**
     * Findet einen Textbereich für ein Agenten-Zitat.
     * Unterstützt optional {@code Zitat|Index} im quote-String.
     */
    public static Optional<QuoteRange> findQuoteRange(String documentText, String quote) {
        if (documentText == null || documentText.isEmpty() || quote == null || quote.isBlank()) {
            return Optional.empty();
        }

        String[] parts = quote.split("\\|", 2);
        String rawQuote = parts[0].trim();
        int quoteIndex = -1;
        if (parts.length > 1) {
            try {
                quoteIndex = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                quoteIndex = -1;
            }
        }

        if (quoteIndex >= 0 && quoteIndex < documentText.length()) {
            int endIdx = Math.min(quoteIndex + rawQuote.length(), documentText.length());
            return Optional.of(expandToIncludeAdjacentQuoteMarks(documentText, rawQuote, quoteIndex, endIdx));
        }

        NormalizedText normalizedDocument = normalizeForSearch(documentText);
        NormalizedText normalizedQuote = normalizeForSearch(rawQuote);
        String haystack = normalizedDocument.text;
        String needle = normalizedQuote.text.trim();
        if (haystack.isEmpty() || needle.isEmpty()) {
            return Optional.empty();
        }

        int found = haystack.indexOf(needle);
        int matchLen = needle.length();

        if (found < 0) {
            int[] tryLengths = {200, 150, 120, 100, 80, 60, 50, 40, 30, 25, 20, 15, 12, 10, 8};
            for (int len : tryLengths) {
                if (len >= needle.length()) {
                    continue;
                }
                if (len < 6) {
                    break;
                }
                String prefix = needle.substring(0, len).trim();
                if (prefix.length() < 6) {
                    continue;
                }
                int idx = haystack.indexOf(prefix);
                if (idx >= 0) {
                    found = idx;
                    matchLen = prefix.length();
                    break;
                }
            }
        }

        if (found < 0) {
            String[] words = needle.split("\\s+");
            for (String word : words) {
                if (word.length() < 5) {
                    continue;
                }
                int idx = haystack.indexOf(word);
                if (idx >= 0) {
                    found = idx;
                    matchLen = word.length();
                    break;
                }
            }
        }

        if (found < 0) {
            return Optional.empty();
        }

        int origStart = normalizedDocument.origPos[found];
        int mapEndExclusive = found + matchLen;
        int origEnd = mapEndExclusive < normalizedDocument.origPos.length
                ? normalizedDocument.origPos[mapEndExclusive]
                : documentText.length();
        if (origEnd <= origStart) {
            origEnd = Math.min(origStart + rawQuote.length(), documentText.length());
        }
        return Optional.of(expandToIncludeAdjacentQuoteMarks(documentText, rawQuote, origStart, origEnd));
    }

    /**
     * Erweitert den Treffer um Anführungszeichen direkt davor/dahinter (Dialog: {@code „…"}).
     * Die Normalisierung entfernt Anführungszeichen – ohne Erweiterung fehlen sie in der Markierung.
     */
    private static QuoteRange expandToIncludeAdjacentQuoteMarks(String document, String rawQuote,
                                                                  int start, int end) {
        int newStart = start;
        int newEnd = end;
        int maxLeading = Math.min(3, Math.max(countLeadingQuoteChars(rawQuote), 1));
        int maxTrailing = Math.min(3, Math.max(countTrailingQuoteChars(rawQuote), 1));
        while (newStart > 0 && maxLeading > 0 && isQuoteChar(document.charAt(newStart - 1))) {
            newStart--;
            maxLeading--;
        }
        boolean closingQuoteIncluded = false;
        while (newEnd < document.length() && maxTrailing > 0 && isQuoteChar(document.charAt(newEnd))) {
            newEnd++;
            maxTrailing--;
            closingQuoteIncluded = true;
        }
        if (!closingQuoteIncluded && maxTrailing > 0 && newEnd > newStart) {
            int scan = newEnd - 1;
            while (scan > newStart && isPunctuationBeforeClosingQuote(document.charAt(scan))) {
                scan--;
            }
            if (scan >= newStart && isQuoteChar(document.charAt(scan))) {
                newEnd = scan + 1;
                closingQuoteIncluded = true;
                maxTrailing--;
            }
        }
        if (!closingQuoteIncluded && maxTrailing > 0) {
            int afterPunctuation = skipTrailingPunctuation(document, newEnd);
            while (afterPunctuation < document.length() && maxTrailing > 0
                    && isQuoteChar(document.charAt(afterPunctuation))) {
                afterPunctuation++;
                maxTrailing--;
            }
            newEnd = afterPunctuation;
        }
        return new QuoteRange(newStart, newEnd);
    }

    private static int skipTrailingPunctuation(String document, int from) {
        int pos = from;
        while (pos < document.length() && isPunctuationBeforeClosingQuote(document.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static boolean isPunctuationBeforeClosingQuote(char c) {
        return c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?'
                || c == '…' || c == '·';
    }

    private static int countLeadingQuoteChars(String s) {
        if (s == null) {
            return 0;
        }
        int count = 0;
        String trimmed = s.stripLeading();
        while (count < trimmed.length() && isQuoteChar(trimmed.charAt(count))) {
            count++;
        }
        return count;
    }

    private static int countTrailingQuoteChars(String s) {
        if (s == null) {
            return 0;
        }
        int count = 0;
        String trimmed = s.stripTrailing();
        while (count < trimmed.length() && isQuoteChar(trimmed.charAt(trimmed.length() - 1 - count))) {
            count++;
        }
        return count;
    }

    private static final class NormalizedText {
        final String text;
        final int[] origPos;

        NormalizedText(String text, int[] origPos) {
            this.text = text;
            this.origPos = origPos;
        }
    }

    private static NormalizedText normalizeForSearch(String s) {
        if (s == null) {
            return new NormalizedText("", new int[]{0});
        }
        int len = s.length();
        StringBuilder sb = new StringBuilder(len);
        int[] map = new int[len + 1];
        int n = 0;
        boolean prevSpace = true;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (isQuoteChar(c)) {
                continue;
            }
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                if (prevSpace) {
                    continue;
                }
                map[n] = i;
                sb.append(' ');
                n++;
                prevSpace = true;
                continue;
            }
            map[n] = i;
            sb.append(Character.toLowerCase(c));
            n++;
            prevSpace = false;
        }
        if (n > 0 && sb.charAt(n - 1) == ' ') {
            sb.deleteCharAt(n - 1);
            n--;
        }
        map[n] = len;
        int[] trimmed = new int[n + 1];
        System.arraycopy(map, 0, trimmed, 0, n + 1);
        return new NormalizedText(sb.toString(), trimmed);
    }

    private static boolean isQuoteChar(char c) {
        return switch (c) {
            case '"', '\'', '`', '\u00AB', '\u00BB', '\u2018', '\u2019', '\u201A', '\u201B',
                 '\u201C', '\u201D', '\u201E', '\u201F', '\u2039', '\u203A' -> true;
            default -> false;
        };
    }
}
