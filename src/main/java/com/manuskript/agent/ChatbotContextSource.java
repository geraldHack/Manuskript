package com.manuskript.agent;

/**
 * Kontext-Quellen fuer den Chatbot (Pills).
 */
public enum ChatbotContextSource {

    WORLD_EDITOR("Welt-Editor", "Alle Welt-Dateien aus dem Projektordner"),
    CURRENT_CHAPTER("Aktuelles Kapitel", "Text aus dem Editor bzw. data/*.md"),
    ALL_CHAPTERS("Alle Kapitel", "Volltext aller Kapitel in der Projektliste"),
    CHAPTERS_BEFORE("Kapitel davor", "N Kapitel vor dem aktuellen"),
    CHAPTERS_AFTER("Kapitel danach", "N Kapitel nach dem aktuellen");

    private final String label;
    private final String tooltip;

    ChatbotContextSource(String label, String tooltip) {
        this.label = label;
        this.tooltip = tooltip;
    }

    public String getLabel() {
        return label;
    }

    public String getTooltip() {
        return tooltip;
    }
}
