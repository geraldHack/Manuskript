package com.manuskript.agent;

import java.util.regex.Pattern;

/**
 * Anzeige-Hilfen für Agenten-Fundstellen (ohne Parsing-Daten zu verändern).
 */
public final class AgentFindingDisplay {

    private static final Pattern INDEX_FIELD = Pattern.compile("(?i)\\s*INDEX:\\s*[^<\"\\n]+");

    private AgentFindingDisplay() {
    }

    /** Entfernt {@code INDEX: …} aus sichtbarem Agenten-Text. */
    public static String stripIndexField(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return INDEX_FIELD.matcher(text).replaceAll("").trim();
    }
}
