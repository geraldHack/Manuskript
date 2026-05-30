package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Agent-Tab für Szenen-Generierung (kein Finding-basiertes Analyse-UI).
 */
public class SceneWritingAgentTab extends VBox {

    private final AgentConfig config;

    private final ToggleButton toggleConfigButton;
    private final VBox configBox;
    private final TextArea promptArea;
    private final TextArea instructionArea;
    private final CheckBox useParameterModelCheck;
    private final ComboBox<String> modelCombo;
    private final Button loadModelsButton;
    private final Button generateButton;
    private final Button insertButton;
    private final Label statusLabel;
    private final Label metaLabel;
    private final TextArea resultArea;

    private Runnable onConfigChanged;
    private Consumer<String> onInsertClicked;
    private SceneGenerationHandler generationHandler;

    private boolean generating = false;
    private List<String> availableModels = new ArrayList<>();

    public interface SceneGenerationHandler {
        /**
         * @return null wenn Generierung gestartet wurde, sonst Validierungsfehlermeldung
         */
        String generate(String instruction, boolean useParameterModel, String overrideModel,
                      Consumer<String> onStatus, Consumer<SceneWritingAgent.GenerationResult> onComplete,
                      Consumer<Throwable> onError);
    }

    public SceneWritingAgentTab(AgentConfig config) {
        this.config = config;
        setPadding(new Insets(8));
        setSpacing(6);
        getStyleClass().addAll("agent-tab", "scene-writing-agent-tab");

        toggleConfigButton = new ToggleButton("⚙ System-Prompt");
        toggleConfigButton.setMaxWidth(Double.MAX_VALUE);
        toggleConfigButton.setSelected(false);

        configBox = new VBox(6);
        configBox.setPadding(new Insets(8));
        configBox.getStyleClass().add("agent-config-box");
        configBox.setVisible(false);
        configBox.setManaged(false);

        promptArea = new TextArea(config.getSystemPrompt());
        promptArea.setPrefRowCount(6);
        promptArea.setWrapText(true);
        promptArea.setMaxWidth(Double.MAX_VALUE);
        promptArea.textProperty().addListener((obs, o, n) -> {
            config.setSystemPrompt(n);
            fireConfigChanged();
        });

        Label promptLabel = new Label("System-Prompt:");
        configBox.getChildren().addAll(promptLabel, promptArea);

        toggleConfigButton.selectedProperty().addListener((obs, o, sel) -> {
            configBox.setVisible(sel);
            configBox.setManaged(sel);
        });

        Label instructionLabel = new Label("Anweisung:");
        instructionArea = new TextArea();
        instructionArea.setPromptText("z.B. Schreibe 1. Szene, berücksichtige die Stimmung aus dem letzten Kapitel. 1000–1500 Zeichen.");
        instructionArea.setPrefRowCount(3);
        instructionArea.setWrapText(true);
        instructionArea.setMaxWidth(Double.MAX_VALUE);

        useParameterModelCheck = new CheckBox("Parameter-Modell verwenden");
        useParameterModelCheck.setSelected(true);
        useParameterModelCheck.setTooltip(new Tooltip("Modell aus den globalen Agenten-Parametern nutzen"));

        modelCombo = new ComboBox<>();
        modelCombo.setEditable(true);
        modelCombo.setMaxWidth(Double.MAX_VALUE);
        modelCombo.setDisable(true);

        loadModelsButton = new Button("Modelle laden");
        loadModelsButton.setDisable(true);

        useParameterModelCheck.selectedProperty().addListener((obs, o, useParams) -> {
            modelCombo.setDisable(useParams);
            loadModelsButton.setDisable(useParams);
        });

        loadModelsButton.setOnAction(e -> loadModelsAsync());

        HBox modelRow = new HBox(8, useParameterModelCheck);
        HBox modelSelectRow = new HBox(8, modelCombo, loadModelsButton);
        HBox.setHgrow(modelCombo, Priority.ALWAYS);
        modelSelectRow.setAlignment(Pos.CENTER_LEFT);

        generateButton = new Button("Szene generieren");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.getStyleClass().add("button primary");
        generateButton.setOnAction(e -> startGeneration());

        statusLabel = new Label("Bereit");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("agent-status-label");

        metaLabel = new Label();
        metaLabel.setWrapText(true);
        metaLabel.getStyleClass().add("scene-meta-label");
        metaLabel.setVisible(false);
        metaLabel.setManaged(false);

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

        getChildren().addAll(
            toggleConfigButton,
            configBox,
            instructionLabel,
            instructionArea,
            modelRow,
            modelSelectRow,
            generateButton,
            statusLabel,
            insertButton,
            metaLabel,
            resultArea
        );
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
        metaLabel.setVisible(false);
        metaLabel.setManaged(false);
        resultArea.clear();
        statusLabel.setText("Generiere Szene…");

        boolean useParams = useParameterModelCheck.isSelected();
        String model = modelCombo.getValue();

        String validationError = generationHandler.generate(
            instruction.trim(),
            useParams,
            model,
            msg -> Platform.runLater(() -> statusLabel.setText(msg)),
            result -> Platform.runLater(() -> finishGeneration(result)),
            err -> Platform.runLater(() -> {
                generating = false;
                generateButton.setDisable(false);
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
        statusLabel.setText(message);
    }

    private void finishGeneration(SceneWritingAgent.GenerationResult result) {
        generating = false;
        generateButton.setDisable(false);
        if (result.getSceneText() != null && !result.getSceneText().isBlank()) {
            resultArea.setText(result.getSceneText());
            insertButton.setDisable(false);
            if (result.isParsedFromTags()) {
                statusLabel.setText("Szene generiert.");
            } else {
                statusLabel.setText("Szene generiert (ohne SCENE-Tags — Rohtext übernommen).");
            }
            if (result.getMetaText() != null && !result.getMetaText().isBlank()) {
                metaLabel.setText("Hinweis (wird nicht eingefügt): " + result.getMetaText());
                metaLabel.setVisible(true);
                metaLabel.setManaged(true);
            }
        } else {
            statusLabel.setText("Keine Szene in der Antwort.");
        }
    }

    private void loadModelsAsync() {
        statusLabel.setText("Lade Modelle…");
        new Thread(() -> {
            try {
                AIBackend backend = createBackendForModelLoad();
                List<String> models = backend.getAvailableModels();
                Platform.runLater(() -> {
                    availableModels = new ArrayList<>(models);
                    modelCombo.getItems().setAll(models);
                    if (!models.isEmpty() && modelCombo.getValue() == null) {
                        modelCombo.setValue(models.get(0));
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
            modelCombo.getItems().setAll(models);
            String paramModel = config.getModel();
            if (paramModel != null && !paramModel.isBlank()) {
                modelCombo.setValue(paramModel);
            } else if (!models.isEmpty()) {
                modelCombo.setValue(models.get(0));
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

    public void applyFontSize(int size) {
        if (size < 8) {
            size = 8;
        } else if (size > 72) {
            size = 72;
        }
        applyFontSizeToNode(this, size);
    }

    private void applyFontSizeToNode(Node node, int size) {
        String fontCss = String.format("-fx-font-size: %dpx;", size);
        if (node instanceof TextInputControl textControl) {
            textControl.setStyle(fontCss);
        } else if (node instanceof Label label) {
            if (label == metaLabel) {
                label.setStyle(fontCss + " -fx-opacity: 0.75;");
            } else {
                label.setStyle(fontCss);
            }
        } else if (node instanceof Labeled labeled) {
            labeled.setStyle(fontCss);
        } else if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyFontSizeToNode(child, size);
            }
        }
    }
}
