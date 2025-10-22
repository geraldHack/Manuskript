package com.manuskript;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.scene.Scene;
import javafx.geometry.Rectangle2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.prefs.Preferences;

/**
 * Zentraler Manager für alle Stage-Instanzen im Programm
 * Sorgt für einheitliches CustomStage-Design und Theme-Synchronisation
 */
public class StageManager {
    
    private static final Logger logger = LoggerFactory.getLogger(StageManager.class);
    private static Preferences preferences = Preferences.userNodeForPackage(StageManager.class);
    
    /**
     * Erstellt eine neue CustomStage mit Standard-Konfiguration
     */
    public static CustomStage createStage() {
        return createStage(null, null, false);
    }
    
    /**
     * Erstellt eine neue CustomStage mit Titel
     */
    public static CustomStage createStage(String title) {
        return createStage(title, null, false);
    }
    
    /**
     * Erstellt eine neue CustomStage als Modal-Dialog
     */
    public static CustomStage createModalStage(String title, Window owner) {
        return createStage(title, owner, true);
    }
    
    /**
     * Erstellt eine neue CustomStage speziell für Export-Dialoge mit kleinerer Größe
     */
    public static CustomStage createExportStage(String title, Window owner) {
        CustomStage stage = new CustomStage();
        
        // Titel setzen
        if (title != null && !title.trim().isEmpty()) {
            stage.setCustomTitle(title);
        }
        
        // Modal-Verhalten
        if (owner != null) {
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(owner);
        }
        
        // Export-Dialog spezifische Größen
        stage.setMinWidth(700);
        stage.setMinHeight(500);
        stage.setWidth(800);
        stage.setHeight(600);
        stage.setMaxWidth(1000);
        stage.setMaxHeight(800);
        
        // Theme aus Preferences laden und anwenden
        int currentTheme = preferences.getInt("main_window_theme", 0);
        stage.setFullTheme(currentTheme);
        
        return stage;
    }
    
    /**
     * Erstellt eine neue CustomStage speziell für Diff-Fenster mit größerer Standardbreite
     */
    public static CustomStage createDiffStage(String title, Window owner) {
        CustomStage stage = new CustomStage();
        // Markiere als Diff-Fenster für gezielte Diagnose/Styling
        stage.getProperties().put("isDiffStage", true);
        
        
        // Titel setzen
        if (title != null && !title.trim().isEmpty()) {
            stage.setCustomTitle(title);
        }
        
        // Modal-Verhalten
        if (owner != null) {
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
        }
        
        // Diff-Fenster: Größere Standardgröße für bessere Übersicht
        stage.setWidth(1600);  // Breiter als normale Editor-Fenster
        stage.setHeight(900);
        stage.setMinWidth(1200);
        stage.setMinHeight(700);
        
        // Theme aus Preferences laden und anwenden
        int currentTheme = preferences.getInt("main_window_theme", 0);
        stage.setFullTheme(currentTheme);
        
        // Diff-Fenster Preferences laden und anwenden
        loadDiffWindowProperties(stage);
        
        
        
        return stage;
    }
    
    /**
     * Erstellt eine neue CustomStage mit allen Optionen
     */
    public static CustomStage createStage(String title, Window owner, boolean modal) {
        CustomStage stage = new CustomStage();
        
        // Titel setzen
        if (title != null && !title.trim().isEmpty()) {
            stage.setCustomTitle(title);
        }
        
        // Modal-Verhalten
        if (modal && owner != null) {
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(owner);
        }
        
        // WICHTIG: Editor-Fenster-Preferences werden in EditorWindow.loadWindowProperties() geladen
        
        // Theme aus Preferences laden und anwenden
        int currentTheme = preferences.getInt("main_window_theme", 0);
        stage.setFullTheme(currentTheme); // WICHTIG: setFullTheme für vollständige Theme-Anwendung
        
        // DEBUG: Zusätzlich setTitleBarTheme aufrufen für Border
        stage.setTitleBarTheme(currentTheme);
        
        
        return stage;
    }
    
    /**
     * Wendet das Theme auf alle offenen CustomStages an
     */
    public static void applyThemeToAllStages(int themeIndex) {
        preferences.putInt("main_window_theme", themeIndex);
        
        // Alle offenen Fenster durchgehen und Theme anwenden
        for (Window window : Window.getWindows()) {
            if (window instanceof CustomStage && window.isShowing()) {
                CustomStage customStage = (CustomStage) window;
                customStage.setFullTheme(themeIndex); // WICHTIG: setFullTheme statt setTitleBarTheme
            }
        }
        
    }
    
    /**
     * Lädt und wendet Diff-Fenster Preferences mit Multi-Monitor-Validierung an
     */
    private static void loadDiffWindowProperties(CustomStage stage) {
        if (preferences != null) {
            // Verwende die neue Multi-Monitor-Validierung
            Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                preferences, "diff_window", 1600.0, 900.0);
            
            // Wende die validierten Eigenschaften an
            PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, windowBounds);
            
            
            // Fenster-Position und Größe speichern (nur wenn gültig)
            stage.xProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 0 && newVal.doubleValue() <= 3000) {
                    preferences.putDouble("diff_window_x", newVal.doubleValue());
                }
            });
            stage.yProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 0 && newVal.doubleValue() <= 2000) {
                    preferences.putDouble("diff_window_y", newVal.doubleValue());
                }
            });
            stage.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1200 && newVal.doubleValue() <= 2500) {
                    preferences.putDouble("diff_window_width", newVal.doubleValue());
                }
            });
            stage.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 700 && newVal.doubleValue() <= 1500) {
                    preferences.putDouble("diff_window_height", newVal.doubleValue());
                }
            });
        }
    }
    
    
    /**
     * Erstellt eine CustomStage und setzt Scene mit CSS-Styling
     */
    public static CustomStage createStageWithScene(String title, Scene scene) {
        CustomStage stage = createStage(title);
        
        // CSS-Ressourcen laden
        try {
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der CSS-Ressourcen", e);
        }
        
        // Scene mit Titelleiste setzen
        stage.setSceneWithTitleBar(scene);
        
        // WICHTIG: Theme NACH dem Setzen der Scene anwenden!
        int currentTheme = preferences.getInt("main_window_theme", 0);
        stage.setFullTheme(currentTheme);
        
        return stage;
    }
    
    /**
     * Hilfsmethode für das Ersetzen von bestehenden Stage-Instanzen
     */
    public static CustomStage replaceStage(Stage oldStage, String title) {
        CustomStage newStage = createStage(title);
        
        // Eigenschaften von der alten Stage übernehmen
        if (oldStage != null) {
            newStage.setWidth(oldStage.getWidth());
            newStage.setHeight(oldStage.getHeight());
            newStage.setX(oldStage.getX());
            newStage.setY(oldStage.getY());
            
            if (oldStage.getScene() != null) {
                newStage.setSceneWithTitleBar(oldStage.getScene());
            }
        }
        
        return newStage;
    }
}
