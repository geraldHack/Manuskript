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
        
        // Theme aus Preferences laden und anwenden
        int currentTheme = preferences.getInt("main_window_theme", 0);
        stage.setFullTheme(currentTheme); // WICHTIG: setFullTheme für vollständige Theme-Anwendung
        
        logger.debug("CustomStage erstellt: '{}', Modal: {}, Theme: {}", title, modal, currentTheme);
        
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
