package com.manuskript;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            
            // CSS mit ResourceManager laden - WICHTIG: editor.css NACH styles.css laden
            String stylesCssPath = ResourceManager.getCssResource("css/styles.css");
            String editorCssPath = ResourceManager.getCssResource("css/editor.css");
            if (stylesCssPath != null) {
                scene.getStylesheets().add(stylesCssPath);
            }
            if (editorCssPath != null) {
                scene.getStylesheets().add(editorCssPath);
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
        launch(args);
    }
} 