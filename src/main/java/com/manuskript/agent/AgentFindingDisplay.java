package com.manuskript.agent;

import java.util.regex.Pattern;

/**
 * Anzeige-Hilfen für Agenten-Fundstellen (ohne Parsing-Daten zu verändern).
 */
public final class AgentFindingDisplay {

    private static final Pattern INDEX_FIELD = Pattern.compile("(?i)\\s*INDEX:\\s*[^<\"\\n]+");
    private static final int QUOTE_PREVIEW_EDGE_CHARS = 120;

    private AgentFindingDisplay() {
    }

    /** Entfernt {@code INDEX: …} aus sichtbarem Agenten-Text. */
    public static String stripIndexField(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return INDEX_FIELD.matcher(text).replaceAll("").trim();
    }

    /** Kürzt lange Zitate in der UI: Anfang … Ende. */
    public static String formatQuotePreview(String quote) {
        if (quote == null || quote.isBlank()) {
            return "";
        }
        if (SelectionRevisionSupport.isMarkedQuotePlaceholder(quote)) {
            return "(Markierung)";
        }
        String normalized = quote.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() <= QUOTE_PREVIEW_EDGE_CHARS * 2 + 24) {
            return normalized;
        }
        String start = normalized.substring(0, QUOTE_PREVIEW_EDGE_CHARS).stripTrailing();
        String end = normalized.substring(normalized.length() - QUOTE_PREVIEW_EDGE_CHARS).stripLeading();
        return start + " … " + end;
    }
}
