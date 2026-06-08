package com.manuskript;

import com.manuskript.agent.AIBackend;
import com.manuskript.agent.OpenAIBackend;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.CustomAlert;
import com.manuskript.StageManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Welt-Editor: Verwaltung von Projekt-Kontextdateien (context, style, worldbuilding, characters, outline, akte, synopsis, chapter).
 * Jede Datei hat einen Tab mit {@link MdTextArea} für manuelle Editierung und KI-Button für automatische Generierung.
 */
public class WorldEditorWindow {

    private static final Logger logger = LoggerFactory.getLogger(WorldEditorWindow.class);

    private CustomStage stage;
    private TabPane tabPane;
    private final Window owner;
    private final String projectDirectory;
    private final MainController mainController;
    private final Map<String, MdTextArea> fileToTextArea = new HashMap<>();
    private final Map<String, Button> fileToAiButton = new HashMap<>();
    private final Map<String, Button> fileToExtractButton = new HashMap<>();
    private final Map<String, Button> fileToSaveButton = new HashMap<>();
    private final Map<String, Tab> fileToTab = new HashMap<>();
    private final Map<String, Label> fileToTabLabel = new HashMap<>();
    private Label statusLabel;
    private AIBackend aiBackend;
    private boolean suppressDirtyTracking = false;
    private int themeIndex;
    private final Map<String, Boolean> fileToDirty = new HashMap<>();

    private static final String[] FILES = {
        "context.txt",
        "style.txt",
        "worldbuilding.txt",
        "characters.txt",
        "outline.txt",
        "akte.txt",
        "synopsis.txt",
        "chapter.txt"
    };

    private static final String[] FILE_LABELS = {
        "Brainstorm",
        "Schreibstil",
        "Worldbuilding",
        "Charaktere",
        "Handlung (Outline)",
        "Akte",
        "Synopsis",
        "Kapitel"
    };

    public WorldEditorWindow(Window owner, String projectDirectory, MainController mainController) {
        this.owner = owner;
        this.projectDirectory = projectDirectory;
        this.mainController = mainController;
        initBackend();
        createUI();
    }

    private void initBackend() {
        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");
        logger.info("Backend-Typ: {}", backendType);
        String model;
        if ("OpenAI".equals(backendType)) {
            aiBackend = new OpenAIBackend();
            model = ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini");
            logger.info("OpenAI-Backend initialisiert");
        } else {
            aiBackend = new OllamaBackend(new OllamaService());
            model = ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
            logger.info("Ollama-Backend initialisiert");
        }
        if (model != null && !model.trim().isEmpty()) {
            aiBackend.setCurrentModel(model);
            logger.info("Modell aus Parametern gesetzt: {}", model);
        } else {
            logger.warn("Kein Modell in Parametern gefunden für Backend {}", backendType);
        }
        double temperature = "OpenAI".equals(backendType)
                ? ResourceManager.getDoubleParameter("agent.openai.temperature", 0.7)
                : ResourceManager.getDoubleParameter("ollama.temperature", 0.3);
        aiBackend.setTemperature(temperature);
        logger.info("Temperature aus Parametern gesetzt ({}): {}", backendType, temperature);
        logger.info("Backend ist null: {}", aiBackend == null);
    }

    private void createUI() {
        stage = StageManager.createStage("Welt-Editor");
        if (owner != null && owner instanceof javafx.stage.Stage) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        // Fenstergröße und -position persistieren (analog zum TTS-Editor)
        java.util.prefs.Preferences worldPrefs =
                java.util.prefs.Preferences.userNodeForPackage(WorldEditorWindow.class);
        javafx.geometry.Rectangle2D windowBounds =
                PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                        worldPrefs, "world_editor_window", 900.0, 600.0);
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, windowBounds);
        setupWorldEditorWindowPersistence(worldPrefs);

        int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
        this.themeIndex = theme;

        // Statuszeile oben rechts
        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("status-label");
        HBox statusBox = new HBox(statusLabel);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        statusBox.setPadding(new Insets(10, 15, 10, 10));

        tabPane = new TabPane();
        tabPane.getStyleClass().addAll("tab-pane", "world-editor-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (int i = 0; i < FILES.length; i++) {
            String filename = FILES[i];
            String label = FILE_LABELS[i];
            Label tabLabel = new Label(label);
            tabLabel.getStyleClass().add("world-editor-tab-label");
            Tab tab = new Tab();
            tab.setGraphic(tabLabel);
            tab.setUserData(filename);
            tab.setContent(createTabContent(filename));
            tabPane.getTabs().add(tab);
            fileToTab.put(filename, tab);
            fileToTabLabel.put(filename, tabLabel);
        }

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateTabEditorInteractivity(oldTab, newTab);
            refreshAllDirtyTabStyles();
            if (newTab != null && newTab.getUserData() instanceof String filename) {
                MdTextArea area = fileToTextArea.get(filename);
                if (area != null) {
                    Platform.runLater(area::requestFocus);
                }
                updateStatusForTab(filename);
            }
        });

        VBox root = new VBox();
        root.getStyleClass().addAll(getThemeStyleClasses(theme));
        root.getChildren().addAll(statusBox, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        stage.setTitleBarTheme(theme);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(theme);
        stage.setOnShown(event -> {
            updateTabEditorInteractivity(null, tabPane.getSelectionModel().getSelectedItem());
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getUserData() instanceof String filename) {
                MdTextArea area = fileToTextArea.get(filename);
                if (area != null) {
                    Platform.runLater(area::requestFocus);
                }
            }
        });

        // Beim Schließen: bei ungespeicherten Änderungen nachfragen
        stage.setOnCloseRequest(e -> {
            if (!hasUnsavedChanges()) {
                return;
            }
            e.consume();
            confirmSaveBeforeClose();
        });
    }

    /**
     * Speichert Position und Größe des Welt-Editors in den Preferences und stellt Listener ein.
     */
    private void setupWorldEditorWindowPersistence(java.util.prefs.Preferences prefs) {
        if (prefs == null || stage == null) return;
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(prefs, "world_editor_window_x", newVal.doubleValue());
            }
        });
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(prefs, "world_editor_window_y", newVal.doubleValue());
            }
        });
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowWidth(prefs, "world_editor_window_width", newVal.doubleValue());
            }
        });
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowHeight(prefs, "world_editor_window_height", newVal.doubleValue());
            }
        });
    }

    private static List<String> getThemeStyleClasses(int themeIndex) {
        switch (themeIndex) {
            case 0: return java.util.Collections.singletonList("weiss-theme");
            case 1: return java.util.Collections.singletonList("theme-dark");
            case 2: return java.util.Collections.singletonList("pastell-theme");
            case 3: return java.util.Collections.singletonList("blau-theme");
            case 4: return java.util.Collections.singletonList("gruen-theme");
            case 5: return java.util.Collections.singletonList("lila-theme");
            default: return java.util.Collections.singletonList("weiss-theme");
        }
    }

    private VBox createTabContent(String filename) {
        MdTextArea textArea = new MdTextArea(MdTextAreaOptions.builder()
                .editable(true)
                .showToolbar(true)
                .enableUndoRedo(true)
                .enableFontControls(true)
                .enableBasicFormatting(true)
                .enableSearch(true)
                .enableHideMarkupToggle(true)
                .hideMarkup(true)
                .themeIndex(themeIndex)
                .build());

        loadFile(filename, textArea);
        fileToTextArea.put(filename, textArea);
        markClean(filename);

        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!suppressDirtyTracking) {
                markDirty(filename);
            }
        });

        Button saveButton = new Button("💾 Speichern");
        saveButton.setOnAction(e -> {
            saveFile(filename, textArea);
            statusLabel.setText(labelForFile(filename) + " gespeichert");
        });
        fileToSaveButton.put(filename, saveButton);

        Button aiButton = new Button("🤖 KI-Generierung");
        aiButton.setOnAction(e -> handleAiGeneration(filename, textArea));
        fileToAiButton.put(filename, aiButton);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));
        buttonBox.getChildren().add(saveButton);
        if (WorldEditorAiPrompts.supportsExtractFromChapters(filename)) {
            Button extractButton = new Button("📖 Aus Kapiteln");
            extractButton.setOnAction(e -> handleExtractFromChapters(filename, textArea));
            fileToExtractButton.put(filename, extractButton);
            buttonBox.getChildren().add(extractButton);
        }
        buttonBox.getChildren().add(aiButton);

        VBox content = new VBox(5, textArea, buttonBox);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        return content;
    }

    private void updateTabEditorInteractivity(Tab oldTab, Tab newTab) {
        for (Tab tab : tabPane.getTabs()) {
            if (!(tab.getContent() instanceof VBox content)) {
                continue;
            }
            boolean selected = tab == newTab;
            content.setMouseTransparent(!selected);
        }
    }

    private void loadFile(String filename, MdTextArea textArea) {
        Path filePath = Paths.get(projectDirectory, filename);
        suppressDirtyTracking = true;
        try {
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath);
                textArea.setText(content);
            } else {
                textArea.setText("");
            }
            markClean(filename);
        } catch (IOException e) {
            logger.error("Fehler beim Laden von {}: {}", filename, e.getMessage());
        } finally {
            suppressDirtyTracking = false;
        }
    }

    private void saveFile(String filename, MdTextArea textArea) {
        Path filePath = Paths.get(projectDirectory, filename);
        try {
            Files.writeString(filePath, textArea.getText());
            markClean(filename);
            logger.info("Datei {} gespeichert", filename);
        } catch (IOException e) {
            logger.error("Fehler beim Speichern von {}: {}", filename, e.getMessage());
            showError("Speichern fehlgeschlagen", filename + ": " + e.getMessage());
        }
    }

    private void saveAllFiles() {
        for (Map.Entry<String, MdTextArea> entry : fileToTextArea.entrySet()) {
            if (Boolean.TRUE.equals(fileToDirty.get(entry.getKey()))) {
                saveFile(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean hasUnsavedChanges() {
        return fileToDirty.values().stream().anyMatch(Boolean::booleanValue);
    }

    private void markDirty(String filename) {
        if (Boolean.TRUE.equals(fileToDirty.get(filename))) {
            updateSaveButtonState(filename);
            updateTabDirtyState(filename);
            return;
        }
        fileToDirty.put(filename, true);
        updateSaveButtonState(filename);
        updateTabDirtyState(filename);
        if (isSelectedTab(filename)) {
            statusLabel.setText(labelForFile(filename) + " – ungespeichert");
        }
    }

    private void markClean(String filename) {
        fileToDirty.put(filename, false);
        updateSaveButtonState(filename);
        updateTabDirtyState(filename);
        if (isSelectedTab(filename)) {
            statusLabel.setText(labelForFile(filename) + " gespeichert");
        }
    }

    private void updateTabDirtyState(String filename) {
        Tab tab = fileToTab.get(filename);
        Label tabLabel = fileToTabLabel.get(filename);
        if (tab == null || tabLabel == null) {
            return;
        }
        boolean dirty = Boolean.TRUE.equals(fileToDirty.get(filename));
        String label = labelForFile(filename);
        tabLabel.setText(dirty ? "● " + label : label);
        if (dirty) {
            if (!tab.getStyleClass().contains("world-editor-tab-dirty")) {
                tab.getStyleClass().add("world-editor-tab-dirty");
            }
            if (!tabLabel.getStyleClass().contains("world-editor-tab-label-dirty")) {
                tabLabel.getStyleClass().add("world-editor-tab-label-dirty");
            }
            applyTabDirtyStyle(filename);
        } else {
            tab.getStyleClass().remove("world-editor-tab-dirty");
            tabLabel.getStyleClass().remove("world-editor-tab-label-dirty");
            tab.setStyle("");
            tabLabel.setStyle("");
        }
    }

    private void refreshAllDirtyTabStyles() {
        for (String filename : FILES) {
            if (Boolean.TRUE.equals(fileToDirty.get(filename))) {
                applyTabDirtyStyle(filename);
            }
        }
    }

    private void applyTabDirtyStyle(String filename) {
        Tab tab = fileToTab.get(filename);
        Label tabLabel = fileToTabLabel.get(filename);
        if (tab == null || tabLabel == null) {
            return;
        }
        boolean selected = isSelectedTab(filename);
        tab.setStyle(dirtyTabBackgroundStyle(selected));
        tabLabel.setStyle(dirtyTabLabelStyle());
    }

    private String dirtyTabBackgroundStyle(boolean selected) {
        return switch (themeIndex) {
            case 1, 3, 4, 5 -> selected
                    ? "-fx-background-color: #6d4c41; -fx-border-color: #ff9800; -fx-border-width: 1 1 0 1; -fx-background-radius: 4 4 0 0;"
                    : "-fx-background-color: #5d4037; -fx-border-color: #ff9800; -fx-border-width: 1 1 0 1; -fx-background-radius: 4 4 0 0;";
            case 2 -> selected
                    ? "-fx-background-color: #ffe082; -fx-border-color: #ff8f00; -fx-border-width: 1 1 0 1; -fx-background-radius: 4 4 0 0;"
                    : "-fx-background-color: #ffecb3; -fx-border-color: #ff8f00; -fx-border-width: 1 1 0 1; -fx-background-radius: 4 4 0 0;";
            default -> selected
                    ? "-fx-background-color: #ffcc80; -fx-border-color: #fb8c00; -fx-border-width: 1 1 0 1; -fx-background-radius: 4 4 0 0;"
                    : "-fx-background-color: #ffe0b2; -fx-border-color: #fb8c00; -fx-border-width: 1 1 0 1; -fx-background-radius: 4 4 0 0;";
        };
    }

    private String dirtyTabLabelStyle() {
        return switch (themeIndex) {
            case 1, 3, 4, 5 -> "-fx-text-fill: #ffcc80; -fx-font-weight: bold;";
            case 2 -> "-fx-text-fill: #e65100; -fx-font-weight: bold;";
            default -> "-fx-text-fill: #bf360c; -fx-font-weight: bold;";
        };
    }

    private boolean isSelectedTab(String filename) {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        return selected != null && filename.equals(selected.getUserData());
    }

    private void updateStatusForTab(String filename) {
        if (Boolean.TRUE.equals(fileToDirty.get(filename))) {
            statusLabel.setText(labelForFile(filename) + " – ungespeichert");
        } else {
            statusLabel.setText("Bereit");
        }
    }

    private List<String> listUnsavedTabLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < FILES.length; i++) {
            if (Boolean.TRUE.equals(fileToDirty.get(FILES[i]))) {
                labels.add(FILE_LABELS[i]);
            }
        }
        return labels;
    }

    private void updateSaveButtonState(String filename) {
        Button saveButton = fileToSaveButton.get(filename);
        if (saveButton == null) {
            return;
        }
        boolean dirty = Boolean.TRUE.equals(fileToDirty.get(filename));
        saveButton.setText(dirty ? "💾 Speichern *" : "💾 Speichern");
    }

    private static String labelForFile(String filename) {
        for (int i = 0; i < FILES.length; i++) {
            if (FILES[i].equals(filename)) {
                return FILE_LABELS[i];
            }
        }
        return filename;
    }

    private void confirmSaveBeforeClose() {
        List<String> unsavedTabs = listUnsavedTabLabels();
        String tabList = String.join(", ", unsavedTabs);

        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
        alert.setHeaderText("Welt-Editor wirklich schließen?");
        alert.setContentText("Ungespeicherte Änderungen in: " + tabList + ".\n\n"
                + "Speichern, verwerfen oder den Editor geöffnet lassen?");
        ButtonType saveButton = new ButtonType("Speichern und schließen");
        ButtonType discardButton = new ButtonType("Verwerfen und schließen");
        ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.setButtonTypes(saveButton, discardButton, cancelButton);
        alert.applyTheme(themeIndex);
        alert.initOwner(stage);
        alert.showAndWait().ifPresent(response -> {
            if (response == saveButton) {
                saveAllFiles();
                stage.close();
            } else if (response == discardButton) {
                stage.close();
            }
        });
    }

    private void handleAiGeneration(String filename, MdTextArea textArea) {
        String currentContent = textArea.getText();
        boolean hasContent = currentContent != null && !currentContent.trim().isEmpty();

        if ("chapter.txt".equals(filename) && hasContent) {
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
            alert.setHeaderText("Die Datei enthält bereits Inhalt.");
            alert.setContentText("Möchtest du nur neue Kapitel einfügen oder alles neu generieren?");
            ButtonType appendButton = new ButtonType("Nur neue Kapitel");
            ButtonType replaceButton = new ButtonType("Alles neu");
            alert.setButtonTypes(appendButton, replaceButton);
            alert.applyTheme(theme);
            alert.initOwner(stage);
            alert.showAndWait().ifPresent(response -> {
                if (response == appendButton) {
                    generateWithAi(filename, textArea, true);
                } else if (response == replaceButton) {
                    generateWithAi(filename, textArea, false);
                }
            });
        } else if (hasContent) {
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
            alert.setHeaderText("Die Datei enthält bereits Inhalt.");
            alert.setContentText("Neuen KI-Text anhängen oder den Tab-Inhalt ersetzen?");
            ButtonType appendButton = new ButtonType("Anhängen");
            ButtonType replaceButton = new ButtonType("Ersetzen");
            ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.setButtonTypes(appendButton, replaceButton, cancel);
            alert.applyTheme(theme);
            alert.initOwner(stage);
            alert.showAndWait().ifPresent(response -> {
                if (response == appendButton) {
                    generateWithAi(filename, textArea, true);
                } else if (response == replaceButton) {
                    generateWithAi(filename, textArea, false);
                }
            });
        } else {
            generateWithAi(filename, textArea, false);
        }
    }

    private void handleExtractFromChapters(String filename, MdTextArea textArea) {
        if (!WorldEditorContextBuilder.hasChapterSources(projectDirectory, mainController)) {
            showError("Keine Kapitelquellen",
                    "Weder chapter.txt noch Markdown-Kapitel unter data/ gefunden.\n"
                            + "Bitte zuerst Kapitel schreiben oder im Tab „Kapitel“ Zusammenfassungen erzeugen.");
            return;
        }
        String currentContent = textArea.getText();
        boolean hasContent = currentContent != null && !currentContent.trim().isEmpty();
        if (hasContent) {
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
            alert.setHeaderText("Aus Manuskript extrahieren");
            alert.setContentText("Bestehenden Tab-Inhalt ergänzen/aktualisieren oder komplett ersetzen?");
            ButtonType appendButton = new ButtonType("Ergänzen");
            ButtonType replaceButton = new ButtonType("Ersetzen");
            ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.setButtonTypes(appendButton, replaceButton, cancel);
            alert.applyTheme(theme);
            alert.initOwner(stage);
            alert.showAndWait().ifPresent(response -> {
                if (response == appendButton) {
                    promptChapterSelectionAndExtract(filename, textArea, true);
                } else if (response == replaceButton) {
                    promptChapterSelectionAndExtract(filename, textArea, false);
                }
            });
        } else {
            promptChapterSelectionAndExtract(filename, textArea, false);
        }
    }

    private void promptChapterSelectionAndExtract(String filename, MdTextArea textArea, boolean append) {
        List<String> availableMd = WorldEditorContextBuilder.listAvailableMdFiles(projectDirectory, mainController);
        if (availableMd.isEmpty()) {
            logger.info("Keine MD-Kapitel unter data/ – Extraktion nutzt chapter.txt");
            extractFromChapters(filename, textArea, append, null);
            return;
        }
        WorldEditorExtractChapterDialog.show(stage, availableMd, themeIndex, append)
                .ifPresent(scope -> extractFromChapters(filename, textArea, append, scope));
    }

    private void extractFromChapters(String filename, MdTextArea textArea, boolean append,
                                     WorldEditorExtractScope extractScope) {
        runAiGeneration(filename, textArea, append, true, extractScope);
    }

    private void generateWithAi(String filename, MdTextArea textArea, boolean append) {
        runAiGeneration(filename, textArea, append, false, null);
    }

    private void runAiGeneration(String filename, MdTextArea textArea, boolean append, boolean extractFromManuscript,
                                 WorldEditorExtractScope extractScope) {
        if (aiBackend == null) {
            logger.error("KI-Backend nicht initialisiert");
            showError("KI-Backend nicht initialisiert",
                    "Bitte konfigurieren Sie das KI-Backend in den Parametern.");
            return;
        }

        if ("chapter.txt".equals(filename) && !extractFromManuscript) {
            generateChapterSummaries(textArea);
            return;
        }

        String prompt = extractFromManuscript
                ? WorldEditorAiPrompts.extractFromChaptersPrompt(filename, append, extractScope)
                : WorldEditorAiPrompts.generatePrompt(filename);
        String currentContent = textArea.getText() == null ? "" : textArea.getText();

        String projectContext = WorldEditorContextBuilder.build(projectDirectory, mainController, filename, extractScope);
        if (projectContext.isBlank() && extractFromManuscript) {
            showError("Kein Manuskript-Kontext", "Es wurden keine Kapiteltexte oder Zusammenfassungen gefunden.");
            return;
        }

        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append(prompt);
        if (!projectContext.isBlank()) {
            fullPrompt.append("\n\n").append(projectContext);
        }
        if (append && !currentContent.trim().isEmpty()) {
            fullPrompt.append("\n\n=== BISHERIGER INHALT DIESES TABS ===\n").append(currentContent.trim());
        }

        logger.info("KI-{} fuer {} gestartet", extractFromManuscript ? "Extraktion" : "Generierung", filename);
        setAiButtonsBusy(filename, true, extractFromManuscript);
        statusLabel.setText(extractFromManuscript ? "Extrahiere aus Kapiteln …" : "KI generiert …");

        int maxTokens = WorldEditorAiPrompts.maxTokensForFile(filename, extractFromManuscript);
        aiBackend.chat(
                "Du bist ein erfahrener deutscher Lektor und Projekt-Assistent. Antworte nur mit dem "
                        + "angeforderten Markdown-Inhalt, ohne Meta-Kommentare.",
                fullPrompt.toString(),
                maxTokens
        ).thenAccept(generatedContent -> Platform.runLater(() -> {
            String result = generatedContent == null ? "" : generatedContent.trim();
            if (append && extractFromManuscript && !currentContent.trim().isEmpty()) {
                textArea.setText(WorldEditorExtractMerge.mergeAppendExtract(filename, currentContent, result));
            } else if (append && !currentContent.trim().isEmpty()) {
                textArea.setText(currentContent.trim() + "\n\n" + result);
            } else {
                textArea.setText(result);
            }
            statusLabel.setText(extractFromManuscript ? "Extraktion abgeschlossen – bitte speichern"
                    : "KI-Generierung abgeschlossen – bitte speichern");
            setAiButtonsBusy(filename, false, extractFromManuscript);
            logger.info("KI-{} fuer {} abgeschlossen", extractFromManuscript ? "Extraktion" : "Generierung", filename);
        })).exceptionally(ex -> {
            logger.error("KI-Aufruf fuer {} fehlgeschlagen: {}", filename, ex.getMessage());
            Platform.runLater(() -> {
                showError("Fehler bei der KI-Verarbeitung", ex.getMessage());
                setAiButtonsBusy(filename, false, extractFromManuscript);
                statusLabel.setText("Fehler");
            });
            return null;
        });
    }

    private void setAiButtonsBusy(String filename, boolean busy, boolean extractRunning) {
        fileToAiButton.values().forEach(b -> b.setDisable(busy));
        fileToExtractButton.values().forEach(b -> b.setDisable(busy));
        Button aiButton = fileToAiButton.get(filename);
        if (aiButton != null) {
            aiButton.setText(busy && !extractRunning ? "🤖 Generiere…" : "🤖 KI-Generierung");
        }
        Button extractButton = fileToExtractButton.get(filename);
        if (extractButton != null) {
            extractButton.setText(busy && extractRunning ? "📖 Extrahiere…" : "📖 Aus Kapiteln");
        }
    }

    private void showError(String header, String content) {
        int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
        CustomAlert errorAlert = new CustomAlert(CustomAlert.AlertType.ERROR);
        errorAlert.setHeaderText(header);
        errorAlert.setContentText(content);
        errorAlert.applyTheme(theme);
        errorAlert.initOwner(stage);
        errorAlert.showAndWait();
    }

    private void generateChapterSummaries(MdTextArea textArea) {
        logger.info("generateChapterSummaries aufgerufen");
        String currentContent = textArea.getText();
        Set<String> existingSummaries = parseExistingSummaries(currentContent);
        logger.info("Existierende Zusammenfassungen: {}", existingSummaries);
        
        // Kapitel aus der rechten Tabelle holen
        List<String> mdFiles = mainController.getMarkdownFilesInOrder();
        logger.info("MD-Dateien aus rechter Tabelle: {}", mdFiles);
        
        setAiButtonsBusy("chapter.txt", true, false);
        suppressDirtyTracking = true;
        generateNextChapter(textArea, mdFiles, existingSummaries, 0);
    }

    private void generateNextChapter(MdTextArea textArea, List<String> mdFiles, Set<String> existingSummaries, int index) {
        if (index >= mdFiles.size()) {
            Platform.runLater(() -> {
                suppressDirtyTracking = false;
                markDirty("chapter.txt");
                statusLabel.setText("Alle Kapitel verarbeitet – bitte speichern");
                setAiButtonsBusy("chapter.txt", false, false);
            });
            return;
        }
        
        String mdFileName = mdFiles.get(index);
        String chapterName = mdFileName.replace(".md", "").trim(); // Leerzeichen am Ende entfernen
        
        if (existingSummaries.contains(chapterName)) {
            // Kapitel hat bereits Zusammenfassung, zum nächsten
            logger.info("Kapitel {} bereits vorhanden, überspringe", chapterName);
            generateNextChapter(textArea, mdFiles, existingSummaries, index + 1);
            return;
        }

        Path mdPath = Paths.get(projectDirectory, "data", mdFileName);
        logger.info("Verarbeite Kapitel {}: {}", index, chapterName);
        logger.info("MD-Pfad: {}", mdPath);
        logger.info("MD-Datei existiert: {}", Files.exists(mdPath));
        
        if (!Files.exists(mdPath)) {
            // Datei existiert nicht, zum nächsten
            generateNextChapter(textArea, mdFiles, existingSummaries, index + 1);
            return;
        }

        try {
            String chapterContent = readMdContent(mdPath);
            chapterContent = chapterContent.replaceAll("(?m)^---+$", "");
            int charCount = chapterContent.length();
            logger.info("Kapitelinhalt geladen, Länge: {}", charCount);
            
            // Aktuelles Modell loggen
            logger.info("Aktuelles KI-Modell: {}", aiBackend.getCurrentModel());
            
            // Statuszeile aktualisieren
            Platform.runLater(() -> {
                statusLabel.setText(String.format("Datei %d von %d. %s (%d Zeichen)", index + 1, mdFiles.size(), chapterName, charCount));
            });
            
            String prompt = "Fasse das folgende Kapitel auf Deutsch zusammen. Kein Vorgeplänkel, keine Floskeln, keine Bewertung. Nur die Zusammenfassung.\n" +
                "Wichtig: Erstelle genau EINE Zusammenfassung für das GESAMTE Kapitel. Unterteile es NICHT in Unterkapitel.\n\n" +
                "Gliedere deine Antwort exakt so:\n" +
                "## Kapitelname\n" +
                "Inhaltsangabe (3-5 Sätze)\n" +
                "**Ort:** ...\n" +
                "**Charaktere:** ...\n" +
                "**Themen:** ...\n" +
                "**Stimmung:** ...";
            String fullPrompt = prompt + "\n\nKapitelinhalt:\n" + chapterContent;
            logger.info("Prompt für KI: {}", prompt);
            logger.info("FullPrompt Länge: {}", fullPrompt.length());
            
            final String finalChapterName = chapterName;
            final int nextIndex = index + 1;
            logger.info("KI-Aufruf für Kapitel: {}", finalChapterName);

            Platform.runLater(() -> {
                String header = "\n\n## " + finalChapterName + "\n";
                textArea.appendText(header);
                statusLabel.setText(String.format("Datei %d von %d. %s – KI generiert …",
                        index + 1, mdFiles.size(), finalChapterName));
            });

            int maxTokens = WorldEditorAiPrompts.maxTokensForFile("chapter.txt", false);
            aiBackend.chat(
                    "Du bist ein hilfreicher deutscher Assistent. Antworte bitte auf Deutsch.",
                    fullPrompt,
                    maxTokens
            ).thenAccept(generatedContent -> Platform.runLater(() -> {
                String result = generatedContent == null ? "" : generatedContent.trim();
                if (!result.isEmpty()) {
                    textArea.appendText(result);
                    if (!result.endsWith("\n")) {
                        textArea.appendText("\n");
                    }
                }
                logger.info("Zusammenfassung für {} generiert ({} Zeichen)", finalChapterName, result.length());
                statusLabel.setText(String.format("Datei %d von %d. %s fertig …", index + 1, mdFiles.size(), finalChapterName));
                generateNextChapter(textArea, mdFiles, existingSummaries, nextIndex);
            })).exceptionally(ex -> {
                logger.error("Fehler bei der Generierung der Zusammenfassung für {}: {}", chapterName, ex.getMessage(), ex);
                Platform.runLater(() -> {
                    showError("Fehler bei der KI-Generierung", ex.getMessage());
                    generateNextChapter(textArea, mdFiles, existingSummaries, nextIndex);
                });
                return null;
            });
        } catch (Exception e) {
            logger.error("Fehler beim Lesen von {}: {}", mdFileName, e.getMessage(), e);
            generateNextChapter(textArea, mdFiles, existingSummaries, index + 1);
        }
    }
    
    private Set<String> parseExistingSummaries(String content) {
        Set<String> summaries = new HashSet<>();
        if (content == null || content.trim().isEmpty()) {
            logger.info("Kein Inhalt vorhanden, keine Zusammenfassungen gefunden");
            return summaries;
        }
        
        // Markdown-Überschriften parsen (## Kapitelname)
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("## ")) {
                String chapterName = line.substring(3).trim();
                summaries.add(chapterName);
                logger.info("Gefundene Zusammenfassung: {}", chapterName);
            }
        }
        logger.info("Insgesamt {} Zusammenfassungen gefunden", summaries.size());
        return summaries;
    }

    private String readMdContent(Path mdPath) throws IOException {
        try {
            String content = Files.readString(mdPath);
            return content;
        } catch (Exception e) {
            logger.error("Fehler beim Lesen von MD {}: {}", mdPath, e.getMessage());
            return "[Fehler beim Lesen des Kapitels]";
        }
    }

    public void show() {
        stage.show();
    }

    public void hide() {
        stage.hide();
    }
}
