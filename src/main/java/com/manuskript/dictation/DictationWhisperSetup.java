package com.manuskript.dictation;

import com.manuskript.CustomAlert;
import com.manuskript.CustomStage;
import com.manuskript.ResourceManager;
import com.manuskript.StageManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interaktives Einrichten von lokalem Whisper (Homebrew + Modell-Download).
 */
final class DictationWhisperSetup {

    private static final Logger logger = LoggerFactory.getLogger(DictationWhisperSetup.class);

    private DictationWhisperSetup() {
    }

    static boolean isWhisperSetupMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("whisper-cli nicht gefunden")
                || lower.contains("whisper-modell")
                || lower.contains("lokales whisper");
    }

    /**
     * Zeigt Setup-Dialog mit optionaler automatischer Installation (macOS/Homebrew).
     */
    static void show(Window owner, int themeIndex, String header, String detail) {
        boolean mac = WhisperRuntime.isMacOS();
        boolean exeMissing = WhisperRuntime.isExecutableMissing();
        boolean modelMissing = WhisperRuntime.isModelMissing();
        boolean brewOk = mac && WhisperRuntime.isHomebrewAvailable();

        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
        alert.setTitle(header != null && !header.isBlank() ? header : "Whisper einrichten");
        alert.setHeaderText(alert.getTitle());

        TextArea area = new TextArea(detail != null ? detail : "");
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(12);
        area.setPrefWidth(540);
        VBox content = new VBox(area);
        VBox.setVgrow(area, Priority.ALWAYS);
        alert.setCustomContent(content);

        ButtonType installAll = new ButtonType("Mit Homebrew einrichten", ButtonBar.ButtonData.OK_DONE);
        ButtonType modelOnly = new ButtonType("Nur Modell laden", ButtonBar.ButtonData.APPLY);
        ButtonType close = new ButtonType("Schließen", ButtonBar.ButtonData.CANCEL_CLOSE);

        if (mac && brewOk && (exeMissing || modelMissing)) {
            if (exeMissing) {
                alert.setButtonTypes(installAll, modelOnly, close);
            } else {
                alert.setButtonTypes(modelOnly, close);
            }
        } else if (mac && !brewOk && exeMissing) {
            alert.setButtonTypes(close);
            area.setText(detail + "\n\nHomebrew fehlt. Bitte zuerst https://brew.sh installieren, "
                    + "dann Diktat erneut versuchen – oder dictation.stt_backend auf OpenAI stellen.");
        } else if (!exeMissing && modelMissing) {
            alert.setButtonTypes(modelOnly, close);
        } else {
            alert.setButtonTypes(close);
        }

        alert.applyTheme(themeIndex);
        if (owner != null) {
            alert.initOwner(owner);
        }
        Optional<ButtonType> choice = owner != null ? alert.showAndWait(owner) : alert.showAndWait();
        if (choice.isEmpty()) {
            return;
        }
        ButtonType selected = choice.get();
        if (selected == installAll) {
            runInstall(owner, themeIndex, true, true);
        } else if (selected == modelOnly) {
            runInstall(owner, themeIndex, false, true);
        }
    }

    private static void runInstall(Window owner, int themeIndex, boolean brew, boolean model) {
        CustomStage stage = StageManager.createModalStage("Whisper wird eingerichtet", owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setWidth(620);
        stage.setHeight(420);
        stage.setTitleBarTheme(themeIndex);

        Label title = new Label(brew ? "Homebrew + Modell" : "Modell-Download");
        title.getStyleClass().add("dialog-title");

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        ProgressBar busy = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        busy.setMaxWidth(Double.MAX_VALUE);

        Button closeBtn = new Button("Schließen");
        closeBtn.setDisable(true);
        closeBtn.setOnAction(e -> stage.close());
        HBox buttons = new HBox(closeBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, title, logArea, busy, buttons);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("dialog-container");
        applyTheme(root, themeIndex);

        Scene scene = new Scene(root);
        String css = ResourceManager.getCssResource("css/manuskript.css");
        if (css != null) {
            scene.getStylesheets().add(css);
        }
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        stage.show();

        AtomicBoolean done = new AtomicBoolean(false);
        ConsumerLog log = line -> Platform.runLater(() -> {
            if (line == null) {
                return;
            }
            logArea.appendText(line);
            if (!line.endsWith("\n")) {
                logArea.appendText("\n");
            }
            logArea.setScrollTop(Double.MAX_VALUE);
        });

        CompletableFuture.supplyAsync(() -> {
            String error = null;
            try {
                if (brew) {
                    log.accept("=== whisper-cpp installieren ===");
                    error = WhisperRuntime.installWhisperCppViaHomebrew(log);
                    if (error != null) {
                        return error;
                    }
                }
                if (model) {
                    log.accept("=== Modell laden ===");
                    error = WhisperRuntime.downloadDefaultModel(log);
                }
                return error;
            } catch (Exception e) {
                logger.warn("Whisper-Setup fehlgeschlagen", e);
                return e.getMessage();
            }
        }).whenComplete((error, ex) -> Platform.runLater(() -> {
            done.set(true);
            busy.setVisible(false);
            busy.setManaged(false);
            closeBtn.setDisable(false);
            if (ex != null) {
                log.accept("FEHLER: " + ex.getMessage());
                title.setText("Einrichtung fehlgeschlagen");
            } else if (error != null && !error.isBlank()) {
                log.accept("FEHLER: " + error);
                title.setText("Einrichtung fehlgeschlagen");
            } else if (WhisperRuntime.isExecutableMissing() || WhisperRuntime.isModelMissing()) {
                log.accept("Noch nicht vollständig eingerichtet – bitte Hinweis prüfen.");
                title.setText("Einrichtung unvollständig");
            } else {
                log.accept("Fertig. Diktat kann erneut gestartet werden.");
                title.setText("Whisper bereit");
            }
        }));
    }

    private interface ConsumerLog extends java.util.function.Consumer<String> {
    }

    private static void applyTheme(VBox root, int themeIndex) {
        root.getStyleClass().removeAll(
                "weiss-theme", "theme-dark", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        switch (themeIndex) {
            case 1 -> root.getStyleClass().add("theme-dark");
            case 2 -> root.getStyleClass().add("pastell-theme");
            case 3 -> root.getStyleClass().add("blau-theme");
            case 4 -> root.getStyleClass().add("gruen-theme");
            case 5 -> root.getStyleClass().add("lila-theme");
            default -> root.getStyleClass().add("weiss-theme");
        }
    }
}
