package com.manuskript;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.manuskript.CustomStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;

import java.io.File;
import java.io.IOException;

public class Main extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    @Override
    public void start(Stage primaryStage) {
        try {
            // CustomStage verwenden statt normaler Stage
            CustomStage customStage = new CustomStage();
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            
            MainController controller = loader.getController();
            controller.setPrimaryStage(customStage); // CustomStage übergeben
            
            Scene scene = new Scene(root, 1200, 800);
            
            // Config-Ordner initialisieren (muss vor allen Ressourcen-Aufrufen passieren)
            ResourceManager.initializeConfigDirectory();
            
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            // CustomStage konfigurieren
            customStage.setCustomTitle("Manuskript - DOCX Verarbeitung");
            customStage.setMinWidth(800);
            customStage.setMinHeight(600);
            
            // Scene mit benutzerdefinierter Titelleiste setzen
            customStage.setSceneWithTitleBar(scene);
            
            
            // WICHTIG: Kein setOnCloseRequest hier - wird in MainController.setPrimaryStage() behandelt
            customStage.show();
            
            logger.info("Anwendung erfolgreich mit CustomStage gestartet");
            
        } catch (IOException e) {
            logger.error("Fehler beim Laden der FXML-Datei", e);
        }
    }
    
    public static void main(String[] args) {
        // macOS-spezifische JavaFX-Einstellungen
        setupMacOSCompatibility();
        
        // Logback-Konfiguration aus config/logback.xml laden
        setupLogging();
        
        launch(args);
    }
    
    private static void setupMacOSCompatibility() {
        // Plattformübergreifende System-Properties für besseres Rendering
        System.setProperty("prism.lcdtext", "false"); // Deaktiviert LCD-Subpixel-Rendering
        System.setProperty("prism.text", "t2k"); // Verwendet T2K-Text-Rendering
        System.setProperty("javafx.platform", "desktop");
        
        // Plattform-spezifische Einstellungen
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) {
            // macOS-spezifische Einstellungen
            System.setProperty("apple.awt.application.name", "Manuskript");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.out.println("macOS-Kompatibilitäts-Einstellungen geladen");
        } else if (osName.contains("windows")) {
            // Windows-spezifische Einstellungen
            System.setProperty("prism.order", "d3d,sw"); // Direkt3D mit Software-Fallback
            System.out.println("Windows-Kompatibilitäts-Einstellungen geladen");
        } else {
            // Linux/andere Plattformen
            System.out.println("Linux/andere Plattform-Kompatibilitäts-Einstellungen geladen");
        }
        
        // Deaktiviert Hardware-Beschleunigung falls Probleme auftreten
        // System.setProperty("prism.order", "sw");
    }
    
    private static void setupLogging() {
        try {
            // Config-Ordner initialisieren
            ResourceManager.initializeConfigDirectory();
            
            // Logback-Konfiguration aus config/logback.xml laden
            File configFile = new File("config/logback.xml");
            if (configFile.exists()) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(context);
                context.reset();
                configurator.doConfigure(configFile);
                StatusPrinter.printInCaseOfErrorsOrWarnings(context);
                System.out.println("Logging-Konfiguration aus config/logback.xml geladen");
            } else {
                System.out.println("config/logback.xml nicht gefunden, verwende Standard-Konfiguration");
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Laden der Logging-Konfiguration: " + e.getMessage());
        }
    }
} 