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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private final TextArea instructionArea;
    private final CheckBox useParameterModelCheck;
    private final FilterableModelSelector modelSelector;
    private final ComboBox<SceneContextSize> contextSizeCombo;
    private final Button generateButton;
    private final Button insertButton;
    private final Label statusLabel;
    private final Label metaLabel;
    private final ScrollPane metaScroll;
    private final TextArea resultArea;

    private Runnable onConfigChanged;
    private Consumer<String> onInsertClicked;
    private SceneGenerationHandler generationHandler;

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
        temperatureValueLabel.setPrefWidth(55);
        temperatureValueLabel.setMinWidth(55);
        temperatureValueLabel.setAlignment(Pos.CENTER_RIGHT);
        temperatureSlider.valueProperty().addListener((obs, old, val) -> {
            temperatureValueLabel.setText(formatValue(val.doubleValue()));
            config.setTemperature(val.doubleValue());
            fireConfigChanged();
        });
        temperatureSlider.setTooltip(new Tooltip(
                "Überschreibt die globale Temperatur aus dem Parameter-Tab für Szenen-Generierung."));

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
        configBox.getChildren().addAll(
                promptLabel, promptArea, tempRow,
                useParameterModelCheck, modelLabel, modelSelector, contextSizeRow,
                defaultInstructionLabel, defaultInstructionArea);

        Label instructionLabel = new Label("Anweisung:");
        instructionArea = new TextArea(loadDefaultInstruction());
        instructionArea.setPrefRowCount(3);
        instructionArea.setWrapText(true);
        instructionArea.setMaxWidth(Double.MAX_VALUE);
        instructionArea.setPromptText("Wird aus dem Default-Prompt in der Konfiguration vorausgefüllt.");

        generateButton = new Button("Szene generieren");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.getStyleClass().add("button primary");
        generateButton.setOnAction(e -> startGeneration());

        statusLabel = new Label("Bereit");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("agent-status-label");

        metaLabel = new Label();
        metaLabel.setWrapText(true);
        metaLabel.setMaxWidth(Double.MAX_VALUE);
        metaLabel.getStyleClass().add("scene-meta-label");

        metaScroll = new ScrollPane(metaLabel);
        metaScroll.getStyleClass().add("scene-meta-scroll");
        AgentScrollPaneSupport.configureFindingsScrollPane(metaScroll);
        metaScroll.setPrefViewportHeight(72);
        metaScroll.setMaxHeight(120);
        metaScroll.setVisible(false);
        metaScroll.setManaged(false);
        metaScroll.widthProperty().addListener((obs, oldW, newW) ->
                metaLabel.setMaxWidth(Math.max(0, newW.doubleValue() - 4)));

        resultArea = new TextArea();
        resultArea.setPrefRowCount(12);
        resultArea.setWrapText(true);
        resultArea.setEditable(true);
        resultArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

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
            statusLabel,
            insertButton,
            metaScroll,
            resultArea
        );

        toggleConfigButton.selectedProperty().addListener((obs, o, sel) -> {
            configBox.setVisible(sel);
            configBox.setManaged(sel);
            AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, resultArea, sel);
        });
        AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, resultArea, false);
    }

    private void setMetaHintVisible(boolean visible) {
        metaScroll.setVisible(visible);
        metaScroll.setManaged(visible);
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

    private void startGeneration() {
        if (generating || generationHandler == null) {
            return;
        }
        String instruction = instructionArea.getText();
        if (instruction == null || instruction.isBlank()) {
            statusLabel.setText("Bitte eine Anweisung eingeben.");
            return;
        }
        generating = true;
        generateButton.setDisable(true);
        insertButton.setDisable(true);
        setConfigControlsDisabled(true);
        setMetaHintVisible(false);
        resultArea.clear();
        statusLabel.setText("Generiere Szene…");
        registerActivity(config.getName() + ": Szene wird generiert…");

        boolean useParams = useParameterModelCheck.isSelected();
        String model = modelSelector.getValue();
        SceneContextSize contextSize = contextSizeCombo.getValue();
        if (contextSize == null) {
            contextSize = SceneContextSize.COMPACT;
        }

        String validationError = generationHandler.generate(
            instruction.trim(),
            contextSize,
            useParams,
            model,
            msg -> Platform.runLater(() -> statusLabel.setText(msg)),
            result -> Platform.runLater(() -> finishGeneration(result)),
            err -> Platform.runLater(() -> {
                generating = false;
                generateButton.setDisable(false);
                setConfigControlsDisabled(false);
                unregisterActivity();
                statusLabel.setText("Fehler: " + (err.getMessage() != null ? err.getMessage() : err.toString()));
            })
        );
        if (validationError != null) {
            abortGeneration(validationError);
        }
    }

    private void abortGeneration(String message) {
        generating = false;
        generateButton.setDisable(false);
        setConfigControlsDisabled(false);
        unregisterActivity();
        statusLabel.setText(message);
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
            if (result.isParsedFromTags()) {
                statusLabel.setText("Szene generiert.");
            } else {
                statusLabel.setText("Szene generiert (ohne SCENE-Tags — Rohtext übernommen).");
            }
            if (result.getMetaText() != null && !result.getMetaText().isBlank()) {
                metaLabel.setText("Hinweis (wird nicht eingefügt): " + result.getMetaText());
                setMetaHintVisible(true);
            }
        } else {
            statusLabel.setText("Keine Szene in der Antwort.");
        }
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
        statusLabel.setText("Lade Modelle…");
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
                    statusLabel.setText(models.size() + " Modelle geladen.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Modelle laden fehlgeschlagen: " + e.getMessage()));
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

    private void fireConfigChanged() {
        if (onConfigChanged != null) {
            onConfigChanged.run();
        }
    }

    private void setConfigControlsDisabled(boolean disabled) {
        promptArea.setDisable(disabled);
        defaultInstructionArea.setDisable(disabled);
        temperatureSlider.setDisable(disabled);
        contextSizeCombo.setDisable(disabled);
        useParameterModelCheck.setDisable(disabled);
        if (disabled) {
            modelSelector.setSelectorDisabled(true);
        } else {
            modelSelector.setSelectorDisabled(useParameterModelCheck.isSelected());
        }
    }

    private static HBox createSliderRow(String labelText, Slider slider, Label valueLabel) {
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
        if (v == (long) v) {
            return String.valueOf((long) v);
        }
        return String.format("%.2f", v);
    }

    public void applyFontSize(int size) {
        AgentFontSizeSupport.apply(this, size, metaLabel);
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
