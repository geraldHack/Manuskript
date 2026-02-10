package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.GridPane;
import javafx.geometry.Pos;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.util.StringConverter;
import javafx.util.Duration;
import javafx.scene.layout.Priority;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Machbarkeitsstudie: Kleines Fenster zum Testen der ComfyUI/Qwen3-TTS-API.
 * Fester Text und Parameter wie im Screenshot; Button zum Abspielen des Ergebnisses.
 */
public class ComfyUITTSTestWindow {

    private static final Logger logger = LoggerFactory.getLogger(ComfyUITTSTestWindow.class);

    /** Executor mit niedriger Thread-Priorität, damit ComfyUI-Generierung das System weniger blockiert. */
    private static final ExecutorService COMFY_LOW_PRIORITY_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "comfyui-tts-worker");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1)); // Unter Windows: etwas unter Normal
        return t;
    });

    private static final String FIXED_TEXT = "Hallo ich bin cursor AI und ich spreche zum erstem Mal mit dir.";
    private static final String FIXED_INSTRUCT = ComfyUIClient.DEFAULT_INSTRUCT_DEUTSCH;
    /** Kurzer Text für Stimmsuche (mehrere Vorschläge schnell abspielbar). */
    private static final String VOICE_SEARCH_SAMPLE = "Hallo, dies ist eine kurze Probe meiner Stimme.";

    /** Aktuell geöffnetes Audio-Wiedergabe-Popup; beim Öffnen eines neuen wird dieses geschlossen. */
    private static CustomStage openAudioPlayerStage;

    private CustomStage stage;
    private final Window owner;
    private Label statusLabel;
    private Button btnRun;
    private Button btnPlayOrOpen;
    private ProgressIndicator progress;
    private TextArea textInputArea;
    private CheckBox highQualityCheckBox;
    private TableView<LexiconEntry> lexiconTable;
    private TableColumn<LexiconEntry, String> colWord;
    private TableColumn<LexiconEntry, String> colReplacement;
    private ObservableList<LexiconEntry> lexiconItems;
    private TextArea logArea;
    private Path lastAudioPath;

    // Stimmsuche-Tab
    private TextArea voiceSearchSampleTextArea;
    private CheckBox voiceSearchHighQualityCheckBox;
    private Slider voiceSearchTopPSlider;
    private Label voiceSearchTopPLabel;
    private Slider voiceSearchTopKSlider;
    private Label voiceSearchTopKLabel;
    private Slider voiceSearchRepetitionPenaltySlider;
    private Label voiceSearchRepetitionPenaltyLabel;
    private TextField voiceSeedField;
    private TextField voiceNameField;
    private Slider voiceTempSlider;
    private Label voiceTempLabel;
    private ComboBox<String> voiceDescriptionCombo;
    /** Auswahl der Qwen3-CustomVoice-Grundstimme (z. B. Ryan, Sohee, Vivian …). */
    private ComboBox<String> customSpeakerCombo;
    private HBox voiceSuggestionsBox;
    private javafx.scene.control.ListView<ComfyUIClient.SavedVoice> savedVoicesList;
    private ObservableList<ComfyUIClient.SavedVoice> savedVoicesItems;
    private ProgressIndicator voiceSearchProgress;

    // TTS-Tab: gewählte Stimme
    private ComboBox<ComfyUIClient.SavedVoice> voiceComboBox;
    private Label voiceSelectionHintLabel;
    private VBox speakContent;
    private VBox searchContent;
    private StackPane contentStack;
    /** Wird von „Für TTS verwenden“ im Stimmsuche-Bereich aufgerufen, um auf „Text vorlesen“ zu wechseln. */
    private Runnable switchToSprechen;

    /** Zuletzt gestarteter Restart-Skript-Prozess; wird beim Schließen des Fensters beendet, damit Java sauber beendet werden kann. */
    private Process lastRestartProcess;

    /** Ein Eintrag im Aussprache-Lexikon (Wort → Ersetzung). */
    public static class LexiconEntry {
        private final SimpleStringProperty word = new SimpleStringProperty("");
        private final SimpleStringProperty replacement = new SimpleStringProperty("");

        public LexiconEntry(String word, String replacement) {
            this.word.set(word != null ? word : "");
            this.replacement.set(replacement != null ? replacement : "");
        }
        public SimpleStringProperty wordProperty() { return word; }
        public SimpleStringProperty replacementProperty() { return replacement; }
        public String getWord() { return word.get(); }
        public String getReplacement() { return replacement.get(); }
        public void setWord(String v) { word.set(v != null ? v : ""); }
        public void setReplacement(String v) { replacement.set(v != null ? v : ""); }
    }

    public ComfyUITTSTestWindow(Window owner) {
        this.owner = owner;
    }

    public static void show(Window owner) {
        ComfyUITTSTestWindow w = new ComfyUITTSTestWindow(owner);
        w.initializeWindow();
        w.stage.show();
    }

    private void initializeWindow() {
        stage = StageManager.createStage("TTS Test (ComfyUI / Qwen3)");
        if (owner instanceof javafx.stage.Stage) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(880);
        stage.setMinHeight(720);
        stage.setWidth(1000);
        stage.setHeight(880);

        // —— Bereich: Sprechen ——
        speakContent = new VBox(12);
        speakContent.setPadding(new Insets(15));

        statusLabel = new Label("Bereit. ComfyUI-URL: " + ComfyUIClient.getBaseUrlFromConfig());
        statusLabel.setWrapText(true);
        Button btnRestartComfyUI = new Button("ComfyUI neu starten");
        btnRestartComfyUI.setTooltip(new javafx.scene.control.Tooltip(
                "Startet das in den Parametern (comfyui.restart_script) hinterlegte Skript zum Neustarten von ComfyUI.\nManuskript (Java) und ComfyUI sind getrennte Prozesse – beim Neustart von ComfyUI läuft Manuskript weiter."));
        btnRestartComfyUI.setOnAction(e -> runComfyUIRestartScript());
        String restartScript = ResourceManager.getParameter("comfyui.restart_script", "");
        btnRestartComfyUI.setDisable(restartScript == null || restartScript.isBlank());
        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(statusLabel, btnRestartComfyUI);

        savedVoicesItems = FXCollections.observableArrayList(ComfyUIClient.loadSavedVoices());
        voiceComboBox = new ComboBox<>();
        voiceComboBox.getItems().add(null); // "Standard" durch null repräsentiert
        voiceComboBox.getItems().addAll(savedVoicesItems);
        voiceComboBox.setPromptText("Stimme: Standard (Seed " + ComfyUIClient.DEFAULT_SEED + ")");
        voiceComboBox.setConverter(new StringConverter<ComfyUIClient.SavedVoice>() {
            @Override
            public String toString(ComfyUIClient.SavedVoice v) {
                if (v == null) return "Standard";
                return v.getName() + " (Seed " + v.getSeed() + ")";
            }
            @Override
            public ComfyUIClient.SavedVoice fromString(String s) { return null; }
        });
        voiceComboBox.getSelectionModel().select(0);
        voiceComboBox.setDisable(false);
        voiceSelectionHintLabel = new Label("Verwendet: Standard");
        voiceSelectionHintLabel.setStyle("-fx-text-fill: -fx-text-background-color; -fx-font-style: italic;");
        voiceComboBox.getSelectionModel().selectedItemProperty().addListener((o, prev, sel) -> {
            if (sel == null) voiceSelectionHintLabel.setText("Verwendet: Standard");
            else voiceSelectionHintLabel.setText("Verwendet: \"" + sel.getName() + "\" (Seed " + sel.getSeed() + ", " + (sel.isHighQuality() ? "1.7B" : "0.6B") + ")");
        });
        HBox voiceRow = new HBox(8);
        voiceRow.getChildren().addAll(new Label("Stimme:"), voiceComboBox, voiceSelectionHintLabel);
        voiceRow.setAlignment(Pos.CENTER_LEFT);
        voiceRow.setDisable(false);

        highQualityCheckBox = new CheckBox("Hohe Qualität (1.7B, bessere Stimme, langsamer)");
        highQualityCheckBox.setSelected(true);

        Label textLabel = new Label("Zu sprechender Text:");
        Button btnUseSampleText = new Button("Gleichen Text wie Probe (Vergleich)");
        btnUseSampleText.setTooltip(new javafx.scene.control.Tooltip("Setzt den Text aus der Stimmsuche ein. So kannst du mit exakt gleichem Text vergleichen, ob „Text vorlesen“ dieselbe Stimme wie „Probe abspielen“ nutzt."));
        btnUseSampleText.setOnAction(e -> {
            String sample = getVoiceSearchSampleText();
            if (textInputArea != null) textInputArea.setText(sample != null ? sample : "");
        });
        HBox textRow = new HBox(8);
        textRow.getChildren().addAll(textLabel, btnUseSampleText);
        textRow.setAlignment(Pos.CENTER_LEFT);

        String lastText = ComfyUIClient.loadLastSpokenText();
        textInputArea = new TextArea(lastText != null && !lastText.isEmpty() ? lastText : FIXED_TEXT);
        textInputArea.setPromptText("Text eingeben…");
        textInputArea.setWrapText(true);
        textInputArea.setPrefRowCount(4);

        // Aussprache-Lexikon: Tabelle mit Scrollbereich, erweitern/ändern/speichern
        Label lexiconLabel = new Label("Aussprache-Lexikon (Wort → Ersetzung für TTS):");
        lexiconItems = FXCollections.observableArrayList();
        loadLexiconIntoTable();
        lexiconTable = new TableView<>(lexiconItems);
        lexiconTable.setEditable(true);
        lexiconTable.setPrefHeight(120);
        colWord = new TableColumn<>("Wort");
        colWord.setCellValueFactory(c -> c.getValue().wordProperty());
        colWord.setCellFactory(column -> createLexiconTableCell());
        colWord.setOnEditCommit(e -> commitLexiconCell(e.getTablePosition().getRow(), e.getNewValue(), true));
        colWord.setPrefWidth(140);
        colReplacement = new TableColumn<>("Ersetzung (Aussprache)");
        colReplacement.setCellValueFactory(c -> c.getValue().replacementProperty());
        colReplacement.setCellFactory(column -> createLexiconTableCell());
        colReplacement.setOnEditCommit(e -> commitLexiconCell(e.getTablePosition().getRow(), e.getNewValue(), false));
        colReplacement.setPrefWidth(200);
        lexiconTable.getColumns().add(colWord);
        lexiconTable.getColumns().add(colReplacement);
        ScrollPane lexiconScroll = new ScrollPane(lexiconTable);
        lexiconScroll.setFitToWidth(true);
        lexiconScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        lexiconScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        Button btnAddLexicon = new Button("+ Eintrag");
        btnAddLexicon.setOnAction(e -> {
            lexiconItems.add(new LexiconEntry("", ""));
            int idx = lexiconItems.size() - 1;
            lexiconTable.getSelectionModel().clearAndSelect(idx);
            lexiconTable.scrollTo(idx);
            Platform.runLater(() -> lexiconTable.edit(idx, colWord));
        });
        Button btnRemoveLexicon = new Button("− Eintrag entfernen");
        btnRemoveLexicon.setOnAction(e -> {
            LexiconEntry selected = lexiconTable.getSelectionModel().getSelectedItem();
            if (selected != null) lexiconItems.remove(selected);
        });
        Button btnSaveLexicon = new Button("Lexikon speichern");
        btnSaveLexicon.setOnAction(e -> saveLexiconToFile());
        HBox lexiconButtons = new HBox(8);
        lexiconButtons.getChildren().addAll(btnAddLexicon, btnRemoveLexicon, btnSaveLexicon);

        btnRun = new Button("TTS ausführen");
        btnRun.setOnAction(e -> runTTS());

        progress = new ProgressIndicator(-1);
        progress.setVisible(false);

        btnPlayOrOpen = new Button("Ergebnis abspielen / öffnen");
        btnPlayOrOpen.setDisable(true);
        btnPlayOrOpen.setOnAction(e -> playOrOpenResult());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setWrapText(true);

        speakContent.getChildren().addAll(
                voiceRow,
                textRow,
                textInputArea,
                highQualityCheckBox,
                lexiconLabel,
                lexiconScroll,
                lexiconButtons,
                statusRow,
                btnRun,
                progress,
                btnPlayOrOpen,
                new Label("Log:"),
                logArea
        );
        VBox.setVgrow(textInputArea, Priority.NEVER);
        VBox.setVgrow(lexiconScroll, Priority.NEVER);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // —— Bereich: Stimmsuche ——
        searchContent = buildVoiceSearchTab();

        // Umschaltung: zwei Buttons wie Tabs, Inhalt in StackPane
        ToggleGroup group = new ToggleGroup();
        ToggleButton btnShowSprechen = new ToggleButton("Text vorlesen");
        btnShowSprechen.setToggleGroup(group);
        btnShowSprechen.setSelected(true);
        btnShowSprechen.setUserData("speak");
        ToggleButton btnShowStimmsuche = new ToggleButton("Stimmsuche");
        btnShowStimmsuche.setToggleGroup(group);
        btnShowStimmsuche.setUserData("search");
        HBox switchBar = new HBox(6);
        switchBar.getChildren().addAll(btnShowSprechen, btnShowStimmsuche);
        switchBar.setPadding(new Insets(8, 15, 4, 15));
        switchBar.setStyle("-fx-border-color: transparent transparent -fx-base transparent; -fx-border-width: 0 0 1 0;");

        contentStack = new StackPane();
        contentStack.getChildren().addAll(searchContent, speakContent);
        speakContent.setVisible(true);
        speakContent.setManaged(true);
        searchContent.setVisible(false);
        searchContent.setManaged(false);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        group.selectedToggleProperty().addListener((o, prev, sel) -> {
            if (sel == null) return;
            boolean speak = "speak".equals(((ToggleButton) sel).getUserData());
            speakContent.setVisible(speak);
            speakContent.setManaged(speak);
            searchContent.setVisible(!speak);
            searchContent.setManaged(!speak);
        });
        switchToSprechen = () -> btnShowSprechen.setSelected(true);

        VBox mainRoot = new VBox();
        mainRoot.getChildren().addAll(switchBar, contentStack);

        Scene scene = new Scene(mainRoot);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        stage.setSceneWithTitleBar(scene);
        stage.setOnCloseRequest(e -> {
            if (textInputArea != null) {
                ComfyUIClient.saveLastSpokenText(textInputArea.getText());
            }
            if (voiceSearchSampleTextArea != null) {
                ComfyUIClient.saveVoiceSearchSampleText(voiceSearchSampleTextArea.getText());
            }
            if (lastRestartProcess != null && lastRestartProcess.isAlive()) {
                lastRestartProcess.destroyForcibly();
                lastRestartProcess = null;
            }
        });

        int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
        stage.setFullTheme(theme);
    }

    private void log(String line) {
        Platform.runLater(() -> {
            logArea.appendText(line + "\n");
        });
    }

    /** Tabellenzelle, die bei Fokusverlust den Edit committet, damit Einträge nicht verloren gehen. */
    private TableCell<LexiconEntry, String> createLexiconTableCell() {
        TableCell<LexiconEntry, String> cell = new TableCell<>() {
            private TextField textField;

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.focusedProperty().addListener((o, wasFocused, nowFocused) -> {
                        if (wasFocused && !nowFocused && isEditing()) {
                            String v = textField.getText();
                            commitEdit(v != null ? v : "");
                        }
                    });
                }
                textField.setText(getItem() != null ? getItem() : "");
                setGraphic(textField);
                setText(null);
                textField.requestFocus();
                textField.selectAll();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem() != null ? getItem() : "");
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (isEditing()) {
                        if (textField != null) textField.setText(item);
                        setText(null);
                        setGraphic(textField);
                    } else {
                        setText(item);
                        setGraphic(null);
                    }
                }
            }
        };
        return cell;
    }

    /** Commit von Tabellenedits direkt ins Listenelement (per Index), damit Einträge bei Fokusverlust erhalten bleiben. */
    private void commitLexiconCell(int rowIndex, String newValue, boolean isWord) {
        if (rowIndex < 0 || rowIndex >= lexiconItems.size()) return;
        LexiconEntry entry = lexiconItems.get(rowIndex);
        if (isWord) entry.setWord(newValue != null ? newValue.trim() : "");
        else entry.setReplacement(newValue != null ? newValue : "");
    }

    private void loadLexiconIntoTable() {
        lexiconItems.clear();
        Map<String, String> map = ComfyUIClient.getDefaultPronunciationLexicon();
        for (Map.Entry<String, String> e : map.entrySet()) {
            lexiconItems.add(new LexiconEntry(e.getKey(), e.getValue()));
        }
    }

    private Map<String, String> getLexiconFromTable() {
        Map<String, String> map = new LinkedHashMap<>();
        for (LexiconEntry entry : lexiconItems) {
            String w = entry.getWord() != null ? entry.getWord().trim() : "";
            if (!w.isEmpty()) {
                map.put(w, entry.getReplacement() != null ? entry.getReplacement() : "");
            }
        }
        return map;
    }

    private void saveLexiconToFile() {
        Path path = Paths.get(ComfyUIClient.PRONUNCIATION_LEXICON_PATH);
        try {
            Files.createDirectories(path.getParent());
            Map<String, String> map = getLexiconFromTable();
            String json = JsonUtil.toJsonPretty(map);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            statusLabel.setText("Lexikon gespeichert: " + path.toAbsolutePath());
            log("Lexikon gespeichert: " + path.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("Lexikon speichern fehlgeschlagen", e);
            statusLabel.setText("Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private void runTTS() {
        btnRun.setDisable(true);
        btnPlayOrOpen.setDisable(true);
        progress.setVisible(true);
        lastAudioPath = null;
        logArea.clear();
        String textToSpeak = textInputArea.getText();
        if (textToSpeak == null || textToSpeak.isBlank()) {
            statusLabel.setText("Bitte Text eingeben.");
            btnRun.setDisable(false);
            progress.setVisible(false);
            return;
        }
        log("Aussprache-Lexikon: " + ComfyUIClient.PRONUNCIATION_LEXICON_PATH);
        ComfyUIClient.SavedVoice selectedVoice = voiceComboBox.getSelectionModel().getSelectedItem();
        final boolean useSavedVoice = (selectedVoice != null);
        java.util.Map<String, String> lexicon = getLexiconFromTable();
        String textAfterPronunciation = lexicon.isEmpty() ? textToSpeak.trim() : ComfyUIClient.applyPronunciationLexicon(textToSpeak.trim(), lexicon);
        if (!textAfterPronunciation.equals(textToSpeak.trim())) {
            log("Nach Aussprache-Ersetzung (Lexikon): " + textAfterPronunciation);
        }
        final String textForTTS = textToSpeak.trim();
        if (useSavedVoice) {
            statusLabel.setText(selectedVoice.isHighQuality() ? "Starte ComfyUI/Qwen3-TTS (Hohe Qualität)…" : "Starte ComfyUI/Qwen3-TTS (Schnell)…");
            log("Text: " + textForTTS);
            log("Gespeicherte Stimme: \"" + selectedVoice.getName() + "\" | Seed=" + selectedVoice.getSeed() + ", Temp=" + selectedVoice.getTemperature() + ", top_p=" + selectedVoice.getTopP() + ", top_k=" + selectedVoice.getTopK() + ", rep_penalty=" + selectedVoice.getRepetitionPenalty() + ", " + (selectedVoice.isHighQuality() ? "1.7B VoiceDesign" : "0.6B CustomVoice"));
            if (selectedVoice.getTemperature() > ComfyUIClient.MAX_TEMPERATURE_FOR_VOICE_CONSISTENCY) {
                log("Für Hörbuch-Konsistenz: Temperatur auf max. " + ComfyUIClient.MAX_TEMPERATURE_FOR_VOICE_CONSISTENCY + " begrenzt (gleiche Stimme über alle Absätze).");
            }
            if (selectedVoice.getVoiceDescription() != null && !selectedVoice.getVoiceDescription().isBlank()) log("  Stimmbeschreibung: " + selectedVoice.getVoiceDescription());
        } else {
            final boolean highQuality = highQualityCheckBox.isSelected();
            statusLabel.setText(highQuality ? "Starte ComfyUI/Qwen3-TTS (Hohe Qualität)…" : "Starte ComfyUI/Qwen3-TTS (Schnell)…");
            log("Text: " + textForTTS);
            log("Qualität: " + (highQuality ? "Hohe Qualität (1.7B)" : "Schnell (0.6B)") + ", CustomVoice (Standard)");
        }
        log("Instruct: " + FIXED_INSTRUCT);

        final ComfyUIClient.SavedVoice voiceForTTS = selectedVoice;
        final boolean hqStandard = highQualityCheckBox.isSelected();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.currentThread().setPriority(Thread.NORM_PRIORITY - 1);
                log("TTS gestartet (ComfyUI wird aufgerufen)…");
                ComfyUIClient client = new ComfyUIClient();
                Map<String, Object> history;
                if (useSavedVoice && voiceForTTS != null) {
                    history = generateTTSWithSavedVoice(client, textForTTS, voiceForTTS, lexicon, true, prettyPrompt ->
                        Platform.runLater(() -> {
                            log("=== ComfyUI Prompt (menschenlesbar) ===");
                            log(prettyPrompt);
                            log("=== Ende Prompt ===");
                        }));
                } else {
                    java.util.function.Consumer<String> logger = prettyPrompt ->
                        Platform.runLater(() -> {
                            log("=== ComfyUI Prompt (menschenlesbar) ===");
                            log(prettyPrompt);
                            log("=== Ende Prompt ===");
                        });
                    history = client.generateQwen3TTS(textForTTS, FIXED_INSTRUCT, hqStandard, true, lexicon, logger, null, null, null, null, null, null);
                }
                log("Fertig. History-Keys: " + history.keySet());

                Map<String, Object> audioInfo = ComfyUIClient.extractFirstAudioFromHistory(history);
                if (audioInfo.isEmpty()) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Kein Audio in der History. Evtl. anderes Output-Format? Prüfe ComfyUI-Ausgabe.");
                        log("outputs: " + history.get("outputs"));
                    });
                    return;
                }

                Path target = Files.createTempFile("manuskript-tts-", ".mp3");
                client.downloadAudioToFile(history, target);
                lastAudioPath = target;
                Path path = target;
                Platform.runLater(() -> {
                    statusLabel.setText("Ergebnis: " + path.toAbsolutePath());
                    btnPlayOrOpen.setDisable(false);
                    log("Gespeichert: " + path.toAbsolutePath());
                });
            } catch (Exception e) {
                logger.error("TTS Test fehlgeschlagen", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                String logMsg = "Fehler: " + msg;
                Platform.runLater(() -> {
                    statusLabel.setText("Fehler: " + msg);
                    log(logMsg);
                });
            } finally {
                Path pathToPlay = lastAudioPath;
                Platform.runLater(() -> {
                    btnRun.setDisable(false);
                    progress.setVisible(false);
                    if (pathToPlay != null) btnPlayOrOpen.setDisable(false);
                });
            }
        }, COMFY_LOW_PRIORITY_EXECUTOR);
    }

    /** Startet das in comfyui.restart_script hinterlegte Skript (z. B. zum Neustarten von ComfyUI). */
    private void runComfyUIRestartScript() {
        String script = ResourceManager.getParameter("comfyui.restart_script", "");
        if (script == null || script.isBlank()) {
            statusLabel.setText("Kein Restart-Skript konfiguriert (Parameter: comfyui.restart_script).");
            return;
        }
        Path scriptPath = Paths.get(script.trim());
        if (!Files.isRegularFile(scriptPath)) {
            statusLabel.setText("Restart-Skript nicht gefunden: " + scriptPath.toAbsolutePath());
            return;
        }
        try {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
            ProcessBuilder pb;
            if (isWindows) {
                // Mit "start": Skript in eigenem Konsolenfenster starten – gleicher Kontext wie beim Start über cmd, Ausgabe sichtbar.
                java.io.File scriptDir = scriptPath.getParent() != null ? scriptPath.getParent().toFile() : null;
                String scriptAbs = scriptPath.toAbsolutePath().toString();
                List<String> cmd = scriptDir != null
                        ? List.of("cmd", "/c", "start", "\"ComfyUI-Restart\"", "/d", scriptDir.getAbsolutePath(), scriptAbs)
                        : List.of("cmd", "/c", "start", "\"ComfyUI-Restart\"", scriptAbs);
                pb = new ProcessBuilder(cmd);
            } else {
                pb = new ProcessBuilder(scriptPath.toAbsolutePath().toString());
                pb.directory(scriptPath.getParent() != null ? scriptPath.getParent().toFile() : null);
            }
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            if (lastRestartProcess != null && lastRestartProcess.isAlive()) {
                lastRestartProcess.destroyForcibly();
            }
            lastRestartProcess = pb.start();
            statusLabel.setText("ComfyUI-Restart-Skript gestartet: " + scriptPath.getFileName());
        } catch (IOException e) {
            logger.warn("ComfyUI-Restart-Skript konnte nicht gestartet werden", e);
            statusLabel.setText("Fehler beim Starten des Skripts: " + e.getMessage());
        }
    }

    private VBox buildVoiceSearchTab() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(15));
        root.setMinWidth(780);
        root.getStyleClass().add("voice-search-tab");

        Label titleLabel = new Label("Stimmen finden & speichern");
        titleLabel.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");

        Label sampleTextLabel = new Label("Suchtext (Probe für alle Vorschläge und gespeicherten Stimmen):");
        String savedSample = ComfyUIClient.loadVoiceSearchSampleText();
        voiceSearchSampleTextArea = new TextArea(savedSample != null && !savedSample.isEmpty() ? savedSample : VOICE_SEARCH_SAMPLE);
        voiceSearchSampleTextArea.getStyleClass().add("voice-search-suchtext");
        voiceSearchSampleTextArea.setWrapText(true);
        voiceSearchSampleTextArea.setPrefRowCount(6);
        voiceSearchSampleTextArea.setPromptText("Text eingeben, der bei „Vorschläge erzeugen“ und „Probe abspielen“ gesprochen wird.");
        voiceSearchSampleTextArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(voiceSearchSampleTextArea, Priority.SOMETIMES);

        // Grundstimme (CustomVoice) auswählen
        Label baseSpeakerLabel = new Label("Grundstimme (CustomVoice):");
        customSpeakerCombo = new ComboBox<>(FXCollections.observableArrayList(ComfyUIClient.getCustomVoiceSpeakers()));
        customSpeakerCombo.setPromptText("z. B. Ryan, Sohee, Vivian …");
        customSpeakerCombo.getSelectionModel().select(ComfyUIClient.DEFAULT_CUSTOM_SPEAKER);
        HBox speakerRow = new HBox(8, baseSpeakerLabel, customSpeakerCombo);
        speakerRow.setAlignment(Pos.CENTER_LEFT);

        GridPane paramsGrid = new GridPane();
        paramsGrid.setHgap(12);
        paramsGrid.setVgap(8);
        javafx.scene.layout.ColumnConstraints col0 = new javafx.scene.layout.ColumnConstraints();
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
        col2.setHgrow(Priority.SOMETIMES);
        paramsGrid.getColumnConstraints().addAll(col0, col1, col2);

        paramsGrid.add(new Label("Seed:"), 0, 0);
        voiceSeedField = new TextField(String.valueOf(ComfyUIClient.DEFAULT_SEED));
        voiceSeedField.setPromptText("z.B. " + ComfyUIClient.DEFAULT_SEED);
        voiceSeedField.setPrefWidth(180);
        Button btnRandomSeed = new Button("Zufall");
        btnRandomSeed.setOnAction(e -> voiceSeedField.setText(String.valueOf(new Random().nextLong(1L, Long.MAX_VALUE))));
        paramsGrid.add(voiceSeedField, 1, 0);
        paramsGrid.add(btnRandomSeed, 2, 0);

        paramsGrid.add(new Label("Name der Stimme:"), 0, 1);
        voiceNameField = new TextField();
        voiceNameField.setPromptText("z.B. Erzähler, Markus (wird beim Speichern verwendet)");
        voiceNameField.setPrefWidth(280);
        paramsGrid.add(voiceNameField, 1, 1);
        GridPane.setColumnSpan(voiceNameField, 2);

        paramsGrid.add(new Label("Stimmbeschreibung:"), 0, 2);
        voiceDescriptionCombo = new ComboBox<>(FXCollections.observableArrayList(ComfyUIClient.loadRecentVoiceDescriptions()));
        voiceDescriptionCombo.setEditable(true);
        voiceDescriptionCombo.setPromptText(ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
        voiceDescriptionCombo.setMaxWidth(Double.MAX_VALUE);
        if (voiceDescriptionCombo.getItems().isEmpty()) {
            voiceDescriptionCombo.getItems().add(ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
        }
        voiceDescriptionCombo.getSelectionModel().selectFirst();
        voiceDescriptionCombo.setTooltip(new javafx.scene.control.Tooltip(
                "Zuletzt verwendete Einträge werden gespeichert. Vorschlag für männlichen Hörbuch-Vorleser: „" + ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK + "“"));
        paramsGrid.add(voiceDescriptionCombo, 1, 2);
        GridPane.setColumnSpan(voiceDescriptionCombo, 2);
        GridPane.setHgrow(voiceDescriptionCombo, Priority.ALWAYS);
        voiceDescriptionCombo.setPrefWidth(400);

        voiceSearchHighQualityCheckBox = new CheckBox("Hohe Qualität (1.7B-Modell)");
        voiceSearchHighQualityCheckBox.setSelected(true);
        voiceSearchHighQualityCheckBox.setTooltip(new javafx.scene.control.Tooltip("1.7B = bessere Qualität, 0.6B = schneller. Wird mit der Stimme gespeichert."));
        paramsGrid.add(voiceSearchHighQualityCheckBox, 0, 3);
        GridPane.setColumnSpan(voiceSearchHighQualityCheckBox, 3);

        HBox tempTopRow = new HBox(20);
        tempTopRow.setAlignment(Pos.CENTER_LEFT);
        tempTopRow.setMaxWidth(Double.MAX_VALUE);
        voiceTempSlider = new Slider(0.2, 1.2, ComfyUIClient.DEFAULT_TEMPERATURE);
        voiceTempSlider.setShowTickMarks(true);
        voiceTempSlider.setShowTickLabels(true);
        voiceTempSlider.setMajorTickUnit(0.5);
        voiceTempSlider.setPrefWidth(220);
        voiceTempSlider.setMinWidth(180);
        HBox.setHgrow(voiceTempSlider, Priority.ALWAYS);
        voiceTempLabel = new Label(String.format("%.2f", ComfyUIClient.DEFAULT_TEMPERATURE));
        voiceTempSlider.valueProperty().addListener((o, a, v) -> voiceTempLabel.setText(String.format("%.2f", v.doubleValue())));
        tempTopRow.getChildren().addAll(new Label("Temperatur:"), voiceTempSlider, voiceTempLabel);

        voiceSearchTopPSlider = new Slider(0.01, 1.0, ComfyUIClient.DEFAULT_TOP_P);
        voiceSearchTopPSlider.setShowTickMarks(true);
        voiceSearchTopPSlider.setShowTickLabels(true);
        voiceSearchTopPSlider.setMajorTickUnit(0.25);
        voiceSearchTopPSlider.setPrefWidth(200);
        voiceSearchTopPSlider.setMinWidth(160);
        HBox.setHgrow(voiceSearchTopPSlider, Priority.ALWAYS);
        voiceSearchTopPSlider.setTooltip(new javafx.scene.control.Tooltip("Nukleus-Sampling (0–1)."));
        voiceSearchTopPLabel = new Label(String.format("%.2f", ComfyUIClient.DEFAULT_TOP_P));
        voiceSearchTopPSlider.valueProperty().addListener((o, a, v) -> voiceSearchTopPLabel.setText(String.format("%.2f", v.doubleValue())));
        tempTopRow.getChildren().addAll(new Label("top_p:"), voiceSearchTopPSlider, voiceSearchTopPLabel);

        voiceSearchTopKSlider = new Slider(1, 100, ComfyUIClient.DEFAULT_TOP_K);
        voiceSearchTopKSlider.setShowTickMarks(true);
        voiceSearchTopKSlider.setShowTickLabels(true);
        voiceSearchTopKSlider.setMajorTickUnit(50);
        voiceSearchTopKSlider.setBlockIncrement(5);
        voiceSearchTopKSlider.setPrefWidth(200);
        voiceSearchTopKSlider.setMinWidth(160);
        HBox.setHgrow(voiceSearchTopKSlider, Priority.ALWAYS);
        voiceSearchTopKSlider.setTooltip(new javafx.scene.control.Tooltip("Top-k."));
        voiceSearchTopKLabel = new Label(String.valueOf(ComfyUIClient.DEFAULT_TOP_K));
        voiceSearchTopKSlider.valueProperty().addListener((o, a, v) -> voiceSearchTopKLabel.setText(String.valueOf((int) Math.round(v.doubleValue()))));
        tempTopRow.getChildren().addAll(new Label("top_k:"), voiceSearchTopKSlider, voiceSearchTopKLabel);

        voiceSearchRepetitionPenaltySlider = new Slider(1.0, 2.0, ComfyUIClient.DEFAULT_REPETITION_PENALTY);
        voiceSearchRepetitionPenaltySlider.setShowTickMarks(true);
        voiceSearchRepetitionPenaltySlider.setShowTickLabels(true);
        voiceSearchRepetitionPenaltySlider.setMajorTickUnit(0.25);
        voiceSearchRepetitionPenaltySlider.setBlockIncrement(0.05);
        voiceSearchRepetitionPenaltySlider.setPrefWidth(200);
        voiceSearchRepetitionPenaltySlider.setMinWidth(160);
        voiceSearchRepetitionPenaltySlider.setTooltip(new javafx.scene.control.Tooltip("Repetition Penalty (1–2)."));
        voiceSearchRepetitionPenaltyLabel = new Label(String.format("%.2f", ComfyUIClient.DEFAULT_REPETITION_PENALTY));
        voiceSearchRepetitionPenaltySlider.valueProperty().addListener((o, a, v) -> voiceSearchRepetitionPenaltyLabel.setText(String.format("%.2f", v.doubleValue())));
        HBox repetitionRow = new HBox(20);
        repetitionRow.setAlignment(Pos.CENTER_LEFT);
        repetitionRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(voiceSearchRepetitionPenaltySlider, Priority.ALWAYS);
        repetitionRow.getChildren().addAll(new Label("Repetition Penalty:"), voiceSearchRepetitionPenaltySlider, voiceSearchRepetitionPenaltyLabel);

        paramsGrid.add(tempTopRow, 0, 4);
        GridPane.setColumnSpan(tempTopRow, 3);
        GridPane.setHgrow(tempTopRow, Priority.ALWAYS);
        paramsGrid.add(repetitionRow, 0, 5);
        GridPane.setColumnSpan(repetitionRow, 3);
        GridPane.setHgrow(repetitionRow, Priority.ALWAYS);

        Label hintLabel = new Label("Mehrere Vorschläge erzeugen: gleiche Parameter, unterschiedliche Seeds. Dann anhören, bewerten und eine Stimme übernehmen oder speichern. — Während der Generierung kann ComfyUI CPU/GPU stark belasten; bei System-Einfrieren im Task-Manager den ComfyUI-Prozess auf „Niedrigere Priorität“ setzen.");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(700);

        HBox generateButtons = new HBox(10);
        Button btn1 = new Button("1 Vorschlag erzeugen");
        btn1.setOnAction(e -> generateVoiceSuggestions(1));
        Button btn3 = new Button("3 Vorschläge erzeugen");
        btn3.setOnAction(e -> generateVoiceSuggestions(3));
        Button btn5 = new Button("5 Vorschläge erzeugen");
        btn5.setOnAction(e -> generateVoiceSuggestions(5));
        voiceSearchProgress = new ProgressIndicator(-1);
        voiceSearchProgress.setVisible(false);
        generateButtons.getChildren().addAll(btn1, btn3, btn5, voiceSearchProgress);
        generateButtons.setAlignment(Pos.CENTER_LEFT);

        Label suggestionsLabel = new Label("Vorschläge (anhören → Übernehmen oder Speichern unter Namen):");
        voiceSuggestionsBox = new HBox(14);
        voiceSuggestionsBox.setAlignment(Pos.CENTER_LEFT);
        voiceSuggestionsBox.setMinHeight(160);
        voiceSuggestionsBox.setFillHeight(false);
        ScrollPane voiceSuggestionsScroll = new ScrollPane(voiceSuggestionsBox);
        voiceSuggestionsScroll.setFitToHeight(true);
        voiceSuggestionsScroll.setFitToWidth(false);
        voiceSuggestionsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        voiceSuggestionsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        voiceSuggestionsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        Label savedLabel = new Label("Gespeicherte Stimmen:");
        savedVoicesList = new javafx.scene.control.ListView<>(savedVoicesItems);
        savedVoicesList.getStyleClass().add("voice-search-list");
        savedVoicesList.setPrefHeight(140);
        savedVoicesList.setCellFactory(lv -> new javafx.scene.control.ListCell<ComfyUIClient.SavedVoice>() {
            @Override
            protected void updateItem(ComfyUIClient.SavedVoice v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                String model = v.isHighQuality() ? "1.7B" : "0.6B";
                setText(v.getName() + " — " + model + ", Seed " + v.getSeed() + ", Temp " + String.format("%.2f", v.getTemperature()));
            }
        });
        savedVoicesList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
                if (v != null) {
                    applySavedVoiceToForm(v);
                }
            }
        });
        HBox savedButtons = new HBox(8);
        Button btnUseForTTS = new Button("Für TTS verwenden");
        btnUseForTTS.setOnAction(e -> {
            ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
            if (v != null) {
                applySavedVoiceToForm(v);
                int idx = savedVoicesItems.indexOf(v);
                if (idx >= 0) voiceComboBox.getSelectionModel().select(idx + 1);
                else voiceComboBox.getSelectionModel().select(v);
                if (switchToSprechen != null) switchToSprechen.run();
            }
        });
        Button btnPlaySaved = new Button("Probe abspielen");
        btnPlaySaved.setOnAction(e -> playSavedVoiceSample(savedVoicesList.getSelectionModel().getSelectedItem()));
        Button btnDeleteSaved = new Button("Löschen");
        btnDeleteSaved.setOnAction(e -> deleteSelectedSavedVoice());
        savedButtons.getChildren().addAll(btnUseForTTS, btnPlaySaved, btnDeleteSaved);

        root.getChildren().addAll(
                titleLabel,
                sampleTextLabel,
                voiceSearchSampleTextArea,
                speakerRow,
                paramsGrid,
                hintLabel,
                generateButtons,
                suggestionsLabel,
                voiceSuggestionsScroll,
                savedLabel,
                savedVoicesList,
                savedButtons
        );
        VBox.setVgrow(voiceSuggestionsScroll, Priority.SOMETIMES);
        VBox.setVgrow(savedVoicesList, Priority.ALWAYS);
        return root;
    }

    private String getVoiceSearchSampleText() {
        if (voiceSearchSampleTextArea == null) return VOICE_SEARCH_SAMPLE;
        String t = voiceSearchSampleTextArea.getText();
        return (t != null && !t.isBlank()) ? t.trim() : VOICE_SEARCH_SAMPLE;
    }

    /** Übernimmt die Werte einer gespeicherten Stimme in die Formularfelder der Stimmsuche (Seed, Temperatur, Beschreibung, Checkboxen, top_p/top_k, Name). */
    private void applySavedVoiceToForm(ComfyUIClient.SavedVoice v) {
        if (v == null) return;
        if (voiceSeedField != null) voiceSeedField.setText(String.valueOf(v.getSeed()));
        if (voiceTempSlider != null) voiceTempSlider.setValue(v.getTemperature());
        String desc = v.getVoiceDescription();
        if (voiceDescriptionCombo != null) {
            if (desc != null && !desc.isBlank()) {
                voiceDescriptionCombo.getEditor().setText(desc);
                if (!voiceDescriptionCombo.getItems().contains(desc)) {
                    voiceDescriptionCombo.getItems().add(0, desc);
                }
                voiceDescriptionCombo.getSelectionModel().select(desc);
            }
        }
        if (voiceSearchHighQualityCheckBox != null) voiceSearchHighQualityCheckBox.setSelected(v.isHighQuality());
        if (voiceSearchTopPSlider != null) voiceSearchTopPSlider.setValue(v.getTopP());
        if (voiceSearchTopKSlider != null) voiceSearchTopKSlider.setValue((double) Math.min(100, v.getTopK()));
        if (voiceSearchRepetitionPenaltySlider != null) voiceSearchRepetitionPenaltySlider.setValue(v.getRepetitionPenalty());
        if (voiceNameField != null) voiceNameField.setText(v.getName());
        if (customSpeakerCombo != null && v.getSpeakerId() != null && customSpeakerCombo.getItems().contains(v.getSpeakerId())) {
            customSpeakerCombo.getSelectionModel().select(v.getSpeakerId());
        }
    }

    private void refreshVoiceDescriptionCombo() {
        List<String> recent = ComfyUIClient.loadRecentVoiceDescriptions();
        String current = voiceDescriptionCombo.getEditor().getText();
        voiceDescriptionCombo.getItems().setAll(recent);
        if (current != null && !current.isBlank() && !recent.contains(current.trim())) {
            voiceDescriptionCombo.getItems().add(0, current.trim());
        }
        voiceDescriptionCombo.getSelectionModel().selectFirst();
    }

    private void generateVoiceSuggestions(int count) {
        long baseSeed;
        try {
            baseSeed = Long.parseLong(voiceSeedField.getText().trim());
        } catch (NumberFormatException e) {
            baseSeed = ComfyUIClient.DEFAULT_SEED;
        }
        double temp = voiceTempSlider.getValue();
        String desc = voiceDescriptionCombo.getEditor().getText();
        if (desc == null || desc.isBlank()) desc = voiceDescriptionCombo.getValue();
        if (desc == null) desc = "";
        final String descToUse = desc.trim();
        if (!descToUse.isEmpty()) {
            ComfyUIClient.addRecentVoiceDescription(descToUse);
            refreshVoiceDescriptionCombo();
        }
        final boolean highQuality = voiceSearchHighQualityCheckBox != null && voiceSearchHighQualityCheckBox.isSelected();
        // Für Preset-Stimmen (CustomVoice) immer konsistente Stimme nutzen
        final boolean consistentVoice = true;
        double topPVal = voiceSearchTopPSlider != null ? voiceSearchTopPSlider.getValue() : ComfyUIClient.DEFAULT_TOP_P;
        int topKVal = voiceSearchTopKSlider != null ? (int) Math.round(voiceSearchTopKSlider.getValue()) : ComfyUIClient.DEFAULT_TOP_K;
        double repPenVal = voiceSearchRepetitionPenaltySlider != null ? voiceSearchRepetitionPenaltySlider.getValue() : ComfyUIClient.DEFAULT_REPETITION_PENALTY;
        voiceSuggestionsBox.getChildren().clear();
        voiceSearchProgress.setVisible(true);
        final String sampleTextForGenerate = getVoiceSearchSampleText();
        Random r = new Random();
        List<Long> seeds = new ArrayList<>();
        seeds.add(baseSeed);
        for (int i = 1; i < count; i++) {
            seeds.add(r.nextLong(1L, Long.MAX_VALUE));
        }
        final String speakerId = (customSpeakerCombo != null && customSpeakerCombo.getValue() != null && !customSpeakerCombo.getValue().isBlank())
                ? customSpeakerCombo.getValue().trim()
                : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER;
        CompletableFuture.runAsync(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient();
                ComfyUIClient.setCurrentCustomSpeaker(speakerId);
                for (int i = 0; i < seeds.size(); i++) {
                    if (i > 0) {
                        try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                    long seed = seeds.get(i);
                    Map<String, Object> history = client.generateQwen3TTS(sampleTextForGenerate, FIXED_INSTRUCT, highQuality, consistentVoice, Map.of(), null, seed, temp, descToUse.isEmpty() ? null : descToUse, topPVal, topKVal, repPenVal);
                    Map<String, Object> audioInfo = ComfyUIClient.extractFirstAudioFromHistory(history);
                    if (audioInfo.isEmpty()) continue;
                    Path path = Files.createTempFile("manuskript-voice-", ".mp3");
                    client.downloadAudioToFile(history, path);
                    final long s = seed;
                    final int idx = i + 1;
                    Platform.runLater(() -> addVoiceSuggestionCard(idx, s, temp, descToUse, path, highQuality, topPVal, topKVal, repPenVal));
                }
            } catch (Exception e) {
                logger.error("Stimmsuche fehlgeschlagen", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Platform.runLater(() -> {
                    voiceSearchProgress.setVisible(false);
                    voiceSuggestionsBox.getChildren().add(new Label("Fehler: " + msg));
                    statusLabel.setText("Stimmsuche fehlgeschlagen: " + msg);
                    log("Stimmsuche Fehler: " + msg);
                });
                return;
            }
            Platform.runLater(() -> voiceSearchProgress.setVisible(false));
        }, COMFY_LOW_PRIORITY_EXECUTOR);
    }

    private void addVoiceSuggestionCard(int index, long seed, double temperature, String voiceDescription, Path audioPath, boolean highQuality, double topP, int topK, double repetitionPenalty) {
        VBox card = new VBox(8);
        card.getStyleClass().add("voice-search-suggestion-card");
        card.setStyle("-fx-padding: 12; -fx-background-radius: 6;");
        card.setMinWidth(300);
        card.setPrefWidth(320);
        card.setMaxWidth(380);
        card.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label titleLabel = new Label("Vorschlag " + index);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.05em;");
        titleLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label seedLabel = new Label("Seed " + seed + (highQuality ? " (1.7B)" : " (0.6B)") + ", p=" + String.format("%.2f", topP) + " k=" + topK + " rep=" + String.format("%.2f", repetitionPenalty));
        seedLabel.setWrapText(true);
        seedLabel.setMinWidth(260);
        seedLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox buttons = new VBox(6);
        buttons.setFillWidth(true);
        Button play = new Button("Abspielen");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setOnAction(e -> showAudioPlayerPopup(audioPath, stage));
        Button adopt = new Button("Übernehmen");
        adopt.setMaxWidth(Double.MAX_VALUE);
        adopt.setOnAction(e -> {
            voiceSeedField.setText(String.valueOf(seed));
            voiceTempSlider.setValue(temperature);
            String d = voiceDescription != null ? voiceDescription : "";
            voiceDescriptionCombo.getEditor().setText(d);
            if (!d.isEmpty() && !voiceDescriptionCombo.getItems().contains(d)) {
                voiceDescriptionCombo.getItems().add(0, d);
            }
            if (voiceSearchHighQualityCheckBox != null) voiceSearchHighQualityCheckBox.setSelected(highQuality);
            if (voiceSearchTopPSlider != null) voiceSearchTopPSlider.setValue(topP);
            if (voiceSearchTopKSlider != null) voiceSearchTopKSlider.setValue(topK);
            if (voiceSearchRepetitionPenaltySlider != null) voiceSearchRepetitionPenaltySlider.setValue(repetitionPenalty);
        });
        Button saveAs = new Button("Speichern unter…");
        saveAs.setMaxWidth(Double.MAX_VALUE);
        saveAs.setOnAction(e -> {
            String defaultName = voiceNameField.getText();
            if (defaultName != null) defaultName = defaultName.trim();
            if (defaultName == null || defaultName.isEmpty()) defaultName = "Neue Stimme";
            javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(defaultName);
            d.setTitle("Stimme speichern");
            d.setHeaderText("Name für diese Stimme eingeben:");
            d.showAndWait().ifPresent(name -> {
                if (name.isBlank()) return;
                String desc = voiceDescription != null ? voiceDescription : "";
                if (!desc.isEmpty()) ComfyUIClient.addRecentVoiceDescription(desc);
                refreshVoiceDescriptionCombo();
                String speakerId = (customSpeakerCombo != null && customSpeakerCombo.getValue() != null && !customSpeakerCombo.getValue().isBlank())
                        ? customSpeakerCombo.getValue().trim()
                        : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER;
                ComfyUIClient.SavedVoice v = new ComfyUIClient.SavedVoice(name.trim(), seed, temperature, desc, highQuality, true, topP, topK, repetitionPenalty, speakerId);
                savedVoicesItems.add(v);
                refreshVoiceComboBoxKeepSelection();
                voiceComboBox.getSelectionModel().select(v);
                try {
                    ComfyUIClient.saveSavedVoices(new ArrayList<>(savedVoicesItems));
                } catch (IOException ex) {
                    logger.warn("Stimmen speichern fehlgeschlagen", ex);
                }
            });
        });
        buttons.getChildren().addAll(play, adopt, saveAs);
        card.getChildren().addAll(titleLabel, seedLabel, buttons);
        voiceSuggestionsBox.getChildren().add(card);
    }

    /**
     * TTS mit gespeicherter Stimme.
     * @param useConsistencyTemperature true = Temperatur für Hörbuch-Konsistenz begrenzen (gleiche Stimme über alle Absätze)
     */
    private static Map<String, Object> generateTTSWithSavedVoice(ComfyUIClient client, String text, ComfyUIClient.SavedVoice voice, java.util.Map<String, String> lexicon, boolean useConsistencyTemperature, java.util.function.Consumer<String> promptLogger) throws IOException, InterruptedException {
        String desc = (voice.getVoiceDescription() == null || voice.getVoiceDescription().isEmpty()) ? null : voice.getVoiceDescription();
        double temp = useConsistencyTemperature
                ? Math.min(voice.getTemperature(), ComfyUIClient.MAX_TEMPERATURE_FOR_VOICE_CONSISTENCY)
                : voice.getTemperature();
        // Für gespeicherte Stimmen immer CustomVoice mit konsistenter Stimme nutzen
        final boolean consistentVoice = true;
        try {
            ComfyUIClient.setCurrentCustomSpeaker(voice.getSpeakerId());
            return client.generateQwen3TTS(text, FIXED_INSTRUCT, voice.isHighQuality(), consistentVoice, lexicon, promptLogger,
                    voice.getSeed(), temp, desc, voice.getTopP(), voice.getTopK(), voice.getRepetitionPenalty());
        } finally {
            ComfyUIClient.clearCurrentCustomSpeaker();
        }
    }

    private void playSavedVoiceSample(ComfyUIClient.SavedVoice v) {
        if (v == null) return;
        final String sampleTextForPlay = getVoiceSearchSampleText();
        voiceSearchProgress.setVisible(true);
        CompletableFuture.runAsync(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient();
                Map<String, Object> history = generateTTSWithSavedVoice(client, sampleTextForPlay, v, Map.of(), false, null);
                Path path = Files.createTempFile("manuskript-voice-", ".mp3");
                client.downloadAudioToFile(history, path);
                Path pathForPopup = path;
                Platform.runLater(() -> {
                    voiceSearchProgress.setVisible(false);
                    showAudioPlayerPopup(pathForPopup, stage);
                });
            } catch (Exception e) {
                logger.error("Probe abspielen fehlgeschlagen", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Platform.runLater(() -> {
                    voiceSearchProgress.setVisible(false);
                    statusLabel.setText("Probe fehlgeschlagen: " + msg);
                    log("Probe abspielen Fehler: " + msg);
                });
            }
        }, COMFY_LOW_PRIORITY_EXECUTOR);
    }

    /** Füllt die TTS-Stimmen-ComboBox neu und stellt die Auswahl per Name wieder her. */
    private void refreshVoiceComboBoxKeepSelection() {
        String selectedName = null;
        ComfyUIClient.SavedVoice sel = voiceComboBox.getSelectionModel().getSelectedItem();
        if (sel != null) selectedName = sel.getName();
        voiceComboBox.getItems().clear();
        voiceComboBox.getItems().add(null);
        voiceComboBox.getItems().addAll(savedVoicesItems);
        if (selectedName != null) {
            for (int i = 0; i < savedVoicesItems.size(); i++) {
                if (selectedName.equals(savedVoicesItems.get(i).getName())) {
                    voiceComboBox.getSelectionModel().select(i + 1);
                    break;
                }
            }
        }
    }

    private void deleteSelectedSavedVoice() {
        ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
        if (v == null) return;
        savedVoicesItems.remove(v);
        refreshVoiceComboBoxKeepSelection();
        try {
            ComfyUIClient.saveSavedVoices(new ArrayList<>(savedVoicesItems));
        } catch (IOException e) {
            logger.warn("Stimmen speichern fehlgeschlagen", e);
        }
    }

    private void playOrOpenResult() {
        if (lastAudioPath == null || !Files.exists(lastAudioPath)) {
            statusLabel.setText("Kein Ergebnis vorhanden.");
            return;
        }
        showAudioPlayerPopup(lastAudioPath, stage);
    }

    /**
     * Öffnet ein Popup-Fenster mit JavaFX MediaPlayer: Start, Stopp, Fortschrittsanzeige, Dauer.
     * Popup wird sofort angezeigt, Media wird danach geladen (damit bei Fehlern das Fenster trotzdem erscheint).
     */
    public static void showAudioPlayerPopup(Path audioPath, Window owner) {
        if (audioPath == null || !Files.exists(audioPath)) return;
        Platform.runLater(() -> {
            if (openAudioPlayerStage != null && openAudioPlayerStage.isShowing()) {
                openAudioPlayerStage.close();
            }
            Label durationLabel = new Label("Lade…");
            ProgressBar progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(360);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            Button playButton = new Button("▶ Start");
            Button stopButton = new Button("■ Stopp");
            playButton.setDisable(true);
            stopButton.setDisable(true);

            CustomStage popup = StageManager.createStage("Wiedergabe");
            openAudioPlayerStage = popup;
            if (owner != null) popup.initOwner(owner);
            popup.setMinWidth(380);
            popup.setMinHeight(140);
            popup.setWidth(420);
            popup.setHeight(160);

            HBox buttons = new HBox(10);
            buttons.getChildren().addAll(playButton, stopButton);
            buttons.setAlignment(Pos.CENTER_LEFT);
            VBox root = new VBox(12);
            root.setPadding(new Insets(15));
            root.getChildren().addAll(durationLabel, progressBar, buttons);
            VBox.setVgrow(progressBar, Priority.NEVER);
            final VBox rootRef = root;
            final Path pathForFallback = audioPath;

            Scene scene = new Scene(root);
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) scene.getStylesheets().add(cssPath);
            popup.setSceneWithTitleBar(scene);
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            popup.setFullTheme(theme);
            popup.show();

            final Label durLabel = durationLabel;
            final ProgressBar progBar = progressBar;
            final Button playBtn = playButton;
            final Button stopBtn = stopButton;
            Platform.runLater(() -> {
                MediaPlayer[] playerRef = new MediaPlayer[1];
                try {
                    String uri = audioPath.toUri().toString();
                    Media media = new Media(uri);
                    MediaPlayer player = new MediaPlayer(media);
                    player.setCycleCount(1);
                    playerRef[0] = player;

                    playBtn.setOnAction(e -> {
                        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                            player.pause();
                            playBtn.setText("▶ Start");
                        } else {
                            if (player.getStatus() == MediaPlayer.Status.STOPPED) {
                                player.seek(Duration.ZERO);
                            }
                            player.play();
                            playBtn.setText("⏸ Pause");
                            stopBtn.setDisable(false);
                        }
                    });
                    stopBtn.setOnAction(e -> {
                        player.stop();
                        playBtn.setText("▶ Start");
                        stopBtn.setDisable(true);
                    });

                    player.currentTimeProperty().addListener((o, oldVal, newVal) -> {
                        Duration total = player.getTotalDuration();
                        if (total != null && total.greaterThan(Duration.ZERO)) {
                            double progress = newVal.toMillis() / total.toMillis();
                            progBar.setProgress(progress);
                            durLabel.setText(formatDuration(newVal) + " / " + formatDuration(total));
                        }
                    });
                    player.setOnReady(() -> {
                        Duration total = player.getTotalDuration();
                        if (total != null && total.greaterThan(Duration.ZERO)) {
                            durLabel.setText("0:00 / " + formatDuration(total));
                        }
                        playBtn.setDisable(false);
                        stopBtn.setDisable(false);
                        player.play();
                        playBtn.setText("⏸ Pause");
                    });
                    player.setOnEndOfMedia(() -> {
                        Platform.runLater(() -> {
                            player.stop();
                            playBtn.setText("▶ Start");
                            stopBtn.setDisable(true);
                            progBar.setProgress(1.0);
                            Duration total = player.getTotalDuration();
                            durLabel.setText((total != null ? formatDuration(total) : "0:00") + " / " + (total != null ? formatDuration(total) : "0:00"));
                        });
                    });
                    player.setOnError(() -> {
                        Platform.runLater(() -> {
                            durLabel.setText("Fehler beim Abspielen (Format ggf. nicht unterstützt).");
                            addOpenWithSystemButton(rootRef, pathForFallback);
                        });
                    });

                    popup.setOnCloseRequest(e -> {
                        if (playerRef[0] != null) {
                            playerRef[0].stop();
                            playerRef[0].dispose();
                        }
                        if (openAudioPlayerStage == popup) {
                            openAudioPlayerStage = null;
                        }
                    });
                } catch (Exception e) {
                    LoggerFactory.getLogger(ComfyUITTSTestWindow.class).warn("Audio laden fehlgeschlagen", e);
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    durLabel.setText("Fehler: " + msg);
                    addOpenWithSystemButton(rootRef, pathForFallback);
                }
            });
        });
    }

    /** Fügt einen Button „Mit System-Player öffnen“ hinzu (Fallback bei unsupported Format, z. B. ComfyUI-WAV). */
    private static void addOpenWithSystemButton(VBox rootRef, Path audioPath) {
        if (rootRef.getChildren().stream().anyMatch(n -> n instanceof Button && ((Button) n).getText().contains("System-Player"))) return;
        Button openWithSystem = new Button("Mit System-Player öffnen");
        openWithSystem.setOnAction(ev -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN) && audioPath != null && Files.exists(audioPath)) {
                    Desktop.getDesktop().open(audioPath.toFile());
                }
            } catch (IOException ex) {
                LoggerFactory.getLogger(ComfyUITTSTestWindow.class).warn("System-Player öffnen fehlgeschlagen", ex);
            }
        });
        rootRef.getChildren().add(openWithSystem);
    }

    private static String formatDuration(Duration d) {
        if (d == null) return "0:00";
        int totalSec = (int) Math.floor(d.toSeconds());
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }
}
