package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import com.manuskript.ResourceManager;

/**
 * Hilfesystem für Manuskript mit blauen Fragezeichen-Icons und WebView-Fenstern
 */
public class HelpSystem {
    private static final Logger logger = LoggerFactory.getLogger(HelpSystem.class);
    
    // Global Help-Toggle
    private static boolean helpEnabled = true;
    
    /**
     * Setzt den globalen Help-Toggle-Status
     */
    public static void setHelpEnabled(boolean enabled) {
        helpEnabled = enabled;
    }
    
    /**
     * Gibt den globalen Help-Toggle-Status zurück
     */
    public static boolean isHelpEnabled() {
        return helpEnabled;
    }
    
    /**
     * Erstellt ein blaues Fragezeichen-Icon mit Tooltip und Hilfefenster
     */
    public static Button createHelpButton(String tooltipText, String helpFileName) {
        Button helpButton = new Button("?");
        helpButton.setStyle(
            "-fx-background-color: #4A90E2 !important; " +
            "-fx-text-fill: white !important; " +
            "-fx-font-weight: bold !important; " +
            "-fx-font-size: 14px !important; " +
            "-fx-min-width: 24px !important; " +
            "-fx-min-height: 24px !important; " +
            "-fx-max-width: 24px !important; " +
            "-fx-max-height: 24px !important; " +
            "-fx-background-radius: 12px !important; " +
            "-fx-border-radius: 12px !important; " +
            "-fx-cursor: hand !important; " +
            "-fx-border: none !important; " +
            "-fx-padding: 0 !important;"
        );
        
        // Tooltip hinzufügen
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle(
            "-fx-background-color: #2C3E50; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 12px; " +
            "-fx-background-radius: 4px;"
        );
        helpButton.setTooltip(tooltip);
        
        // Hilfefenster bei Klick
        helpButton.setOnAction(e -> showHelpWindow(helpFileName));
        
        // Help-Toggle berücksichtigen
        helpButton.setVisible(helpEnabled);
        helpButton.setManaged(helpEnabled);
        
        return helpButton;
    }
    
    /**
     * Zeigt ein Testfenster mit Titelleiste und Theme-Styling
     */
    public static void showHelpWindow(String helpFileName) {
        try {
            // Erstelle CustomStage für Help-Fenster
            CustomStage helpStage = new CustomStage();
            helpStage.setCustomTitle("Hilfe - " + helpFileName);
            helpStage.setResizable(true);
            helpStage.setWidth(900);
            helpStage.setHeight(700);
            
            // WebView für HTML-Content
            WebView webView = new WebView();
            webView.setPrefSize(850, 600);
            webView.getStyleClass().add("help-webview");
            
            // Help-Content laden
            String helpContent = loadHelpContent(helpFileName);
            if (helpContent != null) {
                // Debug: Erste 200 Zeichen der HTML-Datei loggen
                String preview = helpContent.length() > 200 ? helpContent.substring(0, 200) + "..." : helpContent;
                logger.debug("Help-Content geladen (erste 200 Zeichen): {}", preview);
                webView.getEngine().loadContent(helpContent, "text/html");
            } else {
                webView.getEngine().loadContent("<h2>Hilfe nicht gefunden</h2><p>Die Hilfe-Datei '" + helpFileName + "' konnte nicht geladen werden.</p>", "text/html");
            }
            
            // Schließen-Button
            Button closeButton = new Button("Schließen");
            closeButton.getStyleClass().add("secondary-button");
            closeButton.setOnAction(e -> helpStage.close());
            
            // Layout
            VBox root = new VBox(10);
            root.setPadding(new Insets(15));
            root.getStyleClass().add("help-container");
            root.getChildren().addAll(webView, closeButton);
            
            // Scene erstellen
            Scene scene = new Scene(root);
            
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            // WICHTIG: Erst setSceneWithTitleBar, dann Theme setzen!
            helpStage.setSceneWithTitleBar(scene);
            
            // WICHTIG: Theme NACH setSceneWithTitleBar setzen!
            int currentTheme = 0;
            try {
                Preferences preferences = Preferences.userNodeForPackage(MainController.class);
                currentTheme = preferences.getInt("main_window_theme", 0);
            } catch (Exception e) {
                logger.warn("Konnte aktuelles Theme nicht laden, verwende Standard: {}", e.getMessage());
            }
            helpStage.setFullTheme(currentTheme);
            
            helpStage.show();
            
            logger.info("Help-Fenster geöffnet: {}", helpFileName);
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Help-Fensters", e);
        }
    }
    
    /**
     * Lädt Help-Content aus externer HTML-Datei
     */
    private static String loadHelpContent(String helpFileName) {
        try {
            Path helpPath = Paths.get("config/help/" + helpFileName);
            if (Files.exists(helpPath)) {
                return Files.readString(helpPath, StandardCharsets.UTF_8);
            } else {
                logger.warn("Help-Datei nicht gefunden: {}", helpPath);
                return null;
            }
        } catch (IOException e) {
            logger.error("Fehler beim Laden der Help-Datei: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Bereinigt alte temporäre Hilfe-HTML-Dateien (falls vorhanden)
     */
    public static void cleanupOldHelpFiles() {
        try {
            File helpDir = new File("config/help");
            if (!helpDir.exists()) return;
            
            long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
            
            // Nur temporäre Dateien löschen (nicht die statischen HTML-Dateien)
            File[] files = helpDir.listFiles((dir, name) -> name.startsWith("help_") && name.endsWith(".html"));
            if (files != null) {
                for (File file : files) {
                    if (file.lastModified() < oneHourAgo) {
                        if (file.delete()) {
                            logger.debug("Alte temporäre Hilfe-Datei gelöscht: {}", file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Fehler beim Bereinigen alter Hilfe-Dateien", e);
        }
    }
}
