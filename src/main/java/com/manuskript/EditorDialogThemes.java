package com.manuskript;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;

/**
 * Theme-Hilfen für Editor-Dialoge und Kontextmenüs (Canvas- und Legacy-Editor).
 */
public final class EditorDialogThemes {

    private static final String[][] THEMES = {
            {"#ffffff", "#000000", "#f8f9fa", "#e9ecef"},
            {"#1a1a1a", "#ffffff", "#2d2d2d", "#404040"},
            {"#f3e5f5", "#000000", "#e1bee7", "#ce93d8"},
            {"#1e3a8a", "#ffffff", "#3b82f6", "#60a5fa"},
            {"#064e3b", "#ffffff", "#059669", "#10b981"},
            {"#581c87", "#ffffff", "#7c3aed", "#a855f7"}
    };

    private EditorDialogThemes() {
    }

    public static String color(int themeIndex, int colorIndex) {
        int theme = Math.max(0, Math.min(THEMES.length - 1, themeIndex));
        int idx = Math.max(0, Math.min(THEMES[theme].length - 1, colorIndex));
        return THEMES[theme][idx];
    }

    public static void applyToNode(Node node, int themeIndex) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme",
                "weiss-theme", "pastell-theme");
        switch (Math.max(0, Math.min(5, themeIndex))) {
            case 0 -> node.getStyleClass().add("weiss-theme");
            case 1 -> node.getStyleClass().add("theme-dark");
            case 2 -> node.getStyleClass().add("pastell-theme");
            case 3 -> node.getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> node.getStyleClass().addAll("theme-dark", "gruen-theme");
            default -> node.getStyleClass().addAll("theme-dark", "lila-theme");
        }
    }

    public static void styleContextMenu(ContextMenu contextMenu, int themeIndex) {
        if (contextMenu == null) {
            return;
        }
        contextMenu.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme",
                "blau-theme", "gruen-theme", "lila-theme");
        switch (Math.max(0, Math.min(5, themeIndex))) {
            case 0 -> contextMenu.getStyleClass().add("weiss-theme");
            case 1 -> contextMenu.getStyleClass().add("theme-dark");
            case 2 -> contextMenu.getStyleClass().add("pastell-theme");
            case 3 -> contextMenu.getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> contextMenu.getStyleClass().addAll("theme-dark", "gruen-theme");
            default -> contextMenu.getStyleClass().addAll("theme-dark", "lila-theme");
        }
    }
}
