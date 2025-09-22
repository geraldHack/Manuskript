package com.manuskript;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.scene.Scene;
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
        
        logger.debug("Export-Stage erstellt: '{}', Größe: {}x{}", title, 600, 500);
        
        return stage;
    }
    
    /**
     * Erstellt eine neue CustomStage speziell für Diff-Fenster mit größerer Standardbreite
     */
    public static CustomStage createDiffStage(String title, Window owner) {
        CustomStage stage = new CustomStage();
        
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
        
        logger.debug("Diff-Stage erstellt: '{}', Größe: {}x{}", title, 1600, 900);
        
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
        
        // WICHTIG: Editor-Fenster-Preferences laden und anwenden
        loadEditorWindowProperties(stage);
        
        // Theme aus Preferences laden und anwenden
        int currentTheme = preferences.getInt("main_window_theme", 0);
        stage.setFullTheme(currentTheme); // WICHTIG: setFullTheme für vollständige Theme-Anwendung
        
        logger.debug("CustomStage erstellt: '{}', Modal: {}, Theme: {}, Größe: {}x{}", title, modal, currentTheme, stage.getWidth(), stage.getHeight());
        
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
        
        logger.info("Theme {} auf alle offenen CustomStages angewendet", themeIndex);
    }
    
    /**
     * Lädt und wendet Diff-Fenster Preferences an
     */
    private static void loadDiffWindowProperties(CustomStage stage) {
        if (preferences != null) {
            // Robuste Validierung der Preferences mit sinnvollen Standardwerten
            double x = preferences.getDouble("diff_window_x", 100);
            double y = preferences.getDouble("diff_window_y", 100);
            double width = preferences.getDouble("diff_window_width", 1600);
            double height = preferences.getDouble("diff_window_height", 900);
            
            // Validierung: Position muss auf dem Bildschirm sein
            if (x < 0 || x > 3000 || y < 0 || y > 2000) {
                logger.warn("Ungültige Position ({},{}) für Diff-Fenster, setze Standard 100,100", x, y);
                x = 100;
                y = 100;
            }
            
            // Validierung: Größe muss sinnvoll sein
            if (width < 1200 || width > 2500 || height < 700 || height > 1500) {
                logger.warn("Ungültige Größe ({}x{}) für Diff-Fenster, setze Standard 1600x900", width, height);
                width = 1600;
                height = 900;
            }
            
            stage.setX(x);
            stage.setY(y);
            stage.setWidth(width);
            stage.setHeight(height);
            
            logger.info("Diff-Fenster: Position {},{} Größe {}x{}", x, y, width, height);
            
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
     * Lädt und wendet Editor-Fenster Preferences an
     */
    private static void loadEditorWindowProperties(CustomStage stage) {
        if (preferences != null) {
            // Fenster-Größe und Position mit robuster Validierung laden
            double width = PreferencesManager.getEditorWidth(preferences, "editor_window_width", PreferencesManager.DEFAULT_EDITOR_WIDTH);
            double height = PreferencesManager.getEditorHeight(preferences, "editor_window_height", PreferencesManager.DEFAULT_EDITOR_HEIGHT);
            double x = PreferencesManager.getWindowPosition(preferences, "editor_window_x", -1.0);
            double y = PreferencesManager.getWindowPosition(preferences, "editor_window_y", -1.0);
            
            // Mindestgrößen für Editor-Fenster
            double minWidth = PreferencesManager.MIN_EDITOR_WIDTH;
            double minHeight = PreferencesManager.MIN_EDITOR_HEIGHT;
            
            // Mindestgröße für CustomStage setzen
            stage.setMinWidth(minWidth);
            stage.setMinHeight(minHeight);
            
            // Fenster-Größe setzen
            stage.setWidth(width);
            stage.setHeight(height);
            
            // NEU: Validierung der Fenster-Position
            // Prüfe, ob Position gültig ist und auf dem Bildschirm liegt
            if (x >= 0 && y >= 0 && !Double.isNaN(x) && !Double.isNaN(y) &&
                !Double.isInfinite(x) && !Double.isInfinite(y)) {
                
                // Grobe Prüfung: Position sollte nicht zu weit außerhalb des Bildschirms sein
                if (x < -1000 || y < -1000 || x > 5000 || y > 5000) {
                    logger.warn("Editor-Fenster-Position außerhalb des Bildschirms: x={}, y={} - verwende zentriert", x, y);
                    stage.centerOnScreen();
                } else {
                    stage.setX(x);
                    stage.setY(y);
                }
            } else {
                logger.info("Keine gültige Editor-Fenster-Position gefunden - zentriere Fenster");
                stage.centerOnScreen();
            }
            
            // Event-Handler für Fenster-Änderungen hinzufügen
            stage.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    PreferencesManager.putEditorWidth(preferences, "editor_window_width", newVal.doubleValue());
                }
            });
            
            stage.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    PreferencesManager.putEditorHeight(preferences, "editor_window_height", newVal.doubleValue());
                }
            });
            
            stage.xProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    PreferencesManager.putWindowPosition(preferences, "editor_window_x", newVal.doubleValue());
                }
            });
            
            stage.yProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    PreferencesManager.putWindowPosition(preferences, "editor_window_y", newVal.doubleValue());
                }
            });
            
            logger.info("Editor-Fenster-Eigenschaften geladen: Größe={}x{}, Position=({}, {})", width, height, x, y);
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
