package com.manuskript.plugin;

/**
 * Farbpalette für Plugin-Fenster — identisch zu {@code EditorDialogThemes} in der Haupt-App.
 */
public final class PluginHostThemes {

    private static final String[][] THEMES = {
            {"#ffffff", "#000000", "#f8f9fa", "#e9ecef"},
            {"#1a1a1a", "#ffffff", "#2d2d2d", "#404040"},
            {"#f3e5f5", "#000000", "#e1bee7", "#ce93d8"},
            {"#1e3a8a", "#ffffff", "#3b82f6", "#60a5fa"},
            {"#064e3b", "#ffffff", "#059669", "#10b981"},
            {"#581c87", "#ffffff", "#7c3aed", "#a855f7"}
    };

    private PluginHostThemes() {
    }

    /** 0=Hintergrund, 1=Text, 2=Fläche, 3=Rahmen. */
    public static String color(int themeIndex, int colorIndex) {
        int theme = Math.max(0, Math.min(THEMES.length - 1, themeIndex));
        int idx = Math.max(0, Math.min(THEMES[theme].length - 1, colorIndex));
        return THEMES[theme][idx];
    }
}
