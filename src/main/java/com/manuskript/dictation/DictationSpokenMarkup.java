package com.manuskript.dictation;

import com.manuskript.QuotationMarkSupport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nachbearbeitung von Diktat-Text: gesprochene Befehle und LLM-Anführungszeichen
 * in den gewählten Editor-Stil bringen.
 */
final class DictationSpokenMarkup {

    private static final String QUOTE_CHARS = "[\u201E\u201C\u201D\u201A\u2018\u2019\"'«»‹›]";

    private static final String QUOTE_CMD = "anf(?:ue|[uü])hrungszeichen";

    private static final Pattern OPEN_QUOTE_COMMAND = Pattern.compile(
            "(?iu)(?:" + QUOTE_CHARS + "\\s*)?"
                    + "(?:" + QUOTE_CMD + "\\s+unten|" + QUOTE_CMD + "\\s+auf|"
                    + "öffnende\\s+" + QUOTE_CMD + "|" + QUOTE_CMD + "\\s+öffnen|"
                    + QUOTE_CMD + "\\s+open)"
                    + "(?:\\s*" + QUOTE_CHARS + ")?");

    private static final Pattern CLOSE_QUOTE_COMMAND = Pattern.compile(
            "(?iu)(?:" + QUOTE_CHARS + "\\s*)?"
                    + "(?:" + QUOTE_CMD + "\\s+oben|" + QUOTE_CMD + "\\s+zu|"
                    + "schließende\\s+" + QUOTE_CMD + "|" + QUOTE_CMD + "\\s+schließen|"
                    + QUOTE_CMD + "\\s+close)"
                    + "(?:\\s*" + QUOTE_CHARS + ")?");

    private static final Pattern SMART_QUOTE_COMMAND = Pattern.compile(
            "(?iu)(?:" + QUOTE_CHARS + "\\s*)?"
                    + QUOTE_CMD
                    + "(?!\\s+(?:unten|auf|oben|zu|öffnen|schließen|öffnend|schließend|open|close))"
                    + "(?:\\s*" + QUOTE_CHARS + ")?");

    private DictationSpokenMarkup() {
    }

    /**
     * Einzige Nachbearbeitung nach LLM/STT: Befehle auflösen, dann Stil vereinheitlichen.
     *
     * @param generatedText      vom LLM erzeugter Text (wird eingefügt)
     * @param textBeforeInsert   Editor-Text vor dem Cursor (für offen/schließen)
     * @param quoteStyleIndex    Toolbar-Einstellung
     */
    static String finish(String generatedText, String textBeforeInsert, int quoteStyleIndex) {
        if (generatedText == null || generatedText.isBlank()) {
            return generatedText != null ? generatedText : "";
        }
        // 1. LLM-Text: alle Anführungszeichen in den Zielstil (Paare per Regex)
        String normalized = QuotationMarkSupport.convertTextToStyle(generatedText, quoteStyleIndex);
        // 2. Gesprochene Befehle mit Editor-Kontext (einzelne öffnen/schließen)
        String resolved = resolveSpokenQuoteCommands(normalized, textBeforeInsert, quoteStyleIndex);
        return trimSpacesAroundQuotes(resolved, quoteStyleIndex);
    }

    private static String resolveSpokenQuoteCommands(
            String text, String textBeforeInsert, int quoteStyleIndex) {
        String openQuote = openingQuote(quoteStyleIndex);
        String closeQuote = closingQuote(quoteStyleIndex);
        StringBuilder context = new StringBuilder(textBeforeInsert != null ? textBeforeInsert : "");
        String afterOpen = replaceWithContext(OPEN_QUOTE_COMMAND, text, context, openQuote);
        String afterClose = replaceWithContext(CLOSE_QUOTE_COMMAND, afterOpen, context, closeQuote);
        return replaceWithSmartQuotes(SMART_QUOTE_COMMAND, afterClose, context, quoteStyleIndex);
    }

    private static String replaceWithContext(
            Pattern pattern, String text, StringBuilder context, String replacement) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            context.append(text, lastEnd, matcher.start());
            result.append(replacement);
            context.append(replacement);
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private static String replaceWithSmartQuotes(
            Pattern pattern, String text, StringBuilder context, int quoteStyleIndex) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            context.append(text, lastEnd, matcher.start());
            String quote = QuotationMarkSupport.resolveTypedQuote(
                    context.toString(), context.length(), "\"", quoteStyleIndex);
            result.append(quote);
            context.append(quote);
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private static String openingQuote(int quoteStyleIndex) {
        return QuotationMarkSupport.resolveTypedQuote("", 0, "\"", quoteStyleIndex);
    }

    private static String closingQuote(int quoteStyleIndex) {
        String open = openingQuote(quoteStyleIndex);
        return QuotationMarkSupport.resolveTypedQuote(open, open.length(), "\"", quoteStyleIndex);
    }

    private static String trimSpacesAroundQuotes(String text, int quoteStyleIndex) {
        String open = openingQuote(quoteStyleIndex);
        String close = closingQuote(quoteStyleIndex);
        if (open.equals(close)) {
            return trimSameCharQuoteSpaces(text, open);
        }
        String result = text;
        result = result.replaceAll(Pattern.quote(open) + "\\s+", Matcher.quoteReplacement(open));
        result = result.replaceAll("\\s+" + Pattern.quote(close), Matcher.quoteReplacement(close));
        result = result.replaceAll("(?<=\\S)" + Pattern.quote(open), " " + open);
        return result;
    }

    private static String trimSameCharQuoteSpaces(String text, String quote) {
        StringBuilder result = new StringBuilder();
        int quoteCount = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith(quote, i)) {
                if (quoteCount % 2 == 0) {
                    result.append(quote);
                    i += quote.length();
                    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                        i++;
                    }
                } else {
                    while (result.length() > 0 && Character.isWhitespace(result.charAt(result.length() - 1))) {
                        result.deleteCharAt(result.length() - 1);
                    }
                    result.append(quote);
                    i += quote.length();
                }
                quoteCount++;
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    static String spokenMarkupHint() {
        return "„Anführungszeichen“ → passend zum gewählten Anführungszeichen-Stil in der Toolbar";
    }
}
