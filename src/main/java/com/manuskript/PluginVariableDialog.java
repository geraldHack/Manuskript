package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Dynamischer Dialog für Plugin-Variablen
 */
public class PluginVariableDialog {
    
    private CustomStage dialog;
    private Map<String, Control> variableFields = new HashMap<>();
    private Map<String, String> result = new HashMap<>();
    private boolean confirmed = false;
    private int currentThemeIndex = 0;
    private Plugin currentPlugin;
    
    /**
     * Zeigt einen Dialog für Plugin-Variablen an
     * @param plugin Das Plugin mit den Variablen
     * @param selectedText Automatisch gesetzter Text (Editor-Selektion oder Chat-Input)
     * @param themeIndex Theme-Index für Styling
     * @return Map mit Variablen-Werten oder null wenn abgebrochen
     */
    public static Map<String, String> showDialog(Plugin plugin, String selectedText, int themeIndex) {
        PluginVariableDialog dialog = new PluginVariableDialog();
        dialog.currentThemeIndex = themeIndex;
        return dialog.show(plugin, selectedText);
    }
    
    private Map<String, String> show(Plugin plugin, String selectedText) {
        this.currentPlugin = plugin; // Plugin speichern für spätere Verwendung
        
        // Dialog erstellen
        dialog = StageManager.createStage("Plugin-Variablen");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Plugin-Variablen: " + plugin.getName());
        dialog.setResizable(true);
        dialog.setMinWidth(600);
        dialog.setMinHeight(400);
        
        // Haupt-Container
        VBox mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.TOP_CENTER);
        
        // Plugin-Header mit Name und Beschreibung
        VBox headerBox = new VBox(5);
        Label nameLabel = new Label("📦 " + plugin.getName());
        nameLabel.getStyleClass().add("plugin-dialog-title");
        
        Label descriptionLabel = new Label(plugin.getDescription());
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("plugin-dialog-description");
        
        headerBox.getChildren().addAll(nameLabel, descriptionLabel);
        
        // Anleitung
        Label instructionLabel = new Label("Fülle die folgenden Variablen aus, um das Plugin zu verwenden:");
        instructionLabel.getStyleClass().add("plugin-dialog-instruction");
        
        // Variablen-Grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        
        // Variablen-Definitionen aus Plugin extrahieren
        List<PluginVariable> variableDefinitions = plugin.getVariableDefinitions();
        int row = 0;
        
        for (PluginVariable varDef : variableDefinitions) {
            String variable = varDef.getName();
            // "selektierter Text" automatisch setzen
            if (isSelectedTextVariable(variable)) {
                result.put(variable, selectedText != null ? selectedText : "");
                continue;
            }
            
            // Label für Variable
            Label label = new Label(varDef.getDisplayName() + ":");
            label.getStyleClass().add("plugin-dialog-variable-label");
            
            // Tooltip mit Beschreibung falls vorhanden
            if (varDef.getDescription() != null && !varDef.getDescription().isEmpty()) {
                Tooltip tooltip = new Tooltip(varDef.getDescription());
                label.setTooltip(tooltip);
            }
            
            // Erstelle das passende Eingabefeld basierend auf dem Typ
            Control inputField = createInputField(varDef, selectedText);
            
            variableFields.put(variable, inputField);
            
            grid.add(label, 0, row);
            grid.add(inputField, 1, row);
            row++;
        }
        
        // Prompt-Vorschau (nur wenn Variablen vorhanden)
        VBox previewBox = new VBox(5);
        if (!variableDefinitions.isEmpty()) {
            Label previewLabel = new Label("📝 Generierter Prompt:");
            previewLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333;");
            
            TextArea previewArea = new TextArea();
            previewArea.setEditable(false);
            previewArea.setWrapText(true);
            previewArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
            VBox.setVgrow(previewArea, Priority.ALWAYS);
            
            // Live-Vorschau aktualisieren
            updatePreview(previewArea, plugin);
            
            // Listener für Live-Vorschau
            for (Control control : variableFields.values()) {
                if (control instanceof TextField) {
                    ((TextField) control).textProperty().addListener((obs, oldVal, newVal) -> {
                        updatePreview(previewArea, plugin);
                    });
                } else if (control instanceof TextArea) {
                    ((TextArea) control).textProperty().addListener((obs, oldVal, newVal) -> {
                        updatePreview(previewArea, plugin);
                    });
                } else if (control instanceof CheckBox) {
                    ((CheckBox) control).selectedProperty().addListener((obs, oldVal, newVal) -> {
                        updatePreview(previewArea, plugin);
                    });
                } else if (control instanceof ComboBox) {
                    @SuppressWarnings("unchecked")
                    ComboBox<String> comboBox = (ComboBox<String>) control;
                    comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        updatePreview(previewArea, plugin);
                    });
                } else if (control instanceof Spinner) {
                    @SuppressWarnings("unchecked")
                    Spinner<Double> spinner = (Spinner<Double>) control;
                    spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                        updatePreview(previewArea, plugin);
                    });
                }
            }
            
            previewBox.getChildren().addAll(previewLabel, previewArea);
        }
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button okButton = new Button("Plugin ausführen");
        okButton.setDefaultButton(true);
        okButton.setStyle("-fx-background-color: #2E86AB; -fx-text-fill: white; -fx-font-weight: bold;");
        okButton.setOnAction(e -> {
            collectResults();
            confirmed = true;
            dialog.close();
        });
        
        Button cancelButton = new Button("Abbrechen");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> {
            confirmed = false;
            dialog.close();
        });
        
        buttonBox.getChildren().addAll(okButton, cancelButton);
        
        // Layout-Struktur für bessere Größenanpassung
        VBox contentBox = new VBox(15);
        contentBox.getChildren().addAll(headerBox, instructionLabel, grid);
        
        // Preview-Box separat hinzufügen mit VBox.setVgrow
        if (!variableDefinitions.isEmpty()) {
            VBox.setVgrow(previewBox, Priority.ALWAYS);
        }
        
        // Alles zusammenfügen - contentBox wächst, previewBox wächst, buttonBox bleibt unten
        mainContainer.getChildren().addAll(contentBox, previewBox, buttonBox);
        
        // Border um das gesamte Fenster hinzufügen - auf den mainContainer
        mainContainer.setStyle(mainContainer.getStyle() + " -fx-border-color: #cccccc; -fx-border-width: 1px; -fx-border-style: solid;");
        
        // Dialog anzeigen
        Scene scene = new Scene(mainContainer);
        
        // CSS-Styles laden und anwenden
        try {
            String stylesCss = com.manuskript.ResourceManager.getCssResource("css/styles.css");
            String editorCss = com.manuskript.ResourceManager.getCssResource("css/editor.css");
            String manuskriptCss = com.manuskript.ResourceManager.getCssResource("css/manuskript.css");
            if (stylesCss != null) {
                scene.getStylesheets().add(stylesCss);
            }
            if (editorCss != null) {
                scene.getStylesheets().add(editorCss);
            }
            if (manuskriptCss != null) {
                scene.getStylesheets().add(manuskriptCss);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(PluginVariableDialog.class).warn("Fehler beim Laden der CSS-Styles: {}", e.getMessage());
        }
        
        // Theme-Klassen setzen
        applyDialogTheme(mainContainer, currentThemeIndex);
        
        // Auch die Scene-Root mit Theme-Klasse versehen
        if (currentThemeIndex == 2) {
            scene.getRoot().getStyleClass().add("pastell-theme");
        }
        
        dialog.setSceneWithTitleBar(scene);
        
        // WICHTIG: Theme anwenden, damit die Border gesetzt wird
        dialog.setTitleBarTheme(currentThemeIndex);
        
        dialog.showAndWait();
        
        return confirmed ? result : null;
    }
    
    /**
     * Sammelt die eingegebenen Werte
     */
    private void collectResults() {
        for (Map.Entry<String, Control> entry : variableFields.entrySet()) {
            String value = "";
            Control control = entry.getValue();
            
            if (control instanceof TextField) {
                value = ((TextField) control).getText().trim();
            } else if (control instanceof TextArea) {
                value = ((TextArea) control).getText().trim();
            } else if (control instanceof CheckBox) {
                value = String.valueOf(((CheckBox) control).isSelected());
            } else if (control instanceof ComboBox) {
                @SuppressWarnings("unchecked")
                ComboBox<String> comboBox = (ComboBox<String>) control;
                String selectedLabel = comboBox.getValue();
                
                // Konvertiere Label zurück zu Value für die Verwendung im Prompt
                if (selectedLabel != null) {
                    String variableName = entry.getKey();
                    PluginVariable varDef = findVariableDefinition(currentPlugin, variableName);
                    
                    // Suche nach dem Value zum ausgewählten Label
                    if (varDef != null && varDef.getOptions() != null) {
                        for (PluginVariable.Option option : varDef.getOptions()) {
                            if (option.getLabel().equals(selectedLabel)) {
                                value = option.getValue();
                                break;
                            }
                        }
                    }
                    
                    // Fallback: Falls keine Option gefunden, verwende das Label
                    if (value.isEmpty()) {
                        value = selectedLabel;
                    }
                }
            } else if (control instanceof Spinner) {
                @SuppressWarnings("unchecked")
                Spinner<Double> spinner = (Spinner<Double>) control;
                value = String.valueOf(spinner.getValue());
            }
            
            // Alle Werte speichern, auch leere (damit Platzhalter ersetzt werden)
            result.put(entry.getKey(), value);
        }
    }
    
    /**
     * Findet die Variable-Definition für eine Variable
     */
    private PluginVariable findVariableDefinition(Plugin plugin, String variableName) {
        for (PluginVariable varDef : plugin.getVariableDefinitions()) {
            if (varDef.getName().equals(variableName)) {
                return varDef;
            }
        }
        // Fallback: Erstelle eine Standard-Definition
        return new PluginVariable(variableName, PluginVariable.Type.SINGLE_LINE);
    }
    
    /**
     * Erstellt ein Eingabefeld basierend auf dem Variablen-Typ
     */
    private Control createInputField(PluginVariable varDef, String selectedText) {
        if (varDef.getType() == PluginVariable.Type.MULTI_LINE) {
            // TextArea für mehrzeilige Eingaben
            TextArea textArea = new TextArea();
            textArea.setPrefRowCount(4);
            textArea.setPrefColumnCount(40);
            textArea.setWrapText(true);
            textArea.setPromptText("Wert für " + varDef.getDisplayName());
            
            // Standard-Wert setzen
            String defaultValue = varDef.getDefaultValue();
            if (defaultValue != null && !defaultValue.isEmpty()) {
                textArea.setText(defaultValue);
            }
            
            return textArea;
        } else if (varDef.getType() == PluginVariable.Type.BOOLEAN) {
            // CheckBox für Boolean-Eingaben
            CheckBox checkBox = new CheckBox();
            checkBox.setPrefWidth(300);
            
            // Standard-Wert setzen
            String defaultValue = varDef.getDefaultValue();
            if (defaultValue != null && !defaultValue.isEmpty()) {
                boolean defaultBool = Boolean.parseBoolean(defaultValue.toLowerCase());
                checkBox.setSelected(defaultBool);
            }
            
            return checkBox;
        } else if (varDef.getType() == PluginVariable.Type.CHOICE) {
            // ComboBox für Auswahl-Eingaben
            ComboBox<String> comboBox = new ComboBox<>();
            comboBox.setPrefWidth(300);
            
            // Optionen hinzufügen
            if (varDef.getOptions() != null && !varDef.getOptions().isEmpty()) {
                for (PluginVariable.Option option : varDef.getOptions()) {
                    comboBox.getItems().add(option.getLabel());
                }
                // ComboBox editierbar machen falls gewünscht
                comboBox.setEditable(false);
                
                // Standard-Wert setzen
                String defaultValue = varDef.getDefaultValue();
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    // Suche nach dem Label zum Default-Value
                    for (PluginVariable.Option option : varDef.getOptions()) {
                        if (option.getValue().equals(defaultValue)) {
                            comboBox.setValue(option.getLabel());
                            break;
                        }
                    }
                    // Fallback: Falls Label nicht gefunden, versuche direkt zu setzen
                    if (comboBox.getValue() == null) {
                        comboBox.setValue(defaultValue);
                    }
                }
            } else {
                // Fallback falls keine Optionen definiert
                comboBox.setEditable(true);
                comboBox.setPromptText("Wert für " + varDef.getDisplayName());
                String defaultValue = varDef.getDefaultValue();
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    comboBox.setValue(defaultValue);
                }
            }
            
            return comboBox;
        } else if (varDef.getType() == PluginVariable.Type.NUMBER) {
            // Spinner für Zahlen-Eingaben
            double min = varDef.getMinValue() != null ? varDef.getMinValue() : 0;
            double max = varDef.getMaxValue() != null ? varDef.getMaxValue() : 100;
            double initialValue = min;
            
            // Standard-Wert setzen
            String defaultValue = varDef.getDefaultValue();
            if (defaultValue != null && !defaultValue.isEmpty()) {
                try {
                    initialValue = Double.parseDouble(defaultValue);
                    // Sicherstellen dass der Wert im gültigen Bereich liegt
                    initialValue = Math.max(min, Math.min(max, initialValue));
                } catch (NumberFormatException e) {
                    // Falls Parsing fehlschlägt, verwende Minimum
                    initialValue = min;
                }
            }
            
            Spinner<Double> spinner = new Spinner<>(min, max, initialValue, 1.0);
            spinner.setPrefWidth(300);
            spinner.setEditable(true);
            
            return spinner;
        } else {
            // TextField für einzeilige Eingaben (SINGLE_LINE)
            TextField textField = new TextField();
            textField.setPrefWidth(300);
            textField.setPromptText("Wert für " + varDef.getDisplayName());
            
            // Standard-Wert setzen
            String defaultValue = varDef.getDefaultValue();
            if (defaultValue != null && !defaultValue.isEmpty()) {
                textField.setText(defaultValue);
            }
            
            return textField;
        }
    }
    
    /**
     * Prüft ob es sich um eine "selektierter Text" Variable handelt
     */
    private boolean isSelectedTextVariable(String variable) {
        String lowerVar = variable.toLowerCase();
        return lowerVar.contains("selektierter") || 
               lowerVar.contains("selected") ||
               lowerVar.contains("text") ||
               lowerVar.equals("selektierter text") ||
               lowerVar.equals("selected text") ||
               variable.equals("Hier den Text einfügen, den du analysieren möchtest");
    }
    
    /**
     * Formatiert den Variablen-Namen für die Anzeige
     */
    private String formatVariableName(String variable) {
        return variable.replaceAll("([a-z])([A-Z])", "$1 $2")
                      .replaceAll("[-_]", " ")
                      .toLowerCase();
    }
    
    /**
     * Gibt Standard-Werte für bestimmte Variablen zurück
     */
    private String getDefaultValue(String variable, String selectedText) {
        String lowerVar = variable.toLowerCase();
        
        if (lowerVar.contains("genre")) {
            return "Fantasy";
        } else if (lowerVar.contains("länge") || lowerVar.contains("length")) {
            return "Roman";
        } else if (lowerVar.contains("zielgruppe") || lowerVar.contains("target")) {
            return "Erwachsene";
        } else if (lowerVar.contains("charakter") || lowerVar.contains("character")) {
            return "Unbekannter Charakter";
        } else if (lowerVar.contains("grundidee") || lowerVar.contains("idea")) {
            return selectedText != null ? selectedText : "";
        }
        
        return null;
    }

    /**
     * Aktualisiert die Prompt-Vorschau basierend auf den eingegebenen Variablenwerten.
     */
    private void updatePreview(TextArea previewArea, Plugin plugin) {
        try {
            // Sammle aktuelle Variablenwerte
            Map<String, String> currentValues = new HashMap<>();
            
            // Automatisch gesetzte Werte
            for (Map.Entry<String, Control> entry : variableFields.entrySet()) {
                String variable = entry.getKey();
                Control control = entry.getValue();
                
                if (isSelectedTextVariable(variable)) {
                    currentValues.put(variable, result.getOrDefault(variable, ""));
                } else {
                    String value = "";
                    if (control instanceof TextField) {
                        value = ((TextField) control).getText().trim();
                    } else if (control instanceof TextArea) {
                        value = ((TextArea) control).getText().trim();
                    } else if (control instanceof CheckBox) {
                        value = String.valueOf(((CheckBox) control).isSelected());
                    } else if (control instanceof ComboBox) {
                        @SuppressWarnings("unchecked")
                        ComboBox<String> comboBox = (ComboBox<String>) control;
                        String selectedLabel = comboBox.getValue();
                        
                        // Konvertiere Label zurück zu Value für die Verwendung im Prompt
                        if (selectedLabel != null) {
                            PluginVariable varDef = findVariableDefinition(currentPlugin, variable);
                            
                            // Suche nach dem Value zum ausgewählten Label
                            if (varDef != null && varDef.getOptions() != null) {
                                for (PluginVariable.Option option : varDef.getOptions()) {
                                    if (option.getLabel().equals(selectedLabel)) {
                                        value = option.getValue();
                                        break;
                                    }
                                }
                            }
                            
                            // Fallback: Falls keine Option gefunden, verwende das Label
                            if (value.isEmpty()) {
                                value = selectedLabel;
                            }
                        }
                    } else if (control instanceof Spinner) {
                        @SuppressWarnings("unchecked")
                        Spinner<Double> spinner = (Spinner<Double>) control;
                        value = String.valueOf(spinner.getValue());
                    }
                    // Alle Werte speichern, auch leere (damit Platzhalter ersetzt werden)
                    currentValues.put(variable, value);
                }
            }
            
            // Verarbeite den Prompt mit den aktuellen Werten
            String processedPrompt = plugin.getProcessedPrompt(currentValues);
            
            // Zeige den verarbeiteten Prompt an (vollständig)
            previewArea.setText(processedPrompt);
        } catch (Exception e) {
            previewArea.setText("Fehler beim Generieren der Vorschau: " + e.getMessage());
        }
    }
    
    /**
     * Wendet das Theme auf den Dialog an
     */
    private void applyDialogTheme(VBox mainContainer, int themeIndex) {
        // Theme-Klassen am Haupt-Container setzen
        if (themeIndex == 0) mainContainer.getStyleClass().add("weiss-theme");
        else if (themeIndex == 2) mainContainer.getStyleClass().add("pastell-theme");
        else if (themeIndex == 3) mainContainer.getStyleClass().addAll("theme-dark", "blau-theme");
        else if (themeIndex == 4) mainContainer.getStyleClass().addAll("theme-dark", "gruen-theme");
        else if (themeIndex == 5) mainContainer.getStyleClass().addAll("theme-dark", "lila-theme");
        else mainContainer.getStyleClass().add("theme-dark");
        
        // Dialog-Hintergrund und Text-Farbe basierend auf Theme
        String dialogStyle = "";
        String contentStyle = "";
        String labelStyle = "";
        String buttonStyle = "";
        String textFieldStyle = "";
        
        switch (themeIndex) {
            case 0: // Weiß
                dialogStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000;";
                contentStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000;";
                labelStyle = "-fx-text-fill: #000000;";
                buttonStyle = "-fx-background-color: #f0f0f0; -fx-text-fill: #000000; -fx-border-color: #cccccc;";
                textFieldStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-border-color: #cccccc;";
                break;
            case 1: // Schwarz
                dialogStyle = "-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #2d2d2d; -fx-text-fill: #ffffff; -fx-border-color: #404040;";
                textFieldStyle = "-fx-background-color: #2d2d2d; -fx-text-fill: #ffffff; -fx-border-color: #404040;";
                break;
            case 2: // Pastell
                dialogStyle = "-fx-background-color: #f3e5f5; -fx-text-fill: #000000;";
                contentStyle = "-fx-background-color: #f3e5f5; -fx-text-fill: #000000;";
                labelStyle = "-fx-text-fill: #000000;";
                buttonStyle = "-fx-background-color: #e1bee7; -fx-text-fill: #000000; -fx-border-color: #ba68c8;";
                textFieldStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-border-color: #ba68c8;";
                break;
            case 3: // Blau
                dialogStyle = "-fx-background-color: #1e3a8a; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #1e3a8a; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #3b82f6; -fx-text-fill: #ffffff; -fx-border-color: #1d4ed8;";
                textFieldStyle = "-fx-background-color: #1e3a8a; -fx-text-fill: #ffffff; -fx-border-color: #3b82f6;";
                break;
            case 4: // Grün
                dialogStyle = "-fx-background-color: #064e3b; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #064e3b; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #059669; -fx-text-fill: #ffffff; -fx-border-color: #047857;";
                textFieldStyle = "-fx-background-color: #064e3b; -fx-text-fill: #ffffff; -fx-border-color: #059669;";
                break;
            case 5: // Lila
                dialogStyle = "-fx-background-color: #581c87; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #581c87; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #7c3aed; -fx-text-fill: #ffffff; -fx-border-color: #6d28d9;";
                textFieldStyle = "-fx-background-color: #581c87; -fx-text-fill: #ffffff; -fx-border-color: #7c3aed;";
                break;
        }
        
        // Styles anwenden
        mainContainer.setStyle(dialogStyle);
        
        // Alle Labels stylen - rekursiv durch alle Container
        styleAllNodes(mainContainer, labelStyle, buttonStyle, textFieldStyle, themeIndex);
    }
    
    /**
     * Stylt alle Nodes rekursiv
     */
    private void styleAllNodes(javafx.scene.Node node, String labelStyle, String buttonStyle, String textFieldStyle, int themeIndex) {
        if (node instanceof Label) {
            node.setStyle(labelStyle);
        } else if (node instanceof Button) {
            node.setStyle(buttonStyle);
        } else if (node instanceof TextField) {
            node.setStyle(textFieldStyle);
        } else if (node instanceof TextArea) {
            // Spezielle TextArea-Styles - verwende die Theme-Farben direkt
            String textAreaStyle = "";
            switch (themeIndex) {
                case 0: // Weiß
                    textAreaStyle = "-fx-control-inner-background: #ffffff; -fx-text-inner-color: #000000; -fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-border-color: #cccccc; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;";
                    break;
                case 1: // Schwarz
                    textAreaStyle = "-fx-control-inner-background: #2d2d2d; -fx-text-inner-color: #ffffff; -fx-background-color: #2d2d2d; -fx-text-fill: #ffffff; -fx-border-color: #404040; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;";
                    break;
                case 2: // Pastell
                    textAreaStyle = "-fx-control-inner-background: #ffffff; -fx-text-inner-color: #000000; -fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-border-color: #ba68c8; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;";
                    break;
                case 3: // Blau
                    textAreaStyle = "-fx-control-inner-background: #1e3a8a; -fx-text-inner-color: #ffffff; -fx-background-color: #1e3a8a; -fx-text-fill: #ffffff; -fx-border-color: #3b82f6; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;";
                    break;
                case 4: // Grün
                    textAreaStyle = "-fx-control-inner-background: #064e3b; -fx-text-inner-color: #ffffff; -fx-background-color: #064e3b; -fx-text-fill: #ffffff; -fx-border-color: #059669; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;";
                    break;
                case 5: // Lila
                    textAreaStyle = "-fx-control-inner-background: #581c87; -fx-text-inner-color: #ffffff; -fx-background-color: #581c87; -fx-text-fill: #ffffff; -fx-border-color: #7c3aed; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;";
                    break;
                default:
                    textAreaStyle = "-fx-control-inner-background: #2d2d2d; -fx-text-inner-color: #ffffff; -fx-background-color: #2d2d2d; -fx-text-fill: #ffffff; -fx-border-color: #404040; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;";
            }
            node.setStyle(textAreaStyle);
        } else if (node instanceof VBox || node instanceof HBox || node instanceof GridPane) {
            // Rekursiv durch alle Kinder
            if (node instanceof VBox) {
                VBox vbox = (VBox) node;
                for (javafx.scene.Node child : vbox.getChildren()) {
                    styleAllNodes(child, labelStyle, buttonStyle, textFieldStyle, themeIndex);
                }
            } else if (node instanceof HBox) {
                HBox hbox = (HBox) node;
                for (javafx.scene.Node child : hbox.getChildren()) {
                    styleAllNodes(child, labelStyle, buttonStyle, textFieldStyle, themeIndex);
                }
            } else if (node instanceof GridPane) {
                GridPane grid = (GridPane) node;
                for (javafx.scene.Node child : grid.getChildren()) {
                    styleAllNodes(child, labelStyle, buttonStyle, textFieldStyle, themeIndex);
                }
            }
        }
    }
}
