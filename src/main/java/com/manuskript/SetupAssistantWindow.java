package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Prüft und richtet optionale Tools ein (gebündelte Pandoc/FFmpeg, Whisper, LanguageTool, KI).
 */
public final class SetupAssistantWindow {

    private SetupAssistantWindow() {
    }

    public static void show(Window owner, int themeIndex) {
        CustomStage stage = StageManager.createStage("Setup-Assistent", owner, false);
        stage.setCustomTitle("Setup-Assistent – Voraussetzungen");

        Label intro = new Label(
                "Prüft optionale Werkzeuge. Pro Eintrag kannst du selbst entscheiden, "
                        + "was eingerichtet wird (Button rechts bei Bedarf).\n"
                        + "Pandoc und FFmpeg liegen im Programmverzeichnis; Schreiben geht auch ohne die übrigen Komponenten.");
        intro.setWrapText(true);
        intro.getStyleClass().add("dialog-label");

        VBox checksBox = new VBox(10);
        checksBox.setPadding(new Insets(4, 0, 4, 0));

        final Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            checksBox.getChildren().clear();
            for (SetupItem item : runChecks()) {
                checksBox.getChildren().add(buildRow(item, stage, themeIndex, refreshHolder[0]));
            }
            EditorDialogThemes.applyToNode(checksBox, themeIndex);
        };

        Button refresh = new Button("Erneut prüfen");
        refresh.getStyleClass().add("dialog-button");
        refresh.setOnAction(e -> refreshHolder[0].run());

        Button close = new Button("Schließen");
        close.getStyleClass().add("dialog-button");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, refresh, close);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(0, 16, 16, 16));

        VBox content = new VBox(12, intro, new Separator(), checksBox);
        content.setPadding(new Insets(16, 16, 8, 16));
        VBox.setVgrow(checksBox, Priority.ALWAYS);

        String bg = EditorDialogThemes.color(themeIndex, 0);
        String bgStyle = "-fx-background-color: " + bg + "; -fx-background: " + bg + ";";

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(bgStyle + " -fx-border-color: transparent; -fx-padding: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox shell = new VBox(scroll, buttons);
        shell.setStyle(bgStyle);
        shell.getStyleClass().add("dialog-container");
        content.setStyle(bgStyle);
        buttons.setStyle(bgStyle);

        Scene scene = new Scene(shell, 780, 560);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        scene.setFill(Color.web(bg));
        EditorDialogThemes.applyToNode(shell, themeIndex);
        EditorDialogThemes.applyToNode(content, themeIndex);
        stage.setTitleBarTheme(themeIndex);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        refreshHolder[0].run();
        Platform.runLater(() -> applyScrollBackground(scroll, bg));
        stage.show();
        Platform.runLater(() -> applyScrollBackground(scroll, bg));
    }

    private static Node buildRow(SetupItem item, Window setupStage, int themeIndex, Runnable refreshUi) {
        Label row = new Label(item.statusIcon() + "  " + item.label() + " — " + item.detail());
        row.setWrapText(true);
        row.setMaxWidth(560);
        HBox.setHgrow(row, Priority.ALWAYS);
        row.getStyleClass().add("dialog-label");
        if ("ok".equals(item.level())) {
            row.getStyleClass().add("setup-check-ok");
        } else if ("warn".equals(item.level())) {
            row.getStyleClass().add("setup-check-warn");
        }

        HBox box = new HBox(10, row);
        box.setAlignment(Pos.CENTER_LEFT);
        if (item.toolId() != null && item.needsAction()) {
            Button action = new Button(item.actionLabel());
            action.getStyleClass().add("dialog-button");
            action.setOnAction(e ->
                    runInstallDialog(setupStage, themeIndex, item.toolId(), item.actionLabel(), refreshUi));
            box.getChildren().add(action);
        }
        return box;
    }

    private static void runInstallDialog(Window setupStage, int themeIndex, ToolSetupSupport.ToolId toolId,
                                         String titleText, Runnable onDone) {
        // Owner = Setup-Assistent (nicht Hauptfenster), sonst rutscht der Assistent nach Schließen nach hinten.
        CustomStage dialog = StageManager.createModalStage(titleText, setupStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setWidth(680);
        dialog.setHeight(440);
        dialog.setTitleBarTheme(themeIndex);
        dialog.setOnHidden(e -> focusSetupStage(setupStage));

        Label title = new Label(titleText != null ? titleText : "Einrichtung");
        title.getStyleClass().add("dialog-title");
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("dialog-textarea");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setMaxWidth(Double.MAX_VALUE);
        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("dialog-button");
        closeBtn.setDisable(true);
        closeBtn.setOnAction(e -> {
            dialog.close();
            focusSetupStage(setupStage);
        });

        VBox root = new VBox(12, title, bar, logArea, closeBtn);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("dialog-container");
        String bg = EditorDialogThemes.color(themeIndex, 0);
        root.setStyle("-fx-background-color: " + bg + ";");
        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        scene.setFill(Color.web(bg));
        EditorDialogThemes.applyToNode(root, themeIndex);
        dialog.setSceneWithTitleBar(scene);
        dialog.setFullTheme(themeIndex);
        dialog.show();

        Consumer<String> log = line -> Platform.runLater(() -> {
            if (line == null) {
                return;
            }
            logArea.appendText(line);
            if (!line.endsWith("\n")) {
                logArea.appendText("\n");
            }
            logArea.setScrollTop(Double.MAX_VALUE);
        });

        if (toolId == null) {
            logArea.appendText("Kein Werkzeug gewählt.\n");
            bar.setProgress(1);
            closeBtn.setDisable(false);
            return;
        }

        AtomicBoolean done = new AtomicBoolean(false);
        CompletableFuture.supplyAsync(() -> ToolSetupSupport.install(toolId, log))
                .whenComplete((error, ex) -> Platform.runLater(() -> {
                    if (done.getAndSet(true)) {
                        return;
                    }
                    bar.setProgress(1);
                    if (ex != null) {
                        logArea.appendText("\nFehler: " + ex.getMessage() + "\n");
                        title.setText("Fehlgeschlagen");
                    } else if (error != null && !error.isBlank()) {
                        logArea.appendText("\n" + error + "\n");
                        title.setText("Hinweis");
                    } else {
                        logArea.appendText("\nFertig.\n");
                        title.setText("Bereit");
                    }
                    closeBtn.setDisable(false);
                    if (onDone != null) {
                        onDone.run();
                    }
                    if (dialog.isShowing()) {
                        dialog.toFront();
                        dialog.requestFocus();
                    }
                }));
    }

    private static void focusSetupStage(Window setupStage) {
        if (setupStage == null) {
            return;
        }
        Platform.runLater(() -> {
            if (setupStage instanceof javafx.stage.Stage s) {
                if (s.isIconified()) {
                    s.setIconified(false);
                }
                s.toFront();
            } else {
                setupStage.requestFocus();
            }
            setupStage.requestFocus();
        });
    }

    private static void applyScrollBackground(ScrollPane scroll, String bg) {
        if (scroll == null || bg == null) {
            return;
        }
        String style = "-fx-background-color: " + bg + "; -fx-background: " + bg + ";";
        scroll.setStyle(style + " -fx-border-color: transparent; -fx-padding: 0;");
        Node viewport = scroll.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle(style);
        }
        Node corner = scroll.lookup(".corner");
        if (corner != null) {
            corner.setStyle(style);
        }
    }

    static List<SetupItem> runChecks() {
        List<SetupItem> results = new ArrayList<>();
        String os = System.getProperty("os.name", "");
        boolean mac = os.toLowerCase(Locale.ROOT).contains("mac");
        results.add(new SetupItem(
                mac ? "ok" : "warn",
                "Betriebssystem",
                os + (mac ? " (empfohlen)" : " – bitte selbst testen"),
                null, false, null));

        results.add(new SetupItem("ok", "Java",
                System.getProperty("java.version", "?") + " (" + System.getProperty("java.vendor", "") + ")",
                null, false, null));

        String pandocIssue = ToolSetupSupport.pandocStatus();
        if (pandocIssue == null) {
            var exe = ToolSetupSupport.resolvePandocBinary();
            results.add(new SetupItem("ok", "Pandoc (Export)",
                    exe != null ? exe.getAbsolutePath() : "im PATH",
                    ToolSetupSupport.ToolId.PANDOC, false, "Entpacken"));
        } else {
            results.add(new SetupItem("warn", "Pandoc (Export)", pandocIssue,
                    ToolSetupSupport.ToolId.PANDOC, true, "Entpacken"));
        }

        String ffmpegIssue = ToolSetupSupport.ffmpegStatus();
        if (ffmpegIssue == null) {
            var exe = ToolSetupSupport.resolveFfmpegBinary(false);
            results.add(new SetupItem("ok", "FFmpeg (Hörbuch)",
                    exe != null ? exe.getAbsolutePath() : "im PATH",
                    ToolSetupSupport.ToolId.FFMPEG, false, "Entpacken"));
        } else {
            results.add(new SetupItem("warn", "FFmpeg (Hörbuch)", ffmpegIssue,
                    ToolSetupSupport.ToolId.FFMPEG, true, "Entpacken"));
        }

        String whisperIssue = ToolSetupSupport.whisperStatus();
        if (whisperIssue == null) {
            results.add(new SetupItem("ok", "Diktat (lokales Whisper)",
                    "whisper-cli und Modell gefunden",
                    ToolSetupSupport.ToolId.WHISPER, false, "Einrichten"));
        } else {
            results.add(new SetupItem("warn", "Diktat (lokales Whisper)", whisperIssue,
                    ToolSetupSupport.ToolId.WHISPER, true, "Einrichten"));
        }

        String agentBackend = ResourceManager.getParameter("agent.backend", "Ollama");
        if ("OpenAI".equalsIgnoreCase(agentBackend)) {
            String key = ResourceManager.getParameter("agent.openai.api_key", "");
            boolean ok = key != null && !key.isBlank();
            results.add(new SetupItem(
                    ok ? "ok" : "warn",
                    "KI-Agenten (OpenAI-kompatibel)",
                    ok ? "API-Key gesetzt" : "Kein agent.openai.api_key",
                    ToolSetupSupport.ToolId.KI, !ok, "Hinweis"));
        } else {
            boolean ok = ToolSetupSupport.httpReachable("http://127.0.0.1:11434/api/tags");
            results.add(new SetupItem(
                    ok ? "ok" : "warn",
                    "KI-Agenten (Ollama)",
                    ok ? "Erreichbar" : "Nicht erreichbar – installieren/starten",
                    ToolSetupSupport.ToolId.KI, !ok, "Einrichten"));
        }

        String ltJar = ToolSetupSupport.languageToolJarStatus();
        boolean ltUp = ToolSetupSupport.httpReachable("http://localhost:8081/v2/languages");
        if (ltJar != null) {
            results.add(new SetupItem("warn", "LanguageTool", ltJar,
                    ToolSetupSupport.ToolId.LANGUAGE_TOOL, false, "Starten"));
        } else if (ltUp) {
            results.add(new SetupItem("ok", "LanguageTool", "Server erreichbar",
                    ToolSetupSupport.ToolId.LANGUAGE_TOOL, false, "Starten"));
        } else {
            results.add(new SetupItem("warn", "LanguageTool",
                    "JAR vorhanden, Server nicht gestartet",
                    ToolSetupSupport.ToolId.LANGUAGE_TOOL, true, "Starten"));
        }

        return results;
    }

    record SetupItem(String level, String label, String detail, ToolSetupSupport.ToolId toolId,
                     boolean needsAction, String actionLabel) {
        String statusIcon() {
            return switch (level) {
                case "ok" -> "✓";
                case "warn" -> "!";
                default -> "·";
            };
        }
    }
}
