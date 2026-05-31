package com.manuskript;

import java.util.ArrayList;
import java.util.List;

/**
 * Intelligente Anführungszeichen beim Tippen und Fehlerprüfung (wie im Haupt-Editor).
 */
public final class QuotationMarkSupport {

    public static final String[][] STYLE_OPTIONS = {
            {"Deutsche Anführungszeichen", "deutsch"},
            {"Französische Anführungszeichen", "französisch"},
            {"Englische Anführungszeichen", "englisch"},
            {"Schweizer Anführungszeichen", "schweizer"}
    };

    public static final int STYLE_COUNT = STYLE_OPTIONS.length;

    private static final String[][] QUOTE_MAPPING = {
            {"\u201E", "\u201C"},
            {"\u00BB", "\u00AB"},
            {"\"", "\""},
            {"\u00AB", "\u00BB"}
    };

    private static final String[][] SINGLE_QUOTE_MAPPING = {
            {"\u201A", Character.toString('\u2019')},
            {"\u203A", "\u2039"},
            {"'", "'"},
            {"\u2039", "\u203A"}
    };

    private QuotationMarkSupport() {
    }

    public record QuoteError(String paragraph, String type, int count, int textOffset) {
    }

    public static String styleLabel(int styleIndex) {
        if (styleIndex < 0 || styleIndex >= STYLE_COUNT) {
            return STYLE_OPTIONS[0][0];
        }
        return STYLE_OPTIONS[styleIndex][0];
    }

    public static String styleKey(int styleIndex) {
        if (styleIndex < 0 || styleIndex >= STYLE_COUNT) {
            return STYLE_OPTIONS[0][1];
        }
        return STYLE_OPTIONS[styleIndex][1];
    }

    public static String convertTextToStyle(String text, int styleIndex) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return QuotationMarkConverter.convertQuotationMarks(text, styleKey(styleIndex));
    }

    public static String resolveTypedQuote(String content, int caretPosition, String inputQuote, int styleIndex) {
        if (styleIndex < 0 || styleIndex >= STYLE_COUNT) {
            return inputQuote;
        }
        if (isApostropheContext(content, caretPosition, styleIndex)) {
            return "'";
        }
        boolean shouldBeClosing = determineQuotationState(content, caretPosition, "'".equals(inputQuote), styleIndex);
        if ("\"".equals(inputQuote)) {
            return shouldBeClosing ? QUOTE_MAPPING[styleIndex][1] : QUOTE_MAPPING[styleIndex][0];
        }
        return shouldBeClosing ? SINGLE_QUOTE_MAPPING[styleIndex][1] : SINGLE_QUOTE_MAPPING[styleIndex][0];
    }

    public static List<QuoteError> findQuoteErrors(String text) {
        List<QuoteError> errors = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return errors;
        }

        String[] paragraphs = text.split("\n", -1);
        List<int[]> dialogBlocks = findDialogBlocks(paragraphs);
        int offset = 0;
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            int paragraphStart = offset;
            offset += paragraph.length() + 1;

            if (paragraph.trim().isEmpty()) {
                continue;
            }

            boolean isPartOfDialog = isPartOfDialogBlock(i, dialogBlocks);

            int doubleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (isDoubleQuoteChar(c)) {
                    doubleQuotes++;
                }
            }

            int singleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (c == '\u2039' || c == '\u203A') {
                    singleQuotes++;
                }
            }

            if (doubleQuotes % 2 != 0 && !isPartOfDialog) {
                errors.add(new QuoteError(paragraph, "Doppelte Anführungszeichen", doubleQuotes, paragraphStart));
            }
            if (singleQuotes % 2 != 0 && !isPartOfDialog) {
                errors.add(new QuoteError(paragraph, "Einfache Anführungszeichen", singleQuotes, paragraphStart));
            }
        }
        return errors;
    }

    public static boolean isQuotationMark(char c) {
        return c == '\u0022' || c == '\u00AB' || c == '\u00BB' || c == '\u201E' || c == '\u201C'
                || c == '\u2039' || c == '\u203A' || c == '\u201A' || c == '\u2019'
                || c == '\u201B' || c == '\u201D';
    }

    private static boolean isDoubleQuoteChar(char c) {
        return c == '"' || c == '\u201E' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB';
    }

    private static boolean isApostropheContext(String content, int position, int styleIndex) {
        if (position <= 0 || content == null) {
            return false;
        }

        char charBefore = content.charAt(position - 1);
        boolean hasCharAfter = position < content.length();
        char charAfter = hasCharAfter ? content.charAt(position) : '\0';

        if (position > 1) {
            char charBeforeBefore = content.charAt(position - 2);
            if (charBeforeBefore == ' ' || charBeforeBefore == '\n' || charBeforeBefore == '\t') {
                return false;
            }
        }

        if (Character.isLetter(charBefore) && hasCharAfter && Character.isLetter(charAfter)) {
            return true;
        }

        if (Character.isLetter(charBefore) && hasCharAfter && isQuotationMark(charAfter)) {
            return true;
        }

        if (Character.isLetter(charBefore)
                && (!hasCharAfter || charAfter == ' ' || charAfter == '\n' || charAfter == '\t'
                || charAfter == '.' || charAfter == ',' || charAfter == '!' || charAfter == '?'
                || charAfter == ';' || charAfter == ':')) {
            boolean hasOpeningQuote = false;
            for (int i = position - 1; i >= 0; i--) {
                char c = content.charAt(i);
                if (styleIndex >= 0 && styleIndex < SINGLE_QUOTE_MAPPING.length) {
                    String openingQuote = SINGLE_QUOTE_MAPPING[styleIndex][0];
                    if (c == openingQuote.charAt(0)) {
                        hasOpeningQuote = true;
                        break;
                    }
                }
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    break;
                }
                if (c == '"' || c == '\u201E' || c == '\u201C' || c == '\u00AB' || c == '\u00BB') {
                    break;
                }
            }
            return !hasOpeningQuote;
        }

        return false;
    }

    private static boolean determineQuotationState(String content, int position, boolean isSingleQuote, int styleIndex) {
        int quoteCount = 0;
        for (int i = 0; i < position; i++) {
            char c = content.charAt(i);
            if (isSingleQuote) {
                if (styleIndex >= 0 && styleIndex < SINGLE_QUOTE_MAPPING.length) {
                    String openingQuote = SINGLE_QUOTE_MAPPING[styleIndex][0];
                    String closingQuote = SINGLE_QUOTE_MAPPING[styleIndex][1];
                    if (c == openingQuote.charAt(0) || c == closingQuote.charAt(0)
                            || (styleIndex == 2 && c == '\'')) {
                        quoteCount++;
                    }
                }
            } else if (styleIndex >= 0 && styleIndex < QUOTE_MAPPING.length) {
                String openingQuote = QUOTE_MAPPING[styleIndex][0];
                String closingQuote = QUOTE_MAPPING[styleIndex][1];
                if (c == openingQuote.charAt(0) || c == closingQuote.charAt(0)
                        || (styleIndex == 2 && c == '"')) {
                    quoteCount++;
                }
            }
        }
        return quoteCount % 2 == 1;
    }

    private static List<int[]> findDialogBlocks(String[] paragraphs) {
        List<int[]> dialogBlocks = new ArrayList<>();
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (paragraph.isEmpty()) {
                continue;
            }

            int doubleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (isDoubleQuoteChar(c)) {
                    doubleQuotes++;
                }
            }

            if (doubleQuotes > 1 && doubleQuotes % 2 != 0) {
                continue;
            }

            if (doubleQuotes == 1) {
                for (int j = i + 1; j < paragraphs.length; j++) {
                    String nextParagraph = paragraphs[j].trim();
                    if (nextParagraph.isEmpty()) {
                        continue;
                    }

                    int nextDoubleQuotes = 0;
                    for (char c : nextParagraph.toCharArray()) {
                        if (isDoubleQuoteChar(c)) {
                            nextDoubleQuotes++;
                        }
                    }

                    if (nextDoubleQuotes > 0) {
                        if (nextDoubleQuotes > 1) {
                            break;
                        }
                        if (nextDoubleQuotes == 1 && endsWithQuote(nextParagraph)) {
                            dialogBlocks.add(new int[]{i, j});
                        }
                        break;
                    }
                }
            }
        }
        return dialogBlocks;
    }

    private static boolean isPartOfDialogBlock(int paragraphIndex, List<int[]> dialogBlocks) {
        for (int[] block : dialogBlocks) {
            if (paragraphIndex >= block[0] && paragraphIndex <= block[1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithQuote(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        char lastChar = text.trim().charAt(text.trim().length() - 1);
        return isDoubleQuoteChar(lastChar);
    }
}
