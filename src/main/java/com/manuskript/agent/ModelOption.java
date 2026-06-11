package com.manuskript.agent;

/**
 * Modell-Eintrag für Dropdowns mit API-ID und Anzeigetext (z. B. inkl. Kosten).
 */
public final class ModelOption {
    public final String id;
    public final String displayText;

    public ModelOption(String id, String displayText) {
        this.id = id;
        this.displayText = displayText;
    }

    /** Entfernt Kosten-Suffix „ (…)" aus Anzeigetext oder Editor-Eingabe. */
    public static String stripIdFromDisplay(String text) {
        if (text == null) {
            return "";
        }
        String s = text.trim();
        int i = s.indexOf(" (");
        return i >= 0 ? s.substring(0, i).trim() : s;
    }
}
