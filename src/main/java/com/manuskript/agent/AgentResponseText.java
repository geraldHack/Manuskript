package com.manuskript.agent;

/**
 * Normalisiert Text aus KI-Antworten für Anzeige und Ersetzung im Editor.
 */
public final class AgentResponseText {

    private AgentResponseText() {
    }

    /** Wandelt {@code \\n}-Escapes in echte Zeilenumbrüche um; normalisiert CRLF. */
    public static String normalizeModelText(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replace("\\r\\n", "\n");
        normalized = normalized.replace("\\n", "\n");
        normalized = normalized.replace("\\r", "\n");
        normalized = normalized.replace("\\t", "\t");
        return normalized;
    }
}
