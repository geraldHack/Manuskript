package com.manuskript.review;

import com.manuskript.ResourceManager;

import java.io.File;

/**
 * Rolle für das NI-Lektorat (Setup: Autorenmodus / Lektorenmodus).
 */
public enum NiReviewRole {
    AUTHOR,
    LEKTOR;

    public static final String PARAMETER = "ni.lektorat.role";

    public static NiReviewRole current() {
        String raw = ResourceManager.getParameter(PARAMETER, "autor");
        if (raw != null && raw.trim().equalsIgnoreCase("lektor")) {
            return LEKTOR;
        }
        return AUTHOR;
    }

    /**
     * Nur eine importierte Lektor-Kopie (mit Snapshots) ist Lektor.
     * Das echte Autorenbuch bleibt Autor — die Setup-Rolle steuert nur die Toolbar.
     */
    public static NiReviewRole forBook(File bookDir) {
        if (NiReviewProject.hasAuthorSnapshots(bookDir)) {
            return LEKTOR;
        }
        return AUTHOR;
    }

    /**
     * Lektor-UI nur, wenn der Parameter auf Lektor steht und das Buch eine Arbeitskopie ist.
     * Autorenmodus zeigt immer Übernehmen — auch wenn Snapshots im Ordner liegen.
     */
    public static boolean isLektorEditing(File bookDir, boolean returned) {
        if (current() != LEKTOR || returned) {
            return false;
        }
        return forBook(bookDir) == LEKTOR;
    }

    public static void set(NiReviewRole role) {
        ResourceManager.saveParameter(PARAMETER, role == LEKTOR ? "lektor" : "autor");
    }

    public static String reviewerName() {
        String name = ResourceManager.getParameter("ni.lektorat.reviewer_name", "");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return current() == LEKTOR ? "Lektor" : "Autor";
    }
}
