package com.manuskript.mindmap;

import com.google.gson.Gson;
import com.manuskript.plugin.PluginHost;
import com.manuskript.plugin.PluginHostThemes;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MindmapWindow {

    private static final Gson GSON = new Gson();

    private final PluginHost host;
    private Stage stage;
    private WebView webView;
    private WebEngine webEngine;
    private Label statusLabel;
    private Button saveButton;
    private Button reloadButton;
    private Button aiInitialButton;
    private Button aiUpdateButton;
    private Button aiCancelButton;
    private Timeline progressTimeline;
    private long aiStartMs;
    private CompletableFuture<String> aiFuture;
    private volatile boolean aiCancelled;

    private Path projectRoot;
    private MindmapModel model = new MindmapModel();
    private boolean dirty;
    private volatile boolean aiRunning;
    private Timeline windowGeometrySaveTimeline;

    public MindmapWindow(PluginHost host) {
        this.host = host;
    }

    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            reloadFromDisk();
            applyWebTheme();
            return;
        }
        stage = host.createThemedStage("Mindmap");
        MindmapUiSettings ui = MindmapUiSettings.load(host.configDir());
        host.attachScene(stage, new Scene(buildUi(), ui.width, ui.height));
        applyWindowGeometry(ui);
        installWindowGeometryPersistence();
        stage.show();
        reloadFromDisk();
    }

    private void applyWindowGeometry(MindmapUiSettings ui) {
        if (stage == null || ui == null) {
            return;
        }
        stage.setWidth(ui.width);
        stage.setHeight(ui.height);
        if (ui.x != null && ui.y != null) {
            stage.setX(ui.x);
            stage.setY(ui.y);
        }
    }

    private void installWindowGeometryPersistence() {
        if (stage == null) {
            return;
        }
        javafx.beans.value.ChangeListener<Number> listener = (obs, oldVal, newVal) -> scheduleSaveWindowGeometry();
        stage.widthProperty().addListener(listener);
        stage.heightProperty().addListener(listener);
        stage.xProperty().addListener(listener);
        stage.yProperty().addListener(listener);
        stage.setOnHidden(e -> saveWindowGeometry());
    }

    private void scheduleSaveWindowGeometry() {
        if (windowGeometrySaveTimeline != null) {
            windowGeometrySaveTimeline.stop();
        }
        windowGeometrySaveTimeline = new Timeline(
                new KeyFrame(Duration.millis(450), e -> saveWindowGeometry()));
        windowGeometrySaveTimeline.play();
    }

    private void saveWindowGeometry() {
        if (stage == null) {
            return;
        }
        try {
            MindmapUiSettings ui = new MindmapUiSettings();
            ui.width = stage.getWidth();
            ui.height = stage.getHeight();
            ui.x = stage.getX();
            ui.y = stage.getY();
            ui.save(host.configDir());
        } catch (Exception ignored) {
        }
    }

    private BorderPane buildUi() {
        saveButton = button("Speichern", e -> save());
        reloadButton = button("Neu laden", e -> reloadFromDisk());
        aiInitialButton = button("KI Erstlesen", e -> runAiInitial());
        aiUpdateButton = button("KI Aktualisieren", e -> runAiUpdate());
        aiCancelButton = button("Abbrechen", e -> cancelAi());
        aiCancelButton.setDisable(true);

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("dialog-label");

        HBox bar = new HBox(8, saveButton, reloadButton, aiInitialButton, aiUpdateButton,
                aiCancelButton, statusLabel);
        bar.setPadding(new Insets(8));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webView.setContextMenuEnabled(false);
        BorderPane.setMargin(webView, Insets.EMPTY);

        webEngine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
                installBridge();
                pushGraphToView();
                applyWebTheme();
            }
        });

        URL html = MindmapWindow.class.getResource("/mindmap/editor.html");
        if (html != null) {
            webEngine.load(html.toExternalForm());
        } else {
            statusLabel.setText("editor.html nicht gefunden.");
        }

        BorderPane root = new BorderPane();
        root.setTop(bar);
        root.setCenter(webView);
        return root;
    }

    private void installBridge() {
        try {
            JSObject window = (JSObject) webEngine.executeScript("window");
            window.setMember("javaBridge", new MindmapBridge(this));
        } catch (Exception e) {
            setStatus("Java-Bridge fehlgeschlagen: " + e.getMessage());
        }
    }

    private void applyWebTheme() {
        if (webView == null || webEngine == null) {
            return;
        }
        int theme = host.themeIndex();
        String bg = PluginHostThemes.color(theme, 0);
        String text = PluginHostThemes.color(theme, 1);
        String panel = PluginHostThemes.color(theme, 2);
        String border = PluginHostThemes.color(theme, 3);
        webView.setStyle("-fx-background-color: " + bg + ";");
        try {
            String js = String.format(java.util.Locale.ROOT,
                    "if(typeof applyTheme==='function') applyTheme({bg:'%s',text:'%s',panel:'%s',border:'%s',edge:'%s'});",
                    bg, text, panel, border, border);
            webEngine.executeScript(js);
        } catch (Exception ignored) {
        }
    }

    void onGraphChangedFromJs(String json) {
        Platform.runLater(() -> {
            try {
                MindmapModel parsed = GSON.fromJson(decodeGraphPayload(json), MindmapModel.class);
                if (parsed != null) {
                    model = parsed;
                    dirty = true;
                    updateStatusCounts();
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void reloadFromDisk() {
        Optional<Path> root = host.projectRoot();
        if (root.isEmpty()) {
            projectRoot = null;
            model = new MindmapModel();
            pushGraphToView();
            setStatus("Kein Projekt geöffnet.");
            dirty = false;
            return;
        }
        projectRoot = root.get();
        model = MindmapStore.load(projectRoot);
        dirty = false;
        pushGraphToView();
        setStatus("Geladen: data/mindmap.json — " + projectRoot.getFileName());
    }

    private void save() {
        if (projectRoot == null) {
            setStatus("Kein Projekt — Speichern nicht möglich.");
            return;
        }
        pullGraphFromView();
        try {
            MindmapStore.save(projectRoot, model);
            dirty = false;
            int positioned = countPositionedNodes(model);
            int total = model.nodes == null ? 0 : model.nodes.size();
            setStatus("Gespeichert: data/mindmap.json"
                    + (total > 0 ? " (" + positioned + "/" + total + " mit Position)" : ""));
        } catch (Exception e) {
            setStatus("Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private static int countPositionedNodes(MindmapModel model) {
        if (model == null || model.nodes == null) {
            return 0;
        }
        int count = 0;
        for (MindmapModel.MindmapNode node : model.nodes) {
            if (node != null && node.x != null && node.y != null) {
                count++;
            }
        }
        return count;
    }

    private void runAiInitial() {
        if (aiRunning || projectRoot == null) {
            if (projectRoot == null) {
                setStatus("Kein Projekt geöffnet.");
            }
            return;
        }
        pullGraphFromView();
        if (model.nodes != null && !model.nodes.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Die Mindmap enthält bereits Knoten. KI Erstlesen ersetzt sie.",
                    ButtonType.CANCEL, ButtonType.OK);
            alert.setTitle("KI Erstlesen");
            alert.setHeaderText("Mindmap ersetzen?");
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                return;
            }
        }
        startAi(MindmapAiSupport.systemPromptInitial(), null, true);
    }

    private void runAiUpdate() {
        if (aiRunning || projectRoot == null) {
            if (projectRoot == null) {
                setStatus("Kein Projekt geöffnet.");
            }
            return;
        }
        pullGraphFromView();
        startAi(MindmapAiSupport.systemPromptUpdate(), null, false);
    }

    private static final int AI_MAX_TOKENS = 2048;
    private static final int AI_TIMEOUT_SEC = 180;

    private void startAi(String system, String userOrNull, boolean replace) {
        cancelAiQuietly();
        aiCancelled = false;
        aiRunning = true;
        setAiButtonsDisabled(true);
        startProgressTimer("Lese Projekt …");
        Path root = projectRoot;
        pullGraphFromView();
        MindmapModel snapshot = model;

        aiFuture = CompletableFuture.supplyAsync(() -> MindmapContextCollector.collect(root))
                .thenCompose(context -> {
                    if (context == null || context.isBlank()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Kein Kontext — Welt-Dateien oder Kapitel fehlen."));
                    }
                    Platform.runLater(() -> setStatus("KI analysiert Mindmap …"));
                    String user;
                    if (userOrNull != null) {
                        user = userOrNull;
                    } else if (replace) {
                        user = MindmapAiSupport.userPromptInitial(context);
                    } else {
                        user = MindmapAiSupport.userPromptUpdate(context,
                                MindmapAiSupport.compactGraphJson(snapshot));
                    }
                    return host.completeChat(system, user, AI_MAX_TOKENS);
                })
                .orTimeout(AI_TIMEOUT_SEC, TimeUnit.SECONDS)
                .whenComplete((raw, error) -> Platform.runLater(() -> {
                    aiRunning = false;
                    aiFuture = null;
                    stopProgressTimer();
                    setAiButtonsDisabled(false);
                    if (aiCancelled) {
                        setStatus("KI abgebrochen.");
                        return;
                    }
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        if (cause instanceof TimeoutException) {
                            setStatus("KI-Timeout nach " + AI_TIMEOUT_SEC
                                    + " s — schnelleres Modell wählen (z. B. gpt-4o-mini) oder erneut versuchen.");
                            return;
                        }
                        setStatus("KI fehlgeschlagen: "
                                + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
                        return;
                    }
                    if (replace) {
                        model = MindmapGraphParser.parseFull(raw);
                    } else {
                        model = MindmapGraphParser.mergeUpdate(snapshot, raw);
                    }
                    if (model.nodes == null || model.nodes.isEmpty()) {
                        setStatus("KI lieferte keine Knoten — erneut versuchen oder Kontext prüfen.");
                        return;
                    }
                    dirty = true;
                    pushGraphToView();
                    long sec = (System.currentTimeMillis() - aiStartMs) / 1000;
                    setStatus((replace ? "Erstlesen" : "Update") + " fertig (" + sec + " s) — "
                            + model.nodes.size() + " Knoten, " + model.edges.size()
                            + " Kanten. Bitte speichern.");
                }));
    }

    private void cancelAi() {
        aiCancelled = true;
        aiRunning = false;
        if (aiFuture != null) {
            aiFuture.cancel(true);
            aiFuture = null;
        }
        stopProgressTimer();
        setAiButtonsDisabled(false);
        setStatus("KI abgebrochen.");
    }

    private void cancelAiQuietly() {
        aiCancelled = true;
        if (aiFuture != null) {
            aiFuture.cancel(true);
            aiFuture = null;
        }
    }

    private void setAiButtonsDisabled(boolean disabled) {
        if (saveButton != null) {
            saveButton.setDisable(disabled);
        }
        if (reloadButton != null) {
            reloadButton.setDisable(disabled);
        }
        if (aiInitialButton != null) {
            aiInitialButton.setDisable(disabled);
        }
        if (aiUpdateButton != null) {
            aiUpdateButton.setDisable(disabled);
        }
        if (aiCancelButton != null) {
            aiCancelButton.setDisable(!disabled);
        }
    }

    private void startProgressTimer(String initialMessage) {
        stopProgressTimer();
        aiStartMs = System.currentTimeMillis();
        setStatus(initialMessage);
        progressTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (aiRunning) {
                long sec = (System.currentTimeMillis() - aiStartMs) / 1000;
                String hint = sec >= 90
                        ? " — bei >3 Min abbrechen und schnelleres Modell wählen (gpt-4o-mini)"
                        : "";
                setStatus("KI läuft … " + sec + " s" + hint);
            }
        }));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        progressTimeline.play();
    }

    private void stopProgressTimer() {
        if (progressTimeline != null) {
            progressTimeline.stop();
            progressTimeline = null;
        }
    }

    private void pullGraphFromView() {
        if (webEngine == null) {
            return;
        }
        try {
            Object payload = webEngine.executeScript(
                    "typeof flushGraphJson === 'function' ? flushGraphJson() "
                            + ": (typeof getGraphJson === 'function' ? getGraphJson() : null)");
            if (payload instanceof String s && !s.isBlank()) {
                MindmapModel parsed = GSON.fromJson(decodeGraphPayload(s), MindmapModel.class);
                if (parsed != null) {
                    model = parsed;
                }
            }
        } catch (Exception e) {
            setStatus("Graph aus Anzeige lesen fehlgeschlagen: " + e.getMessage());
        }
    }

    private void pushGraphToView() {
        if (webEngine == null || model == null) {
            return;
        }
        try {
            String json = GSON.toJson(model);
            String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            Object base64Ready = webEngine.executeScript("typeof loadGraphFromBase64 === 'function'");
            if (Boolean.TRUE.equals(base64Ready)) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.call("loadGraphFromBase64", b64);
            } else {
                Object jsonReady = webEngine.executeScript("typeof loadGraphFromJson === 'function'");
                if (Boolean.TRUE.equals(jsonReady)) {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.call("loadGraphFromJson", json);
                } else {
                    setStatus("Mindmap-Anzeige nicht bereit — bitte kurz warten und neu laden.");
                    return;
                }
            }
            updateStatusCounts();
        } catch (Exception e) {
            setStatus("Anzeige fehlgeschlagen: " + e.getMessage());
        }
    }

    /** UTF-8-sichere Graph-Payload (Base64) oder Legacy-JSON. */
    private static String decodeGraphPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        return new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
    }

    private void updateStatusCounts() {
        int n = model.nodes == null ? 0 : model.nodes.size();
        int e = model.edges == null ? 0 : model.edges.size();
        String mark = dirty ? " *" : "";
        statusLabel.setText(n + " Knoten, " + e + " Kanten" + mark);
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }

    private static Button button(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(text);
        b.getStyleClass().add("dialog-button");
        b.setOnAction(handler);
        return b;
    }
}
