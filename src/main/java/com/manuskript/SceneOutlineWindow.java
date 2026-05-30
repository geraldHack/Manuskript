package com.manuskript;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.util.Duration;

/**
 * Fenster zum Bearbeiten der nummerierten Szenen-Outline pro Kapitel.
 */
public class SceneOutlineWindow {

    private static final Logger logger = LoggerFactory.getLogger(SceneOutlineWindow.class);
    private static final int AUTO_SAVE_MS = 600;
    private static final String PREFS_PREFIX = "scene_outline_window";

    private CustomStage stage;
    private BorderPane root;
    private VBox centerPanel;
    private Label hintLabel;
    private NumberedListTextArea editor;
    private Label statusLabel;
    private Button btnSave;
    private Button btnClose;
    private File scenesFile;
    private Timeline autoSaveTimeline;
    private boolean dirty = false;
    private int themeIndex = 0;
    private int fontSize = 12;
    private final Preferences preferences = Preferences.userNodeForPackage(EditorWindow.class);

    public void show(Scene ownerScene, File docxFile, String chapterDisplayName, int themeIndex) {
        this.themeIndex = themeIndex;
        this.scenesFile = SceneOutlinePaths.scenesFileForDocx(docxFile);

        if (stage == null) {
            createStage(ownerScene);
        }
        applyTheme(themeIndex);
        applyFontSizeFromPreferences();

        stage.setTitle("Szenen-Outline — " + (chapterDisplayName != null ? chapterDisplayName : "Kapitel"));
        loadFromFile();
        stage.show();
        stage.toFront();
    }

    public void reloadForChapter(Scene ownerScene, File docxFile, String chapterDisplayName, int themeIndex) {
        if (stage == null || !stage.isShowing()) {
            return;
        }
        saveIfDirty();
        show(ownerScene, docxFile, chapterDisplayName, themeIndex);
    }

    public void applyTheme(int themeIndex) {
        this.themeIndex = themeIndex;
        if (stage != null) {
            stage.setTitleBarTheme(themeIndex);
        }
        if (centerPanel == null) {
            return;
        }
        applyThemeToNode(root);
        applyThemeToNode(centerPanel);
        applyThemeToNode(hintLabel);
        applyThemeToNode(editor);
        applyThemeToNode(statusLabel);
        applyThemeToNode(btnSave);
        applyThemeToNode(btnClose);
        applyFontSize(fontSize);
    }

    public void applyFontSize(int size) {
        if (size < 8) {
            size = 8;
        } else if (size > 72) {
            size = 72;
        }
        this.fontSize = size;
        if (editor == null) {
            return;
        }
        String editorFont = String.format(
            "-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: %dpx;", size);
        String labelFont = String.format("-fx-font-size: %dpx;", size);
        editor.setStyle(editorFont);
        if (hintLabel != null) {
            hintLabel.setStyle(labelFont);
        }
        if (statusLabel != null) {
            statusLabel.setStyle(labelFont);
        }
    }

    private void applyFontSizeFromPreferences() {
        applyFontSize(preferences.getInt("fontSize", 12));
    }

    private void applyThemeToNode(Node node) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll(
            "theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
        switch (themeIndex) {
            case 0 -> node.getStyleClass().add("weiss-theme");
            case 1 -> node.getStyleClass().add("theme-dark");
            case 2 -> node.getStyleClass().add("pastell-theme");
            case 3 -> node.getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> node.getStyleClass().addAll("theme-dark", "gruen-theme");
            case 5 -> node.getStyleClass().addAll("theme-dark", "lila-theme");
            default -> node.getStyleClass().add("weiss-theme");
        }
    }

    public void hide() {
        if (stage != null) {
            saveIfDirty();
            saveWindowProperties();
            stage.hide();
        }
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    private void createStage(Scene ownerScene) {
        stage = StageManager.createStage("SceneOutline");
        stage.setTitle("Szenen-Outline");
        stage.setWidth(640);
        stage.setHeight(480);
        stage.setTitleBarTheme(themeIndex);
        stage.initModality(Modality.NONE);
        if (ownerScene != null && ownerScene.getWindow() != null) {
            stage.initOwner(ownerScene.getWindow());
        }

        hintLabel = new Label("Nummerierte Szenenbeschreibungen (Enter = nächste Nummer):");
        hintLabel.getStyleClass().add("scene-outline-hint");

        editor = new NumberedListTextArea();
        editor.setPrefRowCount(20);
        VBox.setVgrow(editor, Priority.ALWAYS);

        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("scene-outline-status");

        btnSave = new Button("Speichern");
        btnSave.getStyleClass().addAll("button", "primary");
        btnSave.setOnAction(e -> saveToFile());

        btnClose = new Button("Schließen");
        btnClose.getStyleClass().add("button");
        btnClose.setOnAction(e -> {
            saveIfDirty();
            saveWindowProperties();
            stage.hide();
        });

        HBox buttons = new HBox(8, btnSave, btnClose);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        centerPanel = new VBox(8, hintLabel, editor, statusLabel, buttons);
        centerPanel.setPadding(new Insets(12));
        centerPanel.getStyleClass().add("scene-outline-window");
        VBox.setVgrow(editor, Priority.ALWAYS);

        root = new BorderPane(centerPanel);
        root.getStyleClass().add("scene-outline-root");

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        stage.setSceneWithTitleBar(scene);

        applyTheme(themeIndex);
        applyFontSizeFromPreferences();
        loadWindowProperties();

        editor.textProperty().addListener((obs, o, n) -> {
            dirty = true;
            scheduleAutoSave();
        });

        stage.setOnCloseRequest(e -> {
            e.consume();
            saveIfDirty();
            saveWindowProperties();
            stage.hide();
        });
    }

    private void loadWindowProperties() {
        Rectangle2D bounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
            preferences, PREFS_PREFIX, 640.0, 480.0);
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, bounds);

        stage.xProperty().addListener((obs, oldVal, newVal) ->
            preferences.putDouble(PREFS_PREFIX + "_x", newVal.doubleValue()));
        stage.yProperty().addListener((obs, oldVal, newVal) ->
            preferences.putDouble(PREFS_PREFIX + "_y", newVal.doubleValue()));
        stage.widthProperty().addListener((obs, oldVal, newVal) ->
            preferences.putDouble(PREFS_PREFIX + "_width", newVal.doubleValue()));
        stage.heightProperty().addListener((obs, oldVal, newVal) ->
            preferences.putDouble(PREFS_PREFIX + "_height", newVal.doubleValue()));
    }

    private void saveWindowProperties() {
        if (stage == null) {
            return;
        }
        preferences.putDouble(PREFS_PREFIX + "_x", stage.getX());
        preferences.putDouble(PREFS_PREFIX + "_y", stage.getY());
        preferences.putDouble(PREFS_PREFIX + "_width", stage.getWidth());
        preferences.putDouble(PREFS_PREFIX + "_height", stage.getHeight());
    }

    private void loadFromFile() {
        if (scenesFile == null) {
            editor.setPlainContent("");
            statusLabel.setText("Kein Kapitel geöffnet");
            dirty = false;
            return;
        }
        try {
            if (scenesFile.exists()) {
                String content = Files.readString(scenesFile.toPath(), StandardCharsets.UTF_8);
                editor.setPlainContent(content);
                statusLabel.setText("Geladen: " + scenesFile.getName());
            } else {
                editor.setPlainContent("");
                statusLabel.setText("Neue Datei: " + scenesFile.getName());
            }
            dirty = false;
        } catch (IOException e) {
            logger.warn("Fehler beim Laden der Szenen-Outline: {}", e.getMessage());
            statusLabel.setText("Fehler beim Laden");
        }
    }

    private void scheduleAutoSave() {
        if (autoSaveTimeline != null) {
            autoSaveTimeline.stop();
        }
        autoSaveTimeline = new Timeline(new KeyFrame(Duration.millis(AUTO_SAVE_MS), e -> saveToFile()));
        autoSaveTimeline.play();
    }

    private void saveIfDirty() {
        if (dirty) {
            saveToFile();
        }
    }

    private void saveToFile() {
        if (scenesFile == null) {
            return;
        }
        try {
            File parent = scenesFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String numbered = editor.getNumberedContent();
            Files.writeString(scenesFile.toPath(), numbered, StandardCharsets.UTF_8);
            dirty = false;
            Platform.runLater(() -> statusLabel.setText("Gespeichert: " + scenesFile.getName()));
        } catch (IOException e) {
            logger.warn("Fehler beim Speichern der Szenen-Outline: {}", e.getMessage());
            Platform.runLater(() -> statusLabel.setText("Speichern fehlgeschlagen"));
        }
    }

    public static String loadScenesOutlineText(File docxFile) {
        File f = SceneOutlinePaths.scenesFileForDocx(docxFile);
        if (f == null || !f.exists()) {
            return "";
        }
        try {
            return Files.readString(f.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Liefert die aktuelle Outline (inkl. ungespeicherter Änderungen im Fenster) für ein Kapitel.
     */
    public String getOutlineTextForDocx(File docxFile) {
        if (docxFile == null) {
            return "";
        }
        File expected = SceneOutlinePaths.scenesFileForDocx(docxFile);
        if (isShowing() && editor != null && scenesFile != null
                && expected != null
                && scenesFile.getAbsolutePath().equals(expected.getAbsolutePath())) {
            saveIfDirty();
            return editor.getNumberedContent().trim();
        }
        return loadScenesOutlineText(docxFile);
    }
}
