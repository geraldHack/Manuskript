package com.manuskript;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Test-Controller für die Test-Stage
 * WICHTIG: Dies ist NUR ein Controller, KEINE CustomStage!
 */
public class TestStage implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(TestStage.class);
    
    @FXML private VBox mainContainer;
    @FXML private Button btnTest1;
    @FXML private Button btnTest2;
    @FXML private TextField txtTest;
    @FXML private ListView<String> listTest;
    @FXML private Button btnClose;
    
    private CustomStage stage;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupEventHandlers();
        setupUI();
        logger.info("TestStage Controller initialisiert");
    }
    
    private void setupEventHandlers() {
        btnTest1.setOnAction(e -> test1Action());
        btnTest2.setOnAction(e -> test2Action());
        btnClose.setOnAction(e -> closeStage());
    }
    
    private void setupUI() {
        // Liste mit Test-Daten füllen
        listTest.getItems().addAll("Test Item 1", "Test Item 2", "Test Item 3");
        txtTest.setPromptText("Test-Text eingeben...");
    }
    
    private void test1Action() {
        logger.info("Test 1 Button geklickt");
        txtTest.setText("Test 1 ausgeführt!");
    }
    
    private void test2Action() {
        logger.info("Test 2 Button geklickt");
        listTest.getItems().add("Neues Test Item: " + System.currentTimeMillis());
    }
    
    private void closeStage() {
        if (stage != null) {
            stage.close();
        }
    }
    
    public void setStage(CustomStage stage) {
        this.stage = stage;
    }
    
    public CustomStage getStage() {
        return stage;
    }
}
