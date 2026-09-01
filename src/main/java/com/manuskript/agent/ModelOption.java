package com.manuskript.agent;

import java.util.List;

/**
 * Modell-Eintrag für Dropdowns mit API-ID und Anzeigetext (z. B. inkl. Kosten).
 */
public final class ModelOption {
    public final String id;
    public final String displayText;
    /** Optionale Filter-Tags (z. B. OpenRouter: free, vision, tools). */
    public final List<String> tags;

    public ModelOption(String id, String displayText) {
        this(id, displayText, List.of());
    }

    public ModelOption(String id, String displayText, List<String> tags) {
        this.id = id;
        this.displayText = displayText;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
    }

    public boolean hasTag(String tag) {
        return tag != null && !tag.isBlank() && tags.contains(tag);
    }

    /** Kosten-Teil aus {@link #displayText}, leer wenn nur die ID gespeichert ist. */
    public String pricingText() {
        if (displayText == null || id == null) {
            return "";
        }
        if (displayText.equals(id)) {
            return "";
        }
        int start = displayText.indexOf(" (");
        if (start < 0 || start + 2 >= displayText.length()) {
            return "";
        }
        String inner = displayText.substring(start + 2);
        if (inner.endsWith(")")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        return inner.trim();
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
