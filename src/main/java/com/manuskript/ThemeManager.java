package com.manuskript;

import javafx.stage.Window;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zentrale Theme-Verwaltung für alle Custom-Komponenten
 * Stellt sicher, dass alle Fenster und Dialoge einheitlich thematisiert werden
 */
public class ThemeManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    
    // Theme-Definitionen: [Hintergrund, Text, Akzent, Rahmen]
    private static final String[][] THEMES = {
        {"#ffffff", "#000000", "#007bff", "#000000"}, // Weiß
        {"#f8f9fa", "#212529", "#6c757d", "#dee2e6"}, // Hellgrau
        {"#e9ecef", "#495057", "#6c757d", "#adb5bd"}, // Grau
        {"#343a40", "#ffffff", "#17a2b8", "#495057"}, // Dunkelgrau
        {"#064e3b", "#ffffff", "#10b981", "#065f46"}, // Grün
        {"#1e293b", "#ffffff", "#3b82f6", "#334155"}, // Blau
        {"#581c87", "#ffffff", "#a855f7", "#7c3aed"}, // Lila
        {"#7c2d12", "#ffffff", "#f97316", "#ea580c"}, // Orange
        {"#dc2626", "#ffffff", "#ef4444", "#b91c1c"}, // Rot
        {"#000000", "#ffffff", "#ffffff", "#333333"}  // Schwarz
    };
    
    private static int currentThemeIndex = 0;
    
    /**
     * Setzt das aktuelle Theme für alle Custom-Komponenten
     */
    public static void setTheme(int themeIndex) {
        if (themeIndex >= 0 && themeIndex < THEMES.length) {
            currentThemeIndex = themeIndex;
            logger.info("Theme auf Index {} gesetzt", themeIndex);
        } else {
            logger.warn("Ungültiger Theme-Index: {}", themeIndex);
        }
    }
    
    /**
     * Gibt den aktuellen Theme-Index zurück
     */
    public static int getCurrentThemeIndex() {
        return currentThemeIndex;
    }
    
    /**
     * Gibt die Hintergrundfarbe des aktuellen Themes zurück
     */
    public static String getBackgroundColor() {
        return THEMES[currentThemeIndex][0];
    }
    
    /**
     * Gibt die Textfarbe des aktuellen Themes zurück
     */
    public static String getTextColor() {
        return THEMES[currentThemeIndex][1];
    }
    
    /**
     * Gibt die Akzentfarbe des aktuellen Themes zurück
     */
    public static String getAccentColor() {
        return THEMES[currentThemeIndex][2];
    }
    
    /**
     * Gibt die Rahmenfarbe des aktuellen Themes zurück
     */
    public static String getBorderColor() {
        return THEMES[currentThemeIndex][3];
    }
    
    /**
     * Wendet das aktuelle Theme auf eine CustomStage an
     */
    public static void applyThemeToStage(CustomStage stage) {
        if (stage != null) {
            // Titelleiste direkt stylen
            stage.setTitleBarTheme(currentThemeIndex);
            
            // Inhalt thematisieren, falls Scene vorhanden
            if (stage.getScene() != null && stage.getScene().getRoot() != null) {
                Node root = stage.getScene().getRoot();
                
                // Alle Theme-Klassen entfernen
                root.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
                
                // Neue Theme-Klasse hinzufügen
                switch (currentThemeIndex) {
                    case 0: // Weiß
                        root.getStyleClass().add("weiss-theme");
                        break;
                    case 1: // Schwarz
                        root.getStyleClass().add("theme-dark");
                        break;
                    case 2: // Pastell
                        root.getStyleClass().add("pastell-theme");
                        break;
                    case 3: // Blau
                        root.getStyleClass().addAll("theme-dark", "blau-theme");
                        break;
                    case 4: // Grün
                        root.getStyleClass().addAll("theme-dark", "gruen-theme");
                        break;
                    case 5: // Lila
                        root.getStyleClass().addAll("theme-dark", "lila-theme");
                        break;
                    default:
                        root.getStyleClass().add("theme-dark");
                        break;
                }
            }
            
            logger.debug("Theme auf CustomStage angewendet: Index={}", currentThemeIndex);
        }
    }
    
    /**
     * Wendet das aktuelle Theme auf eine CustomAlert an
     */
    public static void applyThemeToAlert(CustomAlert alert) {
        if (alert != null) {
            alert.applyTheme(currentThemeIndex);
            logger.debug("Theme auf CustomAlert angewendet: Index={}", currentThemeIndex);
        }
    }
    
    /**
     * Wendet das aktuelle Theme auf alle Custom-Komponenten an
     */
    public static void applyThemeToAll() {
        // Alle aktiven CustomStages thematisieren
        for (Window window : Window.getWindows()) {
            if (window instanceof CustomStage && window.isShowing()) {
                applyThemeToStage((CustomStage) window);
            }
        }
        
        logger.info("Theme auf alle Custom-Komponenten angewendet: Index={}", currentThemeIndex);
    }
    
    /**
     * Gibt alle verfügbaren Themes zurück
     */
    public static String[][] getAllThemes() {
        return THEMES;
    }
    
    /**
     * Gibt die Anzahl der verfügbaren Themes zurück
     */
    public static int getThemeCount() {
        return THEMES.length;
    }
}
