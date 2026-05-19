package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Ein einzelner Agent-Tab: Konfiguration (einklappbar), Aktionen und Findings-Liste.
 */
public class AgentTab extends VBox {

    private final AgentConfig config;

    // Konfigurations-UI
    private final ToggleButton toggleConfigButton;
    private final VBox configBox;
    private final TextField nameField;
    private final TextArea promptArea;
    private final ComboBox<String> backendCombo;
    private final ComboBox<String> modelCombo;
    private final Slider temperatureSlider;
    private final Label temperatureValueLabel;
    private final Slider maxTokensSlider;
    private final Label maxTokensValueLabel;
    private final Slider topPSlider;
    private final Label topPValueLabel;
    private final Slider repeatPenaltySlider;
    private final Label repeatPenaltyValueLabel;
    private final Button restoreDefaultsButton;

    // Aktions-UI
    private final Button analyzeButton;
    private final ToggleButton realtimeToggle;
    private final Label statusLabel;

    // Findings-UI
    private final VBox findingsList;
    private final Label emptyLabel;
    private final ScrollPane scrollPane;

    // Callbacks
    private Runnable onAnalyzeClicked;
    private Consumer<Boolean> onRealtimeToggled;
    private Consumer<String> onQuoteClicked;
    private Consumer<Finding> onSuggestionClicked;
    private Runnable onConfigChanged;

    private boolean realtimeEnabled = false;
    private boolean analyzing = false;
    private List<String> availableModels = new ArrayList<>();

    public AgentTab(AgentConfig config) {
        this.config = config;

        setPadding(new Insets(8));
        setSpacing(6);
        getStyleClass().add("agent-tab");

        // === Konfigurations-Toggle ===
        toggleConfigButton = new ToggleButton("⚙ Konfiguration");
        toggleConfigButton.setMaxWidth(Double.MAX_VALUE);
        toggleConfigButton.getStyleClass().add("agent-config-toggle");
        toggleConfigButton.setSelected(false);

        // === Konfigurationsbereich ===
        configBox = new VBox(6);
        configBox.setPadding(new Insets(8));
        configBox.getStyleClass().add("agent-config-box");
        configBox.setVisible(false);
        configBox.setManaged(false);

        // Name
        Label nameLabel = new Label("Name:");
        nameField = new TextField(config.getName());
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameField.textProperty().addListener((obs, old, val) -> {
            config.setName(val);
            fireConfigChanged();
        });

        // System-Prompt
        Label promptLabel = new Label("System-Prompt:");
        promptArea = new TextArea(config.getSystemPrompt());
        promptArea.setPrefRowCount(8);
        promptArea.setWrapText(true);
        promptArea.setMaxWidth(Double.MAX_VALUE);
        promptArea.textProperty().addListener((obs, old, val) -> {
            config.setSystemPrompt(val);
            fireConfigChanged();
        });

        // Backend
        Label backendLabel = new Label("Backend:");
        backendCombo = new ComboBox<>();
        backendCombo.getItems().addAll("Ollama", "OpenAI");
        backendCombo.setMaxWidth(Double.MAX_VALUE);
        backendCombo.setValue(config.getBackend() != null ? config.getBackend() : "Ollama");
        backendCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                config.setBackend(val);
                fireConfigChanged();
            }
        });

        // Modell
        Label modelLabel = new Label("Modell:");
        modelCombo = new ComboBox<>();
        modelCombo.setMaxWidth(Double.MAX_VALUE);
        modelCombo.setEditable(true);
        modelCombo.setValue(config.getModel());
        modelCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null && !val.equals(old)) {
                config.setModel(val);
                ModelHistory.addModel(val);
                // Neue Modelle zu availableModels hinzufügen
                if (!availableModels.contains(val)) {
                    availableModels.add(val);
                }
                // Dropdown aktualisieren
                List<String> modelsWithHistory = ModelHistory.getHistoryWithAvailableModels(availableModels);
                modelCombo.getItems().setAll(modelsWithHistory);
                modelCombo.setValue(val);
                fireConfigChanged();
            }
        });
        modelCombo.setOnAction(e -> {
            String val = modelCombo.getValue();
            if (val != null && !val.trim().isEmpty()) {
                config.setModel(val);
                ModelHistory.addModel(val);
                // Neue Modelle zu availableModels hinzufügen
                if (!availableModels.contains(val)) {
                    availableModels.add(val);
                }
                // Dropdown aktualisieren
                List<String> modelsWithHistory = ModelHistory.getHistoryWithAvailableModels(availableModels);
                modelCombo.getItems().setAll(modelsWithHistory);
                modelCombo.setValue(val);
                fireConfigChanged();
            }
        });

        // Temperature
        temperatureSlider = new Slider(0.0, 2.0, config.getTemperature());
        temperatureSlider.setMajorTickUnit(0.1);
        temperatureSlider.setBlockIncrement(0.1);
        temperatureValueLabel = new Label(formatValue(config.getTemperature()));
        temperatureValueLabel.setPrefWidth(55);
        temperatureValueLabel.setMinWidth(55);
        temperatureValueLabel.setAlignment(Pos.CENTER_RIGHT);
        temperatureSlider.valueProperty().addListener((obs, old, val) -> {
            temperatureValueLabel.setText(formatValue(val.doubleValue()));
            config.setTemperature(val.doubleValue());
            fireConfigChanged();
        });
        HBox tempRow = createSliderRow("Temperature:", temperatureSlider, temperatureValueLabel);

        // Max Tokens
        maxTokensSlider = new Slider(256, 8192, config.getMaxTokens());
        maxTokensSlider.setMajorTickUnit(512);
        maxTokensSlider.setBlockIncrement(128);
        maxTokensSlider.setSnapToTicks(true);
        maxTokensValueLabel = new Label(String.valueOf(config.getMaxTokens()));
        maxTokensValueLabel.setPrefWidth(55);
        maxTokensValueLabel.setMinWidth(55);
        maxTokensValueLabel.setAlignment(Pos.CENTER_RIGHT);
        maxTokensSlider.valueProperty().addListener((obs, old, val) -> {
            int intVal = (int) val.doubleValue();
            maxTokensValueLabel.setText(String.valueOf(intVal));
            config.setMaxTokens(intVal);
            fireConfigChanged();
        });
        HBox tokensRow = createSliderRow("Max Tokens:", maxTokensSlider, maxTokensValueLabel);

        // Top-P
        topPSlider = new Slider(0.0, 1.0, config.getTopP());
        topPSlider.setMajorTickUnit(0.1);
        topPSlider.setBlockIncrement(0.05);
        topPValueLabel = new Label(formatValue(config.getTopP()));
        topPValueLabel.setPrefWidth(55);
        topPValueLabel.setMinWidth(55);
        topPValueLabel.setAlignment(Pos.CENTER_RIGHT);
        topPSlider.valueProperty().addListener((obs, old, val) -> {
            topPValueLabel.setText(formatValue(val.doubleValue()));
            config.setTopP(val.doubleValue());
            fireConfigChanged();
        });
        HBox topPRow = createSliderRow("Top-P:", topPSlider, topPValueLabel);

        // Repeat Penalty
        repeatPenaltySlider = new Slider(1.0, 2.0, config.getRepeatPenalty());
        repeatPenaltySlider.setMajorTickUnit(0.1);
        repeatPenaltySlider.setBlockIncrement(0.05);
        repeatPenaltyValueLabel = new Label(formatValue(config.getRepeatPenalty()));
        repeatPenaltyValueLabel.setPrefWidth(55);
        repeatPenaltyValueLabel.setMinWidth(55);
        repeatPenaltyValueLabel.setAlignment(Pos.CENTER_RIGHT);
        repeatPenaltySlider.valueProperty().addListener((obs, old, val) -> {
            repeatPenaltyValueLabel.setText(formatValue(val.doubleValue()));
            config.setRepeatPenalty(val.doubleValue());
            fireConfigChanged();
        });
        HBox penaltyRow = createSliderRow("Repeat Penalty:", repeatPenaltySlider, repeatPenaltyValueLabel);

        // Restore Defaults
        restoreDefaultsButton = new Button("↺ Auf Standard zurücksetzen");
        restoreDefaultsButton.setMaxWidth(Double.MAX_VALUE);
        restoreDefaultsButton.getStyleClass().add("agent-restore-btn");
        restoreDefaultsButton.setOnAction(e -> restoreDefaults());

        configBox.getChildren().addAll(
            nameLabel, nameField,
            promptLabel, promptArea,
            backendLabel, backendCombo,
            modelLabel, modelCombo,
            tempRow, tokensRow, topPRow, penaltyRow,
            restoreDefaultsButton
        );

        toggleConfigButton.setOnAction(e -> {
            boolean show = toggleConfigButton.isSelected();
            configBox.setVisible(show);
            configBox.setManaged(show);
        });

        // === Aktionsbereich ===
        analyzeButton = new Button("▶ Jetzt prüfen");
        analyzeButton.setMaxWidth(Double.MAX_VALUE);
        analyzeButton.getStyleClass().add("agent-analyze-btn");
        analyzeButton.setOnAction(e -> {
            if (onAnalyzeClicked != null) onAnalyzeClicked.run();
        });

        realtimeToggle = new ToggleButton("⚡ Echtzeit");
        realtimeToggle.setMaxWidth(Double.MAX_VALUE);
        realtimeToggle.setTooltip(new Tooltip("Beim Tippen automatisch prüfen"));
        realtimeToggle.getStyleClass().add("agent-realtime-btn");
        realtimeToggle.setOnAction(e -> {
            realtimeEnabled = realtimeToggle.isSelected();
            if (onRealtimeToggled != null) onRealtimeToggled.accept(realtimeEnabled);
        });

        HBox buttonRow = new HBox(6);
        buttonRow.getChildren().addAll(analyzeButton, realtimeToggle);
        HBox.setHgrow(analyzeButton, Priority.ALWAYS);
        HBox.setHgrow(realtimeToggle, Priority.ALWAYS);

        // Status
        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("agent-status");

        // === Findings-Liste ===
        findingsList = new VBox(6);
        findingsList.setPadding(new Insets(4, 0, 4, 0));
        findingsList.getStyleClass().add("agent-findings-list");

        emptyLabel = new Label("Noch keine Analyse.\nKlicke ▶ oder aktiviere ⚡.");
        emptyLabel.getStyleClass().add("agent-empty-label");
        emptyLabel.setWrapText(true);
        findingsList.getChildren().add(emptyLabel);

        scrollPane = new ScrollPane(findingsList);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("agent-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(toggleConfigButton, configBox, statusLabel, buttonRow, scrollPane);
    }

    private HBox createSliderRow(String labelText, Slider slider, Label valueLabel) {
        Label caption = new Label(labelText);
        caption.setPrefWidth(110);
        caption.setMinWidth(110);

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(caption, slider, valueLabel);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private static String formatValue(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    // === Öffentliche API ===

    public AgentConfig getAgentConfig() {
        return config;
    }

    public String getAgentId() {
        return config.getId();
    }

    public String getAgentName() {
        return config.getName();
    }

    public void setModels(List<String> models) {
        availableModels = new ArrayList<>(models);
        Platform.runLater(() -> {
            String current = modelCombo.getValue();
            List<String> modelsWithHistory = ModelHistory.getHistoryWithAvailableModels(availableModels);
            modelCombo.getItems().setAll(modelsWithHistory);
        });
    }

    public void setModel(String model) {
        Platform.runLater(() -> {
            modelCombo.setValue(model);
            config.setModel(model);
        });
    }

    public void refreshFromConfig() {
        nameField.setText(config.getName());
        promptArea.setText(config.getSystemPrompt());
        backendCombo.setValue(config.getBackend() != null ? config.getBackend() : "Ollama");
        modelCombo.setValue(config.getModel());
        temperatureSlider.setValue(config.getTemperature());
        maxTokensSlider.setValue(config.getMaxTokens());
        topPSlider.setValue(config.getTopP());
        repeatPenaltySlider.setValue(config.getRepeatPenalty());
    }

    public void restoreDefaults() {
        config.restoreDefaults();
        refreshFromConfig();
        fireConfigChanged();
    }

    // === Callbacks ===

    public void setOnAnalyzeClicked(Runnable callback) {
        this.onAnalyzeClicked = callback;
    }

    public void setOnRealtimeToggled(Consumer<Boolean> callback) {
        this.onRealtimeToggled = callback;
    }

    public void setOnQuoteClicked(Consumer<String> callback) {
        this.onQuoteClicked = callback;
    }

    public void setOnSuggestionClicked(Consumer<Finding> callback) {
        this.onSuggestionClicked = callback;
    }

    public void setOnConfigChanged(Runnable callback) {
        this.onConfigChanged = callback;
    }

    private void fireConfigChanged() {
        if (onConfigChanged != null) onConfigChanged.run();
    }

    // === Status ===

    public boolean isRealtimeEnabled() {
        return realtimeEnabled;
    }

    public void setRealtimeEnabled(boolean enabled) {
        this.realtimeEnabled = enabled;
        realtimeToggle.setSelected(enabled);
    }

    public boolean isAnalyzing() {
        return analyzing;
    }

    // === Findings ===

    public void showFindings(List<Finding> findings) {
        Platform.runLater(() -> {
            findingsList.getChildren().clear();

            if (findings.isEmpty()) {
                Label ok = new Label("✅ Keine Widersprüche gefunden.");
                ok.getStyleClass().add("agent-ok-label");
                findingsList.getChildren().add(ok);
                statusLabel.setText("Analyse abgeschlossen - keine Probleme");
                statusLabel.getStyleClass().removeAll("agent-status-error");
                statusLabel.getStyleClass().add("agent-status-ok");
            } else {
                statusLabel.setText(findings.size() + " Widersprüche gefunden");
                statusLabel.getStyleClass().removeAll("agent-status-ok");
                statusLabel.getStyleClass().add("agent-status-error");

                for (Finding f : findings) {
                    VBox card = createFindingCard(f);
                    findingsList.getChildren().add(card);
                }
            }
            analyzing = false;
            analyzeButton.setDisable(false);
        });
    }

    private VBox createFindingCard(Finding f) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(6));
        card.getStyleClass().add("finding-card");
        card.setMaxWidth(Double.MAX_VALUE);

        // Schriftgröße aus Preferences lesen (wie im Editor)
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(com.manuskript.MainController.class);
        int editorFontSize = prefs.getInt("fontSize", 12);

        Label severityLabel = new Label(f.getSeverityStars());
        severityLabel.getStyleClass().add("finding-severity");
        severityLabel.setWrapText(true);
        severityLabel.setMinWidth(0);
        severityLabel.setMaxWidth(Double.MAX_VALUE);
        severityLabel.setStyle(severityLabel.getStyle() + String.format("-fx-font-size: %dpx;", editorFontSize));

        String theme = com.manuskript.ResourceManager.getParameter("main_window_theme", "0");
        boolean isDarkTheme = theme.equals("1") || theme.equals("3");
        String textColor = isDarkTheme ? "#e0e0e0" : "#000000";

        Text problemText = new Text(f.getProblem());
        problemText.setStyle(String.format("-fx-fill: %s; -fx-font-size: %dpx;", textColor, editorFontSize));
        TextFlow problemFlow = new TextFlow(problemText);
        problemFlow.getStyleClass().add("finding-problem");
        problemFlow.setMinWidth(0);
        problemFlow.setMaxWidth(Double.MAX_VALUE);
        problemFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));

        Text quoteText = new Text("Zitat: " + f.getQuote());
        String quoteColor = isDarkTheme ? "#ffb74d" : "#666";
        quoteText.setStyle(String.format("-fx-fill: %s; -fx-font-size: %dpx;", quoteColor, editorFontSize));
        TextFlow quoteFlow = new TextFlow(quoteText);
        quoteFlow.getStyleClass().add("finding-quote-text");
        quoteFlow.setMinWidth(0);
        quoteFlow.setMaxWidth(Double.MAX_VALUE);
        quoteFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));
        quoteFlow.setCursor(javafx.scene.Cursor.HAND);
        quoteFlow.setOnMouseClicked(e -> {
            if (onQuoteClicked != null && f.getQuote() != null && !f.getQuote().isEmpty()) {
                onQuoteClicked.accept(f.getQuoteWithIndex());
            }
        });

        // Vorschläge anzeigen (mehrere möglich)
        VBox suggestionsBox = new VBox();
        suggestionsBox.setSpacing(4);

        if (f.getSuggestions() != null && !f.getSuggestions().isEmpty()) {
            for (int i = 0; i < f.getSuggestions().size(); i++) {
                String suggestion = f.getSuggestions().get(i);
                Text suggestionTextNode = new Text("Vorschlag " + (i + 1) + ": " + suggestion);
                String suggestionStyle = String.format("-fx-font-size: %dpx;", editorFontSize);
                if (f.getSuggestionIndex() >= 0) {
                    String suggestionTheme = com.manuskript.ResourceManager.getParameter("main_window_theme", "0");
                    boolean suggestionIsDarkTheme = suggestionTheme.equals("1") || suggestionTheme.equals("3");
                    String suggestionColor = suggestionIsDarkTheme ? "#81c784" : "#2e7d32";
                    suggestionStyle = String.format("-fx-fill: %s; -fx-font-size: %dpx;", suggestionColor, editorFontSize);
                }
                suggestionTextNode.setStyle(suggestionStyle);
                TextFlow suggestionFlow = new TextFlow(suggestionTextNode);
                suggestionFlow.getStyleClass().add("finding-suggestion");
                suggestionFlow.setMinWidth(0);
                suggestionFlow.setMaxWidth(Double.MAX_VALUE);
                suggestionFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));

                if (f.getSuggestionIndex() >= 0) {
                    suggestionFlow.setCursor(javafx.scene.Cursor.HAND);
                    final String finalSuggestion = suggestion;
                    suggestionFlow.setOnMouseClicked(e -> {
                        if (onSuggestionClicked != null && finalSuggestion != null && !finalSuggestion.isEmpty()) {
                            Finding tempFinding = new Finding(f.getSeverity(), f.getQuote(), f.getProblem(), finalSuggestion);
                            tempFinding.setSuggestionIndex(f.getSuggestionIndex());
                            onSuggestionClicked.accept(tempFinding);
                        }
                    });
                }

                suggestionsBox.getChildren().add(suggestionFlow);
            }
        } else {
            Text suggestionTextNode = new Text("Vorschlag: " + f.getSuggestion());
            String suggestionStyle = String.format("-fx-font-size: %dpx;", editorFontSize);
            if (f.getSuggestionIndex() >= 0) {
                String suggestionTheme = com.manuskript.ResourceManager.getParameter("main_window_theme", "0");
                boolean suggestionIsDarkTheme = suggestionTheme.equals("1") || suggestionTheme.equals("3");
                String suggestionColor = suggestionIsDarkTheme ? "#81c784" : "#2e7d32";
                suggestionStyle = String.format("-fx-fill: %s; -fx-font-size: %dpx;", suggestionColor, editorFontSize);
            }
            suggestionTextNode.setStyle(suggestionStyle);
            TextFlow suggestionFlow = new TextFlow(suggestionTextNode);
            suggestionFlow.getStyleClass().add("finding-suggestion");
            suggestionFlow.setMinWidth(0);
            suggestionFlow.setMaxWidth(Double.MAX_VALUE);
            suggestionFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));

            if (f.getSuggestionIndex() >= 0) {
                suggestionFlow.setCursor(javafx.scene.Cursor.HAND);
                suggestionFlow.setOnMouseClicked(e -> {
                    if (onSuggestionClicked != null && f.getSuggestion() != null && !f.getSuggestion().isEmpty()) {
                        onSuggestionClicked.accept(f);
                    }
                });
            }
            suggestionsBox.getChildren().add(suggestionFlow);
        }

        card.getChildren().addAll(severityLabel, problemFlow, quoteFlow, suggestionsBox);
        return card;
    }

    public void setAnalyzing(boolean analyzing) {
        this.analyzing = analyzing;
        Platform.runLater(() -> {
            analyzeButton.setDisable(analyzing);
            if (analyzing) {
                String model = modelCombo.getValue();
                if (model != null && !model.isEmpty()) {
                    statusLabel.setText("Analysiere... (Modell: " + model + ")");
                } else {
                    statusLabel.setText("Analysiere...");
                }
                statusLabel.getStyleClass().removeAll("agent-status-ok", "agent-status-error");
                statusLabel.getStyleClass().add("agent-status-running");
            }
        });
    }

    public void clearFindings() {
        Platform.runLater(() -> {
            findingsList.getChildren().clear();
            findingsList.getChildren().add(emptyLabel);
            statusLabel.setText("Bereit");
            statusLabel.getStyleClass().removeAll("agent-status-ok", "agent-status-error", "agent-status-running");
        });
    }

    public void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("Fehler: " + message);
            statusLabel.getStyleClass().removeAll("agent-status-ok", "agent-status-running");
            statusLabel.getStyleClass().add("agent-status-error");
            analyzing = false;
            analyzeButton.setDisable(false);
        });
    }
}
