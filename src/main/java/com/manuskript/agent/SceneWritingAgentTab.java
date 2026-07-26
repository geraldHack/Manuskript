package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import com.manuskript.ResourceManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Agent-Tab für Szenen-Generierung (kein Finding-basiertes Analyse-UI).
 */
public class SceneWritingAgentTab extends ScrollPane {

    private static final String PREF_CONTEXT_SIZE = "context_size";
    private static final String PREF_DEFAULT_INSTRUCTION = "default_instruction";

    private final AgentConfig config;
    private final VBox contentRoot;

    private final ToggleButton toggleConfigButton;
    private final VBox configBox;
    private final TextArea promptArea;
    private final TextArea defaultInstructionArea;
    private final Slider temperatureSlider;
    private final Label temperatureValueLabel;
    private final Slider maxTokensSlider;
    private final Label maxTokensValueLabel;
    private final TextArea instructionArea;
    private final TextArea feedbackArea;
    private final CheckBox useParameterModelCheck;
    private final FilterableModelSelector modelSelector;
    private final ComboBox<SceneContextSize> contextSizeCombo;
    private final Button generateButton;
    private final Button reviseButton;
    private final Button insertButton;
    private final TabPane resultTabs;
    private final Tab resultTab;
    private final Tab metaTab;
    private final TextArea metaArea;
    private final TextArea resultArea;

    private Runnable onConfigChanged;
    private Consumer<String> onInsertClicked;
    private Consumer<String> onStatus;
    private Consumer<String> onStatusError;
    private SceneGenerationHandler generationHandler;
    private SceneRevisionHandler revisionHandler;

    private boolean generating = false;
    private boolean activityRegistered = false;
    private AgentActivityTracker activityTracker;
    private List<String> availableModels = new ArrayList<>();

    public interface SceneGenerationHandler {
        /**
         * @return null wenn Generierung gestartet wurde, sonst Validierungsfehlermeldung
         */
        String generate(String instruction, SceneContextSize contextSize, boolean useParameterModel,
                        String overrideModel,
                      Consumer<String> onStatus, Consumer<SceneWritingAgent.GenerationResult> onComplete,
                      Consumer<Throwable> onError);
    }

    public interface SceneRevisionHandler {
        /**
         * @return null wenn Überarbeitung gestartet wurde, sonst Validierungsfehlermeldung
         */
        String revise(String instruction, String draft, String feedback, SceneContextSize contextSize,
                      boolean useParameterModel, String overrideModel,
                      Consumer<String> onStatus, Consumer<SceneWritingAgent.GenerationResult> onComplete,
                      Consumer<Throwable> onError);
    }

    public SceneWritingAgentTab(AgentConfig config) {
        this.config = config;

        contentRoot = new VBox(6);
        contentRoot.setPadding(new Insets(8));
        contentRoot.setMinHeight(0);
        contentRoot.getStyleClass().addAll("agent-tab", "scene-writing-agent-tab");

        setMinHeight(0);
        AgentScrollPaneSupport.configureEntireTabScroll(this);
        setContent(contentRoot);

        toggleConfigButton = new ToggleButton("⚙ Konfiguration");
        toggleConfigButton.setMaxWidth(Double.MAX_VALUE);
        toggleConfigButton.setSelected(false);
        toggleConfigButton.setTooltip(new Tooltip("System-Prompt, Temperatur, Modell und Kontext"));

        configBox = new VBox(6);
        configBox.setPadding(new Insets(8));
        configBox.getStyleClass().add("agent-config-box");
        configBox.setVisible(false);
        configBox.setManaged(false);

        promptArea = new TextArea(config.getSystemPrompt());
        promptArea.setPrefRowCount(AgentTab.SYSTEM_PROMPT_VISIBLE_ROWS);
        promptArea.setMinHeight(AgentTab.SYSTEM_PROMPT_VISIBLE_ROWS * 18.0);
        promptArea.setWrapText(true);
        promptArea.setMaxWidth(Double.MAX_VALUE);
        promptArea.textProperty().addListener((obs, o, n) -> {
            config.setSystemPrompt(n);
            fireConfigChanged();
        });

        temperatureSlider = new Slider(0.0, 2.0, config.getTemperature());
        temperatureSlider.setMajorTickUnit(0.1);
        temperatureSlider.setBlockIncrement(0.1);
        temperatureValueLabel = new Label(formatValue(config.getTemperature()));
        configureSliderValueLabel(temperatureValueLabel);
        temperatureSlider.valueProperty().addListener((obs, old, val) -> {
            temperatureValueLabel.setText(formatValue(val.doubleValue()));
            config.setTemperature(val.doubleValue());
            fireConfigChanged();
        });
        temperatureSlider.setTooltip(new Tooltip(
                "Überschreibt die globale Temperatur aus dem Parameter-Tab für Szenen-Generierung."));

        int initialMaxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : 16384;
        maxTokensSlider = new Slider(1024, 32768, initialMaxTokens);
        maxTokensSlider.setMajorTickUnit(1024);
        maxTokensSlider.setBlockIncrement(512);
        maxTokensSlider.setSnapToTicks(true);
        maxTokensValueLabel = new Label(String.valueOf(initialMaxTokens));
        configureSliderValueLabel(maxTokensValueLabel);
        maxTokensSlider.valueProperty().addListener((obs, old, val) -> {
            int intVal = (int) Math.round(val.doubleValue());
            maxTokensValueLabel.setText(String.valueOf(intVal));
            config.setMaxTokens(intVal);
            fireConfigChanged();
        });
        maxTokensSlider.setTooltip(new Tooltip(
                "Maximale Ausgabe-Tokens. Reasoning-Modelle (z. B. Kimi) brauchen oft 8192+."));

        useParameterModelCheck = new CheckBox("Parameter-Modell verwenden");
        useParameterModelCheck.setSelected(true);
        useParameterModelCheck.setTooltip(new Tooltip("Modell aus den globalen Agenten-Parametern nutzen"));

        modelSelector = new FilterableModelSelector(true);
        modelSelector.setSelectorDisabled(true);
        modelSelector.setOnLoad(this::loadModelsAsync);

        useParameterModelCheck.selectedProperty().addListener((obs, o, useParams) -> {
            if (!generating) {
                modelSelector.setSelectorDisabled(useParams);
            }
        });

        Label modelLabel = new Label("Modell:");

        Label contextSizeLabel = new Label("Kontext:");
        contextSizeCombo = new ComboBox<>();
        contextSizeCombo.getItems().setAll(SceneContextSize.values());
        contextSizeCombo.setConverter(contextSizeConverter());
        contextSizeCombo.setButtonCell(contextSizeListCell());
        contextSizeCombo.setCellFactory(list -> contextSizeListCell());
        contextSizeCombo.setMaxWidth(Double.MAX_VALUE);
        Preferences scenePrefs = Preferences.userNodeForPackage(SceneWritingAgentTab.class);
        contextSizeCombo.setValue(SceneContextSize.fromName(
                scenePrefs.get(PREF_CONTEXT_SIZE, SceneContextSize.COMPACT.name())));
        contextSizeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                scenePrefs.put(PREF_CONTEXT_SIZE, newVal.name());
                contextSizeCombo.setTooltip(new Tooltip(newVal.getTooltip()));
            }
        });
        if (contextSizeCombo.getValue() != null) {
            contextSizeCombo.setTooltip(new Tooltip(contextSizeCombo.getValue().getTooltip()));
        }
        HBox contextSizeRow = new HBox(8, contextSizeLabel, contextSizeCombo);
        HBox.setHgrow(contextSizeCombo, Priority.ALWAYS);
        contextSizeRow.setAlignment(Pos.CENTER_LEFT);

        Label promptLabel = new Label("System-Prompt:");
        Label defaultInstructionLabel = new Label("Default-Prompt (Anweisung):");
        defaultInstructionArea = new TextArea(loadDefaultInstruction());
        defaultInstructionArea.setPromptText(
                "z.B. Schreibe Szene 1, berücksichtige die Stimmung aus dem letzten Kapitel. 1000–1500 Zeichen.");
        defaultInstructionArea.setPrefRowCount(4);
        defaultInstructionArea.setMinHeight(4 * 18.0);
        defaultInstructionArea.setWrapText(true);
        defaultInstructionArea.setMaxWidth(Double.MAX_VALUE);
        defaultInstructionArea.setTooltip(new Tooltip(
                "Standardtext für das Anweisungsfeld — wird beim Öffnen des Tabs vorausgefüllt."));
        defaultInstructionArea.textProperty().addListener((obs, o, n) ->
                persistDefaultInstruction(n));

        HBox tempRow = createSliderRow("Temperatur:", temperatureSlider, temperatureValueLabel);
        HBox tokensRow = createSliderRow("Max Tokens:", maxTokensSlider, maxTokensValueLabel);
        configBox.getChildren().addAll(
                promptLabel, promptArea, tempRow, tokensRow,
                useParameterModelCheck, modelLabel, modelSelector, contextSizeRow,
                defaultInstructionLabel, defaultInstructionArea);

        Label instructionLabel = new Label("Anweisung:");
        instructionArea = new TextArea(loadDefaultInstruction());
        instructionArea.setPrefRowCount(3);
        instructionArea.setWrapText(true);
        instructionArea.setMaxWidth(Double.MAX_VALUE);
        instructionArea.setPromptText("Wird aus dem Default-Prompt in der Konfiguration vorausgefüllt.");

        Label feedbackLabel = new Label("Feedback (was soll anders werden?):");
        feedbackArea = new TextArea();
        feedbackArea.setPrefRowCount(3);
        feedbackArea.setWrapText(true);
        feedbackArea.setMaxWidth(Double.MAX_VALUE);
        feedbackArea.setPromptText("z.B. Einstieg weniger konstruiert, Dialog natürlicher, Szene kürzer …");
        feedbackArea.setDisable(true);
        feedbackArea.textProperty().addListener((obs, oldText, newText) -> updateReviseButtonState());

        generateButton = new Button("Szene generieren");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.getStyleClass().add("button primary");
        generateButton.setOnAction(e -> startGeneration());

        reviseButton = new Button("Überarbeiten");
        reviseButton.setMaxWidth(Double.MAX_VALUE);
        reviseButton.setDisable(true);
        reviseButton.setTooltip(new Tooltip(
                "Entwurf mit Feedback neu generieren (kein Chat — ein neuer Vorschlag)"));
        reviseButton.setOnAction(e -> startRevision());

        resultArea = new TextArea();
        resultArea.setPrefRowCount(12);
        resultArea.setWrapText(true);
        resultArea.setEditable(true);
        resultArea.setMaxWidth(Double.MAX_VALUE);
        resultArea.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        resultArea.textProperty().addListener((obs, oldText, newText) ->
                updateReviseButtonState());

        metaArea = new TextArea();
        metaArea.setPromptText("Hinweise des Modells erscheinen hier (werden nicht eingefügt).");
        metaArea.setWrapText(true);
        metaArea.setEditable(false);
        metaArea.setMaxWidth(Double.MAX_VALUE);
        metaArea.setMaxHeight(Double.MAX_VALUE);
        metaArea.getStyleClass().add("scene-meta-area");
        VBox.setVgrow(metaArea, Priority.ALWAYS);

        resultTab = new Tab("Ergebnis");
        resultTab.setClosable(false);
        VBox resultBox = new VBox(resultArea);
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        resultBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        resultTab.setContent(resultBox);

        metaTab = new Tab("Hinweis");
        metaTab.setClosable(false);
        metaTab.setDisable(true);
        metaTab.setTooltip(new Tooltip("Optionale Meta-Hinweise des Modells — nicht Teil der Szene"));
        VBox metaBox = new VBox(metaArea);
        VBox.setVgrow(metaArea, Priority.ALWAYS);
        metaBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        metaTab.setContent(metaBox);

        resultTabs = new TabPane(resultTab, metaTab);
        resultTabs.getStyleClass().add("scene-result-tabs");
        resultTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        resultTabs.setMinHeight(160);
        resultTabs.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(resultTabs, Priority.ALWAYS);

        insertButton = new Button("An Cursorposition einfügen");
        insertButton.setMaxWidth(Double.MAX_VALUE);
        insertButton.setDisable(true);
        insertButton.setOnAction(e -> {
            String text = resultArea.getText();
            if (text != null && !text.isBlank() && onInsertClicked != null) {
                onInsertClicked.accept(text);
            }
        });

        contentRoot.getChildren().addAll(
            toggleConfigButton,
            configBox,
            instructionLabel,
            instructionArea,
            generateButton,
            feedbackLabel,
            feedbackArea,
            reviseButton,
            insertButton,
            resultTabs
        );

        toggleConfigButton.selectedProperty().addListener((obs, o, sel) -> {
            configBox.setVisible(sel);
            configBox.setManaged(sel);
            AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, resultTabs, sel);
        });
        AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, resultTabs, false);
    }

    private void clearMetaHint() {
        metaArea.clear();
        metaTab.setDisable(true);
        metaTab.setText("Hinweis");
        resultTabs.getSelectionModel().select(resultTab);
    }

    private void showMetaHint(String metaText) {
        String cleaned = metaText != null ? metaText.trim() : "";
        if (cleaned.isEmpty()) {
            clearMetaHint();
            return;
        }
        metaArea.setText(cleaned);
        metaTab.setDisable(false);
        metaTab.setText("Hinweis");
        metaTab.setTooltip(new Tooltip("Wird nicht eingefügt — nur zur Orientierung"));
    }

    public void bindActivityTracker(AgentActivityTracker tracker) {
        this.activityTracker = tracker;
    }

    public void setOnStatus(Consumer<String> onStatus) {
        this.onStatus = onStatus;
    }

    public void setOnStatusError(Consumer<String> onStatusError) {
        this.onStatusError = onStatusError;
    }

    private void reportStatus(String message) {
        if (onStatus != null && message != null) {
            onStatus.accept(message);
        }
    }

    private void reportStatusError(String message) {
        if (onStatusError != null && message != null) {
            onStatusError.accept(message);
        }
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

    private void startGeneration() {
        if (generating || generationHandler == null) {
            return;
        }
        String instruction = instructionArea.getText();
        if (instruction == null || instruction.isBlank()) {
            reportStatusError("Bitte eine Anweisung eingeben.");
            return;
        }
        beginGenerationRun("Generiere Szene…", true);
        Platform.runLater(() -> runGeneration(false, instruction.trim(), null, null));
    }

    private void startRevision() {
        if (generating || revisionHandler == null) {
            return;
        }
        String draft = resultArea.getText();
        if (draft == null || draft.isBlank()) {
            reportStatusError("Kein Entwurf zum Überarbeiten.");
            return;
        }
        String feedback = feedbackArea.getText();
        if (feedback == null || feedback.isBlank()) {
            reportStatusError("Bitte Feedback eingeben (was soll anders werden?).");
            return;
        }
        String instruction = instructionArea.getText();
        if (instruction == null || instruction.isBlank()) {
            reportStatusError("Bitte die ursprüngliche Anweisung beibehalten oder ergänzen.");
            return;
        }
        beginGenerationRun("Überarbeite Szene…", false);
        Platform.runLater(() -> runGeneration(true, instruction.trim(), draft.trim(), feedback.trim()));
    }

    private void beginGenerationRun(String statusMessage, boolean clearResult) {
        generating = true;
        generateButton.setDisable(true);
        reviseButton.setDisable(true);
        insertButton.setDisable(true);
        setConfigControlsDisabled(true);
        if (clearResult) {
            resultArea.clear();
            clearMetaHint();
            feedbackArea.clear();
            feedbackArea.setDisable(true);
        } else {
            // Überarbeitung: Hinweis aus vorherigem Lauf zurücksetzen
            clearMetaHint();
        }
        reportStatus(statusMessage);
        String name = config.getName() != null ? config.getName() : "Szene Schreiben";
        registerActivity(name + ": " + statusMessage);
    }

    private void runGeneration(boolean revision, String instruction, String draft, String feedback) {
        boolean useParams = useParameterModelCheck.isSelected();
        String model = modelSelector.getValue();
        SceneContextSize contextSize = contextSizeCombo.getValue();
        if (contextSize == null) {
            contextSize = SceneContextSize.COMPACT;
        }

        String validationError;
        try {
            if (revision) {
                validationError = revisionHandler.revise(
                    instruction,
                    draft,
                    feedback,
                    contextSize,
                    useParams,
                    model,
                    msg -> Platform.runLater(() -> reportStatus(msg)),
                    result -> Platform.runLater(() -> finishGeneration(result)),
                    err -> Platform.runLater(() -> handleGenerationError(err))
                );
            } else {
                validationError = generationHandler.generate(
                    instruction,
                    contextSize,
                    useParams,
                    model,
                    msg -> Platform.runLater(() -> reportStatus(msg)),
                    result -> Platform.runLater(() -> finishGeneration(result)),
                    err -> Platform.runLater(() -> handleGenerationError(err))
                );
            }
        } catch (RuntimeException ex) {
            abortGeneration("Fehler: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
            return;
        }
        if (validationError != null) {
            abortGeneration(validationError);
            return;
        }
    }

    private void handleGenerationError(Throwable err) {
        generating = false;
        generateButton.setDisable(false);
        setConfigControlsDisabled(false);
        unregisterActivity();
        updateReviseButtonState();
        reportStatusError("Fehler: " + (err.getMessage() != null ? err.getMessage() : err.toString()));
    }

    private void updateReviseButtonState() {
        if (reviseButton == null) {
            return;
        }
        boolean hasDraft = resultArea.getText() != null && !resultArea.getText().isBlank();
        boolean hasFeedback = feedbackArea.getText() != null && !feedbackArea.getText().isBlank();
        reviseButton.setDisable(generating || !hasDraft || !hasFeedback || revisionHandler == null);
        feedbackArea.setDisable(!hasDraft || generating);
    }

    private void abortGeneration(String message) {
        generating = false;
        generateButton.setDisable(false);
        setConfigControlsDisabled(false);
        unregisterActivity();
        updateReviseButtonState();
        reportStatusError(message);
    }

    private void finishGeneration(SceneWritingAgent.GenerationResult result) {
        generating = false;
        generateButton.setDisable(false);
        setConfigControlsDisabled(false);
        unregisterActivity();
        if (result.getSceneText() != null && !result.getSceneText().isBlank()) {
            resultArea.setText(result.getSceneText());
            scrollResultToTop();
            insertButton.setDisable(false);
            feedbackArea.setDisable(false);
            if (result.isParsedFromTags()) {
                reportStatus("Szene fertig.");
            } else {
                reportStatus("Szene fertig (ohne SCENE-Tags — Rohtext übernommen).");
            }
            if (result.getMetaText() != null && !result.getMetaText().isBlank()) {
                showMetaHint(result.getMetaText());
            } else {
                clearMetaHint();
            }
        } else {
            reportStatusError("Keine Szene in der Antwort.");
        }
        updateReviseButtonState();
    }

    private void scrollResultToTop() {
        Platform.runLater(() -> {
            resultArea.positionCaret(0);
            try {
                resultArea.setScrollTop(0);
            } catch (Exception ignored) {
            }
        });
    }

    private static StringConverter<SceneContextSize> contextSizeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(SceneContextSize value) {
                return value == null ? "" : value.getLabel();
            }

            @Override
            public SceneContextSize fromString(String string) {
                for (SceneContextSize size : SceneContextSize.values()) {
                    if (size.getLabel().equals(string)) {
                        return size;
                    }
                }
                return SceneContextSize.COMPACT;
            }
        };
    }

    private static ListCell<SceneContextSize> contextSizeListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SceneContextSize item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getLabel());
            }
        };
    }

    private void loadModelsAsync() {
        reportStatus("Lade Modelle…");
        new Thread(() -> {
            try {
                AIBackend backend = createBackendForModelLoad();
                List<String> models = backend.getAvailableModels();
                Platform.runLater(() -> {
                    availableModels = new ArrayList<>(models);
                    modelSelector.setModels(models);
                    if (!models.isEmpty() && modelSelector.getValue() == null) {
                        modelSelector.setValue(models.get(0));
                    }
                    reportStatus(models.size() + " Modelle geladen.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> reportStatusError("Modelle laden fehlgeschlagen: " + e.getMessage()));
            }
        }, "SceneAgent-LoadModels").start();
    }

    private AIBackend createBackendForModelLoad() {
        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");
        if ("OpenAI".equals(backendType)) {
            return new OpenAIBackend();
        }
        return new OllamaBackend(new com.manuskript.OllamaService());
    }

    public void setModels(List<String> models) {
        if (models != null) {
            availableModels = new ArrayList<>(models);
            modelSelector.setModels(models);
            String paramModel = config.getModel();
            if (paramModel != null && !paramModel.isBlank()) {
                modelSelector.setValue(paramModel);
            } else if (!models.isEmpty()) {
                modelSelector.setValue(models.get(0));
            }
        }
    }

    public AgentConfig getAgentConfig() {
        return config;
    }

    public String getAgentId() {
        return config.getId();
    }

    public void setOnConfigChanged(Runnable handler) {
        this.onConfigChanged = handler;
    }

    public void setOnInsertClicked(Consumer<String> handler) {
        this.onInsertClicked = handler;
    }

    public void setGenerationHandler(SceneGenerationHandler handler) {
        this.generationHandler = handler;
    }

    public void setRevisionHandler(SceneRevisionHandler handler) {
        this.revisionHandler = handler;
        updateReviseButtonState();
    }

    private void fireConfigChanged() {
        if (onConfigChanged != null) {
            onConfigChanged.run();
        }
    }

    private void setConfigControlsDisabled(boolean disabled) {
        promptArea.setDisable(disabled);
        defaultInstructionArea.setDisable(disabled);
        temperatureSlider.setDisable(disabled);
        maxTokensSlider.setDisable(disabled);
        contextSizeCombo.setDisable(disabled);
        useParameterModelCheck.setDisable(disabled);
        if (disabled) {
            modelSelector.setSelectorDisabled(true);
        } else {
            modelSelector.setSelectorDisabled(useParameterModelCheck.isSelected());
        }
    }

    private static void configureSliderValueLabel(Label valueLabel) {
        valueLabel.setMinWidth(Region.USE_PREF_SIZE);
        valueLabel.setPrefWidth(Region.USE_COMPUTED_SIZE);
        valueLabel.setMaxWidth(Region.USE_PREF_SIZE);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(valueLabel, Priority.NEVER);
    }

    private static HBox createSliderRow(String labelText, Slider slider, Label valueLabel) {
        Label caption = new Label(labelText);
        caption.setMinWidth(Region.USE_PREF_SIZE);
        caption.setPrefWidth(Region.USE_COMPUTED_SIZE);
        caption.setMaxWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(caption, Priority.NEVER);
        configureSliderValueLabel(valueLabel);
        slider.setMinWidth(48);
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(caption, slider, valueLabel);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private static String formatValue(double v) {
        if (v == (long) v) {
            return String.valueOf((long) v);
        }
        return String.format("%.2f", v);
    }

    public void applyFontSize(int size) {
        applyEditorFont(null, size);
    }

    public void applyEditorFont(String fontFamily, int fontSizePx) {
        AgentFontSizeSupport.applyEditorFont(this, fontSizePx, fontFamily, null);
    }

    public static String loadDefaultInstruction() {
        return Preferences.userNodeForPackage(SceneWritingAgentTab.class)
                .get(PREF_DEFAULT_INSTRUCTION, "");
    }

    private static void persistDefaultInstruction(String instruction) {
        Preferences.userNodeForPackage(SceneWritingAgentTab.class)
                .put(PREF_DEFAULT_INSTRUCTION, instruction != null ? instruction : "");
    }
}
