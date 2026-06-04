package com.manuskript;

import com.manuskript.agent.OpenAIBackend;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Online-API-Dialog für Sprechantwort / Phrase / Selektion (OpenAI-kompatibel).
 */
public final class ChapterApiRewriteDialog {

    private ChapterApiRewriteDialog() {
    }

    public static void show(ChapterEditorHost host, Stage ownerStage, int themeIndex, Preferences preferences,
                           String dialogTitle, String originalText, int startPos, int endPos, String mode) {
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
        originalArea.setPrefRowCount(4);
        originalArea.setWrapText(true);
        originalArea.getStyleClass().add("dialog-text-area");
        EditorDialogThemes.applyToNode(originalArea, themeIndex);

        Label instructionLabel = new Label("Anweisung (optional):");
        instructionLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(instructionLabel, themeIndex);
        TextField instructionField = new TextField();
        String prefKey = "speech".equals(mode) ? "api_editor_rewrite_instruction_speech"
                : ("phrase".equals(mode) ? "api_editor_rewrite_instruction_phrase" : "api_editor_rewrite_instruction_selection");
        String persisted = preferences != null ? preferences.get(prefKey, "") : "";
        if (persisted != null && !persisted.isEmpty()) {
            instructionField.setText(persisted);
        } else if ("speech".equals(mode)) {
            instructionField.setText(
                    "Gesamten Satz neu schreiben ohne einfache Sprechantwort (sagte|meinte|erwiderte usw.). Show don't tell.");
        }
        instructionField.setPromptText("z.B. Text soll spannender werden, mehr Show Don't Tell");
        EditorDialogThemes.applyToNode(instructionField, themeIndex);

        CheckBox persistInstructionCheck = new CheckBox("Anweisung dauerhaft speichern");
        persistInstructionCheck.getStyleClass().add("param-check");
        EditorDialogThemes.applyToNode(persistInstructionCheck, themeIndex);

        Label tempLabel = new Label("Temperatur (0 = sachlich, höher = kreativer):");
        tempLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(tempLabel, themeIndex);
        Slider tempSlider = new Slider(0.0, 1.0, 0.7);
        tempSlider.setShowTickLabels(true);
        tempSlider.setMajorTickUnit(0.25);
        tempSlider.setPrefWidth(400);
        Label tempValueLabel = new Label("0.70");
        tempValueLabel.setMinWidth(40);
        tempValueLabel.setStyle(String.format("-fx-text-fill: %s;", EditorDialogThemes.color(themeIndex, 1)));
        tempSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                tempValueLabel.setText(String.format("%.2f", newVal.doubleValue())));
        HBox tempBox = new HBox(10);
        tempBox.setAlignment(Pos.CENTER_LEFT);
        tempBox.getChildren().addAll(tempSlider, tempValueLabel);

        Label modelLabel = new Label("Modell (leer = aus Parametern):");
        modelLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(modelLabel, themeIndex);
        TextField modelField = new TextField(ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini"));
        modelField.setPromptText("z.B. gpt-4o-mini");
        modelField.setPrefWidth(300);
        EditorDialogThemes.applyToNode(modelField, themeIndex);

        Label answersLabel = new Label("Vorschläge:");
        answersLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(answersLabel, themeIndex);
        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(20);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        EditorDialogThemes.applyToNode(progressBar, themeIndex);
        VBox answersBox = new VBox(10);
        ScrollPane scrollPane = new ScrollPane(answersBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(350);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        EditorDialogThemes.applyToNode(scrollPane, themeIndex);

        HBox buttonBox = new HBox(10);
        Button btnGenerate = new Button("Generieren");
        btnGenerate.getStyleClass().add("button");
        EditorDialogThemes.applyToNode(btnGenerate, themeIndex);
        Button btnCancel = new Button("Abbrechen");
        btnCancel.getStyleClass().add("button");
        EditorDialogThemes.applyToNode(btnCancel, themeIndex);
        buttonBox.getChildren().addAll(btnGenerate, btnCancel);

        root.getChildren().addAll(originalLabel, originalArea, instructionLabel, instructionField, persistInstructionCheck,
                tempLabel, tempBox, modelLabel, modelField, answersLabel, progressBar, scrollPane, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.web(EditorDialogThemes.color(themeIndex, 0)));
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        String manuskriptCssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCssPath != null) {
            scene.getStylesheets().add(manuskriptCssPath);
        }
        dialogStage.setSceneWithTitleBar(scene);

        btnGenerate.setOnAction(e -> {
            btnGenerate.setDisable(true);
            btnGenerate.setText("Generiere...");
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            answersBox.getChildren().clear();

            String instruction = instructionField.getText().trim();
            if (persistInstructionCheck.isSelected() && preferences != null) {
                preferences.put(prefKey, instruction);
            }

            String systemPrompt;
            String userMessage;
            if ("speech".equals(mode)) {
                systemPrompt = "Du bist ein erfahrener deutscher Lektor. Deine Aufgabe: Den gegebenen Satz GESAMT neu schreiben. "
                        + "Verwende KEINE einfachen Sprechantworten (kein 'sagte', 'meinte', 'erwiderte', 'fragte', 'antwortete', 'flüsterte', 'rief' usw.). "
                        + "Stattdessen: Show don't tell – zeige durch Handlung, Gestik, Mimik oder Kontext, wie gesprochen wird.\n\n"
                        + "WICHTIG – Ausgabeformat: Antworte AUSSCHLIESSLICH mit 3–5 überarbeiteten Satzversionen. KEINE Einleitung, KEINE Erklärungen, KEINE Anmerkungen, KEINE Nummerierung. Beginne direkt mit der ersten Version. Trenne die Versionen nur durch eine eigene Zeile, die ausschließlich aus drei Bindestrichen besteht: ---";
                String chapterContext = "";
                String fullChapter = host.getText();
                if (fullChapter != null && !fullChapter.isEmpty()) {
                    int len = fullChapter.length();
                    int ctxLen = 6000;
                    int from = Math.max(0, startPos - ctxLen / 2);
                    int to = Math.min(len, endPos + ctxLen / 2);
                    chapterContext = fullChapter.substring(from, to);
                    if (from > 0) {
                        chapterContext = "[…] " + chapterContext;
                    }
                    if (to < len) {
                        chapterContext = chapterContext + " […]";
                    }
                }
                userMessage = "";
                if (!chapterContext.isEmpty()) {
                    userMessage = "Kontext (Ausschnitt aus dem Kapitel):\n" + chapterContext + "\n\n";
                }
                userMessage += "Zu überarbeitender Satz:\n" + originalText;
                if (!instruction.isEmpty()) {
                    userMessage += "\n\nAnweisung: " + instruction;
                }
            } else if ("phrase".equals(mode)) {
                systemPrompt = "Du bist ein erfahrener deutscher Lektor. Deine Aufgabe: Die ausgewählte Phrase korrigieren und stilistisch verbessern, ohne unnötig Bedeutung, Perspektive oder Tonfall zu verändern.\n\n"
                        + "WICHTIG – Ausgabeformat: Antworte AUSSCHLIESSLICH mit 3–5 korrigierten Varianten der Phrase. KEINE Einleitung, KEINE Erklärungen, KEINE Anmerkungen, KEINE Nummerierung. Beginne direkt mit der ersten Variante. Trenne die Varianten nur durch eine eigene Zeile, die ausschließlich aus drei Bindestrichen besteht: ---";
                userMessage = "Zu korrigierende Phrase:\n" + originalText;
                if (!instruction.isEmpty()) {
                    userMessage += "\n\nAnweisung: " + instruction;
                }
            } else {
                systemPrompt = "Du agierst als sehr erfahrener, kritischer deutscher Lektor. Überarbeite den gegebenen Text: Verbessere Stil, Klarheit, Lebendigkeit und Wirkung.\n\n"
                        + "WICHTIG – Ausgabeformat: Antworte AUSSCHLIESSLICH mit 3–5 überarbeiteten Versionen. KEINE Einleitung, KEINE Erklärungen, KEINE Anmerkungen (z. B. „Hier meine Vorschläge“), KEINE Nummerierung. Beginne direkt mit der ersten Version. Jede Version kann mehrere Absätze haben. Trenne die Versionen nur durch eine eigene Zeile, die ausschließlich aus drei Bindestrichen besteht: ---";
                userMessage = "Zu überarbeitender Text:\n" + originalText;
                if (!instruction.isEmpty()) {
                    userMessage += "\n\nAnweisung: " + instruction;
                }
            }

            double temperature = tempSlider.getValue();
            String modelOverride = modelField.getText().trim();
            OpenAIBackend api = new OpenAIBackend();
            api.setCurrentModel(modelOverride.isEmpty()
                    ? ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini") : modelOverride);
            api.setTemperature(temperature);
            api.chat(systemPrompt, userMessage, 2048)
                    .thenAccept(response -> Platform.runLater(() -> {
                        if (response == null || response.trim().isEmpty()) {
                            answersBox.getChildren().add(errorLabel("Keine Antwort von der API."));
                            resetGenerateButton(btnGenerate, progressBar);
                            return;
                        }
                        List<String> variants = parseVariants(response);
                        for (int i = 0; i < variants.size() && i < 5; i++) {
                            String variant = variants.get(i);
                            int variantIndex = i + 1;
                            Button answerBtn = new Button("Variante " + variantIndex);
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
                            VBox variantBox = new VBox(5);
                            variantBox.getChildren().addAll(answerBtn, answerText);
                            EditorDialogThemes.applyToNode(variantBox, themeIndex);
                            answerBtn.setOnAction(ev -> {
                                if (ChapterRewriteReplaceHelper.replaceIfUnchanged(
                                        host, startPos, endPos, originalText, variant, "Text ersetzt.")) {
                                    dialogStage.close();
                                }
                            });
                            answersBox.getChildren().add(variantBox);
                        }
                        resetGenerateButton(btnGenerate, progressBar);
                    }))
                    .exceptionally(throwable -> {
                        Platform.runLater(() -> {
                            String msg = throwable != null && throwable.getCause() != null
                                    ? throwable.getCause().getMessage()
                                    : (throwable != null ? throwable.getMessage() : "Unbekannter Fehler");
                            answersBox.getChildren().add(errorLabel("Fehler: " + msg));
                            resetGenerateButton(btnGenerate, progressBar);
                        });
                        return null;
                    });
        });
        btnCancel.setOnAction(ev -> dialogStage.close());
        dialogStage.showAndWait();
    }

    static List<String> parseVariants(String response) {
        List<String> variants = new ArrayList<>();
        String[] blocks = response.trim().split("\\r?\\n\\s*---\\s*\\r?\\n");
        for (String block : blocks) {
            String stripped = stripVariantCommentLines(block);
            String cleaned = stripped
                    .replaceAll("^\\d+\\.\\s*", "")
                    .replaceAll("^-\\s*", "")
                    .replaceAll("^\\*\\s*", "")
                    .trim();
            if (!cleaned.isEmpty() && cleaned.length() > 3) {
                variants.add(cleaned);
            }
        }
        if (variants.isEmpty()) {
            String fallback = stripVariantCommentLines(response.trim());
            variants.add(fallback.isEmpty() ? response.trim() : fallback);
        }
        return variants;
    }

    private static String stripVariantCommentLines(String block) {
        if (block == null) {
            return "";
        }
        String[] lines = block.split("\\r?\\n");
        int start = 0;
        int end = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.matches("(?i)^(Variante|Vorschlag|Option|Version)\\s*\\d*\\s*:?\\s*$")) {
                continue;
            }
            if (line.matches("(?i)^(Hier|Folgende|Meine|Die)\\s+.+") && line.endsWith(":") && line.length() < 90) {
                continue;
            }
            if (line.length() <= 35 && line.endsWith(":")) {
                continue;
            }
            if (line.matches("^\\d+\\.\\s*$") || line.equals("---")) {
                continue;
            }
            start = i;
            break;
        }
        for (int i = lines.length - 1; i >= start; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.matches("(?i)^(Variante|Vorschlag|Option|Version)\\s*\\d*\\s*:?\\s*$")) {
                continue;
            }
            if (line.length() <= 35 && line.endsWith(":")) {
                continue;
            }
            if (line.matches("^\\d+\\.\\s*$") || line.equals("---")) {
                continue;
            }
            end = i + 1;
            break;
        }
        if (start >= end) {
            return block.trim();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(lines[i]);
        }
        return sb.toString().trim();
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
