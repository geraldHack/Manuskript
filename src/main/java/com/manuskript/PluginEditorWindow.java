package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import com.manuskript.ResourceManager;

/**
 * Einfacher Plugin Editor - OHNE JSON, OHNE VALIDIERUNG, OHNE SYNTAX-HIGHLIGHTING
 */
public class PluginEditorWindow {
    
    private static final Logger logger = Logger.getLogger(PluginEditorWindow.class.getName());
    
    private CustomStage stage;
    private TextField nameField;
    private TextField categoryField;
    private TextArea descriptionArea;
    private Spinner<Double> temperatureSpinner;
    private Spinner<Integer> maxTokensSpinner;
    private CodeArea promptArea;
    private Label statusLabel;
    
    private Plugin currentPlugin;
    private String currentFilePath;
    private boolean isModified = false;
    
    public PluginEditorWindow() {
        createUI();
    }
    
    /**
     * Erstellt die Benutzeroberfläche
     */
    private void createUI() {
        stage = StageManager.createStage("Plugin Editor");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        
        // Haupt-Container
        BorderPane mainContainer = new BorderPane();
        mainContainer.setPadding(new Insets(10));
        mainContainer.getStyleClass().add("plugin-editor-window");
        
        // Obere Toolbar
        ToolBar toolbar = createToolbar();
        mainContainer.setTop(toolbar);
        
        // Zentrum - Plugin-Editor
        VBox editorPanel = createEditorPanel();
        mainContainer.setCenter(editorPanel);
        
        // Untere Statusleiste
        HBox statusBar = createStatusBar();
        mainContainer.setBottom(statusBar);
        
        Scene scene = new Scene(mainContainer);
        
        // CSS-Styles laden - KORREKT STYLEN!
        try {
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
                logger.info("CSS-Styles geladen: " + cssPath);
            }
            
            // Theme aus main_window_theme laden (NICHT editor_theme!)
            int mainTheme = ResourceManager.getIntParameter("main_window_theme", 0);
            
            // Theme-Klassen setzen
            if (mainTheme == 0) scene.getRoot().getStyleClass().add("weiss-theme");
            else if (mainTheme == 2) scene.getRoot().getStyleClass().add("pastell-theme");
            else if (mainTheme == 3) scene.getRoot().getStyleClass().addAll("theme-dark", "blau-theme");
            else if (mainTheme == 4) scene.getRoot().getStyleClass().addAll("theme-dark", "gruen-theme");
            else if (mainTheme == 5) scene.getRoot().getStyleClass().addAll("theme-dark", "lila-theme");
            else scene.getRoot().getStyleClass().add("theme-dark");

            // Titelleiste passend zum Theme einfärben
            stage.setTitleBarTheme(mainTheme);
            
        } catch (Exception e) {
            logger.severe("KRITISCHER FEHLER beim Laden der CSS-Styles: " + e.getMessage());
            e.printStackTrace();
        }
        
        stage.setSceneWithTitleBar(scene);
        
        // WICHTIG: Theme wird bereits von StageManager.createStage() gesetzt!
        // Keine zusätzlichen Theme-Aufrufe nötig!
        

        
        // Event-Handler
        setupEventHandlers();
    }
    

    
    /**
     * Erstellt die Toolbar
     */
    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("plugin-editor-toolbar");
        
        Button newButton = new Button("🆕 Neu");
        newButton.setOnAction(e -> newPlugin());
        newButton.setTooltip(new Tooltip("Neues Plugin erstellen"));
        newButton.getStyleClass().add("plugin-editor-button");
        
        Button saveButton = new Button("💾 Speichern");
        saveButton.setOnAction(e -> savePlugin());
        saveButton.setTooltip(new Tooltip("Plugin speichern"));
        saveButton.getStyleClass().add("plugin-editor-button");
        
        Button openButton = new Button("📂 Öffnen");
        openButton.setOnAction(e -> openPlugin());
        openButton.setTooltip(new Tooltip("Bestehendes Plugin laden"));
        openButton.getStyleClass().add("plugin-editor-button");
        
        Button saveAsButton = new Button("💾 Speichern als...");
        saveAsButton.setOnAction(e -> savePluginAs());
        saveAsButton.setTooltip(new Tooltip("Plugin unter neuem Namen speichern"));
        saveAsButton.getStyleClass().add("plugin-editor-button");
        
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        
        toolbar.getItems().addAll(newButton, openButton, saveButton, saveAsButton, separator);
        
        return toolbar;
    }
    
    /**
     * Erstellt das Haupt-Editor-Panel
     */
    private VBox createEditorPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        
        // Metadaten-Panel
        VBox metadataPanel = createMetadataPanel();
        
        // Prompt-Panel
        VBox promptPanel = createPromptPanel();
        
        panel.getChildren().addAll(metadataPanel, promptPanel);
        VBox.setVgrow(promptPanel, Priority.ALWAYS);
        
        return panel;
    }
    
    /**
     * Erstellt das Metadaten-Panel
     */
    private VBox createMetadataPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("plugin-editor-metadata-panel");
        
        Label titleLabel = new Label("Plugin-Metadaten");
        titleLabel.getStyleClass().add("plugin-editor-title");
        
        // Name
        Label nameLabel = new Label("Name:");
        nameField = new TextField();
        nameField.setPromptText("Plugin-Name eingeben...");
        
        // Kategorie
        Label categoryLabel = new Label("Kategorie:");
        categoryField = new TextField();
        categoryField.setPromptText("z.B. Charakter, Plot, Stil...");
        
        // Beschreibung
        Label descLabel = new Label("Beschreibung:");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Kurze Beschreibung des Plugins...");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);
        
        // Parameter
        HBox paramBox = new HBox(20);
        paramBox.setAlignment(Pos.CENTER_LEFT);
        
        // Temperature
        Label tempLabel = new Label("Temperature:");
        temperatureSpinner = new Spinner<>(0.0, 2.0, 0.7, 0.1);
        temperatureSpinner.setEditable(true);
        temperatureSpinner.setPrefWidth(100);
        temperatureSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            isModified = true;
            updateStatus("Geändert");
        });
        
        // Max Tokens
        Label tokensLabel = new Label("Max Tokens:");
        maxTokensSpinner = new Spinner<>(100, 8192, 2048, 100);
        maxTokensSpinner.setEditable(true);
        maxTokensSpinner.setPrefWidth(100);
        maxTokensSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            isModified = true;
            updateStatus("Geändert");
        });
        
        paramBox.getChildren().addAll(tempLabel, temperatureSpinner, tokensLabel, maxTokensSpinner);
        
        panel.getChildren().addAll(titleLabel, nameLabel, nameField, categoryLabel, categoryField, 
                                 descLabel, descriptionArea, paramBox);
        
        return panel;
    }
    
    /**
     * Erstellt das Prompt-Panel
     */
    private VBox createPromptPanel() {
        VBox panel = new VBox(5);
        panel.getStyleClass().add("plugin-editor-prompt-panel");
        
        Label promptLabel = new Label("Prompt:");
        promptLabel.getStyleClass().add("plugin-editor-prompt-label");
        
        // CodeArea für den Prompt - OHNE SYNTAX-HIGHLIGHTING!
        promptArea = new CodeArea();
        promptArea.setParagraphGraphicFactory(LineNumberFactory.get(promptArea));
        promptArea.setWrapText(true);
        promptArea.setPrefHeight(300);
        promptArea.getStyleClass().add("plugin-editor-codearea");
        promptArea.replaceText("Du bist ein hilfreicher Assistent für die Roman-Schreibarbeit.\n\n" +
                              "Verwende folgende Variablen-Syntax:\n" +
                              "- {selektierter Text} - wird automatisch mit dem selektierten Text aus dem Editor gefüllt\n" +
                              "- {Name:Default:text} für einzeilige Text-Variablen\n" +
                              "- {Name:Default:area} für mehrzeilige Text-Variablen\n" +
                              "- {Name:Default:boolean} für Ja/Nein-Variablen\n\n" +
                              "Beispiel:\n" +
                              "Analysiere diesen Text: {selektierter Text}\n" +
                              "Charakter: {CharakterName:Max Mustermann:text}\n" +
                              "Beschreibung: {Beschreibung::area}\n" +
                              "Ist Protagonist: {Protagonist:true:boolean}\n\n" +
                              "Schreibe hier deinen spezifischen Prompt...");
        
        panel.getChildren().addAll(promptLabel, promptArea);
        VBox.setVgrow(promptArea, Priority.ALWAYS);
        
        return panel;
    }
    
    /**
     * Erstellt die Statusleiste
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("plugin-editor-statusbar");
        
        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("plugin-editor-status-label");
        
        statusBar.getChildren().add(statusLabel);
        
        return statusBar;
    }
    
    /**
     * Richtet die Event-Handler ein
     */
    private void setupEventHandlers() {
        // Metadaten-Änderungen
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            isModified = true;
            updateStatus("Geändert");
        });
        
        categoryField.textProperty().addListener((obs, oldVal, newVal) -> {
            isModified = true;
            updateStatus("Geändert");
        });
        
        descriptionArea.textProperty().addListener((obs, oldVal, newVal) -> {
            isModified = true;
            updateStatus("Geändert");
        });
        
        // Prompt-Änderungen
        promptArea.textProperty().addListener((obs, oldVal, newVal) -> {
            isModified = true;
            updateStatus("Geändert");
        });
    }
    
    /**
     * Öffnet ein neues Plugin
     */
    private void newPlugin() {
        if (isModified) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Ungespeicherte Änderungen");
            alert.setHeaderText("Möchten Sie die aktuellen Änderungen speichern?");
            alert.setContentText("Es gibt ungespeicherte Änderungen.");
            
            // Drei Buttons: Ja, Nein, Abbrechen
            ButtonType saveButton = new ButtonType("Ja, speichern");
            ButtonType noButton = new ButtonType("Nein, verwerfen");
            ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(saveButton, noButton, cancelButton);
            
            // Dialog stylen wie andere Dialoge in der Anwendung
            applyDialogTheme(alert);
            
            var result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == saveButton) {
                    savePlugin();
                } else if (result.get() == cancelButton) {
                    return;
                } else if (result.get() == noButton) {
                    // Änderungen wirklich verwerfen
                    isModified = false;
                    updateStatus("Änderungen verworfen");
                }
            }
        }
        
        // Neues Plugin erstellen
        currentPlugin = new Plugin();
        currentFilePath = null;
        isModified = false;
        
        // UI zurücksetzen
        resetUI();
        
        // Beispiel-Prompt einfügen
        String examplePrompt = "Du bist ein erfahrener Autor.\n\nErstelle basierend auf {Name:Default:text} einen {Typ:Charakter:area}.\n\nVerwende folgende Parameter:\n- Detailliert: {Detailliert:true:boolean}\n- Zeige Statistiken: {ShowStats:false:boolean}";
        
        promptArea.replaceText(examplePrompt);
        updateStatus("Neues Plugin erstellt");
    }
    
    /**
     * Speichert das aktuelle Plugin
     */
    private void savePlugin() {
        if (currentFilePath == null) {
            savePluginAs();
        } else {
            saveToFile(currentFilePath);
        }
    }
    
    /**
     * Öffnet ein bestehendes Plugin
     */
    private void openPlugin() {
        if (isModified) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Ungespeicherte Änderungen");
            alert.setHeaderText("Möchten Sie die aktuellen Änderungen speichern?");
            alert.setContentText("Es gibt ungespeicherte Änderungen.");
            
            // Drei Buttons: Ja, Nein, Abbrechen
            ButtonType saveButton = new ButtonType("Ja, speichern");
            ButtonType noButton = new ButtonType("Nein, verwerfen");
            ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(saveButton, noButton, cancelButton);
            
            // Dialog stylen wie andere Dialoge in der Anwendung
            applyDialogTheme(alert);
            
            var result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == saveButton) {
                    savePlugin();
                } else if (result.get() == cancelButton) {
                    return;
                } else if (result.get() == noButton) {
                    // Änderungen wirklich verwerfen
                    isModified = false;
                    updateStatus("Änderungen verworfen");
                }
            }
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Plugin öffnen...");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Dateien", "*.json")
        );
        
        // Standard-Verzeichnis setzen
        File pluginsDir = new File("config/plugins");
        if (pluginsDir.exists()) {
            fileChooser.setInitialDirectory(pluginsDir);
        }
        
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            loadPluginFromFile(selectedFile.getAbsolutePath());
        }
    }
    
    /**
     * Lädt ein Plugin aus einer Datei
     */
    private void loadPluginFromFile(String filePath) {
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
            
            // Einfaches JSON-Parsing (ohne externe Bibliothek)
            Plugin plugin = parsePluginFromJson(jsonContent);
            
            if (plugin != null) {
                currentPlugin = plugin;
                currentFilePath = filePath;
                isModified = false;
                
                // UI mit Plugin-Daten füllen
                nameField.setText(plugin.getName());
                categoryField.setText(plugin.getCategory());
                descriptionArea.setText(plugin.getDescription());
                // Zeilenumbrüche korrekt ersetzen
                String processedPrompt = plugin.getPrompt().replace("\\n", "\n");
                promptArea.replaceText(processedPrompt);
                temperatureSpinner.getValueFactory().setValue(plugin.getTemperature());
                maxTokensSpinner.getValueFactory().setValue(plugin.getMaxTokens());
                
                updateStatus("Geladen: " + new File(filePath).getName());
            } else {
                updateStatus("Fehler beim Laden: Ungültiges Plugin-Format");
            }
        } catch (IOException e) {
            logger.severe("Fehler beim Laden: " + e.getMessage());
            updateStatus("Fehler beim Laden");
        }
    }
    
    /**
     * Parst ein Plugin aus JSON-String (einfache Implementierung)
     */
    private Plugin parsePluginFromJson(String jsonContent) {
        try {
            // Entferne Whitespace und Zeilenumbrüche
            jsonContent = jsonContent.replaceAll("\\s+", " ").trim();
            
            // Extrahiere die einzelnen Felder mit einfachem String-Parsing
            String name = extractJsonValue(jsonContent, "name");
            String description = extractJsonValue(jsonContent, "description");
            String category = extractJsonValue(jsonContent, "category");
            String prompt = extractJsonValue(jsonContent, "prompt");
            String temperatureStr = extractJsonValue(jsonContent, "temperature");
            String maxTokensStr = extractJsonValue(jsonContent, "maxTokens");
            
            if (name == null || prompt == null) {
                return null; // Mindestanforderungen nicht erfüllt
            }
            
            Plugin plugin = new Plugin();
            plugin.setName(name);
            plugin.setDescription(description != null ? description : "");
            plugin.setCategory(category != null ? category : "");
            plugin.setPrompt(prompt);
            plugin.setTemperature(temperatureStr != null ? Double.parseDouble(temperatureStr) : 0.7);
            plugin.setMaxTokens(maxTokensStr != null ? Integer.parseInt(maxTokensStr) : 2048);
            plugin.setEnabled(true);
            
            return plugin;
        } catch (Exception e) {
            logger.severe("Fehler beim Parsen des Plugins: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extrahiert einen Wert aus einem JSON-String
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        
        if (m.find()) {
            return m.group(1);
        }
        
        // Versuche numerische Werte
        pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
        p = java.util.regex.Pattern.compile(pattern);
        m = p.matcher(json);
        
        if (m.find()) {
            return m.group(1);
        }
        
        return null;
    }
    
    /**
     * Speichert das Plugin unter neuem Namen
     */
    private void savePluginAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Plugin speichern als...");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Dateien", "*.json")
        );
        
        // Standard-Verzeichnis setzen
        File pluginsDir = new File("config/plugins");
        if (pluginsDir.exists()) {
            fileChooser.setInitialDirectory(pluginsDir);
        }
        
        // Dateiname vorschlagen
        String suggestedName = nameField.getText().trim();
        if (!suggestedName.isEmpty()) {
            fileChooser.setInitialFileName(suggestedName + ".json");
        } else {
            fileChooser.setInitialFileName("neues-plugin.json");
        }
        
        File selectedFile = fileChooser.showSaveDialog(stage);
        if (selectedFile != null) {
            currentFilePath = selectedFile.getAbsolutePath();
            saveToFile(currentFilePath);
        }
    }
    
    /**
     * Speichert das Plugin in eine Datei
     */
    private void saveToFile(String filePath) {
        try {
            // Plugin aus UI-Daten erstellen
            Plugin plugin = new Plugin();
            plugin.setName(nameField.getText());
            plugin.setCategory(categoryField.getText());
            plugin.setDescription(descriptionArea.getText());
            plugin.setPrompt(promptArea.getText());
            plugin.setTemperature(temperatureSpinner.getValue());
            plugin.setMaxTokens(maxTokensSpinner.getValue());
            plugin.setEnabled(true);
            
            // Als JSON speichern
            String jsonContent = plugin.toJsonString();
            Files.write(Paths.get(filePath), jsonContent.getBytes());
            isModified = false;
            updateStatus("Gespeichert: " + new File(filePath).getName());
        } catch (IOException e) {
            logger.severe("Fehler beim Speichern: " + e.getMessage());
            updateStatus("Fehler beim Speichern");
        }
    }
    
    /**
     * Setzt die UI zurück
     */
    private void resetUI() {
        nameField.clear();
        categoryField.clear();
        descriptionArea.clear();
        temperatureSpinner.getValueFactory().setValue(0.7);
        maxTokensSpinner.getValueFactory().setValue(2048);
        promptArea.clear();
    }
    
    /**
     * Aktualisiert die Statusleiste
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Wendet das aktuelle Theme auf einen Dialog an (wie in OllamaWindow)
     */
    private void applyDialogTheme(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        
        // CSS (styles.css + editor.css) hinzufügen, damit globale Dialog-Styles greifen
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        if (stylesCss != null && !pane.getStylesheets().contains(stylesCss)) {
            pane.getStylesheets().add(stylesCss);
        }
        if (editorCss != null && !pane.getStylesheets().contains(editorCss)) {
            pane.getStylesheets().add(editorCss);
        }

        // Theme vom Editor übernehmen (wie im OllamaWindow)
        int editorTheme = ResourceManager.getIntParameter("ui.editor_theme", 4);
        
        // Theme-Klassen am DialogPane setzen (wie im EditorWindow)
        if (editorTheme == 0) pane.getStyleClass().add("weiss-theme");
        else if (editorTheme == 2) pane.getStyleClass().add("pastell-theme");
        else if (editorTheme == 3) pane.getStyleClass().addAll("theme-dark", "blau-theme");
        else if (editorTheme == 4) pane.getStyleClass().addAll("theme-dark", "gruen-theme");
        else if (editorTheme == 5) pane.getStyleClass().addAll("theme-dark", "lila-theme");
        else pane.getStyleClass().add("theme-dark");

        // Theme-spezifische Styles
        String dialogStyle = "";
        String contentStyle = "";
        String labelStyle = "";
        String buttonStyle = "";
        String headerBg = "";
        String headerText = "";
        
        switch (editorTheme) {
            case 0: // Weiß
                dialogStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000;";
                contentStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000;";
                labelStyle = "-fx-text-fill: #000000;";
                buttonStyle = "-fx-background-color: #f0f0f0; -fx-text-fill: #000000; -fx-border-color: #cccccc;";
                headerBg = "#ffffff";
                headerText = "#000000";
                break;
            case 2: // Pastell
                dialogStyle = "-fx-background-color: #f3e5f5; -fx-text-fill: #000000;";
                contentStyle = "-fx-background-color: #f3e5f5; -fx-text-fill: #000000;";
                labelStyle = "-fx-text-fill: #000000;";
                buttonStyle = "-fx-background-color: #e1bee7; -fx-text-fill: #000000; -fx-border-color: #ba68c8;";
                headerBg = "#f3e5f5";
                headerText = "#000000";
                break;
            case 3: // Blau
                dialogStyle = "-fx-background-color: #1e1b4b; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #1e1b4b; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #312e81; -fx-text-fill: #ffffff; -fx-border-color: #3b82f6;";
                headerBg = "#1e1b4b";
                headerText = "#ffffff";
                break;
            case 4: // Grün
                dialogStyle = "-fx-background-color: #064e3b; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #064e3b; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #065f46; -fx-text-fill: #ffffff; -fx-border-color: #047857;";
                headerBg = "#064e3b";
                headerText = "#ffffff";
                break;
            case 5: // Lila
                dialogStyle = "-fx-background-color: #2e1065; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #2e1065; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #4c1d95; -fx-text-fill: #ffffff; -fx-border-color: #8b5cf6;";
                headerBg = "#2e1065";
                headerText = "#ffffff";
                break;
            default: // Dunkel
                dialogStyle = "-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #2d2d2d; -fx-text-fill: #ffffff; -fx-border-color: #404040;";
                headerBg = "#1a1a1a";
                headerText = "#ffffff";
                break;
        }
        
        pane.setStyle(dialogStyle);
        
        // Alle Child-Elemente durchgehen und Styles anwenden
        for (javafx.scene.Node node : pane.getChildren()) {
            if (node instanceof VBox) {
                node.setStyle(contentStyle);
                // Rekursiv durch alle Child-Elemente gehen
                applyStyleToChildren(node, labelStyle, buttonStyle);
            }
        }

        // Header-Bereich gezielt einfärben, sobald der Dialog sichtbar ist
        final String headerBgFinal = headerBg;
        final String headerTextFinal = headerText;
        alert.setOnShown(ev -> {
            Node headerPanel = pane.lookup(".header-panel");
            if (headerPanel != null && headerBgFinal != null && !headerBgFinal.isEmpty()) {
                headerPanel.setStyle(String.format("-fx-background-color: %s; -fx-background-insets: 0; -fx-padding: 8 12;", headerBgFinal));
                Node headerLabel = headerPanel.lookup(".label");
                if (headerLabel instanceof Label) {
                    ((Label) headerLabel).setTextFill(javafx.scene.paint.Color.web(headerTextFinal != null && !headerTextFinal.isEmpty() ? headerTextFinal : "#ffffff"));
                }
            }
        });
    }
    
    private void applyStyleToChildren(javafx.scene.Node parent, String labelStyle, String buttonStyle) {
        if (parent instanceof Parent) {
            for (javafx.scene.Node child : ((Parent) parent).getChildrenUnmodifiable()) {
                if (child instanceof Label) {
                    child.setStyle(labelStyle);
                } else if (child instanceof Button) {
                    child.setStyle(buttonStyle);
                } else if (child instanceof HBox || child instanceof VBox) {
                    // Rekursiv für Container
                    applyStyleToChildren(child, labelStyle, buttonStyle);
                }
            }
        }
    }
    
    /**
     * Zeigt das Fenster an
     */
    public void show() {
        stage.show();
    }
    
    /**
     * Zeigt das Fenster mit einem Plugin an
     */
    public void showWithPlugin(Plugin plugin, String filePath) {
        currentPlugin = plugin;
        currentFilePath = filePath;
        isModified = false;
        
        // UI mit Plugin-Daten füllen
        if (plugin != null) {
            nameField.setText(plugin.getName());
            categoryField.setText(plugin.getCategory());
            descriptionArea.setText(plugin.getDescription());
            promptArea.replaceText(plugin.getPrompt());
            temperatureSpinner.getValueFactory().setValue(plugin.getTemperature());
            maxTokensSpinner.getValueFactory().setValue(plugin.getMaxTokens());
        }
        
        stage.show();
    }
    
    /**
     * Setzt das Theme für das Plugin-Editor-Fenster
     */
    public void setTheme(int themeIndex) {
        // Titelleiste themen
        if (stage != null) {
            stage.setTitleBarTheme(themeIndex);
        }
        
        // Root-Container themen
        if (stage != null && stage.getScene() != null) {
            Node root = stage.getScene().getRoot();
            root.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
            if (themeIndex == 0) root.getStyleClass().add("weiss-theme");
            else if (themeIndex == 2) root.getStyleClass().add("pastell-theme");
            else {
                root.getStyleClass().add("theme-dark");
                if (themeIndex == 3) root.getStyleClass().add("blau-theme");
                if (themeIndex == 4) root.getStyleClass().add("gruen-theme");
                if (themeIndex == 5) root.getStyleClass().add("lila-theme");
            }
        }
    }
}
