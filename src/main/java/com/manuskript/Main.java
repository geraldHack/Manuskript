package com.manuskript;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.bridge.SLF4JBridgeHandler;

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
        } else if (osName.contains("windows")) {
            // Windows-spezifische Einstellungen
            System.setProperty("prism.order", "d3d,sw"); // Direkt3D mit Software-Fallback
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

                // WICHTIG: Erst logback.xml laden, dann JUL-Bridge installieren
                // Sonst werden Meldungen übersprungen
                context.reset();
                configurator.doConfigure(configFile);
                
                // Bridge java.util.logging (JUL) zu SLF4J/Logback
                // WICHTIG: Nach logback.xml, damit TurboFilter bereits geladen ist
                java.util.logging.LogManager.getLogManager().reset();
                
                // Blockiere org.docx4j Logger auch auf JUL-Ebene
                java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("org.docx4j");
                julLogger.setLevel(java.util.logging.Level.OFF);
                julLogger.setUseParentHandlers(false);
                
                // Alle org.docx4j Sub-Logger blockieren
                String[] docx4jLoggers = {
                    "org.docx4j", 
                    "org.docx4j.XmlUtils", 
                    "org.docx4j.utils",
                    "org.docx4j.utils.ResourceUtils",
                    "org.docx4j.Docx4jProperties", 
                    "org.docx4j.jaxb",
                    "org.docx4j.jaxb.Context",
                    "org.docx4j.openpackaging",
                    "org.docx4j.openpackaging.io3",
                    "org.docx4j.openpackaging.io3.Load3",
                    "org.docx4j.openpackaging.contenttype",
                    "org.docx4j.openpackaging.contenttype.ContentTypeManager"
                };
                for (String loggerName : docx4jLoggers) {
                    java.util.logging.Logger l = java.util.logging.Logger.getLogger(loggerName);
                    l.setLevel(java.util.logging.Level.OFF);
                    l.setUseParentHandlers(false);
                }
                
                SLF4JBridgeHandler.removeHandlersForRootLogger();
                SLF4JBridgeHandler.install();
                
                StatusPrinter.printInCaseOfErrorsOrWarnings(context);
            } 
        } catch (Exception e) {
            System.err.println("Fehler beim Laden der Logging-Konfiguration: " + e.getMessage());
        }
    }
} 