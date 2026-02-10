package com.manuskript;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import javafx.util.StringConverter;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fenster zur Kapitelbearbeitung mit Sprachsynthese: links TTS-Steuerung, rechts Text mit
 * orangener Markierung (Bearbeitungsblock) und grüner Markierung (gespeicherte Segmente).
 * Linksklick auf gespeicherten Bereich lädt ihn links zum Überarbeiten; Rechtsklick löscht.
 * Nur CustomStage und CustomAlert/DialogFactory; jedes UI-Element wird themegestyled.
 */
public class ChapterTtsEditorWindow {

    private static final Logger logger = LoggerFactory.getLogger(ChapterTtsEditorWindow.class);

    /** [Hintergrund, Textfarbe] pro Theme-Index (0=Weiß, 1=Schwarz, 2=Pastell, 3=Blau, 4=Grün, 5=Lila). */
    private static final String[][] THEMES = {
        {"#ffffff", "#000000"},
        {"#1a1a1a", "#ffffff"},
        {"#f3e5f5", "#000000"},
        {"#1e3a8a", "#ffffff"},
        {"#064e3b", "#ffffff"},
        {"#581c87", "#ffffff"}
    };

    private static final ExecutorService TTS_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chapter-tts-worker");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return t;
    });

    /** Ein gespeichertes Segment: Textbereich + Audiodatei + optional Stimmenname. */
    public static class TtsSegment {
        public int start;
        public int end;
        public String audioPath;
        public String voiceName;
        /** Beim Speichern mitgeschriebene TTS-Parameter (für Laden beim Klick auf Segment). */
        public double temperature = 0.35;
        public double topP = ComfyUIClient.DEFAULT_TOP_P;
        public int topK = ComfyUIClient.DEFAULT_TOP_K;
        public double repetitionPenalty = ComfyUIClient.DEFAULT_REPETITION_PENALTY;
        public boolean highQuality = true;

        public TtsSegment() {}

        public TtsSegment(int start, int end, String audioPath, String voiceName) {
            this.start = start;
            this.end = end;
            this.audioPath = audioPath != null ? audioPath : "";
            this.voiceName = voiceName != null ? voiceName : "";
        }

        public TtsSegment(int start, int end, String audioPath, String voiceName,
                          double temperature, double topP, int topK, double repetitionPenalty, boolean highQuality) {
            this.start = start;
            this.end = end;
            this.audioPath = audioPath != null ? audioPath : "";
            this.voiceName = voiceName != null ? voiceName : "";
            this.temperature = temperature > 0 ? temperature : 0.35;
            this.topP = topP > 0 ? topP : ComfyUIClient.DEFAULT_TOP_P;
            this.topK = topK > 0 ? topK : ComfyUIClient.DEFAULT_TOP_K;
            this.repetitionPenalty = repetitionPenalty >= 1.0 && repetitionPenalty <= 2.0 ? repetitionPenalty : ComfyUIClient.DEFAULT_REPETITION_PENALTY;
            this.highQuality = highQuality;
        }
    }

    private final DocxFile chapterFile;
    private final File mdFile;
    private final File dataDir;
    private final String chapterName;
    private final Path segmentsPath;
    private final Path audioDirPath;
    private final int themeIndex;
    private final Window owner;

    private CustomStage stage;
    private CodeArea codeArea;
    private VirtualizedScrollPane<CodeArea> scrollPane;
    private final ObservableList<TtsSegment> segments = FXCollections.observableArrayList();
    private Path lastGeneratedAudioPath;

    private ComboBox<ComfyUIClient.SavedVoice> voiceCombo;
    private Slider temperatureSlider;
    private Label temperatureLabel;
    private Slider topPSlider;
    private Label topPLabel;
    private Slider topKSlider;
    private Label topKLabel;
    private Slider repetitionPenaltySlider;
    private Label repetitionPenaltyLabel;
    private CheckBox highQualityCheck;
    private Button btnPlay;
    private Button btnPause;
    private Button btnStop;
    private ProgressBar playerProgress;
    private MediaPlayer embeddedPlayer;
    /** Pfad, der gerade geladen werden soll; verhindert, dass veraltete asynchrone Loads den Player überschreiben. */
    private Path pendingAudioPath;
    private Button btnErstellen;
    private Button btnSpeichern;
    private Button btnAlleAbspielen;
    private Button btnGesamtAudiodatei;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;
    private int playingSegmentIndex = -1;
    /** Verhindert Doppelstart und Doppelklick bei „Alle abspielen“. */
    private boolean isPlayingAllSequence = false;
    /** Während „Alle abspielen“: Segmente nach Position im Text (start) sortiert, damit in Textreihenfolge abgespielt wird. */
    private List<TtsSegment> segmentsPlayOrder;
    /** Timer für „Alle abspielen“: wechselt nach Ablauf der Stücklänge genau einmal zum nächsten Segment (vermeidet Doppelauslösung von setOnEndOfMedia). */
    private PauseTransition playAllAdvanceTransition;
    /** Signatur der letzten erfolgreichen TTS-Anfrage (Text + Parameter); bei Übereinstimmung wird keine erneute API-Anfrage gestellt. */
    private String lastGeneratedSignature;
    /** Laufende Nummer des letzten „Erstellen“-Klicks; nur das Ergebnis dieser Anfrage darf lastGeneratedAudioPath setzen (vermeidet falsches Speichern bei mehreren nacheinander gestarteten Erzeugen). */
    private int ttsRequestId;
    /** Verhindert Doppelauslösung von „Erstellen“ (Doppelklick / zweifacher Aufruf). */
    private volatile boolean isTtsGenerationRunning = false;
    /** Pro Signatur (Text+Parameter) die zugehörige Temp-Audiodatei von „Erstellen“ – beim Speichern wird daraus nach block_XXX.mp3 kopiert. Nur Cache; gespeicherte Segmente pro Kapitel sind unbegrenzt. */
    private final Map<String, Path> generatedAudioBySignature = new LinkedHashMap<>();

    private ChapterTtsEditorWindow(DocxFile chapterFile, String content, File mdFile, File dataDir, Window owner, int themeIndex) {
        this.chapterFile = chapterFile;
        this.mdFile = mdFile;
        this.dataDir = dataDir;
        this.owner = owner;
        this.themeIndex = themeIndex;
        String fn = chapterFile.getFileName();
        if (fn != null && fn.toLowerCase().endsWith(".docx")) {
            fn = fn.substring(0, fn.length() - 5);
        }
        this.chapterName = fn != null ? fn : "kapitel";
        this.segmentsPath = dataDir != null ? Paths.get(dataDir.getPath(), chapterName + "-tts-segments.json") : null;
        this.audioDirPath = dataDir != null ? Paths.get(dataDir.getPath(), chapterName + "-tts") : null;
    }

    /** Öffnet den Sprachsynthese-Editor für das gewählte Kapitel. Zeigt die getrennt gespeicherte TTS-Datei, falls vorhanden; sonst den Originalinhalt. */
    public static void open(DocxFile chapterFile, String content, File mdFile, File dataDir, Window owner, int themeIndex) {
        ChapterTtsEditorWindow w = new ChapterTtsEditorWindow(chapterFile, content, mdFile, dataDir, owner, themeIndex);
        String contentToShow = content;
        if (dataDir != null) {
            Path separatePath = Paths.get(dataDir.getPath(), w.chapterName + "-tts-content.md");
            if (Files.isRegularFile(separatePath)) {
                try {
                    contentToShow = Files.readString(separatePath, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.warn("TTS-Editor-Text konnte nicht geladen werden: {}", separatePath, e);
                }
            }
        }
        w.stage = StageManager.createStage("Sprachsynthese: " + chapterFile.getFileName());
        if (owner != null) w.stage.initOwner(owner);
        w.stage.setMinWidth(1000);
        w.stage.setMinHeight(700);
        w.stage.setWidth(1200);
        w.stage.setHeight(800);
        Scene scene = new Scene(w.buildRoot(contentToShow));
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        w.stage.setSceneWithTitleBar(scene);
        w.stage.setFullTheme(themeIndex);
        w.applyThemeToAll(scene.getRoot(), themeIndex);
        w.loadSegments();
        w.refreshHighlight();
        w.stage.setOnCloseRequest(ev -> {
            w.saveSegments();
            w.saveEditorContentToSeparateFile();
        });
        w.stage.show();
    }

    private Parent buildRoot(String content) {
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.32);
        split.getStyleClass().add("tts-editor-split");

        Node left = buildLeftPanel();
        Node right = buildRightPanel(content);
        split.getItems().addAll(left, right);

        applyThemeToNode(split, themeIndex);
        return split;
    }

    private Node buildLeftPanel() {
        VBox left = new VBox(12);
        left.setPadding(new Insets(12));
        left.setMinWidth(320);
        left.setPrefWidth(360);
        left.setMaxWidth(400);

        Label voiceLabel = new Label("Stimme");
        voiceCombo = new ComboBox<>();
        voiceCombo.setPromptText("Stimme wählen");
        List<ComfyUIClient.SavedVoice> voices = ComfyUIClient.loadSavedVoices();
        voiceCombo.getItems().setAll(voices);
        voiceCombo.setConverter(new StringConverter<ComfyUIClient.SavedVoice>() {
            @Override
            public String toString(ComfyUIClient.SavedVoice v) {
                return v != null ? v.getName() : "";
            }
            @Override
            public ComfyUIClient.SavedVoice fromString(String s) { return null; }
        });
        if (!voices.isEmpty()) voiceCombo.getSelectionModel().selectFirst();

        voiceCombo.getSelectionModel().selectedItemProperty().addListener((o, oldVal, newVal) -> applyVoiceToParams(newVal));

        Label tempLabel = new Label("Temperatur");
        temperatureSlider = new Slider(0.1, 1.5, 0.35);
        temperatureSlider.setShowTickLabels(true);
        temperatureSlider.setBlockIncrement(0.05);
        temperatureLabel = new Label(String.format("%.2f", temperatureSlider.getValue()));
        temperatureSlider.valueProperty().addListener((o, a, b) -> temperatureLabel.setText(String.format("%.2f", b.doubleValue())));

        Label topPLabelCaption = new Label("top_p");
        topPSlider = new Slider(0.01, 1.0, ComfyUIClient.DEFAULT_TOP_P);
        topPSlider.setBlockIncrement(0.05);
        topPLabel = new Label(String.format("%.2f", topPSlider.getValue()));
        topPSlider.valueProperty().addListener((o, a, b) -> topPLabel.setText(String.format("%.2f", b.doubleValue())));

        Label topKLabelCaption = new Label("top_k");
        topKSlider = new Slider(1, 100, ComfyUIClient.DEFAULT_TOP_K);
        topKSlider.setBlockIncrement(5);
        topKLabel = new Label(String.valueOf((int) Math.round(topKSlider.getValue())));
        topKSlider.valueProperty().addListener((o, a, b) -> topKLabel.setText(String.valueOf((int) Math.round(b.doubleValue()))));

        Label repPenLabelCaption = new Label("Repetition Penalty");
        repetitionPenaltySlider = new Slider(1.0, 2.0, ComfyUIClient.DEFAULT_REPETITION_PENALTY);
        repetitionPenaltySlider.setBlockIncrement(0.05);
        repetitionPenaltyLabel = new Label(String.format("%.2f", repetitionPenaltySlider.getValue()));
        repetitionPenaltySlider.valueProperty().addListener((o, a, b) -> repetitionPenaltyLabel.setText(String.format("%.2f", b.doubleValue())));

        highQualityCheck = new CheckBox("Hohe Qualität (1.7B)");
        highQualityCheck.setSelected(true);

        Label playerLabel = new Label("Vorschau");
        HBox playerButtons = new HBox(8);
        btnPlay = new Button("▶ Play");
        btnPause = new Button("⏸ Pause");
        btnStop = new Button("■ Stop");
        btnPlay.setDisable(true);
        btnPause.setDisable(true);
        btnStop.setDisable(true);
        playerButtons.getChildren().addAll(btnPlay, btnPause, btnStop);
        playerProgress = new ProgressBar(0);
        playerProgress.setPrefWidth(Double.MAX_VALUE);

        VBox playerBox = new VBox(6);
        playerBox.getChildren().addAll(playerLabel, playerButtons, playerProgress);

        btnErstellen = new Button("Erstellen");
        btnSpeichern = new Button("Speichern");
        btnAlleAbspielen = new Button("Alle abspielen");
        btnGesamtAudiodatei = new Button("Gesamt-Audiodatei erstellen");
        progressIndicator = new ProgressIndicator(-1);
        progressIndicator.setVisible(false);
        statusLabel = new Label(" ");
        statusLabel.setWrapText(true);

        left.getChildren().addAll(
            voiceLabel, voiceCombo,
            tempLabel, temperatureSlider, temperatureLabel,
            topPLabelCaption, topPSlider, topPLabel,
            topKLabelCaption, topKSlider, topKLabel,
            repPenLabelCaption, repetitionPenaltySlider, repetitionPenaltyLabel,
            highQualityCheck,
            playerBox,
            btnErstellen, btnSpeichern,
            new Separator(),
            btnAlleAbspielen, btnGesamtAudiodatei,
            progressIndicator, statusLabel
        );
        VBox.setVgrow(statusLabel, Priority.ALWAYS);

        bindLeftPanelActions(left);
        applyVoiceToParams(voiceCombo.getSelectionModel().getSelectedItem());
        applyThemeToNode(left, themeIndex);
        for (Node c : left.getChildren()) {
            applyThemeToNode(c, themeIndex);
            if (c instanceof VBox) for (Node cc : ((VBox) c).getChildren()) applyThemeToNode(cc, themeIndex);
            if (c instanceof HBox) for (Node cc : ((HBox) c).getChildren()) applyThemeToNode(cc, themeIndex);
        }
        applyThemeToNode(voiceCombo, themeIndex);
        applyThemeToNode(temperatureSlider, themeIndex);
        applyThemeToNode(temperatureLabel, themeIndex);
        applyThemeToNode(topPLabelCaption, themeIndex);
        applyThemeToNode(topPSlider, themeIndex);
        applyThemeToNode(topPLabel, themeIndex);
        applyThemeToNode(topKLabelCaption, themeIndex);
        applyThemeToNode(topKSlider, themeIndex);
        applyThemeToNode(topKLabel, themeIndex);
        applyThemeToNode(repPenLabelCaption, themeIndex);
        applyThemeToNode(repetitionPenaltySlider, themeIndex);
        applyThemeToNode(repetitionPenaltyLabel, themeIndex);
        applyThemeToNode(highQualityCheck, themeIndex);
        applyThemeToNode(btnPlay, themeIndex);
        applyThemeToNode(btnPause, themeIndex);
        applyThemeToNode(btnStop, themeIndex);
        applyThemeToNode(btnErstellen, themeIndex);
        applyThemeToNode(btnSpeichern, themeIndex);
        applyThemeToNode(btnAlleAbspielen, themeIndex);
        applyThemeToNode(btnGesamtAudiodatei, themeIndex);
        applyThemeToNode(progressIndicator, themeIndex);
        applyThemeToNode(statusLabel, themeIndex);
        applyThemeToNode(playerProgress, themeIndex);
        return left;
    }

    private void bindLeftPanelActions(VBox left) {
        btnPlay.setOnAction(e -> {
            if (embeddedPlayer != null) {
                if (embeddedPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    embeddedPlayer.pause();
                } else {
                    embeddedPlayer.seek(Duration.ZERO);
                    embeddedPlayer.play();
                }
            }
        });
        btnPause.setOnAction(e -> {
            if (embeddedPlayer != null) embeddedPlayer.pause();
        });
        btnStop.setOnAction(e -> {
            if (embeddedPlayer != null) embeddedPlayer.stop();
            if (isPlayingAllSequence) {
                // „Alle abspielen“ abbrechen
                isPlayingAllSequence = false;
                playingSegmentIndex = -1;
                segmentsPlayOrder = null;
                if (playAllAdvanceTransition != null) {
                    playAllAdvanceTransition.stop();
                    playAllAdvanceTransition = null;
                }
                if (btnAlleAbspielen != null) btnAlleAbspielen.setDisable(false);
                refreshHighlight();
                setStatus("Wiedergabe abgebrochen.");
            }
        });

        btnErstellen.setOnAction(e -> createTtsForSelection());
        btnSpeichern.setOnAction(e -> saveCurrentAsSegment());
        btnAlleAbspielen.setOnAction(e -> playAllSegments());
        btnGesamtAudiodatei.setOnAction(e -> createFullAudioFile());
    }

    /** Übernimmt die Parameter der gewählten Stimme in die Slider und die Checkbox (beim Wechsel der Stimme bzw. beim Öffnen). */
    private void applyVoiceToParams(ComfyUIClient.SavedVoice v) {
        if (v == null) return;
        if (temperatureSlider != null) {
            double t = v.getTemperature();
            temperatureSlider.setValue(Math.max(0.1, Math.min(1.5, t)));
        }
        if (topPSlider != null) {
            double p = v.getTopP();
            topPSlider.setValue(Math.max(0.01, Math.min(1.0, p)));
        }
        if (topKSlider != null) {
            int k = v.getTopK();
            topKSlider.setValue(Math.max(1, Math.min(100, k)));
        }
        if (repetitionPenaltySlider != null) {
            double r = v.getRepetitionPenalty();
            repetitionPenaltySlider.setValue(Math.max(1.0, Math.min(2.0, r)));
        }
        if (highQualityCheck != null) {
            highQualityCheck.setSelected(v.isHighQuality());
        }
    }

    private Node buildRightPanel(String content) {
        codeArea = new CodeArea();
        codeArea.setWrapText(true);
        codeArea.getStyleClass().add("code-area");
        applyCodeAreaTheme(codeArea, themeIndex);
        codeArea.replaceText(content);

        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) codeArea.getStylesheets().add(cssPath);

        scrollPane = new VirtualizedScrollPane<>(codeArea);
        scrollPane.setStyle("-fx-padding: 5px;");

        codeArea.selectionProperty().addListener((o, a, b) -> refreshHighlight());
        codeArea.textProperty().addListener((o, a, b) -> refreshHighlight());

        codeArea.setOnMouseClicked(me -> {
            if (me.getButton() != MouseButton.PRIMARY && me.getButton() != MouseButton.SECONDARY) return;
            Point2D local = codeArea.screenToLocal(me.getScreenX(), me.getScreenY());
            int offset = codeArea.hit(local.getX(), local.getY()).getInsertionIndex();
            offset = Math.max(0, Math.min(offset, codeArea.getLength()));
            TtsSegment hit = segmentAtOffset(offset);
            if (hit == null) return;
            if (me.getButton() == MouseButton.PRIMARY) {
                loadSegmentToLeft(hit);
            } else {
                deleteSegment(hit);
            }
        });

        codeArea.setOnMouseMoved(me -> refreshHighlight());

        applyThemeToNode(codeArea, themeIndex);
        applyThemeToNode(scrollPane, themeIndex);
        VBox right = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        applyThemeToNode(right, themeIndex);
        return right;
    }

    /** Liefert das Segment an offset; bei Überlappung das kürzeste (genaueste). */
    private TtsSegment segmentAtOffset(int offset) {
        TtsSegment best = null;
        int bestLen = Integer.MAX_VALUE;
        for (TtsSegment s : segments) {
            if (offset >= s.start && offset < s.end) {
                int len = s.end - s.start;
                if (len < bestLen) {
                    bestLen = len;
                    best = s;
                }
            }
        }
        return best;
    }

    private void loadSegmentToLeft(TtsSegment seg) {
        codeArea.selectRange(seg.start, seg.end);
        if (seg.voiceName != null && !seg.voiceName.isEmpty()) {
            for (ComfyUIClient.SavedVoice v : voiceCombo.getItems()) {
                if (v != null && seg.voiceName.equals(v.getName())) {
                    voiceCombo.getSelectionModel().select(v);
                    break;
                }
            }
        }
        if (temperatureSlider != null) {
            double t = seg.temperature > 0 ? seg.temperature : 0.35;
            temperatureSlider.setValue(Math.max(0.1, Math.min(1.5, t)));
        }
        if (topPSlider != null) {
            double p = seg.topP > 0 ? seg.topP : ComfyUIClient.DEFAULT_TOP_P;
            topPSlider.setValue(Math.max(0.01, Math.min(1.0, p)));
        }
        if (topKSlider != null) {
            int k = seg.topK > 0 ? seg.topK : ComfyUIClient.DEFAULT_TOP_K;
            topKSlider.setValue(Math.max(1, Math.min(100, k)));
        }
        if (repetitionPenaltySlider != null) {
            double r = seg.repetitionPenalty >= 1.0 && seg.repetitionPenalty <= 2.0 ? seg.repetitionPenalty : ComfyUIClient.DEFAULT_REPETITION_PENALTY;
            repetitionPenaltySlider.setValue(r);
        }
        if (highQualityCheck != null) {
            highQualityCheck.setSelected(seg.highQuality);
        }
        if (seg.audioPath != null && !seg.audioPath.isEmpty()) {
            Path p = Paths.get(seg.audioPath);
            if (Files.isRegularFile(p)) {
                loadAudioInPlayer(p);
                lastGeneratedAudioPath = p;
            }
        }
        setStatus("Segment zum Überarbeiten geladen.");
        refreshHighlight();
    }

    private void deleteSegment(TtsSegment seg) {
        CustomAlert alert = DialogFactory.createConfirmationAlert(
            "Segment löschen",
            "Segment und Audiodatei löschen?",
            "Der markierte Bereich wird wieder editierbar; die zugehörige Audiodatei wird gelöscht.",
            stage
        );
        alert.applyTheme(themeIndex);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;
        try {
            if (seg.audioPath != null && !seg.audioPath.isEmpty()) {
                Path p = Paths.get(seg.audioPath);
                if (Files.isRegularFile(p)) Files.delete(p);
            }
        } catch (IOException e) {
            logger.warn("Audiodatei konnte nicht gelöscht werden: {}", seg.audioPath, e);
        }
        segments.remove(seg);
        saveSegments();
        refreshHighlight();
        setStatus("Segment gelöscht.");
    }

    private void refreshHighlight() {
        String text = codeArea.getText();
        if (text == null) text = "";
        int len = text.length();
        int selStart = codeArea.getSelection().getStart();
        int selEnd = codeArea.getSelection().getEnd();
        if (selStart > selEnd) { int t = selStart; selStart = selEnd; selEnd = t; }
        selStart = Math.max(0, Math.min(selStart, len));
        selEnd = Math.max(0, Math.min(selEnd, len));

        boolean[] isSaved = new boolean[len];
        for (TtsSegment s : segments) {
            int a = Math.max(0, Math.min(s.start, len));
            int b = Math.max(0, Math.min(s.end, len));
            for (int i = a; i < b; i++) isSaved[i] = true;
        }

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;
        while (pos < len) {
            boolean inSaved = isSaved[pos];
            boolean inSel = (pos >= selStart && pos < selEnd);
            String style = inSaved ? "tts-saved" : (inSel ? "tts-selection" : null);
            int start = pos;
            while (pos < len) {
                boolean s = isSaved[pos];
                boolean sel = (pos >= selStart && pos < selEnd);
                String nextStyle = s ? "tts-saved" : (sel ? "tts-selection" : null);
                if ((nextStyle == null) != (style == null) || (nextStyle != null && !nextStyle.equals(style))) break;
                pos++;
            }
            if (style != null) builder.add(Collections.singleton(style), pos - start);
            else builder.add(Collections.emptyList(), pos - start);
        }
        try {
            codeArea.setStyleSpans(0, builder.create());
        } catch (Exception e) {
            logger.trace("StyleSpans Fehler", e);
        }
    }

    /** Erzeugt eine Signatur aus Text + aktuellen TTS-Parametern für Vergleich „unverändert“. Werte explizit formatiert, damit jede Änderung (z. B. Temperatur) erkannt wird. */
    private String buildTtsRequestSignature(String selectedText, String voiceName, double temp, double topP, int topK, double repPen, boolean hq) {
        return selectedText + "|" + (voiceName != null ? voiceName : "")
            + "|" + String.format(java.util.Locale.ROOT, "%.4f", temp)
            + "|" + String.format(java.util.Locale.ROOT, "%.4f", topP)
            + "|" + topK
            + "|" + String.format(java.util.Locale.ROOT, "%.4f", repPen)
            + "|" + hq;
    }

    private void createTtsForSelection() {
        String sel = codeArea.getSelectedText();
        if (sel == null || sel.isBlank()) {
            setStatus("Bitte Text markieren.");
            return;
        }
        ComfyUIClient.SavedVoice voice = voiceCombo.getSelectionModel().getSelectedItem();
        if (voice == null) {
            setStatus("Bitte eine Stimme wählen.");
            return;
        }
        double temp = temperatureSlider.getValue();
        double topP = topPSlider.getValue();
        int topK = (int) Math.round(topKSlider.getValue());
        double repPen = Math.max(1.0, Math.min(2.0, repetitionPenaltySlider.getValue()));
        boolean hq = highQualityCheck.isSelected();
        String signature = buildTtsRequestSignature(sel, voice.getName(), temp, topP, topK, repPen, hq);

        if (signature.equals(lastGeneratedSignature) && lastGeneratedAudioPath != null && Files.isRegularFile(lastGeneratedAudioPath)) {
            loadAudioInPlayer(lastGeneratedAudioPath);
            setStatus("Unverändert; letzte Erzeugung wird verwendet.");
            return;
        }
        if (isTtsGenerationRunning) {
            setStatus("Erzeugung läuft bereits…");
            return;
        }
        isTtsGenerationRunning = true;

        ComfyUIClient.SavedVoice effectiveVoice = new ComfyUIClient.SavedVoice(
            voice.getName(), voice.getSeed(), temp, voice.getVoiceDescription(), hq, true,
            topP, topK, repPen, voice.getSpeakerId());
        ttsRequestId++;
        final int myRequestId = ttsRequestId;
        progressIndicator.setVisible(true);
        btnErstellen.setDisable(true);
        setStatus("Erzeuge Sprachausgabe…");
        CompletableFuture.runAsync(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient();
                java.util.Map<String, String> lexicon = ComfyUIClient.getDefaultPronunciationLexicon();
                Map<String, Object> history = client.generateTTSWithSavedVoice(sel, effectiveVoice, lexicon, true, null);
                Path path = Files.createTempFile("manuskript-tts-", ".mp3");
                client.downloadAudioToFile(history, path);
                Path p = path;
                String sig = signature;
                Platform.runLater(() -> {
                    if (myRequestId != ttsRequestId) return;
                    isTtsGenerationRunning = false;
                    lastGeneratedAudioPath = p;
                    lastGeneratedSignature = sig;
                    generatedAudioBySignature.put(sig, p);
                    loadAudioInPlayer(p);
                    progressIndicator.setVisible(false);
                    btnErstellen.setDisable(false);
                    setStatus("Erstellt. Sie können speichern oder erneut erstellen.");
                });
            } catch (Exception e) {
                logger.error("TTS Erstellen fehlgeschlagen", e);
                final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Platform.runLater(() -> {
                    if (myRequestId != ttsRequestId) return;
                    isTtsGenerationRunning = false;
                    progressIndicator.setVisible(false);
                    btnErstellen.setDisable(false);
                    if (lastGeneratedAudioPath != null && Files.isRegularFile(lastGeneratedAudioPath)) {
                        loadAudioInPlayer(lastGeneratedAudioPath);
                        setStatus("ComfyUI hat die Anfrage abgelehnt; letzte Erzeugung wird verwendet.");
                    } else {
                        setStatus("Fehler: " + msg);
                        CustomAlert errAlert = DialogFactory.createErrorAlert("TTS-Fehler", "Sprachausgabe", "Erzeugen fehlgeschlagen: " + msg, stage);
                        errAlert.applyTheme(themeIndex);
                        errAlert.showAndWait();
                    }
                });
            }
        }, TTS_EXECUTOR);
    }

    private void loadAudioInPlayer(Path path) {
        if (path == null) return;
        Path requestedPath = path.normalize().toAbsolutePath();
        pendingAudioPath = requestedPath;
        if (playAllAdvanceTransition != null) {
            playAllAdvanceTransition.stop();
            playAllAdvanceTransition = null;
        }
        if (embeddedPlayer != null) {
            embeddedPlayer.stop();
            embeddedPlayer.dispose();
            embeddedPlayer = null;
        }
        try {
            Media media = new Media(requestedPath.toUri().toString());
            MediaPlayer player = new MediaPlayer(media);
            embeddedPlayer = player;
            player.setCycleCount(1);
            btnPlay.setDisable(false);
            btnPause.setDisable(false);
            btnStop.setDisable(false);
            player.currentTimeProperty().addListener((o, a, b) -> {
                if (embeddedPlayer != player) return;
                Duration total = player.getTotalDuration();
                if (total != null && total.greaterThan(Duration.ZERO)) {
                    double p = b.toMillis() / total.toMillis();
                    Platform.runLater(() -> playerProgress.setProgress(p));
                }
            });
            player.setOnReady(() -> Platform.runLater(() -> {
                if (embeddedPlayer != player || !requestedPath.equals(pendingAudioPath)) return;
                playerProgress.setProgress(0);
                if (isPlayingAllSequence) {
                    Duration d = player.getTotalDuration();
                    if (d != null && d.greaterThan(Duration.ZERO)) {
                        if (playAllAdvanceTransition != null) playAllAdvanceTransition.stop();
                        playAllAdvanceTransition = new PauseTransition(d);
                        playAllAdvanceTransition.setOnFinished(e2 -> {
                            if (!isPlayingAllSequence || embeddedPlayer != player) return;
                            playAllAdvanceTransition = null;
                            playerProgress.setProgress(1.0);
                            player.stop();
                            playingSegmentIndex++;
                            playCurrentSegmentInEmbeddedPlayer();
                        });
                        playAllAdvanceTransition.play();
                    }
                }
            }));
            player.setOnEndOfMedia(() -> Platform.runLater(() -> {
                if (embeddedPlayer != player) return;
                playerProgress.setProgress(1.0);
                player.stop();
                if (!isPlayingAllSequence) {
                    // Nur außerhalb „Alle abspielen“ hier reagieren; bei „Alle abspielen“ übernimmt der Timer
                }
            }));
        } catch (Exception e) {
            logger.warn("Audio laden fehlgeschlagen", e);
            if (requestedPath.equals(pendingAudioPath)) pendingAudioPath = null;
        }
    }

    private void saveCurrentAsSegment() {
        int start = codeArea.getSelection().getStart();
        int end = codeArea.getSelection().getEnd();
        if (start >= end) {
            setStatus("Bitte einen Bereich markieren.");
            return;
        }
        if (lastGeneratedAudioPath == null || !Files.isRegularFile(lastGeneratedAudioPath)) {
            setStatus("Zuerst „Erstellen“ ausführen.");
            return;
        }
        ComfyUIClient.SavedVoice voice = voiceCombo.getSelectionModel().getSelectedItem();
        String voiceName = voice != null ? voice.getName() : "";
        double temp = temperatureSlider.getValue();
        String sel = codeArea.getSelectedText();
        String currentSig = buildTtsRequestSignature(sel != null ? sel : "", voiceName, temp, topPSlider.getValue(), (int) Math.round(topKSlider.getValue()), Math.max(1.0, Math.min(2.0, repetitionPenaltySlider.getValue())), highQualityCheck.isSelected());
        Path sourcePath = generatedAudioBySignature.get(currentSig);
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            sourcePath = lastGeneratedSignature != null && currentSig.equals(lastGeneratedSignature) ? lastGeneratedAudioPath : null;
        }
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            setStatus("Bitte zuerst „Erstellen“ für den markierten Bereich ausführen.");
            return;
        }
        double topP = topPSlider.getValue();
        int topK = (int) Math.round(topKSlider.getValue());
        double repPen = Math.max(1.0, Math.min(2.0, repetitionPenaltySlider.getValue()));
        boolean hq = highQualityCheck.isSelected();

        try {
            if (audioDirPath != null) Files.createDirectories(audioDirPath);
            // Neue Block-Nummer VOR dem Entfernen festlegen, damit keine bestehende Segment-Datei überschrieben wird
            int n = segments.size() + 1;
            String baseName = "block_" + String.format("%03d", n) + ".mp3";
            // Überlappende Segmente entfernen (gleicher Bereich mehrfach gespeichert → nur das letzte behalten)
            List<TtsSegment> toRemove = new ArrayList<>();
            for (TtsSegment s : segments) {
                if (s.start < end && s.end > start) toRemove.add(s);
            }
            for (TtsSegment s : toRemove) {
                if (s.audioPath != null && !s.audioPath.isEmpty()) {
                    try {
                        Path p = Paths.get(s.audioPath);
                        if (Files.isRegularFile(p)) Files.delete(p);
                    } catch (IOException e2) {
                        logger.warn("Audiodatei beim Überschreiben nicht gelöscht: {}", s.audioPath, e2);
                    }
                }
                segments.remove(s);
            }
            Path target = audioDirPath != null ? audioDirPath.resolve(baseName) : sourcePath;
            if (audioDirPath != null) Files.copy(sourcePath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String audioPathStr = target.toAbsolutePath().toString();
            TtsSegment seg = new TtsSegment(start, end, audioPathStr, voiceName, temp, topP, topK, repPen, hq);
            segments.add(seg);
            saveSegments();
            refreshHighlight();
            setStatus("Gespeichert: " + baseName);
        } catch (IOException e) {
            logger.error("Speichern fehlgeschlagen", e);
            CustomAlert errAlert = DialogFactory.createErrorAlert("Speichern", "Segment", "Konnte nicht speichern: " + e.getMessage(), stage);
            errAlert.applyTheme(themeIndex);
            errAlert.showAndWait();
        }
    }

    private void playAllSegments() {
        if (segments.isEmpty()) {
            setStatus("Keine Segmente zum Abspielen.");
            return;
        }
        if (isPlayingAllSequence) {
            return;
        }
        isPlayingAllSequence = true;
        playingSegmentIndex = 0;
        segmentsPlayOrder = new ArrayList<>(segments);
        segmentsPlayOrder.sort(Comparator.comparingInt(s -> s.start));
        if (btnAlleAbspielen != null) {
            btnAlleAbspielen.setDisable(true);
        }
        playCurrentSegmentInEmbeddedPlayer();
    }

    private void endPlayAllSequence() {
        isPlayingAllSequence = false;
        playingSegmentIndex = -1;
        segmentsPlayOrder = null;
        if (playAllAdvanceTransition != null) {
            playAllAdvanceTransition.stop();
            playAllAdvanceTransition = null;
        }
        if (btnAlleAbspielen != null) {
            btnAlleAbspielen.setDisable(false);
        }
        refreshHighlight();
        setStatus("Wiedergabe beendet.");
    }

    /**
     * Spielt das aktuelle Segment (playingSegmentIndex) mit dem eingebetteten Player ab.
     * Wird von „Alle abspielen“ verwendet, um die Segmente sequentiell abzuspielen.
     */
    private void playCurrentSegmentInEmbeddedPlayer() {
        if (!isPlayingAllSequence) {
            return; // Sequenz wurde abgebrochen
        }
        List<TtsSegment> list = (segmentsPlayOrder != null) ? segmentsPlayOrder : segments;
        if (playingSegmentIndex < 0 || playingSegmentIndex >= list.size()) {
            endPlayAllSequence();
            return;
        }
        TtsSegment seg = list.get(playingSegmentIndex);
        refreshHighlight();
        codeArea.selectRange(seg.start, seg.end);
        codeArea.requestFollowCaret();
        Path p = Paths.get(seg.audioPath);
        if (!Files.isRegularFile(p)) {
            // Datei fehlt – zum nächsten Segment springen
            playingSegmentIndex++;
            playCurrentSegmentInEmbeddedPlayer();
            return;
        }
        loadAudioInPlayer(p);
        if (embeddedPlayer != null) {
            embeddedPlayer.seek(Duration.ZERO);
            embeddedPlayer.play();
        }
    }

    private void createFullAudioFile() {
        if (segments.isEmpty()) {
            setStatus("Keine Segmente vorhanden.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Gesamt-Audiodatei speichern");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3", "*.mp3"));
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        Path out = f.toPath();
        progressIndicator.setVisible(true);
        setStatus("Füge Audiodateien zusammen…");
        CompletableFuture.runAsync(() -> {
            try {
                List<TtsSegment> byTextOrder = new ArrayList<>(segments);
                byTextOrder.sort(Comparator.comparingInt(s -> s.start));
                List<Path> files = new ArrayList<>();
                for (TtsSegment s : byTextOrder) {
                    Path p = Paths.get(s.audioPath);
                    if (Files.isRegularFile(p)) files.add(p);
                }
                try (java.io.OutputStream os = Files.newOutputStream(out)) {
                    for (Path p : files) {
                        Files.copy(p, os);
                    }
                }
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    setStatus("Gespeichert: " + out.getFileName());
                });
            } catch (Exception e) {
                logger.error("Zusammenfügen fehlgeschlagen", e);
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    setStatus("Fehler: " + e.getMessage());
                    CustomAlert errAlert = DialogFactory.createErrorAlert("Export", "Gesamt-Audiodatei", e.getMessage(), stage);
                    errAlert.applyTheme(themeIndex);
                    errAlert.showAndWait();
                });
            }
        }, TTS_EXECUTOR);
    }

    private void loadSegments() {
        if (segmentsPath == null || !Files.isRegularFile(segmentsPath)) return;
        try {
            String json = Files.readString(segmentsPath, StandardCharsets.UTF_8);
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.reflect.TypeToken<List<TtsSegment>> type = new com.google.gson.reflect.TypeToken<List<TtsSegment>>() {};
            List<TtsSegment> list = gson.fromJson(json, type.getType());
            if (list != null) {
                segments.clear();
                segments.addAll(list);
            }
        } catch (Exception e) {
            logger.warn("Segmente konnten nicht geladen werden: {}", segmentsPath, e);
        }
    }

    private void saveSegments() {
        if (segmentsPath == null) return;
        try {
            Files.createDirectories(segmentsPath.getParent());
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(new ArrayList<>(segments));
            Files.writeString(segmentsPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Segmente konnten nicht gespeichert werden: {}", segmentsPath, e);
        }
    }

    /** Schreibt den Editor-Text in eine getrennte Datei im TTS-Datenordner. Das Original-.md wird nicht angefasst. */
    private void saveEditorContentToSeparateFile() {
        if (dataDir == null || codeArea == null) return;
        try {
            Path path = Paths.get(dataDir.getPath(), chapterName + "-tts-content.md");
            Files.createDirectories(path.getParent());
            String text = codeArea.getText();
            Files.writeString(path, text, StandardCharsets.UTF_8);
            logger.debug("TTS-Editor-Text getrennt gespeichert: {}", path.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("TTS-Editor-Text konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg != null ? msg : " ");
    }

    private void applyThemeToAll(Node root, int themeIndex) {
        if (root.getStyleClass().contains("code-area")) {
            return;
        }
        applyThemeToNode(root, themeIndex);
        if (root instanceof Parent) {
            for (Node child : ((Parent) root).getChildrenUnmodifiable()) {
                applyThemeToAll(child, themeIndex);
            }
        }
    }

    private void applyThemeToNode(Node node, int themeIndex) {
        if (node == null) return;
        node.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
        if (themeIndex == 0) node.getStyleClass().add("weiss-theme");
        else if (themeIndex == 1) node.getStyleClass().add("theme-dark");
        else if (themeIndex == 2) node.getStyleClass().add("pastell-theme");
        else if (themeIndex == 3) { node.getStyleClass().add("theme-dark"); node.getStyleClass().add("blau-theme"); }
        else if (themeIndex == 4) { node.getStyleClass().add("theme-dark"); node.getStyleClass().add("gruen-theme"); }
        else if (themeIndex == 5) { node.getStyleClass().add("theme-dark"); node.getStyleClass().add("lila-theme"); }
    }

    /** CodeArea: Nur Schrift, Hintergrund/Textfarbe kommen aus CSS (.theme-dark .code-area). */
    private void applyCodeAreaTheme(CodeArea area, int themeIndex) {
        int idx = Math.max(0, Math.min(themeIndex, THEMES.length - 1));
        String bg = THEMES[idx][0];
        String text = THEMES[idx][1];
        String style = String.format(
            "-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; " +
            "-rtfx-background-color: %s !important; -fx-text-fill: %s !important; -fx-caret-color: %s !important;",
            bg, text, text);
        area.setStyle(style);
    }
}
