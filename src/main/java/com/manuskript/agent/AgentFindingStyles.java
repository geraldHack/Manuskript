package com.manuskript.agent;

import java.util.prefs.Preferences;

import com.manuskript.MainController;

/**
 * Farben für Plothole-Fundkarten (Zitat-Link, Problemtext).
 * Liest das Hauptfenster-Theme aus Preferences (nicht ResourceManager-Parameter).
 */
public final class AgentFindingStyles {

    private AgentFindingStyles() {
    }

    public static int themeIndex() {
        return Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
    }

    public static boolean isDarkTheme(int themeIndex) {
        return themeIndex == 1 || themeIndex == 3 || themeIndex == 4 || themeIndex == 5;
    }

    /** Auffällige Linkfarbe für klickbare Manuskript-Zitate im Agenten-Panel. */
    public static String quoteFillColor(int themeIndex) {
        return switch (themeIndex) {
            case 0 -> "#c62828";
            case 1 -> "#ffab40";
            case 2 -> "#6a1b9a";
            case 3 -> "#4fc3f7";
            case 4 -> "#ffe082";
            case 5 -> "#ffab40";
            default -> "#e65100";
        };
    }

    public static String quoteFillColor() {
        return quoteFillColor(themeIndex());
    }

    public static String problemFillColor(int themeIndex) {
        return isDarkTheme(themeIndex) ? "#e8e8e8" : "#1a1a1a";
    }

    public static String problemFillColor() {
        return problemFillColor(themeIndex());
    }

    public static String quoteTextStyle(int fontSizePx) {
        return quoteTextStyle(fontSizePx, null);
    }

    public static String quoteTextStyle(int fontSizePx, String fontFamily) {
        String familyCss = fontFamilyCss(fontFamily);
        return String.format("-fx-fill: %s; -fx-font-size: %dpx; -fx-underline: true;%s",
                quoteFillColor(), fontSizePx, familyCss);
    }

    public static String problemTextStyle(int fontSizePx) {
        return problemTextStyle(fontSizePx, null);
    }

    public static String problemTextStyle(int fontSizePx, String fontFamily) {
        String familyCss = fontFamilyCss(fontFamily);
        return String.format("-fx-fill: %s; -fx-font-size: %dpx;%s", problemFillColor(), fontSizePx, familyCss);
    }

    public static String suggestionTextStyle(int fontSizePx, String fontFamily, String fillColor) {
        String familyCss = fontFamilyCss(fontFamily);
        if (fillColor != null && !fillColor.isBlank()) {
            return String.format("-fx-fill: %s; -fx-font-size: %dpx;%s", fillColor, fontSizePx, familyCss);
        }
        return String.format("-fx-font-size: %dpx;%s", fontSizePx, familyCss);
    }

    private static String fontFamilyCss(String fontFamily) {
        String css = AgentFontSizeSupport.cssFontFamily(fontFamily);
        return css != null ? " -fx-font-family: " + css + ";" : "";
    }
}
