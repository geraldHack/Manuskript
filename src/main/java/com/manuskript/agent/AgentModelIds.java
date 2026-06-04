package com.manuskript.agent;

/**
 * Modell-ID für API-Aufrufe (ohne Kosten-Anzeige aus der Parameter-UI).
 */
public final class AgentModelIds {

    private AgentModelIds() {
    }

    public static String apiModelId(String displayOrId) {
        if (displayOrId == null) {
            return "";
        }
        String s = displayOrId.trim();
        int i = s.indexOf(" (");
        return i >= 0 ? s.substring(0, i).trim() : s;
    }
}
