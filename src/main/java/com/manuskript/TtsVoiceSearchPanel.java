package com.manuskript;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Stimmsuche: Vorschlaege erzeugen, anhoeren, als ComfyUI-Stimme speichern.
 * Wird im Kapitel-TTS-Editor (Tab) und optional im TTS-Test-Fenster genutzt.
 */
public final class TtsVoiceSearchPanel {

    private static final Logger logger = LoggerFactory.getLogger(TtsVoiceSearchPanel.class);

    private static final String VOICE_SEARCH_SAMPLE = "Hallo, dies ist eine kurze Probe meiner Stimme.";
    private static final ExecutorService COMFY_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "comfyui-voice-search");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return t;
    });

    public interface Host {
        javafx.stage.Window ownerWindow();

        void onVoiceSaved(ComfyUIClient.SavedVoice voice);

        void onUseForTts(ComfyUIClient.SavedVoice voice);

        void setStatus(String message);
    }

    private final Host host;
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
    private ComboBox<String> customSpeakerCombo;
    private HBox voiceSuggestionsBox;
    private ListView<ComfyUIClient.SavedVoice> savedVoicesList;
    private ObservableList<ComfyUIClient.SavedVoice> savedVoicesItems;
    private ProgressIndicator voiceSearchProgress;

    public TtsVoiceSearchPanel(Host host) {
        this.host = host;
    }

    public Node buildContent() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(15));
        root.setMinWidth(520);
        root.getStyleClass().add("voice-search-tab");

        Label titleLabel = new Label("Stimmen finden & speichern");
        titleLabel.setStyle("-fx-font-size: 1.1em; -fx-font-weight: bold;");

        Label sampleTextLabel = new Label("Suchtext (Probe für Vorschläge und gespeicherte Stimmen):");
        String savedSample = ComfyUIClient.loadVoiceSearchSampleText();
        voiceSearchSampleTextArea = new TextArea(savedSample != null && !savedSample.isEmpty() ? savedSample : VOICE_SEARCH_SAMPLE);
        voiceSearchSampleTextArea.getStyleClass().add("voice-search-suchtext");
        voiceSearchSampleTextArea.setWrapText(true);
        voiceSearchSampleTextArea.setPrefRowCount(4);
        voiceSearchSampleTextArea.setMaxWidth(Double.MAX_VALUE);

        Label baseSpeakerLabel = new Label("Grundstimme (CustomVoice):");
        customSpeakerCombo = new ComboBox<>(FXCollections.observableArrayList(ComfyUIClient.getCustomVoiceSpeakers()));
        customSpeakerCombo.setPromptText("z. B. Ryan, Sohee, Vivian …");
        customSpeakerCombo.getSelectionModel().select(ComfyUIClient.DEFAULT_CUSTOM_SPEAKER);
        HBox speakerRow = new HBox(8, baseSpeakerLabel, customSpeakerCombo);
        speakerRow.setAlignment(Pos.CENTER_LEFT);

        GridPane paramsGrid = buildParamsGrid();

        Label hintLabel = new Label("Mehrere Vorschläge: gleiche Parameter, unterschiedliche Seeds. Anhören, übernehmen oder unter Namen speichern.");
        hintLabel.setWrapText(true);

        HBox generateButtons = new HBox(10);
        Button btn1 = new Button("1 Vorschlag");
        btn1.setOnAction(e -> generateVoiceSuggestions(1));
        Button btn3 = new Button("3 Vorschläge");
        btn3.setOnAction(e -> generateVoiceSuggestions(3));
        Button btn5 = new Button("5 Vorschläge");
        btn5.setOnAction(e -> generateVoiceSuggestions(5));
        voiceSearchProgress = new ProgressIndicator(-1);
        voiceSearchProgress.setVisible(false);
        generateButtons.getChildren().addAll(btn1, btn3, btn5, voiceSearchProgress);

        Label suggestionsLabel = new Label("Vorschläge:");
        voiceSuggestionsBox = new HBox(14);
        voiceSuggestionsBox.setAlignment(Pos.CENTER_LEFT);
        voiceSuggestionsBox.setMinHeight(140);
        ScrollPane voiceSuggestionsScroll = new ScrollPane(voiceSuggestionsBox);
        voiceSuggestionsScroll.setFitToHeight(true);
        voiceSuggestionsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        voiceSuggestionsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        savedVoicesItems = FXCollections.observableArrayList(filterComfyNonEleven(ComfyUIClient.loadSavedVoices()));
        Label savedLabel = new Label("Gespeicherte ComfyUI-Stimmen:");
        savedVoicesList = new ListView<>(savedVoicesItems);
        savedVoicesList.getStyleClass().add("voice-search-list");
        savedVoicesList.setPrefHeight(120);
        savedVoicesList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ComfyUIClient.SavedVoice v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    return;
                }
                String model = v.isVoiceClone() ? "Clone" : (v.isHighQuality() ? "1.7B" : "0.6B");
                setText(v.getName() + " — " + model + ", Seed " + v.getSeed());
            }
        });
        savedVoicesList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
                if (v != null) applySavedVoiceToForm(v);
            }
        });

        Button btnUseForTts = new Button("Für TTS verwenden");
        btnUseForTts.setOnAction(e -> {
            ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
            if (v != null) {
                applySavedVoiceToForm(v);
                host.onUseForTts(v);
            }
        });
        Button btnPlaySaved = new Button("Probe abspielen");
        btnPlaySaved.setOnAction(e -> playSavedVoiceSample(savedVoicesList.getSelectionModel().getSelectedItem()));
        Button btnDeleteSaved = new Button("Löschen");
        btnDeleteSaved.setOnAction(e -> deleteSelectedSavedVoice());
        HBox savedButtons = new HBox(8, btnUseForTts, btnPlaySaved, btnDeleteSaved);

        root.getChildren().addAll(
                titleLabel, sampleTextLabel, voiceSearchSampleTextArea, speakerRow, paramsGrid,
                hintLabel, generateButtons, suggestionsLabel, voiceSuggestionsScroll,
                savedLabel, savedVoicesList, savedButtons);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    public void persistSampleText() {
        if (voiceSearchSampleTextArea != null) {
            ComfyUIClient.saveVoiceSearchSampleText(voiceSearchSampleTextArea.getText());
        }
    }

    public void reloadSavedVoicesList() {
        if (savedVoicesItems != null) {
            savedVoicesItems.setAll(filterComfyNonEleven(ComfyUIClient.loadSavedVoices()));
        }
    }

    /** Parameter aus dem TTS-Tab in die Stimmsuche übernehmen. */
    public void applyParamsFromTtsTab(long seed, double temp, double topP, int topK, double repPen,
                                      boolean highQuality, String voiceDescription, String speakerId, String voiceName) {
        if (voiceSeedField != null) voiceSeedField.setText(String.valueOf(seed));
        if (voiceTempSlider != null) voiceTempSlider.setValue(temp);
        if (voiceSearchTopPSlider != null) voiceSearchTopPSlider.setValue(topP);
        if (voiceSearchTopKSlider != null) voiceSearchTopKSlider.setValue(topK);
        if (voiceSearchRepetitionPenaltySlider != null) voiceSearchRepetitionPenaltySlider.setValue(repPen);
        if (voiceSearchHighQualityCheckBox != null) voiceSearchHighQualityCheckBox.setSelected(highQuality);
        if (voiceDescriptionCombo != null && voiceDescription != null) {
            voiceDescriptionCombo.getEditor().setText(voiceDescription);
        }
        if (customSpeakerCombo != null && speakerId != null && customSpeakerCombo.getItems().contains(speakerId)) {
            customSpeakerCombo.getSelectionModel().select(speakerId);
        }
        if (voiceNameField != null && voiceName != null) voiceNameField.setText(voiceName);
    }

    private GridPane buildParamsGrid() {
        GridPane paramsGrid = new GridPane();
        paramsGrid.setHgap(12);
        paramsGrid.setVgap(8);

        paramsGrid.add(new Label("Seed:"), 0, 0);
        voiceSeedField = new TextField(String.valueOf(ComfyUIClient.DEFAULT_SEED));
        Button btnRandomSeed = new Button("Zufall");
        btnRandomSeed.setOnAction(e -> voiceSeedField.setText(String.valueOf(new Random().nextLong(1L, Long.MAX_VALUE))));
        paramsGrid.add(voiceSeedField, 1, 0);
        paramsGrid.add(btnRandomSeed, 2, 0);

        paramsGrid.add(new Label("Name der Stimme:"), 0, 1);
        voiceNameField = new TextField();
        voiceNameField.setPromptText("z. B. Erzähler, Sohee – ruhig");
        paramsGrid.add(voiceNameField, 1, 1, 1, 1);

        paramsGrid.add(new Label("Stimmbeschreibung:"), 0, 2);
        voiceDescriptionCombo = new ComboBox<>(FXCollections.observableArrayList(ComfyUIClient.loadRecentVoiceDescriptions()));
        voiceDescriptionCombo.setEditable(true);
        voiceDescriptionCombo.setPromptText(ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
        if (voiceDescriptionCombo.getItems().isEmpty()) {
            voiceDescriptionCombo.getItems().add(ComfyUIClient.DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
        }
        voiceDescriptionCombo.getSelectionModel().selectFirst();
        paramsGrid.add(voiceDescriptionCombo, 1, 2, 2, 1);

        voiceSearchHighQualityCheckBox = new CheckBox("Hohe Qualität (1.7B)");
        voiceSearchHighQualityCheckBox.setSelected(true);
        paramsGrid.add(voiceSearchHighQualityCheckBox, 0, 3, 3, 1);

        voiceTempSlider = new Slider(0.2, 1.2, ComfyUIClient.DEFAULT_TEMPERATURE);
        voiceTempLabel = new Label(String.format("%.2f", ComfyUIClient.DEFAULT_TEMPERATURE));
        voiceTempSlider.valueProperty().addListener((o, a, v) -> voiceTempLabel.setText(String.format("%.2f", v.doubleValue())));
        HBox tempRow = new HBox(8, new Label("Temperatur:"), voiceTempSlider, voiceTempLabel);
        tempRow.setAlignment(Pos.CENTER_LEFT);
        paramsGrid.add(tempRow, 0, 4, 3, 1);

        voiceSearchTopPSlider = new Slider(0.01, 1.0, ComfyUIClient.DEFAULT_TOP_P);
        voiceSearchTopPLabel = new Label(String.format("%.2f", ComfyUIClient.DEFAULT_TOP_P));
        voiceSearchTopPSlider.valueProperty().addListener((o, a, v) -> voiceSearchTopPLabel.setText(String.format("%.2f", v.doubleValue())));
        voiceSearchTopKSlider = new Slider(1, 100, ComfyUIClient.DEFAULT_TOP_K);
        voiceSearchTopKLabel = new Label(String.valueOf(ComfyUIClient.DEFAULT_TOP_K));
        voiceSearchTopKSlider.valueProperty().addListener((o, a, v) -> voiceSearchTopKLabel.setText(String.valueOf((int) Math.round(v.doubleValue()))));
        voiceSearchRepetitionPenaltySlider = new Slider(1.0, 2.0, ComfyUIClient.DEFAULT_REPETITION_PENALTY);
        voiceSearchRepetitionPenaltyLabel = new Label(String.format("%.2f", ComfyUIClient.DEFAULT_REPETITION_PENALTY));
        voiceSearchRepetitionPenaltySlider.valueProperty().addListener((o, a, v) -> voiceSearchRepetitionPenaltyLabel.setText(String.format("%.2f", v.doubleValue())));

        paramsGrid.add(new HBox(8, new Label("top_p:"), voiceSearchTopPSlider, voiceSearchTopPLabel), 0, 5, 3, 1);
        paramsGrid.add(new HBox(8, new Label("top_k:"), voiceSearchTopKSlider, voiceSearchTopKLabel), 0, 6, 3, 1);
        paramsGrid.add(new HBox(8, new Label("Rep. Penalty:"), voiceSearchRepetitionPenaltySlider, voiceSearchRepetitionPenaltyLabel), 0, 7, 3, 1);

        return paramsGrid;
    }

    private static List<ComfyUIClient.SavedVoice> filterComfyNonEleven(List<ComfyUIClient.SavedVoice> all) {
        List<ComfyUIClient.SavedVoice> out = new ArrayList<>();
        if (all == null) return out;
        for (ComfyUIClient.SavedVoice v : all) {
            if (v == null) continue;
            if ("elevenlabs".equalsIgnoreCase(v.getProvider())) continue;
            out.add(v);
        }
        return out;
    }

    private void applySavedVoiceToForm(ComfyUIClient.SavedVoice v) {
        if (v == null) return;
        if (voiceSeedField != null) voiceSeedField.setText(String.valueOf(v.getSeed()));
        if (voiceTempSlider != null) voiceTempSlider.setValue(v.getTemperature());
        String desc = v.getVoiceDescription();
        if (voiceDescriptionCombo != null && desc != null && !desc.isBlank()) {
            voiceDescriptionCombo.getEditor().setText(desc);
        }
        if (voiceSearchHighQualityCheckBox != null) voiceSearchHighQualityCheckBox.setSelected(v.isHighQuality());
        if (voiceSearchTopPSlider != null) voiceSearchTopPSlider.setValue(v.getTopP());
        if (voiceSearchTopKSlider != null) voiceSearchTopKSlider.setValue(v.getTopK());
        if (voiceSearchRepetitionPenaltySlider != null) voiceSearchRepetitionPenaltySlider.setValue(v.getRepetitionPenalty());
        if (voiceNameField != null) voiceNameField.setText(v.getName());
        if (customSpeakerCombo != null && v.getSpeakerId() != null && customSpeakerCombo.getItems().contains(v.getSpeakerId())) {
            customSpeakerCombo.getSelectionModel().select(v.getSpeakerId());
        }
    }

    private String getVoiceSearchSampleText() {
        if (voiceSearchSampleTextArea == null) return VOICE_SEARCH_SAMPLE;
        String t = voiceSearchSampleTextArea.getText();
        return (t != null && !t.isBlank()) ? t.trim() : VOICE_SEARCH_SAMPLE;
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
        final String descToUse = desc != null ? desc.trim() : "";
        if (!descToUse.isEmpty()) {
            ComfyUIClient.addRecentVoiceDescription(descToUse);
        }
        final boolean highQuality = voiceSearchHighQualityCheckBox != null && voiceSearchHighQualityCheckBox.isSelected();
        double topPVal = voiceSearchTopPSlider != null ? voiceSearchTopPSlider.getValue() : ComfyUIClient.DEFAULT_TOP_P;
        int topKVal = voiceSearchTopKSlider != null ? (int) Math.round(voiceSearchTopKSlider.getValue()) : ComfyUIClient.DEFAULT_TOP_K;
        double repPenVal = voiceSearchRepetitionPenaltySlider != null ? voiceSearchRepetitionPenaltySlider.getValue() : ComfyUIClient.DEFAULT_REPETITION_PENALTY;
        voiceSuggestionsBox.getChildren().clear();
        voiceSearchProgress.setVisible(true);
        host.setStatus("Stimmsuche: " + count + " Vorschlag/Vorschläge werden erzeugt …");
        final String sampleText = getVoiceSearchSampleText();
        Random r = new Random();
        List<Long> seeds = new ArrayList<>();
        seeds.add(baseSeed);
        for (int i = 1; i < count; i++) {
            seeds.add(r.nextLong(1L, Long.MAX_VALUE));
        }
        final String speakerId = getSelectedSpeakerId();
        CompletableFuture.runAsync(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient();
                ComfyUIClient.setCurrentCustomSpeaker(speakerId);
                for (int i = 0; i < seeds.size(); i++) {
                    if (i > 0) Thread.sleep(5000);
                    long seed = seeds.get(i);
                    Map<String, Object> history = client.generateQwen3TTS(
                            sampleText, ComfyUIClient.DEFAULT_INSTRUCT_DEUTSCH, highQuality, true,
                            Map.of(), null, seed, temp, descToUse.isEmpty() ? null : descToUse,
                            topPVal, topKVal, repPenVal);
                    if (ComfyUIClient.extractFirstAudioFromHistory(history).isEmpty()) continue;
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
                    host.setStatus("Stimmsuche fehlgeschlagen: " + msg);
                });
                return;
            }
            Platform.runLater(() -> {
                voiceSearchProgress.setVisible(false);
                host.setStatus("Stimmsuche fertig.");
            });
        }, COMFY_EXECUTOR);
    }

    private String getSelectedSpeakerId() {
        if (customSpeakerCombo != null && customSpeakerCombo.getValue() != null && !customSpeakerCombo.getValue().isBlank()) {
            return customSpeakerCombo.getValue().trim();
        }
        return ComfyUIClient.DEFAULT_CUSTOM_SPEAKER;
    }

    private void addVoiceSuggestionCard(int index, long seed, double temperature, String voiceDescription,
                                        Path audioPath, boolean highQuality, double topP, int topK, double repetitionPenalty) {
        VBox card = new VBox(8);
        card.getStyleClass().add("voice-search-suggestion-card");
        card.setStyle("-fx-padding: 12; -fx-background-radius: 6;");
        card.setMinWidth(260);
        card.setPrefWidth(280);
        Label titleLabel = new Label("Vorschlag " + index);
        titleLabel.setStyle("-fx-font-weight: bold;");
        Label seedLabel = new Label("Seed " + seed + (highQuality ? " (1.7B)" : " (0.6B)"));
        seedLabel.setWrapText(true);
        VBox buttons = new VBox(6);
        Button play = new Button("Abspielen");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setOnAction(e -> ComfyUITTSTestWindow.showAudioPlayerPopup(audioPath, host.ownerWindow()));
        Button adopt = new Button("Übernehmen");
        adopt.setMaxWidth(Double.MAX_VALUE);
        adopt.setOnAction(e -> {
            voiceSeedField.setText(String.valueOf(seed));
            voiceTempSlider.setValue(temperature);
            if (voiceDescription != null) voiceDescriptionCombo.getEditor().setText(voiceDescription);
            if (voiceSearchHighQualityCheckBox != null) voiceSearchHighQualityCheckBox.setSelected(highQuality);
            if (voiceSearchTopPSlider != null) voiceSearchTopPSlider.setValue(topP);
            if (voiceSearchTopKSlider != null) voiceSearchTopKSlider.setValue(topK);
            if (voiceSearchRepetitionPenaltySlider != null) voiceSearchRepetitionPenaltySlider.setValue(repetitionPenalty);
        });
        Button saveAs = new Button("Speichern unter…");
        saveAs.setMaxWidth(Double.MAX_VALUE);
        saveAs.setOnAction(e -> saveVoiceUnderName(seed, temperature, voiceDescription, highQuality, topP, topK, repetitionPenalty));
        buttons.getChildren().addAll(play, adopt, saveAs);
        card.getChildren().addAll(titleLabel, seedLabel, buttons);
        voiceSuggestionsBox.getChildren().add(card);
    }

    private void saveVoiceUnderName(long seed, double temperature, String voiceDescription,
                                    boolean highQuality, double topP, int topK, double repetitionPenalty) {
        String defaultName = voiceNameField.getText();
        if (defaultName != null) defaultName = defaultName.trim();
        if (defaultName == null || defaultName.isEmpty()) defaultName = "Neue Stimme";
        TextInputDialog d = new TextInputDialog(defaultName);
        d.setTitle("Stimme speichern");
        d.setHeaderText("Name für diese ComfyUI-Stimme:");
        d.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            String desc = voiceDescription != null ? voiceDescription : "";
            if (!desc.isEmpty()) ComfyUIClient.addRecentVoiceDescription(desc);
            ComfyUIClient.SavedVoice v = new ComfyUIClient.SavedVoice(
                    name.trim(), seed, temperature, desc, highQuality, true,
                    topP, topK, repetitionPenalty, getSelectedSpeakerId());
            persistVoice(v);
        });
    }

    private void persistVoice(ComfyUIClient.SavedVoice v) {
        try {
            List<ComfyUIClient.SavedVoice> all = new ArrayList<>(ComfyUIClient.loadSavedVoices());
            all.removeIf(existing -> existing != null && v.getName().equals(existing.getName()));
            all.add(v);
            ComfyUIClient.saveSavedVoices(all);
            reloadSavedVoicesList();
            host.onVoiceSaved(v);
            host.setStatus("Stimme \"" + v.getName() + "\" gespeichert.");
        } catch (IOException ex) {
            logger.warn("Stimme speichern fehlgeschlagen", ex);
            host.setStatus("Speichern fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void playSavedVoiceSample(ComfyUIClient.SavedVoice v) {
        if (v == null) return;
        if (v.isVoiceClone()) {
            host.setStatus("Voice-Clone-Stimme: Probe im Tab „Voice Clone“ oder TTS-Erstellen.");
            return;
        }
        final String sampleText = getVoiceSearchSampleText();
        voiceSearchProgress.setVisible(true);
        CompletableFuture.runAsync(() -> {
            try {
                Path path = Files.createTempFile("manuskript-voice-", ".mp3");
                TtsBackend.generateTtsToFile(sampleText, v, Map.of(), path, false, null);
                Platform.runLater(() -> {
                    voiceSearchProgress.setVisible(false);
                    ComfyUITTSTestWindow.showAudioPlayerPopup(path, host.ownerWindow());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    voiceSearchProgress.setVisible(false);
                    host.setStatus("Probe fehlgeschlagen: " + e.getMessage());
                });
            }
        }, COMFY_EXECUTOR);
    }

    private void deleteSelectedSavedVoice() {
        ComfyUIClient.SavedVoice v = savedVoicesList.getSelectionModel().getSelectedItem();
        if (v == null) return;
        try {
            List<ComfyUIClient.SavedVoice> all = new ArrayList<>(ComfyUIClient.loadSavedVoices());
            all.removeIf(existing -> existing != null && v.getName().equals(existing.getName()));
            ComfyUIClient.saveSavedVoices(all);
            reloadSavedVoicesList();
            host.setStatus("Stimme \"" + v.getName() + "\" gelöscht.");
        } catch (IOException e) {
            host.setStatus("Löschen fehlgeschlagen: " + e.getMessage());
        }
    }

    /** Aktuelle TTS-Tab-Parameter als neue Stimme speichern (ComfyUI CustomVoice). */
    public static ComfyUIClient.SavedVoice buildVoiceFromTtsParams(String name, long seed, double temp, double topP, int topK,
                                                                    double repPen, boolean highQuality, String voiceDescription,
                                                                    String speakerId) {
        return new ComfyUIClient.SavedVoice(
                name.trim(), seed, temp, voiceDescription != null ? voiceDescription : "",
                highQuality, true, topP, topK, repPen,
                speakerId != null && !speakerId.isBlank() ? speakerId : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER);
    }

    public static void saveVoiceToConfig(ComfyUIClient.SavedVoice v) throws IOException {
        List<ComfyUIClient.SavedVoice> all = new ArrayList<>(ComfyUIClient.loadSavedVoices());
        all.removeIf(existing -> existing != null && v.getName().equals(existing.getName()));
        all.add(v);
        ComfyUIClient.saveSavedVoices(all);
    }
}
