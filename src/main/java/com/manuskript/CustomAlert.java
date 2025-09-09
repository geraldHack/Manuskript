package com.manuskript;

import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Eigene Alert-Klasse mit benutzerdefinierter Titelleiste
 * Wrapper um die Standard JavaFX Alert-Klasse
 */
public class CustomAlert {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomAlert.class);
    
    private Alert originalAlert;
    private CustomStage customStage;
    private HBox titleBar;
    private Label titleLabel;
    private Button minimizeBtn;
    private Button maximizeBtn;
    private Button closeBtn;
    
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;
    
    private ButtonType result = null;
    
    /**
     * Erstellt einen neuen CustomAlert
     */
    public CustomAlert(Alert.AlertType alertType) {
        this.originalAlert = new Alert(alertType);
        setupCustomAlert();
    }
    
    /**
     * Erstellt einen neuen CustomAlert mit Titel und Content
     */
    public CustomAlert(Alert.AlertType alertType, String contentText) {
        this.originalAlert = new Alert(alertType, contentText);
        setupCustomAlert();
    }
    
    /**
     * Erstellt einen neuen CustomAlert mit allen Parametern
     */
    public CustomAlert(Alert.AlertType alertType, String contentText, ButtonType... buttonTypes) {
        this.originalAlert = new Alert(alertType, contentText, buttonTypes);
        setupCustomAlert();
    }
    
    /**
     * Richtet den CustomAlert ein
     */
    private void setupCustomAlert() {
        // CustomStage erstellen
        customStage = new CustomStage();
        
        // Stage-Konfiguration
        customStage.initModality(Modality.APPLICATION_MODAL);
        customStage.setResizable(true);
        customStage.setMinWidth(400);
        customStage.setMinHeight(200);
        
        // Eigene Scene mit Titelzeile erstellen
        setupCustomScene();
        
        // Theme anwenden (aktuelles Theme verwenden, nicht immer Theme 0)
        int currentTheme = ThemeManager.getCurrentThemeIndex();
        applyTheme(currentTheme);
    }
    
    /**
     * Richtet die eigene Scene mit Titelzeile ein
     */
    private void setupCustomScene() {
        // Originale DialogPane holen
        DialogPane originalDialogPane = originalAlert.getDialogPane();
        
        // Hauptcontainer
        VBox mainContainer = new VBox();
        mainContainer.setStyle("-fx-background-color: transparent; -fx-spacing: 0;");
        
        // Titelzeile erstellen
        setupTitleBar();
        
        // Button-Handler werden später in show()/showAndWait() eingerichtet
        
        // Alles zusammenfügen
        mainContainer.getChildren().addAll(titleBar, originalDialogPane);
        VBox.setVgrow(originalDialogPane, Priority.ALWAYS);
        
        // Scene erstellen
        Scene scene = new Scene(mainContainer);
        customStage.setScene(scene);
        
        // Resize-Handles hinzufügen
        setupResizeHandles(scene);
        
        // Theme und Button-Handler nach der Anzeige anwenden
        customStage.setOnShown(event -> {
            javafx.application.Platform.runLater(() -> {
                try {
                    // Button-Handler einrichten
                    setupDialogButtonHandlers();
                    // Theme anwenden
                    applyDialogPaneTheme(getCurrentThemeIndex(), getCurrentBackgroundColor(), getCurrentTextColor());
                } catch (Exception e) {
                    // Ignoriere Fehler beim DialogPane-Styling
                }
            });
        });
    }
    
    /**
     * Richtet die Titelzeile ein
     */
    private void setupTitleBar() {
        titleBar = new HBox();
        titleBar.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, #2c3e50 0%, #34495e 100%); -fx-padding: 8px; -fx-spacing: 5px; -fx-border-color: #1a252f; -fx-border-width: 0 0 1 0;");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setMinHeight(35);
        
        // Titel-Label
        titleLabel = new Label(originalAlert.getTitle() != null ? originalAlert.getTitle() : "Alert");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0 10px; -fx-background-color: transparent;");
        
        // Spacer
        Region spacer = new Region();
        spacer.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Window-Buttons
        minimizeBtn = new Button("🗕");
        minimizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        minimizeBtn.setOnAction(e -> customStage.setIconified(true));
        
        maximizeBtn = new Button("🗗");
        maximizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        maximizeBtn.setOnAction(e -> toggleMaximize());
        
        closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        closeBtn.setOnAction(e -> {
            result = ButtonType.CANCEL;
            close();
        });
        
        // Hover-Effekte für Buttons
        setupButtonHoverEffects();
        
        // Drag-Funktionalität
        setupDragFunctionality();
        
        titleBar.getChildren().addAll(titleLabel, spacer, minimizeBtn, maximizeBtn, closeBtn);
    }
    
    /**
     * Richtet die Button-Handler für die Dialog-Buttons ein
     */
    private void setupDialogButtonHandlers() {
        // Alle Buttons im DialogPane finden und Handler hinzufügen
        for (ButtonType buttonType : originalAlert.getButtonTypes()) {
            Button button = (Button) originalAlert.getDialogPane().lookupButton(buttonType);
            if (button != null) {
                button.setOnAction(e -> {
                    result = buttonType;
                    try {
                        if (customStage != null && customStage.isShowing()) {
                            customStage.close();
                        }
                    } catch (Exception ex) {
                        // Fehler beim Schließen ignorieren
                    }
                });
            }
        }
    }
    
    /**
     * Richtet Hover-Effekte für die Window-Buttons ein
     */
    private void setupButtonHoverEffects() {
        String hoverStyle = "-fx-background-color: rgba(255, 255, 255, 0.2); -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
        String closeHoverStyle = "-fx-background-color: rgba(231, 76, 60, 0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
        
        minimizeBtn.setOnMouseEntered(e -> minimizeBtn.setStyle(hoverStyle));
        minimizeBtn.setOnMouseExited(e -> minimizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
        
        maximizeBtn.setOnMouseEntered(e -> maximizeBtn.setStyle(hoverStyle));
        maximizeBtn.setOnMouseExited(e -> maximizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
        
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(closeHoverStyle));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
    }
    
    /**
     * Richtet Drag-Funktionalität ein
     */
    private void setupDragFunctionality() {
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        
        titleBar.setOnMouseDragged(event -> {
            if (!isMaximized) {
                customStage.setX(event.getScreenX() - xOffset);
                customStage.setY(event.getScreenY() - yOffset);
            }
        });
    }
    
    /**
     * Richtet Resize-Handles ein
     */
    private void setupResizeHandles(Scene scene) {
        final int RESIZE_BORDER = 8;
        
        mouseMovedHandler = event -> {
            try {
                if (isMaximized || scene == null) {
                    scene.setCursor(javafx.scene.Cursor.DEFAULT);
                    return;
                }
                
                // Prüfe, ob wir uns in der Titelzeile befinden
                if (titleBar != null && event.getY() < titleBar.getHeight()) {
                    scene.setCursor(javafx.scene.Cursor.DEFAULT);
                    return;
                }
                
                double x = event.getSceneX();
                double y = event.getSceneY();
                double width = scene.getWidth();
                double height = scene.getHeight();
                
                if (x < RESIZE_BORDER && y < RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.NW_RESIZE);
                } else if (x > width - RESIZE_BORDER && y < RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.NE_RESIZE);
                } else if (x < RESIZE_BORDER && y > height - RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.SW_RESIZE);
                } else if (x > width - RESIZE_BORDER && y > height - RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.SE_RESIZE);
                } else if (x < RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.W_RESIZE);
                } else if (x > width - RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.E_RESIZE);
                } else if (y < RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.N_RESIZE);
                } else if (y > height - RESIZE_BORDER) {
                    scene.setCursor(javafx.scene.Cursor.S_RESIZE);
                } else {
                    scene.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            } catch (Exception e) {
                // Fehler beim Resize-Handling ignorieren
            }
        };
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        
        mousePressedHandler = event -> {
            try {
                if (isMaximized || scene == null) return;
                
                if (titleBar != null && event.getY() < titleBar.getHeight()) {
                    return;
                }
                
                double x = event.getSceneX();
                double y = event.getSceneY();
                double width = scene.getWidth();
                double height = scene.getHeight();
                
                if (x < RESIZE_BORDER || x > width - RESIZE_BORDER || 
                    y < RESIZE_BORDER || y > height - RESIZE_BORDER) {
                    startResize(event, x, y, width, height);
                    event.consume();
                }
            } catch (Exception e) {
                // Fehler beim Resize-Handling ignorieren
            }
        };
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        
        mouseDraggedHandler = event -> {
            try {
                if (isResizing) {
                    performResize(event);
                    event.consume();
                }
            } catch (Exception e) {
                // Fehler beim Resize-Handling ignorieren
            }
        };
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        
        mouseReleasedHandler = event -> {
            try {
                if (isResizing) {
                    isResizing = false;
                    if (scene != null) {
                        scene.setCursor(javafx.scene.Cursor.DEFAULT);
                    }
                    event.consume();
                }
            } catch (Exception e) {
                // Fehler beim Resize-Handling ignorieren
            }
        };
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
    }
    
    private double resizeStartX, resizeStartY, resizeStartWidth, resizeStartHeight;
    private boolean isResizing = false;
    private String resizeDirection = "";
    private int currentThemeIndex = 0;
    private String currentBackgroundColor = "";
    private String currentTextColor = "";
    
    // Event-Handler für sauberes Entfernen
    private EventHandler<MouseEvent> mouseMovedHandler;
    private EventHandler<MouseEvent> mousePressedHandler;
    private EventHandler<MouseEvent> mouseDraggedHandler;
    private EventHandler<MouseEvent> mouseReleasedHandler;

    /**
     * Startet das Resizing
     */
    private void startResize(MouseEvent event, double x, double y, double width, double height) {
        if (isMaximized) return;
        
        resizeStartX = event.getScreenX();
        resizeStartY = event.getScreenY();
        resizeStartWidth = customStage.getWidth();
        resizeStartHeight = customStage.getHeight();
        
        final int RESIZE_BORDER = 8;
        if (x < RESIZE_BORDER && y < RESIZE_BORDER) {
            resizeDirection = "NW";
        } else if (x > width - RESIZE_BORDER && y < RESIZE_BORDER) {
            resizeDirection = "NE";
        } else if (x < RESIZE_BORDER && y > height - RESIZE_BORDER) {
            resizeDirection = "SW";
        } else if (x > width - RESIZE_BORDER && y > height - RESIZE_BORDER) {
            resizeDirection = "SE";
        } else if (x < RESIZE_BORDER) {
            resizeDirection = "W";
        } else if (x > width - RESIZE_BORDER) {
            resizeDirection = "E";
        } else if (y < RESIZE_BORDER) {
            resizeDirection = "N";
        } else if (y > height - RESIZE_BORDER) {
            resizeDirection = "S";
        }
        
        isResizing = true;
    }
    
    /**
     * Führt das Resizing durch
     */
    private void performResize(MouseEvent event) {
        if (!isResizing || isMaximized) return;
        
        double deltaX = event.getScreenX() - resizeStartX;
        double deltaY = event.getScreenY() - resizeStartY;
        
        double newWidth = resizeStartWidth;
        double newHeight = resizeStartHeight;
        double newX = customStage.getX();
        double newY = customStage.getY();
        
        double minWidth = Math.max(customStage.getMinWidth(), 300.0);
        double minHeight = Math.max(customStage.getMinHeight(), 200.0);
        
        switch (resizeDirection) {
            case "SE":
                newWidth = Math.max(minWidth, resizeStartWidth + deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight + deltaY);
                break;
            case "SW":
                newWidth = Math.max(minWidth, resizeStartWidth - deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight + deltaY);
                newX = customStage.getX() + (resizeStartWidth - newWidth);
                break;
            case "NE":
                newWidth = Math.max(minWidth, resizeStartWidth + deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight - deltaY);
                newY = customStage.getY() + (resizeStartHeight - newHeight);
                break;
            case "NW":
                newWidth = Math.max(minWidth, resizeStartWidth - deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight - deltaY);
                newX = customStage.getX() + (resizeStartWidth - newWidth);
                newY = customStage.getY() + (resizeStartHeight - newHeight);
                break;
            case "E":
                newWidth = Math.max(minWidth, resizeStartWidth + deltaX);
                break;
            case "W":
                newWidth = Math.max(minWidth, resizeStartWidth - deltaX);
                newX = customStage.getX() + (resizeStartWidth - newWidth);
                break;
            case "S":
                newHeight = Math.max(minHeight, resizeStartHeight + deltaY);
                break;
            case "N":
                newHeight = Math.max(minHeight, resizeStartHeight - deltaY);
                newY = customStage.getY() + (resizeStartHeight - newHeight);
                break;
        }
        
        customStage.setX(newX);
        customStage.setY(newY);
        customStage.setWidth(newWidth);
        customStage.setHeight(newHeight);
    }
    
    /**
     * Wechselt zwischen maximiert und normal
     */
    private void toggleMaximize() {
        if (isMaximized) {
            customStage.setMaximized(false);
            isMaximized = false;
            maximizeBtn.setText("🗗");
        } else {
            customStage.setMaximized(true);
            isMaximized = true;
            maximizeBtn.setText("🗖");
        }
    }
    
    /**
     * Wendet ein Theme an
     */
    public void applyTheme(int themeIndex) {
        String backgroundColor;
        String textColor;
        String borderColor;
        
        switch (themeIndex) {
            case 0: // Weiß
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #ffffff 0%, #f8f9fa 100%)";
                textColor = "black";
                borderColor = "black";
                break;
            case 1: // Schwarz
                backgroundColor = "#1a1a1a";
                textColor = "white";
                borderColor = "white";
                break;
            case 2: // Pastell
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #f3e5f5 0%, #e1bee7 100%)";
                textColor = "black";
                borderColor = "black";
                break;
            case 3: // Blau
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #1e3a8a 0%, #3b82f6 100%)";
                textColor = "white";
                borderColor = "white";
                break;
            case 4: // Grün
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #064e3b 0%, #10b981 100%)";
                textColor = "white";
                borderColor = "white";
                break;
            case 5: // Lila
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #581c87 0%, #8b5cf6 100%)";
                textColor = "white";
                borderColor = "white";
                break;
            default:
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #2c3e50 0%, #34495e 100%)";
                textColor = "white";
                borderColor = "white";
                break;
        }
        
        // Titelzeile
        titleBar.setStyle("-fx-background-color: " + backgroundColor + "; -fx-padding: 8px; -fx-spacing: 5px; -fx-border-color: " + borderColor + "; -fx-border-width: 0 0 1 0;");
        
        // Titel-Label
        titleLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0 10px; -fx-background-color: transparent;");
        
        // Button-Styles aktualisieren
        String buttonStyle = "-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
        String closeButtonStyle = "-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
        
        minimizeBtn.setStyle(buttonStyle);
        maximizeBtn.setStyle(buttonStyle);
        closeBtn.setStyle(closeButtonStyle);
        
        currentTextColor = textColor;
        setupButtonHoverEffects();
        
        // Aktuelle Theme-Werte speichern
        currentThemeIndex = themeIndex;
        currentBackgroundColor = backgroundColor;
        currentTextColor = textColor;
        
        // CustomStage Theme anwenden
        customStage.setTitleBarTheme(themeIndex);
        
        // DialogPane Theme anwenden
        applyDialogPaneTheme(themeIndex, backgroundColor, textColor);
    }
    
    /**
     * Wendet das Theme auf die DialogPane an
     */
    private void applyDialogPaneTheme(int themeIndex, String backgroundColor, String textColor) {
        DialogPane dialogPane = originalAlert.getDialogPane();
        if (dialogPane == null) return;
        
        // Bestimme Solid-Color für DialogPane (ohne Gradient)
        String solidBackgroundColor;
        switch (themeIndex) {
            case 0: // Weiß
                solidBackgroundColor = "#ffffff";
                break;
            case 1: // Schwarz
                solidBackgroundColor = "#1a1a1a";
                break;
            case 2: // Pastell
                solidBackgroundColor = "#f3e5f5";
                break;
            case 3: // Blau
                solidBackgroundColor = "#1e3a8a";
                break;
            case 4: // Grün
                solidBackgroundColor = "#064e3b";
                break;
            case 5: // Lila
                solidBackgroundColor = "#581c87";
                break;
            default:
                solidBackgroundColor = "#2c3e50";
                break;
        }
        
        // DialogPane Hintergrund setzen
        dialogPane.setStyle("-fx-background-color: " + solidBackgroundColor + "; -fx-text-fill: " + textColor + ";");
        
        // Alle Labels in der DialogPane thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".label")) {
            if (node instanceof javafx.scene.control.Label) {
                javafx.scene.control.Label label = (javafx.scene.control.Label) node;
                label.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: transparent;");
            }
        }
        
        // Alle Buttons in der DialogPane thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".button")) {
            if (node instanceof javafx.scene.control.Button) {
                javafx.scene.control.Button button = (javafx.scene.control.Button) node;
                String buttonBgColor = (themeIndex == 0 || themeIndex == 2) ? "#e9ecef" : "#495057";
                button.setStyle("-fx-background-color: " + buttonBgColor + "; -fx-text-fill: " + textColor + "; -fx-border-color: " + textColor + "; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            }
        }
        
        // Content-Bereich thematisieren
        javafx.scene.Node content = dialogPane.lookup(".content");
        if (content != null) {
            content.setStyle("-fx-background-color: " + solidBackgroundColor + "; -fx-text-fill: " + textColor + ";");
        }
        
        // Header-Bereich thematisieren
        javafx.scene.Node header = dialogPane.lookup(".header-panel");
        if (header != null) {
            header.setStyle("-fx-background-color: " + solidBackgroundColor + "; -fx-text-fill: " + textColor + ";");
        }
        
        // Icon-Bereich thematisieren (für das ! Icon)
        try {
            javafx.scene.Node graphicContainer = dialogPane.lookup(".graphic-container");
            if (graphicContainer != null) {
                graphicContainer.setStyle("-fx-background-color: " + solidBackgroundColor + "; -fx-text-fill: " + textColor + ";");
            }
        } catch (Exception e) {
            // Ignoriere Fehler beim Icon-Styling
        }
        
        // Alle Checkboxen in der DialogPane thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".check-box")) {
            if (node instanceof javafx.scene.control.CheckBox) {
                javafx.scene.control.CheckBox checkBox = (javafx.scene.control.CheckBox) node;
                checkBox.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: transparent;");
            }
        }
        
        // Alle Checkbox-Labels thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".check-box .label")) {
            if (node instanceof javafx.scene.control.Label) {
                javafx.scene.control.Label label = (javafx.scene.control.Label) node;
                label.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: transparent;");
            }
        }
        
        // Text-Eingabefelder thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".text-field")) {
            if (node instanceof javafx.scene.control.TextField) {
                javafx.scene.control.TextField textField = (javafx.scene.control.TextField) node;
                textField.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // TextArea thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".text-area")) {
            if (node instanceof javafx.scene.control.TextArea) {
                javafx.scene.control.TextArea textArea = (javafx.scene.control.TextArea) node;
                textArea.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // PasswordField thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".password-field")) {
            if (node instanceof javafx.scene.control.PasswordField) {
                javafx.scene.control.PasswordField passwordField = (javafx.scene.control.PasswordField) node;
                passwordField.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // ComboBox thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".combo-box")) {
            if (node instanceof javafx.scene.control.ComboBox) {
                javafx.scene.control.ComboBox<?> comboBox = (javafx.scene.control.ComboBox<?>) node;
                comboBox.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // ListView thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".list-view")) {
            if (node instanceof javafx.scene.control.ListView) {
                javafx.scene.control.ListView<?> listView = (javafx.scene.control.ListView<?>) node;
                listView.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // TableView thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".table-view")) {
            if (node instanceof javafx.scene.control.TableView) {
                javafx.scene.control.TableView<?> tableView = (javafx.scene.control.TableView<?>) node;
                tableView.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // Slider thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".slider")) {
            if (node instanceof javafx.scene.control.Slider) {
                javafx.scene.control.Slider slider = (javafx.scene.control.Slider) node;
                slider.setStyle("-fx-text-fill: " + textColor + ";");
            }
        }
        
        // ScrollPane thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".scroll-pane")) {
            if (node instanceof javafx.scene.control.ScrollPane) {
                javafx.scene.control.ScrollPane scrollPane = (javafx.scene.control.ScrollPane) node;
                scrollPane.setStyle("-fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // TabPane thematisieren
        for (javafx.scene.Node node : dialogPane.lookupAll(".tab-pane")) {
            if (node instanceof javafx.scene.control.TabPane) {
                javafx.scene.control.TabPane tabPane = (javafx.scene.control.TabPane) node;
                tabPane.setStyle("-fx-background-color: " + solidBackgroundColor + "; -fx-border-color: " + textColor + ";");
            }
        }
        
        // Alle weiteren Labels thematisieren (falls noch nicht abgedeckt)
        for (javafx.scene.Node node : dialogPane.lookupAll(".label")) {
            if (node instanceof javafx.scene.control.Label) {
                javafx.scene.control.Label label = (javafx.scene.control.Label) node;
                // Nur stylen wenn noch kein Style gesetzt ist
                if (label.getStyle() == null || label.getStyle().isEmpty()) {
                    label.setStyle("-fx-text-fill: " + textColor + "; -fx-background-color: transparent;");
                }
            }
        }
    }
    
    /**
     * Zeigt den Alert an und wartet auf Benutzerinteraktion
     */
    public Optional<ButtonType> showAndWait() {
        try {
            // Prüfe ob customStage noch existiert
            if (customStage == null) {
                return Optional.empty();
            }
            
            // Titel aktualisieren
            if (originalAlert.getTitle() != null && titleLabel != null) {
                titleLabel.setText(originalAlert.getTitle());
            }
            
            // CustomStage anzeigen
            customStage.showAndWait();
            
            // Ergebnis zurückgeben
            return Optional.ofNullable(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Zeigt den Alert an
     */
    public void show() {
        try {
            // Titel aktualisieren
            if (originalAlert.getTitle() != null) {
                titleLabel.setText(originalAlert.getTitle());
            }
            
            // CustomStage anzeigen
            customStage.show();
        } catch (Exception e) {
            // Fehler ignorieren
        }
    }
    
    /**
     * Schließt den Alert
     */
    public void close() {
        try {
            if (customStage != null && customStage.isShowing()) {
                // Event-Filter entfernen, um NullPointerException zu vermeiden
                Scene scene = customStage.getScene();
                if (scene != null) {
                    if (mouseMovedHandler != null) {
                        scene.removeEventFilter(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
                    }
                    if (mousePressedHandler != null) {
                        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
                    }
                    if (mouseDraggedHandler != null) {
                        scene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
                    }
                    if (mouseReleasedHandler != null) {
                        scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
                    }
                }
                customStage.close();
            }
        } catch (Exception e) {
            // Ignoriere Fehler beim Schließen
        }
    }
    
    /**
     * Setzt den Owner-Window
     */
    public void initOwner(Window owner) {
        customStage.initOwner(owner);
    }
    
    /**
     * Setzt die Größe
     */
    public void setSize(double width, double height) {
        customStage.setWidth(width);
        customStage.setHeight(height);
    }
    
    /**
     * Setzt die Position
     */
    public void setPosition(double x, double y) {
        customStage.setX(x);
        customStage.setY(y);
    }
    
    /**
     * Zentriert den Alert auf dem Bildschirm
     */
    public void centerOnScreen() {
        customStage.centerOnScreen();
    }
    
    // Wrapper-Methoden für die Original-Alert-Funktionalität
    
    public void setTitle(String title) {
        originalAlert.setTitle(title);
        if (titleLabel != null) {
            titleLabel.setText(title);
        }
    }
    
    public String getTitle() {
        return originalAlert.getTitle();
    }
    
    public void setHeaderText(String headerText) {
        originalAlert.setHeaderText(headerText);
    }
    
    public String getHeaderText() {
        return originalAlert.getHeaderText();
    }
    
    public void setContentText(String contentText) {
        originalAlert.setContentText(contentText);
    }
    
    public String getContentText() {
        return originalAlert.getContentText();
    }
    
    public void setButtonTypes(ButtonType... buttonTypes) {
        originalAlert.getButtonTypes().setAll(buttonTypes);
    }
    
    public DialogPane getDialogPane() {
        return originalAlert.getDialogPane();
    }
    
    // Helper-Methoden für Theme-Anwendung
    private int getCurrentThemeIndex() {
        return currentThemeIndex;
    }
    
    private String getCurrentBackgroundColor() {
        return currentBackgroundColor;
    }
    
    private String getCurrentTextColor() {
        return currentTextColor;
    }
}
