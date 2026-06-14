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
public class AgentTab extends ScrollPane {

    /** Mindesthöhe des System-Prompt-Felds in sichtbaren Zeilen (alle Agenten-Tabs). */
    static final int SYSTEM_PROMPT_VISIBLE_ROWS = 20;

    private final AgentConfig config;
    private final VBox contentRoot;

    // Konfigurations-UI
    private final ToggleButton toggleConfigButton;
    private final VBox configBox;
    private final TextField nameField;
    private final TextArea promptArea;
    private final ComboBox<String> backendCombo;
    private final FilterableModelSelector modelSelector;
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
    private boolean activityRegistered = false;
    private AgentActivityTracker activityTracker;
    private int currentFontSize = 12;
    private List<String> availableModels = new ArrayList<>();
    private TextArea revisionInstructionField;

    /** Letzte Markierung für den Überarbeiten-Agenten (Ersetzung auch bei Platzhalter-Zitat). */
    private int revisionSelectionStart = -1;
    private int revisionSelectionEnd = -1;
    private String revisionSelectedText = "";

    public AgentTab(AgentConfig config) {
        this.config = config;

        contentRoot = new VBox(6);
        contentRoot.setPadding(new Insets(8));
        contentRoot.setFillWidth(true);
        contentRoot.setMinHeight(0);
        contentRoot.getStyleClass().add("agent-tab");

        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setMinHeight(0);
        AgentScrollPaneSupport.configureEntireTabScroll(this);
        setContent(contentRoot);

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
        promptArea.setPrefRowCount(SYSTEM_PROMPT_VISIBLE_ROWS);
        promptArea.setMinHeight(SYSTEM_PROMPT_VISIBLE_ROWS * 18.0);
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
        modelSelector = new FilterableModelSelector(false);
        modelSelector.setUseModelHistory(true);
        modelSelector.setValue(config.getModel());
        modelSelector.setOnModelChanged(model -> {
            config.setModel(model);
            if (!availableModels.contains(model)) {
                availableModels.add(model);
            }
            fireConfigChanged();
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
            modelLabel, modelSelector,
            tempRow, tokensRow, topPRow, penaltyRow,
            restoreDefaultsButton
        );

        // === Aktionsbereich ===
        boolean selectionRevision = config.isSelectionRevisionAgent();
        analyzeButton = new Button(selectionRevision ? "▶ Markierung prüfen" : "▶ Jetzt prüfen");
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
        AgentActionButtonSupport.configureRow(buttonRow, analyzeButton, realtimeToggle);
        if (selectionRevision) {
            realtimeToggle.setVisible(false);
            realtimeToggle.setManaged(false);
        }

        // Status
        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("agent-status");

        // === Findings-Liste ===
        findingsList = new VBox(6);
        findingsList.setPadding(new Insets(4, 0, 4, 0));
        findingsList.getStyleClass().add("agent-findings-list");

        emptyLabel = new Label(selectionRevision
                ? "Text markieren, Anweisung optional eingeben,\ndann ▶ oder Rechtsklick → Überarbeiten (Agent)."
                : "Noch keine Analyse.\nKlicke ▶ oder aktiviere ⚡.");
        emptyLabel.getStyleClass().add("agent-empty-label");
        emptyLabel.setWrapText(true);
        findingsList.getChildren().add(emptyLabel);

        scrollPane = new ScrollPane(findingsList);
        AgentScrollPaneSupport.configureFindingsScrollPane(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        if (selectionRevision) {
            Label instructionLabel = new Label("Anweisung (optional):");
            revisionInstructionField = new TextArea();
            revisionInstructionField.setPromptText("z.B. Name der Gruppe ergänzen, klarer formulieren");
            revisionInstructionField.setWrapText(true);
            revisionInstructionField.setPrefRowCount(4);
            revisionInstructionField.setMinHeight(4 * 18.0);
            revisionInstructionField.setMaxWidth(Double.MAX_VALUE);
            String savedInstruction = com.manuskript.SelectionRevisionDialog.loadPersistedInstruction();
            if (savedInstruction != null && !savedInstruction.isBlank()) {
                revisionInstructionField.setText(savedInstruction);
            }
            revisionInstructionField.textProperty().addListener((obs, old, val) ->
                    com.manuskript.SelectionRevisionDialog.syncInstruction(val));
            VBox instructionBox = new VBox(4, instructionLabel, revisionInstructionField);
            contentRoot.getChildren().addAll(toggleConfigButton, configBox, instructionBox, statusLabel, buttonRow, scrollPane);
        } else {
            contentRoot.getChildren().addAll(toggleConfigButton, configBox, statusLabel, buttonRow, scrollPane);
        }

        toggleConfigButton.setOnAction(e -> {
            boolean show = toggleConfigButton.isSelected();
            configBox.setVisible(show);
            configBox.setManaged(show);
            AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, scrollPane, show);
        });
        AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, scrollPane, false);
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

    public void applyFontSize(int size) {
        if (size < 8) {
            size = 8;
        } else if (size > 72) {
            size = 72;
        }
        currentFontSize = size;
        AgentFontSizeSupport.apply(this, size);
        AgentActionButtonSupport.applyFontSize(size, analyzeButton, realtimeToggle);
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
        Platform.runLater(() -> modelSelector.setModels(availableModels));
    }

    public void setModel(String model) {
        Platform.runLater(() -> {
            modelSelector.setValue(model);
            config.setModel(model);
        });
    }

    public String getRevisionInstruction() {
        if (revisionInstructionField == null) {
            return "";
        }
        String text = revisionInstructionField.getText();
        return text != null ? text.trim() : "";
    }

    public void setRevisionInstruction(String instruction) {
        if (revisionInstructionField != null) {
            revisionInstructionField.setText(instruction != null ? instruction : "");
        }
    }

    /** Speichert die aktuelle Editor-Markierung für Ersetzung und Zitat-Anzeige. */
    public void setSelectionRevisionContext(int start, int end, String selectedText) {
        this.revisionSelectionStart = start;
        this.revisionSelectionEnd = end;
        this.revisionSelectedText = selectedText != null ? selectedText : "";
    }

    public void clearSelectionRevisionContext() {
        revisionSelectionStart = -1;
        revisionSelectionEnd = -1;
        revisionSelectedText = "";
    }

    private boolean isSelectionRevisionAgent() {
        return config.isSelectionRevisionAgent();
    }

    public void refreshFromConfig() {
        nameField.setText(config.getName());
        promptArea.setText(config.getSystemPrompt());
        backendCombo.setValue(config.getBackend() != null ? config.getBackend() : "Ollama");
        modelSelector.setValue(config.getModel());
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

    public void bindActivityTracker(AgentActivityTracker tracker) {
        this.activityTracker = tracker;
    }

    private void registerActivity(String message) {
        if (activityTracker != null && !activityRegistered) {
            activityTracker.begin(message);
            activityRegistered = true;
        }
    }

    private void unregisterActivity() {
        if (activityTracker != null && activityRegistered) {
            activityTracker.end();
            activityRegistered = false;
        }
    }

    private String activityMessageForAnalyzing() {
        String name = config.getName() != null ? config.getName() : "Agent";
        String model = modelSelector.getValue();
        if (model != null && !model.isBlank()) {
            return name + ": Analysiere… (Modell: " + model + ")";
        }
        return name + ": Analysiere…";
    }

    public boolean isAnalyzing() {
        return analyzing;
    }

    // === Findings ===

    public void showParseResult(PlotholeParseResult result) {
        if (result == null) {
            showError("Keine Antwort vom Analysemodul.");
            return;
        }
        Platform.runLater(() -> {
            findingsList.getChildren().clear();
            switch (result.getOutcome()) {
                case UNPARSEABLE -> {
                    Label warn = new Label("⚠ " + result.getDetailMessage());
                    warn.getStyleClass().add("agent-empty-label");
                    warn.setWrapText(true);
                    findingsList.getChildren().add(warn);
                    statusLabel.setText("Antwort nicht auswertbar");
                    statusLabel.getStyleClass().removeAll("agent-status-ok");
                    statusLabel.getStyleClass().add("agent-status-error");
                }
                case NO_PROBLEMS -> {
                    Label ok = new Label(isSelectionRevisionAgent()
                            ? "✅ Keine Überarbeitung nötig — der markierte Text ist ausreichend."
                            : "✅ Keine Widersprüche gefunden.");
                    ok.getStyleClass().add("agent-ok-label");
                    findingsList.getChildren().add(ok);
                    statusLabel.setText(isSelectionRevisionAgent()
                            ? "Markierung in Ordnung"
                            : "Analyse abgeschlossen - keine Probleme");
                    statusLabel.getStyleClass().removeAll("agent-status-error");
                    statusLabel.getStyleClass().add("agent-status-ok");
                }
                case FINDINGS -> {
                    List<Finding> findings = result.getFindings();
                    if (isSelectionRevisionAgent()) {
                        findings = enrichSelectionRevisionFindings(findings);
                    }
                    showFindingsInternal(findings);
                }
            }
            analyzing = false;
            analyzeButton.setDisable(false);
            unregisterActivity();
        });
    }

    public void showFindings(List<Finding> findings) {
        showParseResult(PlotholeParseResult.findings(findings != null ? findings : List.of()));
    }

    private void showFindingsInternal(List<Finding> findings) {
        if (findings.isEmpty()) {
            Label ok = new Label(isSelectionRevisionAgent()
                    ? "✅ Keine Überarbeitung nötig — der markierte Text ist ausreichend."
                    : "✅ Keine Widersprüche gefunden.");
            ok.getStyleClass().add("agent-ok-label");
            findingsList.getChildren().add(ok);
            statusLabel.setText(isSelectionRevisionAgent()
                    ? "Markierung in Ordnung"
                    : "Analyse abgeschlossen - keine Probleme");
            statusLabel.getStyleClass().removeAll("agent-status-error");
            statusLabel.getStyleClass().add("agent-status-ok");
            return;
        }
        if (isSelectionRevisionAgent()) {
            statusLabel.setText(findings.size() == 1
                    ? "Überarbeitung empfohlen"
                    : findings.size() + " Überarbeitungsvorschläge");
        } else {
            statusLabel.setText(findings.size() + " Widersprüche gefunden");
        }
        statusLabel.getStyleClass().removeAll("agent-status-ok");
        statusLabel.getStyleClass().add("agent-status-error");
        for (Finding f : findings) {
            findingsList.getChildren().add(createFindingCard(f));
        }
    }

    private VBox createFindingCard(Finding f) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(6));
        card.getStyleClass().add("finding-card");
        card.setMaxWidth(Double.MAX_VALUE);

        // Schriftgröße aus aktuellem Editor-Stand (wird via applyFontSize gesetzt)
        int editorFontSize = currentFontSize;

        Label severityLabel = new Label(f.getSeverityStars());
        severityLabel.getStyleClass().add("finding-severity");
        severityLabel.setWrapText(true);
        severityLabel.setMinWidth(0);
        severityLabel.setMaxWidth(Double.MAX_VALUE);
        severityLabel.setStyle(severityLabel.getStyle() + String.format("-fx-font-size: %dpx;", editorFontSize));

        Text problemText = new Text(AgentFindingDisplay.stripIndexField(f.getProblem()));
        problemText.setStyle(AgentFindingStyles.problemTextStyle(editorFontSize));
        TextFlow problemFlow = new TextFlow(problemText);
        problemFlow.getStyleClass().add("finding-problem");
        problemFlow.setMinWidth(0);
        problemFlow.setMaxWidth(Double.MAX_VALUE);
        problemFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));

        Text quoteText = new Text("Zitat: " + AgentFindingDisplay.formatQuotePreview(
                isSelectionRevisionAgent() && revisionSelectedText != null && !revisionSelectedText.isBlank()
                        ? revisionSelectedText
                        : AgentFindingDisplay.stripIndexField(f.getQuote())));
        quoteText.setStyle(AgentFindingStyles.quoteTextStyle(editorFontSize));
        TextFlow quoteFlow = new TextFlow(quoteText);
        quoteFlow.getStyleClass().add("finding-quote-text");
        quoteFlow.setMinWidth(0);
        quoteFlow.setMaxWidth(Double.MAX_VALUE);
        quoteFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));
        quoteFlow.setCursor(javafx.scene.Cursor.HAND);
        quoteFlow.setOnMouseClicked(e -> {
            String jumpQuote = isSelectionRevisionAgent()
                    && revisionSelectedText != null && !revisionSelectedText.isBlank()
                    ? revisionSelectedText
                    : f.getQuote();
            if (onQuoteClicked != null && jumpQuote != null && !jumpQuote.isEmpty()) {
                onQuoteClicked.accept(jumpQuote);
            }
        });

        // Vorschläge anzeigen (mehrere möglich)
        VBox suggestionsBox = new VBox();
        suggestionsBox.setSpacing(4);

        if (f.getSuggestions() != null && !f.getSuggestions().isEmpty()) {
            for (int i = 0; i < f.getSuggestions().size(); i++) {
                String suggestion = f.getSuggestions().get(i);
                Text suggestionTextNode = new Text("Vorschlag " + (i + 1) + ": "
                        + AgentFindingDisplay.stripIndexField(suggestion));
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
                            tempFinding.setReplaceRangeStart(f.getReplaceRangeStart());
                            tempFinding.setReplaceRangeEnd(f.getReplaceRangeEnd());
                            onSuggestionClicked.accept(tempFinding);
                        }
                    });
                }

                suggestionsBox.getChildren().add(suggestionFlow);
            }
        } else {
            Text suggestionTextNode = new Text("Vorschlag: "
                    + AgentFindingDisplay.stripIndexField(f.getSuggestion()));
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

    private List<Finding> enrichSelectionRevisionFindings(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return findings != null ? findings : List.of();
        }
        if (revisionSelectedText == null || revisionSelectedText.isBlank()
                || revisionSelectionStart < 0 || revisionSelectionEnd <= revisionSelectionStart) {
            return findings;
        }
        List<Finding> enriched = new ArrayList<>();
        for (Finding source : findings) {
            Finding f = copyFinding(source);
            f.setQuote(revisionSelectedText);
            f.setReplaceRangeStart(revisionSelectionStart);
            f.setReplaceRangeEnd(revisionSelectionEnd);
            f.setProblem(trimLeakedProblemText(f.getProblem()));
            enriched.add(f);
        }
        return enriched;
    }

    private static Finding copyFinding(Finding source) {
        Finding f = new Finding(source.getSeverity(), source.getQuote(), source.getProblem(), source.getSuggestion());
        if (source.getSuggestions() != null) {
            f.setSuggestions(new ArrayList<>(source.getSuggestions()));
        }
        f.setSuggestionIndex(source.getSuggestionIndex());
        return f;
    }

    private static String trimLeakedProblemText(String problem) {
        if (problem == null || problem.isBlank()) {
            return problem != null ? problem : "";
        }
        String lower = problem.toLowerCase(java.util.Locale.ROOT);
        int cut = -1;
        for (String marker : new String[]{"vorschläge:", "vorschlaege:", "vorschlag:"}) {
            int idx = lower.indexOf(marker);
            if (idx >= 0 && (cut < 0 || idx < cut)) {
                cut = idx;
            }
        }
        if (cut >= 0) {
            return problem.substring(0, cut).trim();
        }
        return problem;
    }

    public void setAnalyzing(boolean analyzing) {
        this.analyzing = analyzing;
        Platform.runLater(() -> {
            analyzeButton.setDisable(analyzing);
            if (analyzing) {
                String model = modelSelector.getValue();
                if (model != null && !model.isEmpty()) {
                    statusLabel.setText("Analysiere... (Modell: " + model + ")");
                } else {
                    statusLabel.setText("Analysiere...");
                }
                statusLabel.getStyleClass().removeAll("agent-status-ok", "agent-status-error");
                statusLabel.getStyleClass().add("agent-status-running");
                registerActivity(activityMessageForAnalyzing());
            } else {
                unregisterActivity();
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
            unregisterActivity();
        });
    }
}
