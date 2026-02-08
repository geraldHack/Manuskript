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

/**
 * Machbarkeitsstudie: Kleines Fenster zum Testen der ComfyUI/Qwen3-TTS-API.
 * Fester Text und Parameter wie im Screenshot; Button zum Abspielen des Ergebnisses.
 */
public class ComfyUITTSTestWindow {

    private static final Logger logger = LoggerFactory.getLogger(ComfyUITTSTestWindow.class);

    private static final String FIXED_TEXT = "Hallo ich bin cursor AI und ich spreche zum erstem Mal mit dir.";
    private static final String FIXED_INSTRUCT = ComfyUIClient.DEFAULT_INSTRUCT_DEUTSCH;
    /** Kurzer Text für Stimmsuche (mehrere Vorschläge schnell abspielbar). */
    private static final String VOICE_SEARCH_SAMPLE = "Hallo, dies ist eine kurze Probe meiner Stimme.";

    private CustomStage stage;
    private final Window owner;
    private Label statusLabel;
    private Button btnRun;
    private Button btnPlayOrOpen;
    private ProgressIndicator progress;
    private TextArea textInputArea;
    private CheckBox highQualityCheckBox;
    private CheckBox consistentVoiceCheckBox;
    private TableView<LexiconEntry> lexiconTable;
    private TableColumn<LexiconEntry, String> colWord;
    private TableColumn<LexiconEntry, String> colReplacement;
    private ObservableList<LexiconEntry> lexiconItems;
    private TextArea logArea;
    private Path lastAudioPath;

    // Stimmsuche-Tab
    private TextField voiceSeedField;
    private TextField voiceNameField;
    private Slider voiceTempSlider;
    private Label voiceTempLabel;
    private ComboBox<String> voiceDescriptionCombo;
    private VBox voiceSuggestionsBox;
    private javafx.scene.control.ListView<ComfyUIClient.SavedVoice> savedVoicesList;
    private ObservableList<ComfyUIClient.SavedVoice> savedVoicesItems;
    private ProgressIndicator voiceSearchProgress;

    // TTS-Tab: gewählte Stimme für Sprechen
    private ComboBox<ComfyUIClient.SavedVoice> voiceComboBox;
    private VBox speakContent;
    private VBox searchContent;
    private StackPane contentStack;
    /** Wird von „Für TTS verwenden“ im Stimmsuche-Bereich aufgerufen, um auf „Sprechen“ zu wechseln. */
    private Runnable switchToSprechen;

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

        statusLabel = new Label("Bereit. ComfyUI-URL: http://127.0.0.1:8188");
        statusLabel.setWrapText(true);

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
        HBox voiceRow = new HBox(8);
        voiceRow.getChildren().addAll(new Label("Stimme:"), voiceComboBox);
        voiceRow.setAlignment(Pos.CENTER_LEFT);
        voiceRow.setDisable(false);

        Label textLabel = new Label("Zu sprechender Text:");
        String lastText = ComfyUIClient.loadLastSpokenText();
        textInputArea = new TextArea(lastText != null && !lastText.isEmpty() ? lastText : FIXED_TEXT);
        textInputArea.setPromptText("Text eingeben…");
        textInputArea.setWrapText(true);
        textInputArea.setPrefRowCount(4);

        highQualityCheckBox = new CheckBox("Hohe Qualität (1.7B, bessere Stimme, langsamer)");
        highQualityCheckBox.setSelected(true);

        consistentVoiceCheckBox = new CheckBox("Konsistente Stimme (immer gleicher Sprecher, kein VoiceDesign)");
        consistentVoiceCheckBox.setSelected(false);
        consistentVoiceCheckBox.setTooltip(new javafx.scene.control.Tooltip(
                "An: CustomVoice mit festem Sprecher (gleiche Stimme bei jedem Lauf).\nAus: VoiceDesign (bei Hohe Qualität) – kann pro Lauf leicht variieren."));

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
                textLabel,
                textInputArea,
                highQualityCheckBox,
                consistentVoiceCheckBox,
                lexiconLabel,
                lexiconScroll,
                lexiconButtons,
                statusLabel,
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
        ToggleButton btnShowSprechen = new ToggleButton("Sprechen");
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
        log("Aussprache-Lexikon: " + ComfyUIClient.PRONUNCIATION_LEXICON_PATH);
        String textToSpeak = textInputArea.getText();
        if (textToSpeak == null || textToSpeak.isBlank()) {
            statusLabel.setText("Bitte Text eingeben.");
            btnRun.setDisable(false);
            progress.setVisible(false);
            return;
        }
        ComfyUIClient.SavedVoice selectedVoice = voiceComboBox.getSelectionModel().getSelectedItem();
        boolean highQuality = selectedVoice != null ? selectedVoice.isHighQuality() : highQualityCheckBox.isSelected();
        boolean consistentVoice = selectedVoice != null ? selectedVoice.isConsistentVoice() : consistentVoiceCheckBox.isSelected();
        Long seed = selectedVoice != null ? selectedVoice.getSeed() : null;
        Double temperature = selectedVoice != null ? selectedVoice.getTemperature() : null;
        String voiceDescription = selectedVoice != null && selectedVoice.getVoiceDescription() != null ? selectedVoice.getVoiceDescription() : null;
        if (selectedVoice == null) {
            highQuality = highQualityCheckBox.isSelected();
            consistentVoice = consistentVoiceCheckBox.isSelected();
        }
        java.util.Map<String, String> lexicon = getLexiconFromTable();
        String textAfterPronunciation = lexicon.isEmpty() ? textToSpeak.trim() : ComfyUIClient.applyPronunciationLexicon(textToSpeak.trim(), lexicon);
        if (!textAfterPronunciation.equals(textToSpeak.trim())) {
            log("Nach Aussprache-Ersetzung (Lexikon): " + textAfterPronunciation);
        }
        statusLabel.setText(highQuality ? "Starte ComfyUI/Qwen3-TTS (Hohe Qualität)…" : "Starte ComfyUI/Qwen3-TTS (Schnell)…");
        log("Text: " + textToSpeak.trim());
        log("Qualität: " + (highQuality ? "Hohe Qualität (1.7B)" : "Schnell (0.6B)") + ", konsistente Stimme: " + consistentVoice + (seed != null ? ", Seed: " + seed : ""));
        log("Instruct: " + FIXED_INSTRUCT);

        final boolean hq = highQuality;
        final boolean cv = consistentVoice;
        CompletableFuture.runAsync(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient("http://127.0.0.1:8188");
                Map<String, Object> history = client.generateQwen3TTS(textToSpeak.trim(), FIXED_INSTRUCT, hq, cv, lexicon, prettyPrompt ->
                    Platform.runLater(() -> {
                        log("=== ComfyUI Prompt (menschenlesbar) ===");
                        log(prettyPrompt);
                        log("=== Ende Prompt ===");
                    })
                , seed, temperature, voiceDescription);
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
                String msg = e.getMessage();
                Platform.runLater(() -> {
                    statusLabel.setText("Fehler: " + (msg != null ? msg : e.getClass().getSimpleName()));
                    log("Fehler: " + e.getMessage());
                });
            } finally {
                Path pathToPlay = lastAudioPath;
                Platform.runLater(() -> {
                    btnRun.setDisable(false);
                    progress.setVisible(false);
                    if (pathToPlay != null) btnPlayOrOpen.setDisable(false);
                });
            }
        });
    }

    private VBox buildVoiceSearchTab() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(15));

        Label titleLabel = new Label("Stimmen finden & speichern");
        titleLabel.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");

        GridPane paramsGrid = new GridPane();
        paramsGrid.setHgap(12);
        paramsGrid.setVgap(8);

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

        paramsGrid.add(new Label("Temperatur:"), 0, 2);
        voiceTempSlider = new Slider(0.2, 1.2, ComfyUIClient.DEFAULT_TEMPERATURE);
        voiceTempSlider.setShowTickMarks(true);
        voiceTempSlider.setShowTickLabels(true);
        voiceTempSlider.setMajorTickUnit(0.5);
        voiceTempSlider.setPrefWidth(200);
        voiceTempLabel = new Label(String.format("%.2f", ComfyUIClient.DEFAULT_TEMPERATURE));
        voiceTempSlider.valueProperty().addListener((o, a, v) -> voiceTempLabel.setText(String.format("%.2f", v.doubleValue())));
        paramsGrid.add(voiceTempSlider, 1, 2);
        paramsGrid.add(voiceTempLabel, 2, 2);

        paramsGrid.add(new Label("Stimmbeschreibung:"), 0, 3);
        voiceDescriptionCombo = new ComboBox<>(FXCollections.observableArrayList(ComfyUIClient.loadRecentVoiceDescriptions()));
        voiceDescriptionCombo.setEditable(true);
        voiceDescriptionCombo.setPromptText(ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
        voiceDescriptionCombo.setPrefWidth(420);
        if (voiceDescriptionCombo.getItems().isEmpty()) {
            voiceDescriptionCombo.getItems().add(ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
        }
        voiceDescriptionCombo.getSelectionModel().selectFirst();
        voiceDescriptionCombo.setTooltip(new javafx.scene.control.Tooltip(
                "Zuletzt verwendete Einträge werden gespeichert. Vorschlag für männlichen Hörbuch-Vorleser: „" + ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK + "“"));
        paramsGrid.add(voiceDescriptionCombo, 1, 3);
        GridPane.setColumnSpan(voiceDescriptionCombo, 2);

        Label hintLabel = new Label("Mehrere Vorschläge erzeugen: gleiche Parameter, unterschiedliche Seeds. Dann anhören, bewerten und eine Stimme übernehmen oder speichern.");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(600);

        HBox generateButtons = new HBox(10);
        Button btn3 = new Button("3 Vorschläge erzeugen");
        btn3.setOnAction(e -> generateVoiceSuggestions(3));
        Button btn5 = new Button("5 Vorschläge erzeugen");
        btn5.setOnAction(e -> generateVoiceSuggestions(5));
        voiceSearchProgress = new ProgressIndicator(-1);
        voiceSearchProgress.setVisible(false);
        generateButtons.getChildren().addAll(btn3, btn5, voiceSearchProgress);
        generateButtons.setAlignment(Pos.CENTER_LEFT);

        Label suggestionsLabel = new Label("Vorschläge (anhören → Übernehmen oder Speichern unter Namen):");
        voiceSuggestionsBox = new VBox(8);
        voiceSuggestionsBox.setMinHeight(80);

        Label savedLabel = new Label("Gespeicherte Stimmen:");
        savedVoicesList = new javafx.scene.control.ListView<>(savedVoicesItems);
        savedVoicesList.setPrefHeight(140);
        savedVoicesList.setCellFactory(lv -> new javafx.scene.control.ListCell<ComfyUIClient.SavedVoice>() {
            @Override
            protected void updateItem(ComfyUIClient.SavedVoice v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v.getName() + " — Seed " + v.getSeed() + ", Temp " + String.format("%.2f", v.getTemperature()));
            }
        });
        HBox savedButtons = new HBox(8);
        Button btnUseForTTS = new Button("Für TTS verwenden");
        btnUseForTTS.setOnAction(e -> {
            ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
            if (v != null) {
                voiceComboBox.getSelectionModel().select(v);
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
                paramsGrid,
                hintLabel,
                generateButtons,
                suggestionsLabel,
                voiceSuggestionsBox,
                savedLabel,
                savedVoicesList,
                savedButtons
        );
        VBox.setVgrow(voiceSuggestionsBox, Priority.SOMETIMES);
        VBox.setVgrow(savedVoicesList, Priority.ALWAYS);
        return root;
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
        voiceSuggestionsBox.getChildren().clear();
        voiceSearchProgress.setVisible(true);
        Random r = new Random();
        List<Long> seeds = new ArrayList<>();
        seeds.add(baseSeed);
        for (int i = 1; i < count; i++) {
            seeds.add(r.nextLong(1L, Long.MAX_VALUE));
        }
        CompletableFuture.runAsync(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient("http://127.0.0.1:8188");
                for (int i = 0; i < seeds.size(); i++) {
                    long seed = seeds.get(i);
                    Map<String, Object> history = client.generateQwen3TTS(VOICE_SEARCH_SAMPLE, FIXED_INSTRUCT, true, false, Map.of(), null, seed, temp, descToUse.isEmpty() ? null : descToUse);
                    Map<String, Object> audioInfo = ComfyUIClient.extractFirstAudioFromHistory(history);
                    if (audioInfo.isEmpty()) continue;
                    Path path = Files.createTempFile("manuskript-voice-", ".mp3");
                    client.downloadAudioToFile(history, path);
                    final int idx = i;
                    final long s = seed;
                    Platform.runLater(() -> addVoiceSuggestionCard(s, temp, descToUse, path));
                }
            } catch (Exception e) {
                logger.error("Stimmsuche fehlgeschlagen", e);
                Platform.runLater(() -> {
                    voiceSearchProgress.setVisible(false);
                    voiceSuggestionsBox.getChildren().add(new Label("Fehler: " + e.getMessage()));
                });
                return;
            }
            Platform.runLater(() -> voiceSearchProgress.setVisible(false));
        });
    }

    private void addVoiceSuggestionCard(long seed, double temperature, String voiceDescription, Path audioPath) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-padding: 8; -fx-background-color: -fx-control-inner-background; -fx-background-radius: 4;");
        Label seedLabel = new Label("Seed " + seed);
        seedLabel.setMinWidth(140);
        Button play = new Button("Abspielen");
        play.setOnAction(e -> showAudioPlayerPopup(audioPath, stage));
        Button adopt = new Button("Übernehmen");
        adopt.setOnAction(e -> {
            voiceSeedField.setText(String.valueOf(seed));
            voiceTempSlider.setValue(temperature);
            String d = voiceDescription != null ? voiceDescription : "";
            voiceDescriptionCombo.getEditor().setText(d);
            if (!d.isEmpty() && !voiceDescriptionCombo.getItems().contains(d)) {
                voiceDescriptionCombo.getItems().add(0, d);
            }
        });
        Button saveAs = new Button("Speichern unter…");
        saveAs.setOnAction(e -> {
            String defaultName = voiceNameField.getText();
            if (defaultName != null) defaultName = defaultName.trim();
            if (defaultName.isEmpty()) defaultName = "Neue Stimme";
            javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(defaultName);
            d.setTitle("Stimme speichern");
            d.setHeaderText("Name für diese Stimme eingeben:");
            d.showAndWait().ifPresent(name -> {
                if (name.isBlank()) return;
                String desc = voiceDescription != null ? voiceDescription : "";
                if (!desc.isEmpty()) ComfyUIClient.addRecentVoiceDescription(desc);
                refreshVoiceDescriptionCombo();
                ComfyUIClient.SavedVoice v = new ComfyUIClient.SavedVoice(name.trim(), seed, temperature, desc, true, false);
                savedVoicesItems.add(v);
                voiceComboBox.getItems().clear();
                voiceComboBox.getItems().add(null);
                voiceComboBox.getItems().addAll(savedVoicesItems);
                try {
                    ComfyUIClient.saveSavedVoices(new ArrayList<>(savedVoicesItems));
                } catch (IOException ex) {
                    logger.warn("Stimmen speichern fehlgeschlagen", ex);
                }
            });
        });
        card.getChildren().addAll(seedLabel, play, adopt, saveAs);
        voiceSuggestionsBox.getChildren().add(card);
    }

    private void playSavedVoiceSample(ComfyUIClient.SavedVoice v) {
        if (v == null) return;
        voiceSearchProgress.setVisible(true);
        CompletableFuture.runAsync(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient("http://127.0.0.1:8188");
                Map<String, Object> history = client.generateQwen3TTS(VOICE_SEARCH_SAMPLE, FIXED_INSTRUCT, v.isHighQuality(), v.isConsistentVoice(), Map.of(), null, v.getSeed(), v.getTemperature(), v.getVoiceDescription().isEmpty() ? null : v.getVoiceDescription());
                Path path = Files.createTempFile("manuskript-voice-", ".mp3");
                client.downloadAudioToFile(history, path);
                Path pathForPopup = path;
                Platform.runLater(() -> {
                    voiceSearchProgress.setVisible(false);
                    showAudioPlayerPopup(pathForPopup, stage);
                });
            } catch (Exception e) {
                logger.error("Probe abspielen fehlgeschlagen", e);
                Platform.runLater(() -> voiceSearchProgress.setVisible(false));
            }
        });
    }

    private void deleteSelectedSavedVoice() {
        ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
        if (v == null) return;
        savedVoicesItems.remove(v);
        voiceComboBox.getItems().clear();
        voiceComboBox.getItems().add(null);
        voiceComboBox.getItems().addAll(savedVoicesItems);
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
            Label durationLabel = new Label("Lade…");
            ProgressBar progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(360);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            Button playButton = new Button("▶ Start");
            Button stopButton = new Button("■ Stopp");
            playButton.setDisable(true);
            stopButton.setDisable(true);

            CustomStage popup = StageManager.createStage("Wiedergabe");
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
                    playerRef[0] = player;

                    playBtn.setOnAction(e -> {
                        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                            player.pause();
                            playBtn.setText("▶ Start");
                        } else {
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
                    });
                    player.setOnEndOfMedia(() -> {
                        Platform.runLater(() -> {
                            playBtn.setText("▶ Start");
                            stopBtn.setDisable(true);
                            progBar.setProgress(0);
                            Duration total = player.getTotalDuration();
                            durLabel.setText("0:00 / " + (total != null ? formatDuration(total) : "0:00"));
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
