package com.manuskript;

import com.manuskript.agent.AgentFontSizeSupport;
import com.manuskript.plugin.PluginHostThemes;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;

/**
 * Theme- und Schrift-Hilfen für Editor-Dialoge und Kontextmenüs (Canvas- und Legacy-Editor).
 */
public final class EditorDialogThemes {

    private EditorDialogThemes() {
    }

    public static String color(int themeIndex, int colorIndex) {
        return PluginHostThemes.color(themeIndex, colorIndex);
    }

    /**
     * Schriftgröße/-art vom Kapitel-Editor (Haupttext) auf Dialog-/Nebenfenster-Inhalt übertragen.
     */
    public static void applyEditorFont(Node root, ChapterEditorHost host) {
        if (host == null) {
            return;
        }
        applyEditorFont(root, host.getEditorFontSizePx(), host.getEditorFontFamily());
    }

    public static void applyEditorFont(Node root, int fontSizePx, String fontFamily) {
        AgentFontSizeSupport.applyEditorFont(root, fontSizePx, fontFamily, null);
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
