package com.manuskript;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Komplett eigene Alert-Implementierung mit schöner Titelleiste und Themeing
 * Völlig unabhängig von JavaFX's Alert-Klasse
 */
public class CustomAlert {
    
    // Theme-Konstanten
    private static final String[] THEME_BACKGROUNDS = {
        "#ffffff", // Weiß
        "#1a1a1a", // Schwarz
        "#f3e5f5", // Pastell
        "#1e3a8a", // Blau
        "#064e3b", // Grün
        "#581c87"  // Lila
    };
    
    private static final String[] THEME_TITLES = {
        "#2c3e50", // Weiß
        "#ffffff", // Schwarz
        "#4a148c", // Pastell
        "#ffffff", // Blau
        "#ffffff", // Grün
        "#ffffff"  // Lila
    };
    
    private static final String[] THEME_TEXTS = {
        "#2c3e50", // Weiß
        "#ffffff", // Schwarz
        "#4a148c", // Pastell
        "#ffffff", // Blau
        "#ffffff", // Grün
        "#ffffff"  // Lila
    };
    
    // UI-Komponenten
    private Stage stage;
    private VBox rootContainer;
    private HBox titleBar;
    private Label titleLabel;
    private Button minimizeBtn, maximizeBtn, closeBtn;
    private VBox contentContainer;
    private Label headerLabel, contentLabel;
    private ImageView graphicView;
    private HBox buttonContainer;
    
    // Alert-Eigenschaften
    private String title = "Alert";
    private String headerText = "";
    private String contentText = "";
    private javafx.scene.image.Image graphic;
    private ObservableList<ButtonType> buttonTypes = FXCollections.observableArrayList();
    private boolean hasCustomContent = false;
    private TextField textField = null;
    private VBox customContentBox = null;
    private ButtonType result = null;
    
    // Theme
    private int currentTheme = 0;
    
    // Drag-Funktionalität
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean isMaximized = false;
    private double originalX, originalY, originalWidth, originalHeight;
    
    /**
     * Konstruktor
     */
    public CustomAlert() {
        this(AlertType.INFORMATION);
    }
    
    /**
     * Konstruktor mit AlertType
     */
    public CustomAlert(AlertType alertType) {
        initializeUI();
        setDefaultButtonTypes(alertType);
        applyTheme(currentTheme);
        // WICHTIG: Content und Buttons nach der Initialisierung aktualisieren
        updateContent();
        updateButtons();
    }
    
    /**
     * Konstruktor mit AlertType und Titel (für Kompatibilität)
     */
    public CustomAlert(javafx.scene.control.Alert.AlertType alertType, String title) {
        this(convertAlertType(alertType));
        setTitle(title);
    }
    
    /**
     * JavaFX AlertType zu CustomAlert AlertType konvertieren
     */
    private static AlertType convertAlertType(javafx.scene.control.Alert.AlertType javafxType) {
        switch (javafxType) {
            case CONFIRMATION: return AlertType.CONFIRMATION;
            case WARNING: return AlertType.WARNING;
            case ERROR: return AlertType.ERROR;
            case INFORMATION: return AlertType.INFORMATION;
            default: return AlertType.INFORMATION;
        }
    }
    
    /**
     * UI initialisieren
     */
    private void initializeUI() {
        // Stage erstellen
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(true);
        stage.setMinWidth(400);
        stage.setMinHeight(200);
        
        // Root Container
        rootContainer = new VBox();
        rootContainer.setSpacing(0);
        
        // Titelleiste erstellen
        createTitleBar();
        
        // Content Container
        contentContainer = new VBox();
        contentContainer.setSpacing(15);
        contentContainer.setPadding(new Insets(20));
        
        // Button Container
        buttonContainer = new HBox();
        buttonContainer.setSpacing(10);
        buttonContainer.setAlignment(Pos.CENTER_RIGHT);
        buttonContainer.setPadding(new Insets(10, 20, 20, 20));
        
        // Alles zusammenfügen
        rootContainer.getChildren().addAll(titleBar, contentContainer, buttonContainer);
        
        // Scene erstellen
        Scene scene = new Scene(rootContainer);
        stage.setScene(scene);
        
        // Drag-Funktionalität
        setupDragFunctionality();
    }
    
    /**
     * Titelleiste erstellen
     */
    private void createTitleBar() {
        titleBar = new HBox();
        titleBar.setPrefHeight(35);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 0, 0, 15));
        
        // Titel
        titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Window Buttons
        HBox buttonBox = new HBox();
        buttonBox.setSpacing(0);
        
        // Minimize Button
        minimizeBtn = new Button("−");
        minimizeBtn.setPrefSize(35, 35);
        minimizeBtn.setFont(Font.font("System", FontWeight.BOLD, 16));
        minimizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;");
        
        // Maximize Button
        maximizeBtn = new Button("□");
        maximizeBtn.setPrefSize(35, 35);
        maximizeBtn.setFont(Font.font("System", FontWeight.BOLD, 14));
        maximizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;");
        
        // Close Button
        closeBtn = new Button("✕");
        closeBtn.setPrefSize(45, 35); // Breiter gemacht
        closeBtn.setFont(Font.font("System", FontWeight.BOLD, 18)); // Größere Schrift
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;");
        
        // Button Actions
        minimizeBtn.setOnAction(e -> stage.setIconified(true));
        maximizeBtn.setOnAction(e -> toggleMaximize());
        closeBtn.setOnAction(e -> {
            result = ButtonType.CANCEL;
            stage.close();
        });
        
        // Hover Effects
        setupButtonHoverEffects();
        
        buttonBox.getChildren().addAll(minimizeBtn, maximizeBtn, closeBtn);
        titleBar.getChildren().addAll(titleLabel, spacer, buttonBox);
    }
    
    /**
     * Button Hover Effects
     */
    private void setupButtonHoverEffects() {
        String hoverStyle = "-fx-background-color: rgba(255,255,255,0.1);";
        String closeHoverStyle = "-fx-background-color: #e74c3c;";
        
        minimizeBtn.setOnMouseEntered(e -> minimizeBtn.setStyle(minimizeBtn.getStyle() + hoverStyle));
        minimizeBtn.setOnMouseExited(e -> minimizeBtn.setStyle(minimizeBtn.getStyle().replace(hoverStyle, "")));
        
        maximizeBtn.setOnMouseEntered(e -> maximizeBtn.setStyle(maximizeBtn.getStyle() + hoverStyle));
        maximizeBtn.setOnMouseExited(e -> maximizeBtn.setStyle(maximizeBtn.getStyle().replace(hoverStyle, "")));
        
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(closeBtn.getStyle() + closeHoverStyle));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(closeBtn.getStyle().replace(closeHoverStyle, "")));
    }
    
    /**
     * Drag-Funktionalität einrichten
     */
    private void setupDragFunctionality() {
        titleBar.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        
        titleBar.setOnMouseDragged(e -> {
            if (!isMaximized) {
                stage.setX(e.getScreenX() - dragOffsetX);
                stage.setY(e.getScreenY() - dragOffsetY);
            }
        });
        
        titleBar.setOnMouseReleased(e -> {
            if (e.getClickCount() == 2) {
                toggleMaximize();
            }
        });
    }
    
    /**
     * Maximize/Minimize umschalten
     */
    private void toggleMaximize() {
        if (isMaximized) {
            // Restore
            stage.setX(originalX);
            stage.setY(originalY);
            stage.setWidth(originalWidth);
            stage.setHeight(originalHeight);
            maximizeBtn.setText("□");
            isMaximized = false;
        } else {
            // Maximize
            originalX = stage.getX();
            originalY = stage.getY();
            originalWidth = stage.getWidth();
            originalHeight = stage.getHeight();
            
            stage.setMaximized(true);
            maximizeBtn.setText("❐");
            isMaximized = true;
        }
    }
    
    /**
     * Standard Button Types setzen
     */
    private void setDefaultButtonTypes(AlertType alertType) {
        buttonTypes.clear();
        switch (alertType) {
            case CONFIRMATION:
                buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL);
                break;
            case WARNING:
            case ERROR:
                buttonTypes.addAll(ButtonType.OK);
                break;
            default:
                buttonTypes.addAll(ButtonType.OK);
                break;
        }
    }
    
    
    /**
     * Button Styles aktualisieren
     */
    private void updateButtonStyles(String textColor, String backgroundColor) {
        for (javafx.scene.Node node : buttonContainer.getChildren()) {
            if (node instanceof Button) {
                Button button = (Button) node;
                String buttonStyle = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10 20; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;",
                    backgroundColor, textColor, textColor
                );
                button.setStyle(buttonStyle);
            }
        }
    }
    
    /**
     * Content aktualisieren
     */
    private void updateContent() {
        
        contentContainer.getChildren().clear();
        
        // Header
        if (headerText != null && !headerText.isEmpty() && !headerText.equals("null")) {
            headerLabel = new Label(headerText);
            headerLabel.setWrapText(true);
            headerLabel.setTextAlignment(TextAlignment.LEFT);
            headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + THEME_TEXTS[currentTheme] + ";");
            contentContainer.getChildren().add(headerLabel);
        }
        
        // Graphic
        if (graphic != null) {
            graphicView = new ImageView(graphic);
            graphicView.setFitWidth(64);
            graphicView.setFitHeight(64);
            graphicView.setPreserveRatio(true);
            contentContainer.getChildren().add(graphicView);
        }
        
        // Content ODER TextField ODER Custom Content
        if (hasCustomContent) {
            if (customContentBox != null) {
                // Mehrere Controls anzeigen
                contentContainer.getChildren().add(customContentBox);
            } else if (textField != null) {
                // Einzelnes TextField anzeigen
                contentContainer.getChildren().add(textField);
            }
        } else if (contentText != null && !contentText.isEmpty() && !contentText.equals("null")) {
            // Normaler Content anzeigen
            contentLabel = new Label(contentText);
            contentLabel.setWrapText(true);
            contentLabel.setTextAlignment(TextAlignment.LEFT);
            contentLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + THEME_TEXTS[currentTheme] + ";");
            contentContainer.getChildren().add(contentLabel);
        }
        
    }
    
    /**
     * Buttons aktualisieren
     */
    private void updateButtons() {
        
        buttonContainer.getChildren().clear();
        
        // Prüfe ob buttonTypes leer ist
        if (buttonTypes == null || buttonTypes.isEmpty()) {
            return;
        }
        
        for (ButtonType buttonType : buttonTypes) {
            Button button = new Button(buttonType.getText());
            button.setOnAction(e -> {
                result = buttonType;
                stage.close();
            });
            
            // Button sofort stylen
            String textColor = THEME_TEXTS[currentTheme];
            String backgroundColor = THEME_BACKGROUNDS[currentTheme];
            String buttonStyle = String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10 20; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;",
                backgroundColor, textColor, textColor
            );
            button.setStyle(buttonStyle);
            
            buttonContainer.getChildren().add(button);
        }
        
        
        // WICHTIG: Theme NACH dem Hinzufügen der Buttons anwenden
        // applyTheme(currentTheme); // Wird in showAndWait() aufgerufen
    }
    
    // Getter und Setter
    public String getTitle() { return title; }
    public void setTitle(String title) { 
        this.title = title; 
        if (titleLabel != null) {
            titleLabel.setText(title);
        }
    }
    
    public String getHeaderText() { return headerText; }
    public void setHeaderText(String headerText) { 
        this.headerText = headerText; 
        // WICHTIG: updateContent() wird in showAndWait()/show() aufgerufen
    }
    
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { 
        this.contentText = contentText; 
        // WICHTIG: updateContent() wird in showAndWait()/show() aufgerufen
    }
    
    public javafx.scene.image.Image getGraphic() { return graphic; }
    public void setGraphic(javafx.scene.image.Image graphic) { 
        this.graphic = graphic; 
        // WICHTIG: updateContent() wird in showAndWait()/show() aufgerufen
    }
    
    public ObservableList<ButtonType> getButtonTypes() { return buttonTypes; }
    public void setButtonTypes(ObservableList<ButtonType> buttonTypes) { 
        this.buttonTypes = buttonTypes; 
        // WICHTIG: updateButtons() wird in showAndWait()/show() aufgerufen
    }
    
    public int getCurrentTheme() { return currentTheme; }
    public void setCurrentTheme(int themeIndex) { 
        applyTheme(themeIndex);
    }
    
    /**
     * Theme anwenden (public für Kompatibilität)
     */
    public void applyTheme(int themeIndex) {
        if (themeIndex < 0 || themeIndex >= THEME_BACKGROUNDS.length) {
            themeIndex = 0;
        }
        
        currentTheme = themeIndex;
        String backgroundColor = THEME_BACKGROUNDS[themeIndex];
        String titleColor = THEME_TITLES[themeIndex];
        String textColor = THEME_TEXTS[themeIndex];
        
        // Root Container
        rootContainer.setStyle("-fx-background-color: " + backgroundColor + ";");
        
        // Titelleiste
        titleBar.setStyle("-fx-background-color: " + backgroundColor + "; -fx-border-color: " + titleColor + "; -fx-border-width: 0 0 1 0;");
        titleLabel.setStyle("-fx-text-fill: " + titleColor + ";");
        
        // Window Buttons
        String buttonStyle = "-fx-background-color: transparent; -fx-text-fill: " + titleColor + "; -fx-border-color: transparent;";
        minimizeBtn.setStyle(buttonStyle);
        maximizeBtn.setStyle(buttonStyle);
        closeBtn.setStyle(buttonStyle);
        
        // Content - WICHTIG: Nur anwenden wenn Labels existieren
        if (headerLabel != null) {
            headerLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 16px;");
        }
        if (contentLabel != null) {
            contentLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 14px;");
        }
        
        // Buttons
        updateButtonStyles(textColor, backgroundColor);
        
        // Custom Content Controls stylen (falls vorhanden) - rekursiv
        if (hasCustomContent && customContentBox != null) {
            applyThemeToNodesRecursively(customContentBox, textColor, backgroundColor);
        }
        
        // Border für den gesamten Dialog
        String borderColor = textColor;
        rootContainer.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8;",
            backgroundColor, borderColor
        ));
    }
    
    /**
     * Owner setzen (für Kompatibilität)
     */
    public void initOwner(Window owner) {
        if (stage != null) {
            stage.initOwner(owner);
        }
    }
    
    /**
     * Dummy DialogPane für Kompatibilität
     */
    public DialogPane getDialogPane() {
        // Erstelle ein Dummy DialogPane für Kompatibilität
        DialogPane dummyPane = new DialogPane();
        dummyPane.setContent(contentContainer);
        return dummyPane;
    }
    
    /**
     * Setzt ein TextField für Eingabe (für TextInputDialog-Ersatz)
     */
    public void setTextField(TextField textField) {
        hasCustomContent = true;
        this.textField = textField;
        
        // TextField stylen
        textField.setStyle("-fx-background-color: white; -fx-text-fill: black; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8 12; -fx-font-size: 12px;");
    }
    
    /**
     * Setzt mehrere Textfelder/Controls (für komplexe Dialoge)
     */
    public void setCustomContent(VBox contentBox) {
        hasCustomContent = true;
        this.customContentBox = contentBox;
        
        // Alle Controls in der VBox stylen (rekursiv für HBox/VBox)
        styleNodesRecursively(contentBox);
    }
    
    /**
     * Wendet Theme rekursiv auf alle Nodes an
     */
    private void applyThemeToNodesRecursively(javafx.scene.Parent parent, String textColor, String backgroundColor) {
        for (Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof Label) {
                Label label = (Label) node;
                label.setStyle("-fx-font-size: 12px; -fx-text-fill: " + textColor + ";");
            } else if (node instanceof CheckBox) {
                CheckBox cb = (CheckBox) node;
                cb.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 12px;");
            } else if (node instanceof Button) {
                Button btn = (Button) node;
                // Theme-spezifisches Button-Styling
                String customButtonStyle = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 4px 8px; -fx-font-size: 11px; -fx-font-weight: bold;",
                    backgroundColor, textColor, textColor
                );
                btn.setStyle(customButtonStyle);
            } else if (node instanceof javafx.scene.Parent) {
                // Rekursiv für Container (HBox, VBox, etc.)
                applyThemeToNodesRecursively((javafx.scene.Parent) node, textColor, backgroundColor);
            }
        }
    }
    
    /**
     * Stylt alle Nodes rekursiv (für HBox/VBox Container)
     */
    private void styleNodesRecursively(javafx.scene.Parent parent) {
        for (Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof TextField) {
                TextField tf = (TextField) node;
                tf.setStyle("-fx-background-color: white; -fx-text-fill: black; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8 12; -fx-font-size: 12px;");
            } else if (node instanceof TextArea) {
                TextArea ta = (TextArea) node;
                ta.setStyle("-fx-background-color: white; -fx-text-fill: black; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8 12; -fx-font-size: 12px;");
            } else if (node instanceof Label) {
                Label label = (Label) node;
                label.setStyle("-fx-font-size: 12px; -fx-text-fill: " + THEME_TEXTS[currentTheme] + ";");
            } else if (node instanceof CheckBox) {
                CheckBox cb = (CheckBox) node;
                cb.setStyle("-fx-text-fill: " + THEME_TEXTS[currentTheme] + "; -fx-font-size: 12px;");
            } else if (node instanceof Button) {
                Button btn = (Button) node;
                // Theme-spezifisches Button-Styling
                String backgroundColor = THEME_BACKGROUNDS[currentTheme];
                String textColor = THEME_TEXTS[currentTheme];
                String buttonStyle = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 4px 8px; -fx-font-size: 11px; -fx-font-weight: bold;",
                    backgroundColor, textColor, textColor
                );
                btn.setStyle(buttonStyle);
            } else if (node instanceof javafx.scene.Parent) {
                // Rekursiv für Container (HBox, VBox, etc.)
                styleNodesRecursively((javafx.scene.Parent) node);
            }
        }
    }
    
    /**
     * Gibt das TextField zurück (für TextInputDialog-Ersatz)
     */
    public TextField getTextField() {
        return textField;
    }
    
    /**
     * Gibt die Custom Content Box zurück
     */
    public VBox getCustomContent() {
        return customContentBox;
    }
    
    /**
     * ButtonTypes setzen (varargs für Kompatibilität)
     */
    public void setButtonTypes(ButtonType... buttonTypes) {
        this.buttonTypes.clear();
        this.buttonTypes.addAll(buttonTypes);
        // WICHTIG: updateButtons() wird in showAndWait()/show() aufgerufen
    }
    
    /**
     * Alert anzeigen und warten
     */
    public Optional<ButtonType> showAndWait() {
        return showAndWait(null);
    }
    
    /**
     * Alert anzeigen und warten (mit Owner)
     */
    public Optional<ButtonType> showAndWait(Window owner) {
        
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        
        // WICHTIG: Content und Buttons IMMER aktualisieren vor dem Anzeigen
        updateContent();
        updateButtons();
        
        
        // Anzeigen
        stage.showAndWait();
        
        
        return Optional.ofNullable(result);
    }
    
    /**
     * Alert anzeigen (nicht blockierend)
     */
    public void show() {
        show(null);
    }
    
    /**
     * Alert anzeigen (nicht blockierend, mit Owner)
     */
    public void show(Window owner) {
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.NONE);
        }
        
        // WICHTIG: Content und Buttons IMMER aktualisieren vor dem Anzeigen
        updateContent();
        updateButtons();
        
        // Anzeigen
        stage.show();
    }
    
    /**
     * Alert schließen
     */
    public void close() {
        if (stage != null) {
            stage.close();
        }
    }
    
    /**
     * Debug-Methode zum Testen
     */
    public void debugInfo() {
        System.out.println("  Title: '" + title + "'");
        System.out.println("  HeaderText: '" + headerText + "'");
        System.out.println("  ContentText: '" + contentText + "'");
        System.out.println("  ButtonTypes: " + buttonTypes.size() + " buttons");
        for (ButtonType bt : buttonTypes) {
            System.out.println("    - " + bt.getText());
        }
    }
    
    /**
     * Alert-Typen (kompatibel mit JavaFX AlertType)
     */
    public enum AlertType {
        NONE,
        INFORMATION,
        WARNING,
        CONFIRMATION,
        ERROR
    }
}
