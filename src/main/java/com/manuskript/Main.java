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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            
            MainController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            
            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            primaryStage.setTitle("Manuskript - DOCX Verarbeitung");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            // Speichern der Dateiauswahl beim Beenden
            primaryStage.setOnCloseRequest(event -> {
                String dir = controller.getTxtDirectoryPath().getText();
                if (dir != null && !dir.isEmpty()) {
                    controller.saveSelection(new java.io.File(dir));
                }
            });
            primaryStage.show();
            
            logger.info("Anwendung erfolgreich gestartet");
            
        } catch (IOException e) {
            logger.error("Fehler beim Laden der FXML-Datei", e);
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
} 