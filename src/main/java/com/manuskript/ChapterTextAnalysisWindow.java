package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Textanalyse-Fenster für den Canvas-Kapitel-Editor.
 */
public class ChapterTextAnalysisWindow {

    public interface Host {
        String getChapterText();

        void applyAnalysisResult(TextAnalysisEngine.AnalysisResult result);

        void clearAnalysisMarks();

        void revealAnalysisRange(int start, int end);

        void updateStatus(String message);

        void updateStatusError(String message);

        Window getOwnerWindow();

        int getThemeIndex();

        void applyThemeToNode(Node node, int themeIndex);
    }

    private final Host host;
    private final TextAnalysisEngine engine = new TextAnalysisEngine();

    private CustomStage stage;
    private TextArea statusArea;
    private TextField abstandField;

    private TextAnalysisEngine.AnalysisResult currentResult;
    private int currentNavIndex = -1;

    public ChapterTextAnalysisWindow(Host host) {
        this.host = host;
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    public void toggle() {
        if (isShowing()) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        if (stage == null) {
            createStage();
        }
        stage.setTitleBarTheme(host.getThemeIndex());
        host.applyThemeToNode(stage.getScene().getRoot(), host.getThemeIndex());
        stage.show();
        stage.toFront();
        host.updateStatus("Textanalyse-Fenster geöffnet");
    }

    public void hide() {
        if (stage != null) {
            stage.hide();
        }
        host.updateStatus("Textanalyse-Fenster geschlossen");
    }

    public void applyTheme(int themeIndex) {
        if (stage != null && stage.isShowing()) {
            stage.setTitleBarTheme(themeIndex);
            host.applyThemeToNode(stage.getScene().getRoot(), themeIndex);
        }
    }

    private void createStage() {
        stage = StageManager.createStage("Textanalyse");
        stage.setTitle("Textanalyse");
        stage.setWidth(820);
        stage.setHeight(620);
        stage.setTitleBarTheme(host.getThemeIndex());
        stage.initModality(Modality.NONE);
        Window owner = host.getOwnerWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = buildPanel();
        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        stage.setSceneWithTitleBar(scene);
        host.applyThemeToNode(root, host.getThemeIndex());

        stage.setOnCloseRequest(event -> {
            event.consume();
            host.clearAnalysisMarks();
            stage.hide();
        });
    }

    private VBox buildPanel() {
        VBox mainPanel = new VBox(10);
        mainPanel.setPadding(new Insets(15));
        mainPanel.getStyleClass().add("text-analysis-panel");

        Label titleLabel = new Label("Textanalyse");
        titleLabel.getStyleClass().add("sidebar-title");

        VBox analysisButtons = new VBox(8);
        analysisButtons.getChildren().addAll(
                analysisRow("Sprechwörter finden",
                        "Findet und markiert Sprechwörter (sagte, fragte, rief, …)",
                        () -> runSafe(engine::analyzeSprechwoerter)),
                analysisRow("Sprechantworten finden",
                        "Findet einfache Sprechantworten (sagte er., fragte sie., …)",
                        () -> runSafe(engine::analyzeSprechantworten)),
                wortwiederholungenRow(),
                analysisRow("Wortwiederholung nah",
                        "Wortwiederholungen im Abstand 5–10 Wörter",
                        () -> runSafe(engine::analyzeWortwiederholungNah)),
                analysisRow("Füllwörter finden",
                        "Typische Füllwörter (eigentlich, irgendwie, halt, …)",
                        () -> runSafe(engine::analyzeFuellwoerterDetailed)),
                analysisRow("Phrasen finden",
                        "Typische Phrasen (begann zu, sagte er, …)",
                        () -> runSafe(engine::analyzePhrasenDetailed)),
                analysisRow("Satzlängen analysieren",
                        "Markiert Sätze nach Länge (kurz / mittel / lang)",
                        () -> runSafe(engine::analyzeSentenceLengthsDetailed))
        );

        HBox navigationBox = new HBox(10);
        Button btnNext = new Button("↓ Nächster Treffer");
        Button btnPrevious = new Button("↑ Vorheriger Treffer");
        Button btnClear = new Button("Markierungen löschen");
        btnNext.setOnAction(e -> navigate(1));
        btnPrevious.setOnAction(e -> navigate(-1));
        btnClear.setOnAction(e -> {
            host.clearAnalysisMarks();
            currentResult = null;
            currentNavIndex = -1;
            statusArea.setText("Markierungen entfernt.");
        });
        navigationBox.getChildren().addAll(btnNext, btnPrevious, btnClear);

        statusArea = new TextArea();
        statusArea.setPrefRowCount(10);
        statusArea.setEditable(false);
        statusArea.setWrapText(true);
        statusArea.setPromptText("Analyse-Ergebnisse werden hier angezeigt…");
        statusArea.getStyleClass().add("status-area");
        VBox.setVgrow(statusArea, Priority.ALWAYS);

        mainPanel.getChildren().addAll(titleLabel, analysisButtons, navigationBox, statusArea);
        return mainPanel;
    }

    private HBox analysisRow(String buttonText, String description, Runnable action) {
        Button button = new Button(buttonText);
        button.setPrefWidth(200);
        button.getStyleClass().add("button");
        button.setOnAction(e -> action.run());
        Label label = new Label(description);
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox row = new HBox(10, button, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox wortwiederholungenRow() {
        Button button = new Button("Wortwiederholungen finden");
        button.setPrefWidth(200);
        button.getStyleClass().add("button");
        abstandField = new TextField("10");
        abstandField.setPrefWidth(40);
        abstandField.setPromptText("10");
        Label abstandLabel = new Label("Max. Abstand:");
        Label unitLabel = new Label("Wörter");
        VBox config = new VBox(4);
        Label desc = new Label("Findet Wörter, die sich in kurzem Abstand wiederholen");
        desc.setWrapText(true);
        HBox abstandBox = new HBox(5, abstandLabel, abstandField, unitLabel);
        abstandBox.setAlignment(Pos.CENTER_LEFT);
        config.getChildren().addAll(desc, abstandBox);
        HBox.setHgrow(config, Priority.ALWAYS);
        button.setOnAction(e -> {
            try {
                int abstand = Integer.parseInt(abstandField.getText().trim());
                runSafe(text -> engine.analyzeWortwiederholungen(text, abstand));
            } catch (NumberFormatException ex) {
                statusArea.setText("Fehler: Bitte eine gültige Zahl für den Abstand eingeben.");
            }
        });
        HBox row = new HBox(10, button, config);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    @FunctionalInterface
    private interface AnalysisFunction {
        TextAnalysisEngine.AnalysisResult apply(String text) throws Exception;
    }

    private void runSafe(AnalysisFunction function) {
        try {
            String text = host.getChapterText();
            if (text == null || text.isBlank()) {
                statusArea.setText("Kein Text zum Analysieren vorhanden.");
                return;
            }
            TextAnalysisEngine.AnalysisResult result = function.apply(text);
            applyResult(result);
            host.updateStatus("Textanalyse abgeschlossen");
        } catch (Exception e) {
            statusArea.setText("Fehler bei der Analyse: " + e.getMessage());
            host.updateStatusError("Textanalyse fehlgeschlagen: " + e.getMessage());
        }
    }

    private void applyResult(TextAnalysisEngine.AnalysisResult result) {
        currentResult = result;
        host.clearAnalysisMarks();
        host.applyAnalysisResult(result);
        currentNavIndex = result.navigationHits().isEmpty() ? -1 : 0;
        statusArea.setText(result.summary());
        if (currentNavIndex >= 0) {
            revealCurrentHit();
        }
    }

    private void navigate(int direction) {
        if (currentResult == null || currentResult.navigationHits().isEmpty()) {
            statusArea.appendText("\n\nKeine Treffer zur Navigation.");
            return;
        }
        int size = currentResult.navigationHits().size();
        if (currentNavIndex < 0) {
            currentNavIndex = 0;
        } else {
            currentNavIndex = (currentNavIndex + direction + size) % size;
        }
        revealCurrentHit();
        statusArea.appendText("\n\n→ Treffer " + (currentNavIndex + 1) + " von " + size);
    }

    private void revealCurrentHit() {
        TextAnalysisEngine.AnalysisSpan hit = currentResult.navigationHits().get(currentNavIndex);
        host.revealAnalysisRange(hit.start(), hit.end());
    }
}
