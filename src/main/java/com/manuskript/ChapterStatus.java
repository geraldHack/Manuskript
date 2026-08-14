package com.manuskript;

/**
 * Manueller Kapitelstatus in der rechten Haupttabelle.
 */
public enum ChapterStatus {
    IN_ARBEIT("in_arbeit", "In Arbeit", "✎", "#6c757d"),
    UEBERARBEITEN("ueberarbeiten", "Überarbeiten", "⚠", "#c62828"),
    LEKTORAT("lektorat", "Lektorat", "👓", "#e65100"),
    LEKTORIERT("lektoriert", "Lektoriert", "✓", "#1565c0"),
    FERTIG("fertig", "Fertig", "●", "#2e7d32");

    private final String id;
    private final String label;
    private final String icon;
    private final String color;

    ChapterStatus(String id, String label, String icon, String color) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }

    public String color() {
        return color;
    }

    public static ChapterStatus fromId(String value) {
        if (value == null || value.isBlank()) {
            return IN_ARBEIT;
        }
        String normalized = value.trim().toLowerCase();
        for (ChapterStatus status : values()) {
            if (status.id.equals(normalized)) {
                return status;
            }
        }
        return IN_ARBEIT;
    }
}
