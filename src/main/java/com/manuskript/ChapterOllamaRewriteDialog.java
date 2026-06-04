package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Ollama-Dialog für Kontextmenü-Überarbeitungen im Kapitel-Editor.
 */
public final class ChapterOllamaRewriteDialog {

    public enum Mode {
        SPEECH, PHRASE, SELECTION
    }

    private ChapterOllamaRewriteDialog() {
    }

    public static void show(ChapterEditorHost host, Stage ownerStage, int themeIndex, Preferences preferences,
                            File chapterDocx, String dialogTitle, String originalText, int startPos, int endPos,
                            Mode mode) {
        if (host == null) {
            return;
        }
        CustomStage dialogStage = StageManager.createModalStage(dialogTitle, ownerStage);
        dialogStage.setTitle(dialogTitle);
        dialogStage.setWidth(1000);
        dialogStage.setHeight(800);
        dialogStage.setTitleBarTheme(themeIndex);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("dialog-container");
        EditorDialogThemes.applyToNode(root, themeIndex);

        Label originalLabel = new Label("Original:");
        originalLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(originalLabel, themeIndex);
        TextArea originalArea = new TextArea(originalText);
        originalArea.setEditable(false);
        originalArea.setPrefRowCount(6);
        originalArea.setWrapText(true);
        originalArea.getStyleClass().add("dialog-text-area");
        EditorDialogThemes.applyToNode(originalArea, themeIndex);

        Label instructionLabel = new Label("Anweisung (optional):");
        instructionLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(instructionLabel, themeIndex);
        TextField instructionField = new TextField();
        String prefKey = "api_editor_rewrite_instruction_selection";
        if (preferences != null) {
            String persisted = preferences.get(prefKey, "");
            if (persisted != null && !persisted.isEmpty()) {
                instructionField.setText(persisted);
            }
        }
        instructionField.setPromptText("z.B. Text soll spannender werden, mehr Show Don't Tell");
        EditorDialogThemes.applyToNode(instructionField, themeIndex);

        Label creativityLabel = new Label("Kreativität:");
        creativityLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(creativityLabel, themeIndex);
        Slider creativitySlider = new Slider(0.0, 1.0, 0.4);
        creativitySlider.setShowTickLabels(true);
        creativitySlider.setMajorTickUnit(0.25);
        creativitySlider.setPrefWidth(400);
        Label creativityValueLabel = new Label("0.40");
        creativityValueLabel.setMinWidth(40);
        creativityValueLabel.setStyle(String.format("-fx-text-fill: %s;", EditorDialogThemes.color(themeIndex, 1)));
        creativitySlider.valueProperty().addListener((obs, oldVal, newVal) ->
                creativityValueLabel.setText(String.format("%.2f", newVal.doubleValue())));
        HBox creativityBox = new HBox(10);
        creativityBox.setAlignment(Pos.CENTER_LEFT);
        creativityBox.getChildren().addAll(creativitySlider, creativityValueLabel);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        EditorDialogThemes.applyToNode(progressBar, themeIndex);
        VBox answersBox = new VBox(10);
        ScrollPane scrollPane = new ScrollPane(answersBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(350);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        EditorDialogThemes.applyToNode(scrollPane, themeIndex);

        Button btnGenerate = new Button("Generieren");
        btnGenerate.getStyleClass().add("button");
        EditorDialogThemes.applyToNode(btnGenerate, themeIndex);
        Button btnCancel = new Button("Abbrechen");
        btnCancel.getStyleClass().add("button");
        EditorDialogThemes.applyToNode(btnCancel, themeIndex);
        HBox buttonBox = new HBox(10, btnGenerate, btnCancel);

        root.getChildren().addAll(originalLabel, originalArea, instructionLabel, instructionField,
                creativityLabel, creativityBox, progressBar, scrollPane, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.web(EditorDialogThemes.color(themeIndex, 0)));
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        String manuskriptCss = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCss != null) {
            scene.getStylesheets().add(manuskriptCss);
        }
        dialogStage.setSceneWithTitleBar(scene);

        btnGenerate.setOnAction(e -> {
            btnGenerate.setDisable(true);
            btnGenerate.setText("Generiere...");
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            answersBox.getChildren().clear();
            String instruction = instructionField.getText().trim();
            if (preferences != null) {
                preferences.put(prefKey, instruction);
            }
            generate(host, chapterDocx, themeIndex, mode, originalText, startPos, endPos, instruction,
                    creativitySlider.getValue(), answersBox, btnGenerate, progressBar, dialogStage);
        });
        btnCancel.setOnAction(e -> dialogStage.close());
        dialogStage.showAndWait();
    }

    private static void generate(ChapterEditorHost host, File chapterDocx, int themeIndex, Mode mode,
                                 String originalText, int startPos, int endPos, String instruction,
                                 double creativity, VBox answersBox, Button btnGenerate, ProgressBar progressBar,
                                 CustomStage dialogStage) {
        String context = loadProjectContext(chapterDocx, host.getText(), startPos, endPos);
        String prompt = buildPrompt(mode, originalText, instruction);
        OllamaService ollamaService = new OllamaService();
        String targetModel = ResourceManager.getParameter("agent.ollama.model", "gemma3:4b").trim();
        Label statusLabel = new Label("Lade verfügbare Modelle...");
        statusLabel.setStyle(String.format("-fx-text-fill: %s;", EditorDialogThemes.color(themeIndex, 1)));
        answersBox.getChildren().add(statusLabel);

        ollamaService.getAvailableModels().thenAccept(models -> Platform.runLater(() -> {
            String modelToUse = pickModel(models, targetModel);
            if (modelToUse == null) {
                answersBox.getChildren().clear();
                answersBox.getChildren().add(errorLabel("Keine Ollama-Modelle verfügbar."));
                resetGenerateButton(btnGenerate, progressBar);
                return;
            }
            ollamaService.setModel(modelToUse);
            answersBox.getChildren().clear();
            ollamaService.generateText(prompt, context, creativity, ollamaService.getMaxTokens(),
                            ollamaService.getTopP(), ollamaService.getRepeatPenalty())
                    .thenAccept(response -> Platform.runLater(() -> {
                        if (response == null || response.trim().isEmpty()) {
                            answersBox.getChildren().add(errorLabel("Keine Antwort von Ollama."));
                            resetGenerateButton(btnGenerate, progressBar);
                            return;
                        }
                        List<String> variants = ChapterApiRewriteDialog.parseVariants(response);
                        for (int i = 0; i < variants.size() && i < 5; i++) {
                            String variant = variants.get(i);
                            Button answerBtn = new Button("Variante " + (i + 1));
                            answerBtn.setMaxWidth(Double.MAX_VALUE);
                            answerBtn.setAlignment(Pos.CENTER_LEFT);
                            answerBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;",
                                    EditorDialogThemes.color(themeIndex, 2), EditorDialogThemes.color(themeIndex, 1)));
                            TextArea answerText = new TextArea(variant);
                            answerText.setEditable(false);
                            answerText.setWrapText(true);
                            answerText.setPrefRowCount(12);
                            answerText.getStyleClass().add("dialog-text-area");
                            EditorDialogThemes.applyToNode(answerText, themeIndex);
                            VBox variantBox = new VBox(5, answerBtn, answerText);
                            EditorDialogThemes.applyToNode(variantBox, themeIndex);
                            answerBtn.setOnAction(ev -> {
                                if (ChapterRewriteReplaceHelper.replaceIfUnchanged(
                                        host, startPos, endPos, originalText, variant,
                                        mode == Mode.SPEECH ? "Satz ersetzt." : "Text ersetzt.")) {
                                    dialogStage.close();
                                }
                            });
                            answersBox.getChildren().add(variantBox);
                        }
                        resetGenerateButton(btnGenerate, progressBar);
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            answersBox.getChildren().add(errorLabel("Ollama-Fehler: " + ex.getMessage()));
                            resetGenerateButton(btnGenerate, progressBar);
                        });
                        return null;
                    });
        }));
    }

    private static String pickModel(String[] models, String targetModel) {
        if (models == null || models.length == 0) {
            return null;
        }
        String lowerTarget = targetModel.toLowerCase();
        for (String model : models) {
            if (model.equals(targetModel) || model.equalsIgnoreCase(targetModel)
                    || model.toLowerCase().contains(lowerTarget)) {
                return model;
            }
        }
        return models[0];
    }

    private static String buildPrompt(Mode mode, String originalText, String instruction) {
        StringBuilder prompt = new StringBuilder();
        if (mode == Mode.SPEECH) {
            prompt.append("Du bist ein erfahrener deutscher Lektor. Schreibe den folgenden Satz GESAMT neu. ");
            prompt.append("Verwende KEINE einfachen Sprechantworten (sagte, meinte, fragte, erwiderte usw.). ");
            prompt.append("Show don't tell.\n\n");
        } else {
            prompt.append("Du bist ein erfahrener deutscher Lektor. Überarbeite den folgenden Text.\n\n");
        }
        prompt.append("**Zu überarbeitender Text:**\n").append(originalText).append("\n\n");
        if (instruction != null && !instruction.isEmpty()) {
            prompt.append("**Anweisung:**\n").append(instruction).append("\n\n");
        }
        prompt.append("Gib 3-5 Varianten. Trenne Varianten nur mit einer Zeile ---.\n");
        prompt.append("Keine Einleitung, keine Erklärungen, keine Nummerierung.\n");
        return prompt.toString();
    }

    private static String loadProjectContext(File chapterDocx, String chapterText, int startPos, int endPos) {
        StringBuilder contextBuilder = new StringBuilder();
        if (chapterDocx != null && chapterDocx.getParentFile() != null) {
            File projectDir = chapterDocx.getParentFile();
            appendFile(contextBuilder, new File(projectDir, "characters.txt"), "=== CHARAKTERE ===\n");
            appendFile(contextBuilder, new File(projectDir, "synopsis.txt"), "=== SYNOPSIS ===\n");
        }
        if (chapterText != null && !chapterText.isEmpty()) {
            int len = chapterText.length();
            int from = Math.max(0, startPos - 3000);
            int to = Math.min(len, endPos + 3000);
            contextBuilder.append("=== KAPITEL-AUSSCHNITT ===\n").append(chapterText, from, to).append("\n\n");
        }
        return contextBuilder.toString();
    }

    private static void appendFile(StringBuilder builder, File file, String header) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!content.isBlank()) {
                builder.append(header).append(content).append("\n\n");
            }
        } catch (Exception ignored) {
            // optional
        }
    }

    private static Label errorLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: red;");
        return label;
    }

    private static void resetGenerateButton(Button btnGenerate, ProgressBar progressBar) {
        if (btnGenerate != null) {
            btnGenerate.setDisable(false);
            btnGenerate.setText("Generieren");
        }
        if (progressBar != null) {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
        }
    }
}
