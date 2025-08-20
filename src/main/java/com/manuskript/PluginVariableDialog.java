package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.logging.Logger;
import com.manuskript.ResourceManager;

/**
 * Dynamischer Dialog für Plugin-Variablen
 */
public class PluginVariableDialog {
    
    private CustomStage dialog;
    private Map<String, Control> variableFields = new HashMap<>();
    private Map<String, String> result = new HashMap<>();
    private boolean confirmed = false;
    private int currentThemeIndex = 0;
    
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
        // Dialog erstellen
        dialog = StageManager.createStage("Plugin-Variablen");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Plugin-Variablen: " + plugin.getName());
        dialog.setResizable(false);
        
        // Haupt-Container
        VBox mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.CENTER);
        
        // Plugin-Header mit Name und Beschreibung
        VBox headerBox = new VBox(5);
        Label nameLabel = new Label("📦 " + plugin.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2E86AB;");
        
        Label descriptionLabel = new Label(plugin.getDescription());
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        
        headerBox.getChildren().addAll(nameLabel, descriptionLabel);
        
        // Anleitung
        Label instructionLabel = new Label("Fülle die folgenden Variablen aus, um das Plugin zu verwenden:");
        instructionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333;");
        
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
            label.setStyle("-fx-font-weight: bold;");
            
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
            Label previewLabel = new Label("Prompt-Vorschau:");
            previewLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333;");
            
            TextArea previewArea = new TextArea();
            previewArea.setPrefRowCount(4);
            previewArea.setEditable(false);
            previewArea.setWrapText(true);
            previewArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
            
            // Live-Vorschau aktualisieren
            previewArea.setText("Fülle die Variablen aus, um den Prompt zu sehen...");
            
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
        
        // Alles zusammenfügen
        mainContainer.getChildren().addAll(headerBox, instructionLabel, grid, previewBox, buttonBox);
        
        // Dialog anzeigen
        Scene scene = new Scene(mainContainer);
        
        // CSS-Styles laden und anwenden
        try {
            String stylesCss = com.manuskript.ResourceManager.getCssResource("css/styles.css");
            String editorCss = com.manuskript.ResourceManager.getCssResource("css/editor.css");
            if (stylesCss != null) {
                scene.getStylesheets().add(stylesCss);
            }
            if (editorCss != null) {
                scene.getStylesheets().add(editorCss);
            }
        } catch (Exception e) {
            Logger.getLogger(PluginVariableDialog.class.getName()).warning("Fehler beim Laden der CSS-Styles: " + e.getMessage());
        }
        
        // Theme-Klassen setzen
        applyDialogTheme(mainContainer, currentThemeIndex);
        
        dialog.setSceneWithTitleBar(scene);
        dialog.showAndWait();
        
        return confirmed ? result : null;
    }
    
    /**
     * Sammelt die eingegebenen Werte
     */
    private void collectResults() {
        for (Map.Entry<String, Control> entry : variableFields.entrySet()) {
            String value = "";
            if (entry.getValue() instanceof TextField) {
                value = ((TextField) entry.getValue()).getText().trim();
            } else if (entry.getValue() instanceof TextArea) {
                value = ((TextArea) entry.getValue()).getText().trim();
            } else if (entry.getValue() instanceof CheckBox) {
                value = String.valueOf(((CheckBox) entry.getValue()).isSelected());
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
        } else {
            // TextField für einzeilige Eingaben
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
        return variable.toLowerCase().contains("selektierter") || 
               variable.toLowerCase().contains("selected") ||
               variable.toLowerCase().contains("text") ||
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
                    }
                    // Alle Werte speichern, auch leere (damit Platzhalter ersetzt werden)
                    currentValues.put(variable, value);
                }
            }
            
            // Verarbeite den Prompt mit den aktuellen Werten
            String processedPrompt = plugin.getProcessedPrompt(currentValues);
            
            // Zeige den verarbeiteten Prompt an
            if (processedPrompt.length() > 500) {
                previewArea.setText(processedPrompt.substring(0, 500) + "\n\n... (gekürzt)");
            } else {
                previewArea.setText(processedPrompt);
            }
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
        
        // Alle Labels stylen
        for (javafx.scene.Node node : mainContainer.getChildren()) {
            if (node instanceof Label) {
                node.setStyle(labelStyle);
            } else if (node instanceof GridPane) {
                GridPane grid = (GridPane) node;
                for (javafx.scene.Node gridNode : grid.getChildren()) {
                    if (gridNode instanceof Label) {
                        gridNode.setStyle(labelStyle);
                    } else if (gridNode instanceof TextField) {
                        gridNode.setStyle(textFieldStyle);
                    }
                }
            } else if (node instanceof HBox) {
                HBox buttonBox = (HBox) node;
                for (javafx.scene.Node buttonNode : buttonBox.getChildren()) {
                    if (buttonNode instanceof Button) {
                        buttonNode.setStyle(buttonStyle);
                    }
                }
            }
        }
    }
}
