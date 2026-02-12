package com.manuskript;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fenster zur Kapitelbearbeitung mit Sprachsynthese: links TTS-Steuerung, rechts Text mit
 * orangener Markierung (Bearbeitungsblock) und grüner Markierung (gespeicherte Segmente).
 * Linksklick auf gespeichertes Segment lädt es links zum Überarbeiten; Rechtsklick löscht. Neue Segmente: Text per Drag auswählen → Erstellen → Speichern.
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

    /** Anzahl vordefinierter Segmentfarben (Palette). */
    /** Pause in Sekunden zwischen den Segmenten in der Gesamt-Audiodatei (zwischen den „Kapiteln“). */
    private static final double FULL_AUDIO_PAUSE_SECONDS = 3.5;
    /** MP3-Qualitätsoptionen für Gesamt-Audiodatei / Hörbuch (Anzeige). Index 0 = kleine Datei, 1 = Standard, 2 = hohe Qualität. */
    private static final String[] MP3_QUALITY_OPTIONS = { "Kleine Datei (geringe Qualität)", "Standard", "Hohe Qualität (große Datei)" };
    /** FFmpeg -q:a Werte zu MP3_QUALITY_OPTIONS (0=klein, 1=Standard, 2=groß). */
    private static final int[] MP3_QUALITY_VALUES = { 6, 4, 2 };
    /** Verzeichnis für gebündeltes FFmpeg (analog zu pandoc/) – enthält ffmpeg.exe (Windows) bzw. ffmpeg (Linux/macOS) oder bin/ffmpeg.exe. */
    private static final String FFMPEG_DIR = "ffmpeg";
    private static final int SEGMENT_PALETTE_SIZE = 16;
    /** Farben für die Segment-Palette: Reihe 1 dezent, Reihe 2 knallig. */
    private static final String[] SEGMENT_PALETTE_COLORS = {
        /* Reihe 1 – dezent, etwas kräftiger */
        "#a8e6a1", "#81c784", "#64b5f6", "#fff59d", "#f48fb1", "#ce93d8", "#ffb74d", "#80cbc4",
        /* Reihe 2 – knallig */
        "#66bb6a", "#42a5f5", "#ffca28", "#ec407a", "#ab47bc", "#ffa726", "#26c6da", "#ef5350"
    };

    /** Ein gespeichertes Segment: Textbereich + Audiodatei + Stimmenname + exakter Seed/Speaker für gleiche Stimme. */
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
        /** Stimmbeschreibung, mit der dieses Segment erzeugt wurde (leer = keine). Wird mitgespeichert und beim Laden ins Feld übernommen. */
        public String voiceDescription = "";
        /** Index in der Segmentfarben-Palette (0..PALETTE_SIZE-1), oder -1 = abwechselnd (even/odd). */
        public int highlightColorIndex = -1;
        /** Exakter Seed, mit dem dieses Segment erzeugt wurde (0 = nicht gesetzt, dann Fallback auf Stimme). */
        public long seed = 0;
        /** Speaker-ID, mit der dieses Segment erzeugt wurde (leer = Fallback auf Stimme). */
        public String speakerId = "";
        /** true = Segment wurde mit ElevenLabs erzeugt; ElevenLabs-Parameter aus den folgenden Feldern übernehmen. */
        public boolean hasElevenLabsParams = false;
        public String elevenLabsModelId = "";
        public double elevenLabsStability = 0.5;
        public double elevenLabsSimilarityBoost = 0.75;
        public double elevenLabsSpeed = 1.0;
        public boolean elevenLabsUseSpeakerBoost = true;
        public double elevenLabsStyle = 0.0;

        public TtsSegment() {}

        public TtsSegment(int start, int end, String audioPath, String voiceName) {
            this.start = start;
            this.end = end;
            this.audioPath = audioPath != null ? audioPath : "";
            this.voiceName = voiceName != null ? voiceName : "";
        }

        public TtsSegment(int start, int end, String audioPath, String voiceName,
                          double temperature, double topP, int topK, double repetitionPenalty, boolean highQuality) {
            this(start, end, audioPath, voiceName, temperature, topP, topK, repetitionPenalty, highQuality, "");
        }

        public TtsSegment(int start, int end, String audioPath, String voiceName,
                          double temperature, double topP, int topK, double repetitionPenalty, boolean highQuality, String voiceDescription) {
            this.start = start;
            this.end = end;
            this.audioPath = audioPath != null ? audioPath : "";
            this.voiceName = voiceName != null ? voiceName : "";
            this.temperature = temperature > 0 ? temperature : 0.35;
            this.topP = topP > 0 ? topP : ComfyUIClient.DEFAULT_TOP_P;
            this.topK = topK > 0 ? topK : ComfyUIClient.DEFAULT_TOP_K;
            this.repetitionPenalty = repetitionPenalty >= 1.0 && repetitionPenalty <= 2.0 ? repetitionPenalty : ComfyUIClient.DEFAULT_REPETITION_PENALTY;
            this.highQuality = highQuality;
            this.voiceDescription = voiceDescription != null ? voiceDescription : "";
        }
    }

    private final DocxFile chapterFile;
    private final File mdFile;
    private final File dataDir;
    private final String chapterName;
    private final Path segmentsPath;
    private final Path audioDirPath;
    private final Path originalHashPath;
    private final int themeIndex;
    private final Window owner;

    private CustomStage stage;
    private CodeArea codeArea;
    private VirtualizedScrollPane<CodeArea> scrollPane;
    /** Klick-Vorschau: Bereich [start, end), der bei einfachem Klick (ohne Drag) grau hervorgehoben wird. -1 = keine. */
    private int hoverRangeStart = -1;
    private int hoverRangeEnd = -1;
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
    /** Stimmbeschreibung auf dem Haupt-Tab (vor Segmentfarbe); wird beim Generieren mitgegeben und bei Stimmenwechsel aus der gewählten Stimme geladen. Bei geklonter Stimme ausgeblendet. */
    private TextArea voiceDescriptionArea;
    /** Container (Label + TextArea) für Stimmbeschreibung, zum Ein-/Ausblenden bei Voice Clone / ElevenLabs. */
    private Node voiceDescriptionContainer;
    /** Container für ComfyUI-Parameter (Temperatur, top_p, top_k, Rep. Penalty, Hohe Qualität); bei ElevenLabs ausgeblendet. */
    private Node comfyuiParamsContainer;
    /** ElevenLabs-Modell-Dropdown; nur sichtbar wenn eine ElevenLabs-Stimme gewählt ist. */
    private ComboBox<String> elevenLabsModelCombo;
    private Node elevenLabsModelContainer;
    /** Anzeige des ElevenLabs-Zeichen-Guthabens (verbleibend / Limit). */
    private Label elevenLabsBalanceLabel;
    private Slider elevenLabsSpeedSlider;
    private Slider elevenLabsStabilitySlider;
    private Slider elevenLabsSimilaritySlider;
    private CheckBox elevenLabsSpeakerBoostCheck;
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
    /** MP3-Qualität für Gesamt-Audiodatei (0–2 → kleine Datei / Standard / hohe Qualität). */
    private ComboBox<String> fullAudioQualityCombo;
    /** Batch-Modus: alle noch nicht markierten Teile (Satz oder Absatz) nacheinander rendern. Ein Button wechselt zwischen Start/Beenden. */
    private ComboBox<String> batchModeCombo;
    private Button btnBatchToggle;
    private volatile boolean batchCancelled = false;
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
    /** Aktuell zum Bearbeiten geladenes Segment (für Farbauswahl). */
    private TtsSegment selectedSegmentForColor = null;
    /** Seed und Speaker für die nächste Erzeugung – aus geladenem Segment oder aus gewählter Stimme, damit die Stimme gleich bleibt. */
    private long currentSeedForGeneration = ComfyUIClient.DEFAULT_SEED;
    private String currentSpeakerIdForGeneration = ComfyUIClient.DEFAULT_CUSTOM_SPEAKER;
    /** Grünes Häkchen nach erfolgreicher Generierung (15 s angezeigt). */
    private Label successCheckmark;
    private PauseTransition successCheckmarkHideTransition;

    /** Voice-Clone-Tab: gewählte Referenz-Audiodatei. */
    private File vcRefAudioFile;
    private Label vcRefAudioLabel;
    private TextArea vcTranscriptArea;
    private TextArea vcVoiceDescriptionArea;
    private TextField vcVoiceNameField;
    private TextArea vcTestTextArea;
    private Slider vcTempSlider;
    private Slider vcTopPSlider;
    private Slider vcTopKSlider;
    private Slider vcRepPenSlider;
    private CheckBox vcHighQualityCheck;
    private Button vcKlonenButton;
    private Button vcAlsStimmeSpeichernButton;
    private ProgressIndicator vcProgressIndicator;
    private Label vcStatusLabel;

    /** Aussprache-Lexikon im TTS-Tab: Tabelle wird bei TTS-Erzeugung und Batch verwendet (direkte Änderungen sofort wirksam). */
    private TableView<ComfyUITTSTestWindow.LexiconEntry> lexiconTable;
    private ObservableList<ComfyUITTSTestWindow.LexiconEntry> lexiconItems;

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
        this.originalHashPath = dataDir != null ? Paths.get(dataDir.getPath(), chapterName + "-tts-original-hash.txt") : null;
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
        Preferences ttsPrefs = Preferences.userNodeForPackage(ChapterTtsEditorWindow.class);
        Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
            ttsPrefs, "tts_editor_window", 1200.0, 950.0);
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(w.stage, windowBounds);
        w.setupTtsWindowPersistence(ttsPrefs);
        Scene scene = new Scene(w.buildRoot(contentToShow));
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        w.stage.setSceneWithTitleBar(scene);
        w.stage.setFullTheme(themeIndex);
        w.applyThemeToAll(scene.getRoot(), themeIndex);
        w.checkAndUpdateOriginalHash(content);
        w.loadSegments();
        w.refreshHighlight();
        w.stage.setOnCloseRequest(ev -> {
            w.saveSegments();
            w.saveEditorContentToSeparateFile();
        });
        w.stage.show();
        Platform.runLater(() -> w.scrollEditorToTop());
    }

    /** Scrollt den Textbereich nach dem Laden ganz nach oben. */
    private void scrollEditorToTop() {
        if (codeArea == null) return;
        codeArea.selectRange(0, 0);
        codeArea.requestFollowCaret();
    }

    /** Speichert Position und Größe des TTS-Editors in den Preferences und stellt Listener ein. */
    private void setupTtsWindowPersistence(Preferences prefs) {
        if (prefs == null || stage == null) return;
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(prefs, "tts_editor_window_x", newVal.doubleValue());
            }
        });
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(prefs, "tts_editor_window_y", newVal.doubleValue());
            }
        });
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowWidth(prefs, "tts_editor_window_width", newVal.doubleValue());
            }
        });
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowHeight(prefs, "tts_editor_window_height", newVal.doubleValue());
            }
        });
    }

    private Parent buildRoot(String content) {
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.42);
        split.getStyleClass().add("tts-editor-split");

        Node left = buildLeftPanel();
        Node right = buildRightPanel(content);
        split.getItems().addAll(left, right);
        // Beim Ziehen des Teilers nur die linke Spalte breiter/schmaler machen, rechte Seite (Tabelle/Editor) behält Breite
        SplitPane.setResizableWithParent(right, false);

        applyThemeToNode(split, themeIndex);
        return split;
    }

    private Node buildLeftPanel() {
        VBox left = new VBox(12);
        left.setPadding(new Insets(12));
        left.setMinWidth(480);
        left.setPrefWidth(560);
        left.setMaxWidth(700);

        Label voiceLabel = new Label("Stimme");
        voiceCombo = new ComboBox<>();
        voiceCombo.setPromptText("Stimme wählen");
        loadCombinedVoiceList();
        voiceCombo.setConverter(new StringConverter<ComfyUIClient.SavedVoice>() {
            @Override
            public String toString(ComfyUIClient.SavedVoice v) {
                return v != null ? v.getName() : "";
            }
            @Override
            public ComfyUIClient.SavedVoice fromString(String s) { return null; }
        });
        if (!voiceCombo.getItems().isEmpty()) voiceCombo.getSelectionModel().selectFirst();

        elevenLabsModelCombo = new ComboBox<>();
        elevenLabsModelCombo.getItems().setAll(ElevenLabsClient.KNOWN_MODEL_IDS);
        elevenLabsModelCombo.getSelectionModel().select(ElevenLabsClient.DEFAULT_MODEL_ID);
        if (!elevenLabsModelCombo.getItems().isEmpty() && elevenLabsModelCombo.getSelectionModel().getSelectedItem() == null) {
            elevenLabsModelCombo.getSelectionModel().selectFirst();
        }
        Label elevenLabsModelLabel = new Label("ElevenLabs-Modell");
        elevenLabsBalanceLabel = new Label("Guthaben: —");
        elevenLabsBalanceLabel.setWrapText(true);
        elevenLabsBalanceLabel.setMaxWidth(Double.MAX_VALUE);
        elevenLabsSpeedSlider = new Slider(0.7, 1.2, 1.0);
        elevenLabsSpeedSlider.setBlockIncrement(0.05);
        Label elevenLabsSpeedLabel = new Label("1.00");
        elevenLabsSpeedLabel.setMinWidth(28);
        elevenLabsSpeedSlider.valueProperty().addListener((o, a, b) -> {
            elevenLabsSpeedLabel.setText(String.format("%.2f", b.doubleValue()));
            ComfyUIClient.SavedVoice v = voiceCombo.getSelectionModel().getSelectedItem();
            if (v != null && "elevenlabs".equalsIgnoreCase(v.getProvider())) {
                v.setElevenLabsSpeed(b.doubleValue());
                persistSelectedVoiceElevenLabsParams();
            }
        });
        HBox elevenLabsSpeedRow = new HBox(6);
        elevenLabsSpeedRow.getChildren().addAll(new Label("Geschwindigkeit"), elevenLabsSpeedLabel, elevenLabsSpeedSlider);
        HBox.setHgrow(elevenLabsSpeedSlider, Priority.ALWAYS);
        elevenLabsStabilitySlider = new Slider(0, 1, 0.5);
        elevenLabsStabilitySlider.setBlockIncrement(0.05);
        Label elevenLabsStabilityLabel = new Label("0.50");
        elevenLabsStabilityLabel.setMinWidth(28);
        elevenLabsStabilitySlider.valueProperty().addListener((o, a, b) -> {
            elevenLabsStabilityLabel.setText(String.format("%.2f", b.doubleValue()));
            ComfyUIClient.SavedVoice v = voiceCombo.getSelectionModel().getSelectedItem();
            if (v != null && "elevenlabs".equalsIgnoreCase(v.getProvider())) {
                v.setElevenLabsStability(b.doubleValue());
                persistSelectedVoiceElevenLabsParams();
            }
        });
        HBox elevenLabsStabilityRow = new HBox(6);
        elevenLabsStabilityRow.getChildren().addAll(new Label("Stabilität"), elevenLabsStabilityLabel, elevenLabsStabilitySlider);
        HBox.setHgrow(elevenLabsStabilitySlider, Priority.ALWAYS);
        elevenLabsSimilaritySlider = new Slider(0, 1, 0.75);
        elevenLabsSimilaritySlider.setBlockIncrement(0.05);
        Label elevenLabsSimilarityLabel = new Label("0.75");
        elevenLabsSimilarityLabel.setMinWidth(28);
        elevenLabsSimilaritySlider.valueProperty().addListener((o, a, b) -> {
            elevenLabsSimilarityLabel.setText(String.format("%.2f", b.doubleValue()));
            ComfyUIClient.SavedVoice v = voiceCombo.getSelectionModel().getSelectedItem();
            if (v != null && "elevenlabs".equalsIgnoreCase(v.getProvider())) {
                v.setElevenLabsSimilarityBoost(b.doubleValue());
                persistSelectedVoiceElevenLabsParams();
            }
        });
        HBox elevenLabsSimilarityRow = new HBox(6);
        elevenLabsSimilarityRow.getChildren().addAll(new Label("Similarity"), elevenLabsSimilarityLabel, elevenLabsSimilaritySlider);
        HBox.setHgrow(elevenLabsSimilaritySlider, Priority.ALWAYS);
        elevenLabsSpeakerBoostCheck = new CheckBox("Speaker Boost");
        elevenLabsSpeakerBoostCheck.setSelected(true);
        elevenLabsSpeakerBoostCheck.selectedProperty().addListener((o, a, b) -> {
            ComfyUIClient.SavedVoice v = voiceCombo.getSelectionModel().getSelectedItem();
            if (v != null && "elevenlabs".equalsIgnoreCase(v.getProvider())) {
                v.setElevenLabsUseSpeakerBoost(b != null && b);
                persistSelectedVoiceElevenLabsParams();
            }
        });
        elevenLabsModelContainer = new VBox(4);
        ((VBox) elevenLabsModelContainer).getChildren().addAll(
            elevenLabsModelLabel, elevenLabsModelCombo,
            elevenLabsSpeedRow, elevenLabsStabilityRow, elevenLabsSimilarityRow, elevenLabsSpeakerBoostCheck,
            elevenLabsBalanceLabel);
        elevenLabsModelContainer.setVisible(false);
        elevenLabsModelContainer.setManaged(false);
        elevenLabsModelCombo.getSelectionModel().selectedItemProperty().addListener((o, oldVal, newVal) -> {
            ComfyUIClient.SavedVoice v = voiceCombo.getSelectionModel().getSelectedItem();
            if (v != null && "elevenlabs".equalsIgnoreCase(v.getProvider()) && newVal != null) {
                v.setElevenLabsModelId(newVal);
                persistSelectedVoiceElevenLabsParams();
            }
        });

        voiceCombo.getSelectionModel().selectedItemProperty().addListener((o, oldVal, newVal) -> applyVoiceToParams(newVal));

        temperatureSlider = new Slider(0.1, 1.5, 0.35);
        temperatureSlider.setBlockIncrement(0.05);
        temperatureLabel = new Label(String.format("%.2f", temperatureSlider.getValue()));
        temperatureLabel.setMinWidth(36);
        temperatureSlider.valueProperty().addListener((o, a, b) -> temperatureLabel.setText(String.format("%.2f", b.doubleValue())));
        HBox tempRow = new HBox(6);
        tempRow.getChildren().addAll(new Label("Temperatur"), temperatureLabel, temperatureSlider);
        HBox.setHgrow(temperatureSlider, Priority.ALWAYS);

        topPSlider = new Slider(0.01, 1.0, ComfyUIClient.DEFAULT_TOP_P);
        topPSlider.setBlockIncrement(0.05);
        topPLabel = new Label(String.format("%.2f", topPSlider.getValue()));
        topPLabel.setMinWidth(36);
        topPSlider.valueProperty().addListener((o, a, b) -> topPLabel.setText(String.format("%.2f", b.doubleValue())));
        HBox topPRow = new HBox(6);
        topPRow.getChildren().addAll(new Label("top_p"), topPLabel, topPSlider);
        HBox.setHgrow(topPSlider, Priority.ALWAYS);

        topKSlider = new Slider(1, 100, ComfyUIClient.DEFAULT_TOP_K);
        topKSlider.setBlockIncrement(5);
        topKLabel = new Label(String.valueOf((int) Math.round(topKSlider.getValue())));
        topKLabel.setMinWidth(28);
        topKSlider.valueProperty().addListener((o, a, b) -> topKLabel.setText(String.valueOf((int) Math.round(b.doubleValue()))));
        HBox topKRow = new HBox(6);
        topKRow.getChildren().addAll(new Label("top_k"), topKLabel, topKSlider);
        HBox.setHgrow(topKSlider, Priority.ALWAYS);

        repetitionPenaltySlider = new Slider(1.0, 2.0, ComfyUIClient.DEFAULT_REPETITION_PENALTY);
        repetitionPenaltySlider.setBlockIncrement(0.05);
        repetitionPenaltyLabel = new Label(String.format("%.2f", repetitionPenaltySlider.getValue()));
        repetitionPenaltyLabel.setMinWidth(36);
        repetitionPenaltySlider.valueProperty().addListener((o, a, b) -> repetitionPenaltyLabel.setText(String.format("%.2f", b.doubleValue())));
        HBox repPenRow = new HBox(6);
        repPenRow.getChildren().addAll(new Label("Rep. Penalty"), repetitionPenaltyLabel, repetitionPenaltySlider);
        HBox.setHgrow(repetitionPenaltySlider, Priority.ALWAYS);

        highQualityCheck = new CheckBox("Hohe Qualität (1.7B)");
        highQualityCheck.setSelected(true);

        comfyuiParamsContainer = new VBox(4);
        ((VBox) comfyuiParamsContainer).getChildren().addAll(tempRow, topPRow, topKRow, repPenRow, highQualityCheck);

        // Aussprache-Lexikon: Tabelle (direkte Änderungen werden bei Erstellen/Batch verwendet)
        Label lexiconLabel = new Label("Aussprache-Lexikon (Wort → Ersetzung)");
        lexiconItems = FXCollections.observableArrayList();
        loadLexiconIntoTable();
        lexiconTable = new TableView<>(lexiconItems);
        lexiconTable.setEditable(true);
        lexiconTable.setPrefHeight(180);
        TableColumn<ComfyUITTSTestWindow.LexiconEntry, String> colWord = new TableColumn<>("Wort");
        colWord.setCellValueFactory(c -> c.getValue().wordProperty());
        colWord.setCellFactory(column -> createLexiconTableCell(true));
        colWord.setOnEditCommit(e -> commitLexiconCell(e.getTablePosition().getRow(), e.getNewValue(), true));
        colWord.setPrefWidth(120);
        TableColumn<ComfyUITTSTestWindow.LexiconEntry, String> colReplacement = new TableColumn<>("Ersetzung");
        colReplacement.setCellValueFactory(c -> c.getValue().replacementProperty());
        colReplacement.setCellFactory(column -> createLexiconTableCell(false));
        colReplacement.setOnEditCommit(e -> commitLexiconCell(e.getTablePosition().getRow(), e.getNewValue(), false));
        colReplacement.setPrefWidth(160);
        lexiconTable.getColumns().add(colWord);
        lexiconTable.getColumns().add(colReplacement);
        ScrollPane lexiconScroll = new ScrollPane(lexiconTable);
        lexiconScroll.setFitToWidth(true);
        lexiconScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        Button btnLexiconLoad = new Button("Aus Datei laden");
        btnLexiconLoad.setOnAction(e -> loadLexiconIntoTable());
        Button btnLexiconAdd = new Button("+ Eintrag");
        btnLexiconAdd.setOnAction(e -> {
            lexiconItems.add(new ComfyUITTSTestWindow.LexiconEntry("", ""));
            int idx = lexiconItems.size() - 1;
            lexiconTable.getSelectionModel().clearAndSelect(idx);
            lexiconTable.scrollTo(idx);
            Platform.runLater(() -> lexiconTable.edit(idx, colWord));
        });
        Button btnLexiconRemove = new Button("− Entfernen");
        btnLexiconRemove.setOnAction(e -> {
            ComfyUITTSTestWindow.LexiconEntry sel = lexiconTable.getSelectionModel().getSelectedItem();
            if (sel != null) lexiconItems.remove(sel);
        });
        Button btnLexiconSave = new Button("Lexikon speichern");
        btnLexiconSave.setOnAction(e -> saveLexiconToFile());
        HBox lexiconButtons = new HBox(6);
        lexiconButtons.getChildren().addAll(btnLexiconLoad, btnLexiconAdd, btnLexiconRemove, btnLexiconSave);
        VBox lexiconBox = new VBox(4);
        lexiconBox.getChildren().addAll(lexiconLabel, lexiconScroll, lexiconButtons);

        Label voiceDescLabel = new Label("Stimmbeschreibung");
        voiceDescriptionArea = new TextArea();
        voiceDescriptionArea.setPromptText("z. B. Männlich, tief, ruhig – wird beim Generieren mitgegeben.");
        voiceDescriptionArea.setPrefRowCount(2);
        voiceDescriptionArea.setWrapText(true);
        voiceDescriptionContainer = new VBox(4);
        ((VBox) voiceDescriptionContainer).getChildren().addAll(voiceDescLabel, voiceDescriptionArea);

        Label segmentColorLabel = new Label("Segmentfarbe");
        VBox colorPalette = new VBox(4);
        HBox colorRow1 = new HBox(4);
        colorRow1.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Region standardSquare = createColorSquare(-1);
        standardSquare.setOnMouseClicked(e -> applySegmentColor(-1));
        colorRow1.getChildren().add(standardSquare);
        Tooltip.install(standardSquare, new Tooltip("Standard (abwechselnd)"));
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            Region r = createColorSquare(idx);
            r.setOnMouseClicked(e -> applySegmentColor(idx));
            colorRow1.getChildren().add(r);
        }
        HBox colorRow2 = new HBox(4);
        colorRow2.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        for (int i = 8; i < SEGMENT_PALETTE_SIZE; i++) {
            final int idx = i;
            Region r = createColorSquare(idx);
            r.setOnMouseClicked(e -> applySegmentColor(idx));
            colorRow2.getChildren().add(r);
        }
        colorPalette.getChildren().addAll(colorRow1, colorRow2);

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
        btnAlleAbspielen = new Button("Ab hier abspielen");
        btnGesamtAudiodatei = new Button("Gesamt-Audiodatei erstellen");
        batchModeCombo = new ComboBox<>();
        batchModeCombo.getItems().addAll("Satz", "Absatz");
        batchModeCombo.getSelectionModel().select(0);
        batchModeCombo.setMaxWidth(Double.MAX_VALUE);
        btnBatchToggle = new Button("Batch starten");
        progressIndicator = new ProgressIndicator(-1);
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setMinSize(20, 20);
        successCheckmark = new Label("✓");
        successCheckmark.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 16px; -fx-font-weight: bold;");
        successCheckmark.setVisible(false);
        successCheckmark.setMinSize(20, 20);
        successCheckmark.setPrefSize(20, 20);
        successCheckmark.setMaxSize(20, 20);
        successCheckmark.setAlignment(javafx.geometry.Pos.CENTER);
        successCheckmarkHideTransition = new PauseTransition(Duration.seconds(15));
        successCheckmarkHideTransition.setOnFinished(e -> {
            if (successCheckmark != null) successCheckmark.setVisible(false);
        });
        StackPane erstellenIconStack = new StackPane();
        erstellenIconStack.setMinSize(20, 20);
        erstellenIconStack.setPrefSize(20, 20);
        erstellenIconStack.setMaxSize(20, 20);
        erstellenIconStack.getChildren().addAll(progressIndicator, successCheckmark);
        HBox erstellenRow = new HBox(8);
        erstellenRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        erstellenRow.getChildren().addAll(btnErstellen, erstellenIconStack);
        statusLabel = new Label(" ");
        statusLabel.setWrapText(true);

        HBox batchRow = new HBox(8);
        batchRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        batchRow.getChildren().addAll(new Label("Batch:"), batchModeCombo, btnBatchToggle);

        // Linke Spalte: Stimme, Parameter, Segmentfarbe, Player, Buttons, Batch
        VBox leftColumn = new VBox(12);
        leftColumn.setMinWidth(220);
        leftColumn.setPrefWidth(300);
        leftColumn.getChildren().addAll(
            voiceLabel, voiceCombo,
            elevenLabsModelContainer,
            comfyuiParamsContainer,
            voiceDescriptionContainer,
            segmentColorLabel, colorPalette,
            playerBox,
            erstellenRow, btnSpeichern,
            new Separator(),
            btnAlleAbspielen,
            buildFullAudioQualityRow(),
            btnGesamtAudiodatei,
            new Separator(),
            batchRow,
            statusLabel
        );
        VBox.setVgrow(statusLabel, Priority.ALWAYS);

        // Rechte Spalte: Aussprache-Lexikon
        VBox rightColumn = new VBox(12);
        rightColumn.setMinWidth(220);
        rightColumn.setPrefWidth(320);
        VBox.setVgrow(lexiconBox, Priority.ALWAYS);
        rightColumn.getChildren().addAll(lexiconBox);

        HBox twoCol = new HBox(16);
        twoCol.getChildren().addAll(leftColumn, rightColumn);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);
        left.getChildren().add(twoCol);
        VBox.setVgrow(twoCol, Priority.ALWAYS);

        TabPane tabPane = new TabPane();
        Tab ttsTab = new Tab("TTS", left);
        ttsTab.setClosable(false);
        Node vcContent = buildVoiceCloneTabContent();
        Tab vcTab = new Tab("Voice Clone", vcContent);
        vcTab.setClosable(false);
        tabPane.getTabs().addAll(ttsTab, vcTab);

        VBox wrapper = new VBox(12);
        wrapper.setPadding(new Insets(12));
        wrapper.setMinWidth(520);
        wrapper.setPrefWidth(600);
        wrapper.setMaxWidth(750);
        wrapper.getChildren().add(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        ScrollPane leftScroll = new ScrollPane(wrapper);
        leftScroll.setFitToWidth(true);
        leftScroll.setFitToHeight(false);
        leftScroll.setMinWidth(540);
        leftScroll.setPrefWidth(620);
        leftScroll.setMaxWidth(780);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        wrapper.minHeightProperty().bind(leftScroll.heightProperty());

        bindLeftPanelActions(left);
        applyVoiceToParams(voiceCombo.getSelectionModel().getSelectedItem());
        applyThemeToNode(left, themeIndex);
        for (Node c : left.getChildren()) {
            applyThemeToNode(c, themeIndex);
            if (c instanceof VBox) for (Node cc : ((VBox) c).getChildren()) applyThemeToNode(cc, themeIndex);
            if (c instanceof HBox) {
                for (Node cc : ((HBox) c).getChildren()) {
                    applyThemeToNode(cc, themeIndex);
                    if (cc instanceof VBox) for (Node ccc : ((VBox) cc).getChildren()) applyThemeToNode(ccc, themeIndex);
                }
            }
        }
        applyThemeToNode(voiceCombo, themeIndex);
        applyThemeToNode(comfyuiParamsContainer, themeIndex);
        applyThemeToNode(tempRow, themeIndex);
        applyThemeToNode(topPRow, themeIndex);
        applyThemeToNode(topKRow, themeIndex);
        applyThemeToNode(repPenRow, themeIndex);
        applyThemeToNode(temperatureSlider, themeIndex);
        applyThemeToNode(temperatureLabel, themeIndex);
        applyThemeToNode(topPSlider, themeIndex);
        applyThemeToNode(topPLabel, themeIndex);
        applyThemeToNode(topKSlider, themeIndex);
        applyThemeToNode(topKLabel, themeIndex);
        applyThemeToNode(repetitionPenaltySlider, themeIndex);
        applyThemeToNode(repetitionPenaltyLabel, themeIndex);
        applyThemeToNode(highQualityCheck, themeIndex);
        applyThemeToNode(voiceDescriptionArea, themeIndex);
        applyThemeToNode(lexiconBox, themeIndex);
        applyThemeToNode(lexiconTable, themeIndex);
        applyThemeToNode(segmentColorLabel, themeIndex);
        applyThemeToNode(colorPalette, themeIndex);
        applyThemeToNode(btnPlay, themeIndex);
        applyThemeToNode(btnPause, themeIndex);
        applyThemeToNode(btnStop, themeIndex);
        applyThemeToNode(erstellenRow, themeIndex);
        applyThemeToNode(btnErstellen, themeIndex);
        applyThemeToNode(progressIndicator, themeIndex);
        applyThemeToNode(successCheckmark, themeIndex);
        applyThemeToNode(erstellenIconStack, themeIndex);
        applyThemeToNode(btnSpeichern, themeIndex);
        applyThemeToNode(btnAlleAbspielen, themeIndex);
        if (fullAudioQualityCombo != null) applyThemeToNode(fullAudioQualityCombo, themeIndex);
        applyThemeToNode(btnGesamtAudiodatei, themeIndex);
        applyThemeToNode(batchModeCombo, themeIndex);
        applyThemeToNode(btnBatchToggle, themeIndex);
        applyThemeToNode(batchRow, themeIndex);
        applyThemeToNode(statusLabel, themeIndex);
        applyThemeToNode(playerProgress, themeIndex);
        applyThemeToNode(wrapper, themeIndex);
        applyThemeToNode(tabPane, themeIndex);
        applyThemeToNode(vcContent, themeIndex);
        if (vcContent instanceof VBox) {
            for (Node c : ((VBox) vcContent).getChildren()) applyThemeToNode(c, themeIndex);
        }
        applyThemeToNode(leftScroll, themeIndex);
        return leftScroll;
    }

    private Node buildVoiceCloneTabContent() {
        VBox vc = new VBox(10);
        vc.setPadding(new Insets(8));

        Label refLabel = new Label("Referenz-Audio");
        Button refChooseBtn = new Button("Datei wählen…");
        vcRefAudioLabel = new Label("(keine)");
        vcRefAudioLabel.setWrapText(true);
        refChooseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Referenz-Audio für Voice Clone");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio", "*.wav", "*.mp3", "*.flac", "*.ogg", "*.m4a"));
            File f = fc.showOpenDialog(stage);
            if (f != null) {
                vcRefAudioFile = f;
                vcRefAudioLabel.setText(f.getName());
            }
        });
        vc.getChildren().addAll(refLabel, refChooseBtn, vcRefAudioLabel);

        Label transLabel = new Label("Transkript der Referenz (voice_clone_prompt)");
        vcTranscriptArea = new TextArea();
        vcTranscriptArea.setPromptText("Exakter Text, der in der Referenz-Audio gesprochen wird…");
        vcTranscriptArea.setPrefRowCount(4);
        vcTranscriptArea.setWrapText(true);
        vc.getChildren().addAll(transLabel, vcTranscriptArea);

        Label vcDescLabel = new Label("Stimmbeschreibung (optional)");
        vcVoiceDescriptionArea = new TextArea();
        vcVoiceDescriptionArea.setPromptText("z. B. Männlich, tief, ruhig – wird mit der Stimme gespeichert.");
        vcVoiceDescriptionArea.setTooltip(new Tooltip("Wird mit der Stimme gespeichert. Das Qwen3-TTS-Base-Modell (Voice Clone) unterstützt Stil-Anweisungen derzeit nicht; zukünftige Modelle können sie nutzen."));
        vcVoiceDescriptionArea.setPrefRowCount(2);
        vcVoiceDescriptionArea.setWrapText(true);
        vc.getChildren().addAll(vcDescLabel, vcVoiceDescriptionArea);

        Label testLabel = new Label("Test-Text (wird mit geklonter Stimme gesprochen)");
        vcTestTextArea = new TextArea();
        vcTestTextArea.setText("Der Wind strich leise durch die Bäume.");
        vcTestTextArea.setPrefRowCount(2);
        vcTestTextArea.setWrapText(true);
        vc.getChildren().addAll(testLabel, vcTestTextArea);

        vcTempSlider = new Slider(0.1, 1.5, 0.3);
        vcTempSlider.setBlockIncrement(0.05);
        Label vcTempL = new Label("Temperatur: " + String.format("%.2f", vcTempSlider.getValue()));
        vcTempSlider.valueProperty().addListener((o, a, b) -> vcTempL.setText("Temperatur: " + String.format("%.2f", b.doubleValue())));
        vcTopPSlider = new Slider(0.01, 1.0, ComfyUIClient.DEFAULT_TOP_P);
        vcTopPSlider.setBlockIncrement(0.05);
        Label vcTopPL = new Label("top_p: " + String.format("%.2f", vcTopPSlider.getValue()));
        vcTopPSlider.valueProperty().addListener((o, a, b) -> vcTopPL.setText("top_p: " + String.format("%.2f", b.doubleValue())));
        vcTopKSlider = new Slider(1, 100, ComfyUIClient.DEFAULT_TOP_K);
        Label vcTopKL = new Label("top_k: " + (int) Math.round(vcTopKSlider.getValue()));
        vcTopKSlider.valueProperty().addListener((o, a, b) -> vcTopKL.setText("top_k: " + (int) Math.round(b.doubleValue())));
        vcRepPenSlider = new Slider(1.0, 2.0, ComfyUIClient.DEFAULT_REPETITION_PENALTY);
        Label vcRepL = new Label("Rep. Penalty: " + String.format("%.2f", vcRepPenSlider.getValue()));
        vcRepPenSlider.valueProperty().addListener((o, a, b) -> vcRepL.setText("Rep. Penalty: " + String.format("%.2f", b.doubleValue())));
        vcHighQualityCheck = new CheckBox("Hohe Qualität (1.7B)");
        vcHighQualityCheck.setSelected(true);
        vc.getChildren().addAll(vcTempL, vcTempSlider, vcTopPL, vcTopPSlider, vcTopKL, vcTopKSlider, vcRepL, vcRepPenSlider, vcHighQualityCheck);

        vcVoiceNameField = new TextField();
        vcVoiceNameField.setPromptText("Name der neuen Stimme");
        vc.getChildren().add(new Label("Stimmenname (zum Speichern)"));
        vc.getChildren().add(vcVoiceNameField);

        vcKlonenButton = new Button("Klonen (Test)");
        vcAlsStimmeSpeichernButton = new Button("Als Stimme speichern");
        vcProgressIndicator = new ProgressIndicator(-1);
        vcProgressIndicator.setVisible(false);
        vcProgressIndicator.setPrefSize(24, 24);
        vcStatusLabel = new Label(" ");
        vcStatusLabel.setWrapText(true);
        HBox vcBtnRow = new HBox(8);
        vcBtnRow.getChildren().addAll(vcKlonenButton, vcAlsStimmeSpeichernButton, vcProgressIndicator);
        vc.getChildren().addAll(vcBtnRow, vcStatusLabel);
        VBox.setVgrow(vcStatusLabel, Priority.ALWAYS);

        vcKlonenButton.setOnAction(e -> runVoiceCloneTest());
        vcAlsStimmeSpeichernButton.setOnAction(e -> saveVoiceCloneAsVoice());
        return vc;
    }

    private void runVoiceCloneTest() {
        if (vcRefAudioFile == null || !vcRefAudioFile.isFile()) {
            if (vcStatusLabel != null) vcStatusLabel.setText("Bitte zuerst Referenz-Audio wählen.");
            return;
        }
        String transcriptVal = vcTranscriptArea != null ? vcTranscriptArea.getText() : "";
        if (transcriptVal == null) transcriptVal = "";
        String textToSpeakVal = vcTestTextArea != null ? vcTestTextArea.getText() : "Der Wind strich leise durch die Bäume.";
        if (textToSpeakVal == null || textToSpeakVal.isBlank()) textToSpeakVal = "Test.";
        final String transcript = transcriptVal;
        final String textToSpeak = textToSpeakVal;
        if (vcProgressIndicator != null) vcProgressIndicator.setVisible(true);
        if (vcStatusLabel != null) vcStatusLabel.setText("Voice Clone wird erzeugt…");
        if (vcKlonenButton != null) vcKlonenButton.setDisable(true);
        TTS_EXECUTOR.execute(() -> {
            try {
                ComfyUIClient client = new ComfyUIClient();
                String vcDesc = (vcVoiceDescriptionArea != null && vcVoiceDescriptionArea.getText() != null && !vcVoiceDescriptionArea.getText().trim().isEmpty()) ? vcVoiceDescriptionArea.getText().trim() : null;
                Map<String, Object> history = client.generateVoiceCloneTTS(
                        vcRefAudioFile.toPath(), transcript, textToSpeak,
                        ComfyUIClient.DEFAULT_SEED,
                        vcTempSlider.getValue(), vcTopPSlider.getValue(), (int) Math.round(vcTopKSlider.getValue()), vcRepPenSlider.getValue(),
                        vcHighQualityCheck.isSelected(), ComfyUIClient.getDefaultPronunciationLexicon(), null, vcDesc);
                Path outPath = Files.createTempFile("tts_vc_test_", ".mp3");
                client.downloadAudioToFile(history, outPath);
                Platform.runLater(() -> {
                    if (vcProgressIndicator != null) vcProgressIndicator.setVisible(false);
                    if (vcKlonenButton != null) vcKlonenButton.setDisable(false);
                    if (vcStatusLabel != null) vcStatusLabel.setText("Fertig. Abspielen: " + outPath.getFileName());
                    try {
                        if (embeddedPlayer != null) embeddedPlayer.stop();
                        Media media = new Media(outPath.toUri().toString());
                        embeddedPlayer = new MediaPlayer(media);
                        embeddedPlayer.setAutoPlay(true);
                        btnPlay.setDisable(false);
                        btnPause.setDisable(false);
                        btnStop.setDisable(false);
                    } catch (Exception ex) {
                        logger.warn("Abspielen fehlgeschlagen", ex);
                    }
                });
            } catch (Exception ex) {
                logger.error("Voice Clone fehlgeschlagen", ex);
                Platform.runLater(() -> {
                    if (vcProgressIndicator != null) vcProgressIndicator.setVisible(false);
                    if (vcKlonenButton != null) vcKlonenButton.setDisable(false);
                    if (vcStatusLabel != null) vcStatusLabel.setText("Fehler: " + ex.getMessage());
                });
            }
        });
    }

    private void saveVoiceCloneAsVoice() {
        if (vcRefAudioFile == null || !vcRefAudioFile.isFile()) {
            if (vcStatusLabel != null) vcStatusLabel.setText("Bitte zuerst Referenz-Audio wählen.");
            return;
        }
        String name = vcVoiceNameField != null ? vcVoiceNameField.getText() : null;
        if (name == null || name.isBlank()) {
            if (vcStatusLabel != null) vcStatusLabel.setText("Bitte Stimmenname eingeben.");
            return;
        }
        String transcriptVal = vcTranscriptArea != null ? vcTranscriptArea.getText() : "";
        if (transcriptVal == null) transcriptVal = "";
        final String transcript = transcriptVal;
        if (vcProgressIndicator != null) vcProgressIndicator.setVisible(true);
        if (vcStatusLabel != null) vcStatusLabel.setText("Stimme wird gespeichert…");
        TTS_EXECUTOR.execute(() -> {
            try {
                String refFilename = ComfyUIClient.copyRefAudioToVoiceCloneDir(vcRefAudioFile.toPath(), name);
                String desc = (vcVoiceDescriptionArea != null && vcVoiceDescriptionArea.getText() != null) ? vcVoiceDescriptionArea.getText().trim() : "";
                ComfyUIClient.SavedVoice voice = new ComfyUIClient.SavedVoice(
                        name.trim(),
                        ComfyUIClient.DEFAULT_SEED,
                        vcTempSlider.getValue(),
                        desc,
                        vcHighQualityCheck.isSelected(),
                        true,
                        vcTopPSlider.getValue(),
                        (int) Math.round(vcTopKSlider.getValue()),
                        vcRepPenSlider.getValue(),
                        "",
                        refFilename,
                        transcript,
                        true
                );
                java.util.List<ComfyUIClient.SavedVoice> voices = new java.util.ArrayList<>(ComfyUIClient.loadSavedVoices());
                voices.add(voice);
                ComfyUIClient.saveSavedVoices(voices);
                Platform.runLater(() -> {
                    if (vcProgressIndicator != null) vcProgressIndicator.setVisible(false);
                    if (vcStatusLabel != null) vcStatusLabel.setText("Stimme \"" + name.trim() + "\" gespeichert. Im TTS-Tab auswählbar.");
                    voiceCombo.getItems().add(voice);
                    voiceCombo.getSelectionModel().select(voice);
                });
            } catch (Exception ex) {
                logger.error("Stimme speichern fehlgeschlagen", ex);
                Platform.runLater(() -> {
                    if (vcProgressIndicator != null) vcProgressIndicator.setVisible(false);
                    if (vcStatusLabel != null) vcStatusLabel.setText("Fehler: " + ex.getMessage());
                });
            }
        });
    }

    /** Tabellenzelle, die bei Fokusverlust und bei cancelEdit (z. B. Klick in andere Zelle) den Edit ins Modell übernimmt. */
    private TableCell<ComfyUITTSTestWindow.LexiconEntry, String> createLexiconTableCell(boolean isWordColumn) {
        final boolean isWord = isWordColumn;
        TableCell<ComfyUITTSTestWindow.LexiconEntry, String> cell = new TableCell<>() {
            private TextField textField;

            private void applyCommitToModel() {
                if (textField == null) return;
                int row = getIndex();
                if (row >= 0 && row < lexiconItems.size()) {
                    String v = textField.getText();
                    commitLexiconCell(row, v != null ? v : "", isWord);
                }
            }

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
                // Bevor die Zelle geschlossen wird (z. B. Klick in andere Zelle): aktuellen Text ins Modell schreiben
                applyCommitToModel();
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

    private void commitLexiconCell(int rowIndex, String newValue, boolean isWord) {
        if (rowIndex < 0 || rowIndex >= lexiconItems.size()) return;
        ComfyUITTSTestWindow.LexiconEntry entry = lexiconItems.get(rowIndex);
        if (isWord) entry.setWord(newValue != null ? newValue.trim() : "");
        else entry.setReplacement(newValue != null ? newValue : "");
    }

    private void loadLexiconIntoTable() {
        if (lexiconItems == null) return;
        lexiconItems.clear();
        Map<String, String> map = ComfyUIClient.getDefaultPronunciationLexicon();
        for (Map.Entry<String, String> e : map.entrySet()) {
            lexiconItems.add(new ComfyUITTSTestWindow.LexiconEntry(e.getKey(), e.getValue()));
        }
    }

    private Map<String, String> getLexiconFromTable() {
        Map<String, String> map = new LinkedHashMap<>();
        if (lexiconItems == null) return map;
        for (ComfyUITTSTestWindow.LexiconEntry entry : lexiconItems) {
            String w = entry.getWord() != null ? entry.getWord().trim() : "";
            if (!w.isEmpty()) {
                map.put(w, entry.getReplacement() != null ? entry.getReplacement() : "");
            }
        }
        return map;
    }

    private void saveLexiconToFile() {
        // Speichern erst nach dem nächsten Event-Pulse, damit eine offene Tabellenbearbeitung
        // (noch nicht durch Enter/Fokuswechsel committed) zuerst übernommen wird.
        Platform.runLater(() -> {
            Path path = Paths.get(ComfyUIClient.PRONUNCIATION_LEXICON_PATH).toAbsolutePath().normalize();
            try {
                Files.createDirectories(path.getParent());
                Map<String, String> map = getLexiconFromTable();
                String json = JsonUtil.toJsonPretty(map);
                Files.writeString(path, json, StandardCharsets.UTF_8);
                setStatus("Lexikon gespeichert: " + path);
            } catch (IOException e) {
                logger.warn("Lexikon speichern fehlgeschlagen", e);
                setStatus("Lexikon speichern fehlgeschlagen: " + e.getMessage());
            }
        });
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
            if (isPlayingAllSequence) {
                abortPlayAllSequence();
            } else if (embeddedPlayer != null) {
                embeddedPlayer.stop();
            }
        });

        btnErstellen.setOnAction(e -> createTtsForSelection());
        btnSpeichern.setOnAction(e -> saveCurrentAsSegment());
        btnAlleAbspielen.setOnAction(e -> {
            if (isPlayingAllSequence) {
                abortPlayAllSequence();
            } else {
                playAllSegments();
            }
        });
        btnGesamtAudiodatei.setOnAction(e -> createFullAudioFile());
        btnBatchToggle.setOnAction(e -> startBatch());
    }

    /** Zeile mit MP3-Qualitätsauswahl für Gesamt-Audiodatei. */
    private HBox buildFullAudioQualityRow() {
        fullAudioQualityCombo = new ComboBox<>();
        fullAudioQualityCombo.getItems().addAll(MP3_QUALITY_OPTIONS);
        fullAudioQualityCombo.getSelectionModel().select(1);
        fullAudioQualityCombo.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getChildren().addAll(new Label("MP3-Qualität:"), fullAudioQualityCombo);
        HBox.setHgrow(fullAudioQualityCombo, Priority.ALWAYS);
        return row;
    }

    /** Liefert den FFmpeg -q:a Wert (0–9) für die aktuelle Qualitätsauswahl (Gesamt-Audiodatei / Hörbuch). */
    private int getFullAudioMp3Quality() {
        if (fullAudioQualityCombo == null) return 4;
        int idx = fullAudioQualityCombo.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= MP3_QUALITY_VALUES.length) return 4;
        return MP3_QUALITY_VALUES[idx];
    }

    /** Liefert den FFmpeg -q:a Wert für Qualitätsindex 0–2 (statisch für AudiobookDialog). */
    public static int getMp3QualityByIndex(int index) {
        if (index < 0 || index >= MP3_QUALITY_VALUES.length) return 4;
        return MP3_QUALITY_VALUES[index];
    }

    /** Liefert die MP3-Qualitätsoptionen für Hörbuch-Dialog (gleiche wie Gesamt-Audiodatei). */
    public static String[] getMp3QualityOptions() {
        return MP3_QUALITY_OPTIONS.clone();
    }

    /** Liefert die aktuell auf dem Haupt-Tab eingetragene Stimmbeschreibung (wird beim Generieren mitgegeben). */
    private String getEffectiveVoiceDescription() {
        if (voiceDescriptionArea == null || voiceDescriptionArea.getText() == null) return "";
        return voiceDescriptionArea.getText().trim();
    }

    /** Lädt asynchron das ElevenLabs-Zeichen-Guthaben (GET /v1/user/subscription) und aktualisiert die Anzeige. */
    private void refreshElevenLabsBalance() {
        if (elevenLabsBalanceLabel == null) return;
        elevenLabsBalanceLabel.setText("Guthaben: wird geladen…");
        String apiKey = ResourceManager.getParameter("tts.elevenlabs_api_key", "");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            elevenLabsBalanceLabel.setText("Guthaben: API-Key fehlt (Parameter-Verwaltung).");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                ElevenLabsClient client = new ElevenLabsClient();
                client.setApiKey(apiKey.trim());
                ElevenLabsClient.SubscriptionInfo sub = client.getSubscription();
                int remaining = sub.getCharactersRemaining();
                int limit = sub.getCharacterLimit();
                int used = sub.getCharacterCount();
                String tier = sub.getTier();
                String tierStr = (tier != null && !tier.isEmpty()) ? " (" + tier + ")" : "";
                String text = String.format("Verbleibend: %s von %s Zeichen%s",
                        formatInt(remaining), formatInt(limit), tierStr);
                Platform.runLater(() -> {
                    if (elevenLabsBalanceLabel != null) {
                        elevenLabsBalanceLabel.setText(text);
                        elevenLabsBalanceLabel.setTooltip(null);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (elevenLabsBalanceLabel != null) {
                        elevenLabsBalanceLabel.setText("Guthaben: —");
                        elevenLabsBalanceLabel.setTooltip(null);
                    }
                });
            }
        }, TTS_EXECUTOR);
    }

    private static String formatInt(int n) {
        if (n < 1000) return String.valueOf(n);
        return String.format(java.util.Locale.ROOT, "%d.%03d", n / 1000, n % 1000);
    }

    /** Lädt ComfyUI-Stimmen aus tts-voices.json und ergänzt ElevenLabs-Stimmen von der API, die noch nicht gespeichert sind. */
    private void loadCombinedVoiceList() {
        java.util.List<ComfyUIClient.SavedVoice> combined = new java.util.ArrayList<>(ComfyUIClient.loadSavedVoices());
        java.util.Set<String> savedElevenLabsIds = new java.util.HashSet<>();
        for (ComfyUIClient.SavedVoice s : combined) {
            if (s != null && "elevenlabs".equalsIgnoreCase(s.getProvider()) && s.getElevenLabsVoiceId() != null && !s.getElevenLabsVoiceId().isEmpty()) {
                savedElevenLabsIds.add(s.getElevenLabsVoiceId());
            }
        }
        String apiKey = ResourceManager.getParameter("tts.elevenlabs_api_key", "");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            ElevenLabsClient client = new ElevenLabsClient();
            client.setApiKey(apiKey.trim());
            try {
                java.util.List<ElevenLabsClient.ElevenLabsVoice> elVoices = client.getVoices();
                for (ElevenLabsClient.ElevenLabsVoice ev : elVoices) {
                    if (savedElevenLabsIds.contains(ev.getId())) continue;
                    ComfyUIClient.SavedVoice sv = new ComfyUIClient.SavedVoice();
                    sv.setName(ev.getName() + " (ElevenLabs)");
                    sv.setProvider("elevenlabs");
                    sv.setElevenLabsVoiceId(ev.getId());
                    combined.add(sv);
                }
            } catch (Exception e) {
                logger.warn("ElevenLabs-Stimmen konnten nicht geladen werden: {}", e.getMessage());
            }
        }
        voiceCombo.getItems().setAll(combined);
    }

    /** Schreibt die ElevenLabs-Parameter der aktuell gewählten Stimme in die tts-voices.json. Ist die Stimme noch nicht in der Datei (z. B. nur aus der API-Liste), wird sie mit aktuellen Parametern hinzugefügt. */
    private void persistSelectedVoiceElevenLabsParams() {
        ComfyUIClient.SavedVoice v = voiceCombo.getSelectionModel().getSelectedItem();
        if (v == null || !"elevenlabs".equalsIgnoreCase(v.getProvider())) return;
        try {
            java.util.List<ComfyUIClient.SavedVoice> list = new java.util.ArrayList<>(ComfyUIClient.loadSavedVoices());
            boolean found = false;
            for (ComfyUIClient.SavedVoice saved : list) {
                if (saved != null && v.getName() != null && v.getName().equals(saved.getName())) {
                    saved.setElevenLabsModelId(v.getElevenLabsModelId());
                    saved.setElevenLabsStability(v.getElevenLabsStability());
                    saved.setElevenLabsSimilarityBoost(v.getElevenLabsSimilarityBoost());
                    saved.setElevenLabsSpeed(v.getElevenLabsSpeed());
                    saved.setElevenLabsUseSpeakerBoost(v.isElevenLabsUseSpeakerBoost());
                    saved.setElevenLabsStyle(v.getElevenLabsStyle());
                    found = true;
                    break;
                }
            }
            if (!found) {
                ComfyUIClient.SavedVoice copy = new ComfyUIClient.SavedVoice();
                copy.setName(v.getName());
                copy.setProvider(v.getProvider());
                copy.setElevenLabsVoiceId(v.getElevenLabsVoiceId());
                copy.setElevenLabsModelId(v.getElevenLabsModelId());
                copy.setElevenLabsStability(v.getElevenLabsStability());
                copy.setElevenLabsSimilarityBoost(v.getElevenLabsSimilarityBoost());
                copy.setElevenLabsSpeed(v.getElevenLabsSpeed());
                copy.setElevenLabsUseSpeakerBoost(v.isElevenLabsUseSpeakerBoost());
                copy.setElevenLabsStyle(v.getElevenLabsStyle());
                list.add(copy);
            }
            ComfyUIClient.saveSavedVoices(list);
        } catch (Exception e) {
            logger.warn("ElevenLabs-Parameter konnten nicht gespeichert werden: {}", e.getMessage());
        }
    }

    /** Übernimmt die Parameter der gewählten Stimme in die Slider und setzt Seed/Speaker für die nächste Erzeugung. „Hohe Qualität“ wird nicht überschrieben. Stimmbeschreibung (Haupt-Tab und bei Voice-Clone der VC-Tab) wird aus der Stimme geladen. Bei geklonter Stimme wird das Stimmbeschreibungs-Feld ausgeblendet. Bei ElevenLabs-Stimme wird das Modell-Dropdown angezeigt. */
    private void applyVoiceToParams(ComfyUIClient.SavedVoice v) {
        if (v == null) return;
        boolean isElevenLabs = "elevenlabs".equalsIgnoreCase(v.getProvider());
        if (elevenLabsModelContainer != null) {
            elevenLabsModelContainer.setVisible(isElevenLabs);
            elevenLabsModelContainer.setManaged(isElevenLabs);
        }
        if (isElevenLabs) {
            refreshElevenLabsBalance();
        }
        if (isElevenLabs && elevenLabsModelCombo != null) {
            String modelId = v.getElevenLabsModelId();
            if (modelId != null && !modelId.isBlank() && elevenLabsModelCombo.getItems().contains(modelId)) {
                elevenLabsModelCombo.getSelectionModel().select(modelId);
            } else {
                elevenLabsModelCombo.getSelectionModel().select(ElevenLabsClient.DEFAULT_MODEL_ID);
                if (elevenLabsModelCombo.getSelectionModel().getSelectedItem() == null) elevenLabsModelCombo.getSelectionModel().selectFirst();
            }
        }
        if (isElevenLabs) {
            if (elevenLabsSpeedSlider != null) elevenLabsSpeedSlider.setValue(v.getElevenLabsSpeed());
            if (elevenLabsStabilitySlider != null) elevenLabsStabilitySlider.setValue(v.getElevenLabsStability());
            if (elevenLabsSimilaritySlider != null) elevenLabsSimilaritySlider.setValue(v.getElevenLabsSimilarityBoost());
            if (elevenLabsSpeakerBoostCheck != null) elevenLabsSpeakerBoostCheck.setSelected(v.isElevenLabsUseSpeakerBoost());
        }
        if (comfyuiParamsContainer != null) {
            comfyuiParamsContainer.setVisible(!isElevenLabs);
            comfyuiParamsContainer.setManaged(!isElevenLabs);
        }
        boolean isVoiceClone = v.isVoiceClone();
        if (voiceDescriptionContainer != null) {
            boolean showVoiceDesc = !isVoiceClone && !isElevenLabs;
            voiceDescriptionContainer.setVisible(showVoiceDesc);
            voiceDescriptionContainer.setManaged(showVoiceDesc);
        }
        if (voiceDescriptionArea != null) {
            String d = v.getVoiceDescription();
            voiceDescriptionArea.setText(d != null ? d : "");
        }
        long s = v.getSeed();
        currentSeedForGeneration = (s != 0) ? s : ComfyUIClient.DEFAULT_SEED;
        currentSpeakerIdForGeneration = (v.getSpeakerId() != null && !v.getSpeakerId().isBlank()) ? v.getSpeakerId() : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER;
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
        if (v.isVoiceClone() && vcVoiceDescriptionArea != null) {
            String d = v.getVoiceDescription();
            vcVoiceDescriptionArea.setText(d != null ? d : "");
        }
        // Hohe Qualität nicht aus Stimme übernehmen – Nutzer-Einstellung bleibt erhalten
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
        codeArea.textProperty().addListener((o, oldText, newText) -> {
            if (oldText != null && newText != null && !oldText.equals(newText))
                adjustSegmentsForTextChange(oldText, newText);
            refreshHighlight();
        });

        codeArea.setOnMouseClicked(me -> {
            if (me.getButton() != MouseButton.PRIMARY && me.getButton() != MouseButton.SECONDARY) return;
            Point2D local = codeArea.screenToLocal(me.getScreenX(), me.getScreenY());
            if (local == null) return;
            int offset = codeArea.hit(local.getX(), local.getY()).getInsertionIndex();
            offset = Math.max(0, Math.min(offset, codeArea.getLength()));
            TtsSegment hit = segmentAtOffset(offset);
            if (me.getButton() == MouseButton.SECONDARY && hit == null) {
                // Rechtsklick auf noch nicht markierten Text: Satz/Absatz aus Batch-Modus auswählen
                String text = codeArea.getText();
                boolean byParagraph = batchModeCombo != null && batchModeCombo.getSelectionModel().getSelectedIndex() == 1;
                int[] range = findRangeContainingOffset(text, offset, byParagraph);
                if (range != null) {
                    codeArea.selectRange(range[0], range[1]);
                    codeArea.requestFollowCaret();
                    me.consume();
                }
                return;
            }
            if (me.getButton() == MouseButton.PRIMARY) {
                if (hit != null) {
                    loadSegmentToLeft(hit);
                    return;
                }
                // Linksklick auf nicht markierten Text: Satz/Absatz grau anzeigen (gleicher Offset wie bei Rechtsklick)
                String text = codeArea.getText();
                if (text != null) {
                    boolean byParagraph = batchModeCombo != null && batchModeCombo.getSelectionModel().getSelectedIndex() == 1;
                    int[] range = findRangeContainingOffset(text, offset, byParagraph);
                    if (range != null) {
                        hoverRangeStart = range[0];
                        hoverRangeEnd = range[1];
                        // runLater: erst nach CodeArea-Klickverarbeitung neu zeichnen, sonst wird Grau überschrieben
                        Platform.runLater(() -> {
                            if (codeArea == null) return;
                            if (codeArea.getSelection().getLength() > 0) {
                                hoverRangeStart = -1;
                                hoverRangeEnd = -1;
                            }
                            refreshHighlight();
                        });
                    }
                }
                return;
            }
            if (hit == null) return;
            if (me.getButton() == MouseButton.SECONDARY) {
                deleteSegment(hit);
            }
        });

        applyThemeToNode(codeArea, themeIndex);
        applyThemeToNode(scrollPane, themeIndex);
        VBox right = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        applyThemeToNode(right, themeIndex);
        return right;
    }

    /** Erzeugt ein kleines Farbquadrat für die Segmentfarb-Palette. Index -1 = Standard (abwechselnd). */
    private Region createColorSquare(int paletteIndex) {
        Region r = new Region();
        r.setMinSize(22, 22);
        r.setPrefSize(22, 22);
        r.setMaxSize(22, 22);
        String base = "-fx-cursor: hand; -fx-border-color: #666; -fx-border-width: 1px; -fx-border-radius: 2px; -fx-background-radius: 2px;";
        if (paletteIndex >= 0 && paletteIndex < SEGMENT_PALETTE_COLORS.length) {
            r.setStyle(base + " -fx-background-color: " + SEGMENT_PALETTE_COLORS[paletteIndex] + ";");
        } else {
            String c1 = SEGMENT_PALETTE_COLORS.length > 0 ? SEGMENT_PALETTE_COLORS[0] : "#b8ddb8";
            String c2 = SEGMENT_PALETTE_COLORS.length > 1 ? SEGMENT_PALETTE_COLORS[1] : "#a5d6a7";
            r.setStyle(base + " -fx-background-color: linear-gradient(to right, " + c1 + " 50%, " + c2 + " 50%);");
        }
        return r;
    }

    /** Wendet die gewählte Palette-Farbe auf das aktuell geladene Segment an. */
    private void applySegmentColor(int paletteIndex) {
        if (selectedSegmentForColor == null) {
            setStatus("Bitte zuerst ein Segment anklicken (links laden).");
            return;
        }
        selectedSegmentForColor.highlightColorIndex = paletteIndex;
        saveSegments();
        refreshHighlight();
        setStatus(paletteIndex < 0 ? "Segmentfarbe: Standard (abwechselnd)." : "Segmentfarbe gesetzt.");
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
        selectedSegmentForColor = seg;
        codeArea.selectRange(seg.start, seg.end);
        ComfyUIClient.SavedVoice selectedVoice = null;
        if (seg.voiceName != null && !seg.voiceName.isEmpty()) {
            for (ComfyUIClient.SavedVoice v : voiceCombo.getItems()) {
                if (v != null && seg.voiceName.equals(v.getName())) {
                    voiceCombo.getSelectionModel().select(v);
                    selectedVoice = v;
                    break;
                }
            }
        }
        if (selectedVoice == null) selectedVoice = voiceCombo.getSelectionModel().getSelectedItem();
        if (voiceDescriptionArea != null) {
            voiceDescriptionArea.setText(seg.voiceDescription != null ? seg.voiceDescription : "");
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
        if (seg.hasElevenLabsParams && selectedVoice != null && "elevenlabs".equalsIgnoreCase(selectedVoice.getProvider())) {
            final TtsSegment segRef = seg;
            final ComfyUIClient.SavedVoice voiceRef = selectedVoice;
            Platform.runLater(() -> {
                if (voiceRef != voiceCombo.getSelectionModel().getSelectedItem()) return;
                if (elevenLabsModelCombo != null && segRef.elevenLabsModelId != null && !segRef.elevenLabsModelId.isEmpty() && elevenLabsModelCombo.getItems().contains(segRef.elevenLabsModelId)) {
                    elevenLabsModelCombo.getSelectionModel().select(segRef.elevenLabsModelId);
                }
                if (elevenLabsSpeedSlider != null) elevenLabsSpeedSlider.setValue(Math.max(0.7, Math.min(1.2, segRef.elevenLabsSpeed)));
                if (elevenLabsStabilitySlider != null) elevenLabsStabilitySlider.setValue(Math.max(0, Math.min(1, segRef.elevenLabsStability)));
                if (elevenLabsSimilaritySlider != null) elevenLabsSimilaritySlider.setValue(Math.max(0, Math.min(1, segRef.elevenLabsSimilarityBoost)));
                if (elevenLabsSpeakerBoostCheck != null) elevenLabsSpeakerBoostCheck.setSelected(segRef.elevenLabsUseSpeakerBoost);
            });
        }
        // Seed und Speaker aus dem Segment übernehmen, damit neue Segmente dieselbe Stimme bekommen
        currentSeedForGeneration = (seg.seed != 0) ? seg.seed : (selectedVoice != null && selectedVoice.getSeed() != 0 ? selectedVoice.getSeed() : ComfyUIClient.DEFAULT_SEED);
        currentSpeakerIdForGeneration = (seg.speakerId != null && !seg.speakerId.isBlank()) ? seg.speakerId : (selectedVoice != null && selectedVoice.getSpeakerId() != null && !selectedVoice.getSpeakerId().isBlank() ? selectedVoice.getSpeakerId() : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER);
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
        if (selectedSegmentForColor == seg) {
            selectedSegmentForColor = null;
        }
        saveSegments();
        refreshHighlight();
        setStatus("Segment gelöscht.");
    }

    /**
     * Passt Start/Ende aller Segmente an, wenn sich der Text geändert hat (Einfügen/Löschen).
     * Ermittelt die geänderte Region aus alt/neu und verschiebt oder erweitert/verkürzt Segmentgrenzen.
     */
    private void adjustSegmentsForTextChange(String oldText, String newText) {
        if (segments.isEmpty()) return;
        int oldLen = oldText.length();
        int newLen = newText.length();
        int prefix = 0;
        while (prefix < oldLen && prefix < newLen && oldText.charAt(prefix) == newText.charAt(prefix)) prefix++;
        int suffixOld = 0;
        while (suffixOld < oldLen - prefix && suffixOld < newLen - prefix
                && oldText.charAt(oldLen - 1 - suffixOld) == newText.charAt(newLen - 1 - suffixOld)) suffixOld++;
        int changeStart = prefix;
        int oldChangeLen = oldLen - prefix - suffixOld;
        int newChangeLen = newLen - prefix - suffixOld;
        int delta = newChangeLen - oldChangeLen;
        if (delta == 0 && changeStart + oldChangeLen == oldLen - suffixOld) return;

        for (TtsSegment seg : segments) {
            int s = seg.start;
            int e = seg.end;
            int changeEnd = changeStart + oldChangeLen;
            if (e <= changeStart) continue;
            if (s >= changeEnd) {
                seg.start = s + delta;
                seg.end = e + delta;
            } else if (s < changeStart && e > changeEnd) {
                seg.end = e + delta;
            } else if (s < changeStart && e > changeStart) {
                seg.end = changeStart + newChangeLen;
            } else if (s >= changeStart && s < changeEnd && e > changeEnd) {
                seg.start = changeStart;
                seg.end = e + delta;
            } else if (s >= changeStart && e <= changeEnd) {
                seg.start = changeStart;
                seg.end = changeStart + newChangeLen;
            }
            seg.start = Math.max(0, Math.min(seg.start, newLen));
            seg.end = Math.max(seg.start, Math.min(seg.end, newLen));
        }
        segments.removeIf(seg -> {
            if (seg.start >= seg.end) {
                if (selectedSegmentForColor == seg) selectedSegmentForColor = null;
                return true;
            }
            return false;
        });
        saveSegments();
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

        List<TtsSegment> sortedSegments = new ArrayList<>(segments);
        sortedSegments.sort(Comparator.comparingInt(s -> s.start));
        Map<TtsSegment, Integer> segmentToSortedIndex = new java.util.IdentityHashMap<>();
        for (int i = 0; i < sortedSegments.size(); i++) {
            segmentToSortedIndex.put(sortedSegments.get(i), i);
        }

        boolean inHoverRange = (hoverRangeStart >= 0 && hoverRangeEnd > hoverRangeStart);
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;
        while (pos < len) {
            boolean inSel = (pos >= selStart && pos < selEnd);
            boolean inHover = inHoverRange && (pos >= hoverRangeStart && pos < hoverRangeEnd);
            String style = getSegmentStyleAt(pos, len, selStart, selEnd, segmentToSortedIndex);
            if (style == null && inSel) style = "tts-selection";
            if (style == null && inHover) style = "tts-hover-preview";  // Grau; gleiche Logik wie Segmente
            int start = pos;
            while (pos < len) {
                boolean sel = (pos >= selStart && pos < selEnd);
                boolean hov = inHoverRange && (pos >= hoverRangeStart && pos < hoverRangeEnd);
                String nextStyle = getSegmentStyleAt(pos, len, selStart, selEnd, segmentToSortedIndex);
                if (nextStyle == null && sel) nextStyle = "tts-selection";
                if (nextStyle == null && hov) nextStyle = "tts-hover-preview";
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

    /** Stil für Position in einem gespeicherten Segment: Palette-Klasse oder tts-saved-even/odd; außerhalb von Segmenten null. */
    private String getSegmentStyleAt(int pos, int len, int selStart, int selEnd, Map<TtsSegment, Integer> segmentToSortedIndex) {
        TtsSegment seg = segmentAtOffset(pos);
        if (seg == null) return null;
        Integer sortedIdx = segmentToSortedIndex.get(seg);
        int idx = sortedIdx != null ? sortedIdx : 0;
        int colorIdx = seg.highlightColorIndex;
        if (colorIdx >= 0 && colorIdx < SEGMENT_PALETTE_SIZE) {
            return "tts-palette-" + colorIdx;
        }
        return (idx % 2 == 0) ? "tts-saved-even" : "tts-saved-odd";
    }

    /** Erzeugt eine Signatur aus Text + aktuellen TTS-Parametern für Vergleich „unverändert“. Werte explizit formatiert, damit jede Änderung (z. B. Temperatur, ElevenLabs-Modell) erkannt wird. */
    private String buildTtsRequestSignature(String selectedText, ComfyUIClient.SavedVoice voice, double temp, double topP, int topK, double repPen, boolean hq) {
        String voiceName = voice != null ? voice.getName() : "";
        String modelPart = "";
        if (voice != null && "elevenlabs".equalsIgnoreCase(voice.getProvider()) && elevenLabsModelCombo != null) {
            String m = elevenLabsModelCombo.getSelectionModel().getSelectedItem();
            modelPart = "|" + (m != null ? m : "");
        }
        return selectedText + "|" + voiceName
            + "|" + String.format(java.util.Locale.ROOT, "%.4f", temp)
            + "|" + String.format(java.util.Locale.ROOT, "%.4f", topP)
            + "|" + topK
            + "|" + String.format(java.util.Locale.ROOT, "%.4f", repPen)
            + "|" + hq + modelPart;
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
        String signature = buildTtsRequestSignature(sel, voice, temp, topP, topK, repPen, hq);

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
        if (successCheckmark != null) successCheckmark.setVisible(false);
        if (successCheckmarkHideTransition != null) successCheckmarkHideTransition.stop();

        // Immer aktuellen Seed/Speaker verwenden (aus geladenem Segment oder aus Dropdown), damit die Stimme gleich bleibt
        long seed = (currentSeedForGeneration != 0) ? currentSeedForGeneration : ComfyUIClient.DEFAULT_SEED;
        String speakerId = (currentSpeakerIdForGeneration != null && !currentSpeakerIdForGeneration.isBlank()) ? currentSpeakerIdForGeneration : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER;
        String voiceDesc = getEffectiveVoiceDescription();
        ComfyUIClient.SavedVoice effectiveVoice;
        if ("elevenlabs".equalsIgnoreCase(voice.getProvider()) && voice.getElevenLabsVoiceId() != null && !voice.getElevenLabsVoiceId().isBlank()) {
            effectiveVoice = new ComfyUIClient.SavedVoice(voice.getName(), ComfyUIClient.DEFAULT_SEED, ComfyUIClient.DEFAULT_TEMPERATURE, "", true, false);
            effectiveVoice.setProvider("elevenlabs");
            effectiveVoice.setElevenLabsVoiceId(voice.getElevenLabsVoiceId());
            String modelId = (elevenLabsModelCombo != null && elevenLabsModelCombo.getSelectionModel().getSelectedItem() != null)
                ? elevenLabsModelCombo.getSelectionModel().getSelectedItem() : voice.getElevenLabsModelId();
            effectiveVoice.setElevenLabsModelId(modelId != null ? modelId : "");
            effectiveVoice.setElevenLabsStability(voice.getElevenLabsStability());
            effectiveVoice.setElevenLabsSimilarityBoost(voice.getElevenLabsSimilarityBoost());
            effectiveVoice.setElevenLabsSpeed(voice.getElevenLabsSpeed());
            effectiveVoice.setElevenLabsUseSpeakerBoost(voice.isElevenLabsUseSpeakerBoost());
            effectiveVoice.setElevenLabsStyle(voice.getElevenLabsStyle());
        } else if (voice.isVoiceClone()) {
            effectiveVoice = new ComfyUIClient.SavedVoice(
                voice.getName(), seed, temp, voiceDesc, hq, true,
                topP, topK, repPen, speakerId,
                voice.getRefAudioPath(), voice.getVoiceCloneTranscript(), true);
        } else {
            effectiveVoice = new ComfyUIClient.SavedVoice(
                voice.getName(), seed, temp, voiceDesc, hq, true,
                topP, topK, repPen, speakerId);
        }
        // Alle Parameter loggen; voiceDesc wird an ComfyUI instruct angehängt („Stimme: …“)
        logger.info("TTS Erstellen – voiceDescription=\"{}\" | voiceName={} | effectiveSeed={} | temperature={} | topP={} | topK={} | repetitionPenalty={} | highQuality={} | textLength={}",
            voiceDesc, voice.getName(), seed, temp, topP, topK, repPen, hq, sel != null ? sel.length() : 0);
        ttsRequestId++;
        final int myRequestId = ttsRequestId;
        progressIndicator.setVisible(true);
        btnErstellen.setDisable(true);
        setStatus("Erzeuge Sprachausgabe…");
        CompletableFuture.runAsync(() -> {
            try {
                java.util.Map<String, String> lexicon = getLexiconFromTable();
                Path path = Files.createTempFile("manuskript-tts-", ".mp3");
                TtsBackend.generateTtsToFile(sel, effectiveVoice, lexicon, path, true, null);
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
                    if (successCheckmark != null) {
                        successCheckmark.setVisible(true);
                        if (successCheckmarkHideTransition != null) {
                            successCheckmarkHideTransition.playFromStart();
                        }
                    }
                    btnErstellen.setDisable(false);
                    setStatus("Erstellt. Sie können speichern oder erneut erstellen.");
                    if ("elevenlabs".equalsIgnoreCase(effectiveVoice.getProvider())) refreshElevenLabsBalance();
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

    /**
     * Teilt den gesamten Text in Bereiche (Satz oder Absatz).
     * Absatz-Grenzen: doppelter Zeilenumbruch, Zeile nur "---", oder Zeilenumbruch gefolgt von öffnendem Anführungszeichen (neue wörtliche Rede).
     * @return Liste von [start, end] (inklusive end = Zeichen nach dem letzten Zeichen des Satzes/Absatzes)
     */
    private List<int[]> splitTextIntoRanges(String fullText, boolean byParagraph) {
        List<int[]> out = new ArrayList<>();
        if (fullText == null || fullText.isEmpty()) return out;
        if (byParagraph) {
            // Grenzen: Leerzeile, Zeile nur ---, oder \n vor neuem Anführungszeichen; explizit: »\n« (Rede Ende, neue Rede Anfang)
            Pattern boundary = Pattern.compile("(\\n\\s*\\n|\\n-{3,}\\s*\\n|\\n(?=\\s*[\u201E\u2018\u00AB\u201C\"])|(?<=\u00BB)\\s*\\n\\s*(?=\u00AB))");
            Matcher m = boundary.matcher(fullText);
            int segmentStart = 0;
            while (m.find()) {
                int end = m.start();
                String segment = fullText.substring(segmentStart, end).trim();
                if (end > segmentStart && !segment.isEmpty() && !segment.matches("-{3,}\\s*"))
                    out.add(new int[] { segmentStart, end });
                segmentStart = m.end();
            }
            String lastSegment = fullText.substring(segmentStart).trim();
            if (segmentStart < fullText.length() && !lastSegment.isEmpty() && !lastSegment.matches("-{3,}\\s*"))
                out.add(new int[] { segmentStart, fullText.length() });
            if (out.isEmpty() && fullText.trim().length() > 0)
                out.add(new int[] { 0, fullText.length() });
        } else {
            out.addAll(splitIntoSentencesRespectingQuotes(fullText));
        }
        return out;
    }

    /** Überspringt Leerzeichen, Tabs und Kommas ab Position from (z. B. nach schließendem Anführungszeichen: ", sagte sie." → nächster Satz beginnt bei "s"). */
    private static int skipSpacesAndComma(String fullText, int len, int from) {
        while (from < len) {
            char c = fullText.charAt(from);
            if (c == ' ' || c == '\t' || c == ',') from++;
            else break;
        }
        return from;
    }

    /** Verschiedene Anführungszeichen (öffnend/schließend); direkte Rede wird als ein Satz behandelt. */
    private static final String OPENING_QUOTES = "\u201E\u2018";            // „ '
    private static final String CLOSING_QUOTES = "\u201D\u2019";             // " '
    /** Guillemets « » und " – je nach Kontext öffnend/schließend (z. B. »öffnet« Rede, «schließt» sie). */
    private static final String AMBIGUOUS_QUOTES = "\u00AB\u00BB\u201C";    // « » "
    private static final char ASCII_QUOTE = '"'; // U+0022 – Toggle

    /**
     * Teilt Text in Sätze, wobei . ! ? innerhalb von Anführungszeichen (direkte Rede) keine Satzgrenze sind.
     * Berücksichtigt deutsche („ "), französische (« »), einfache (' ') und ASCII-Anführungszeichen (").
     */
    private List<int[]> splitIntoSentencesRespectingQuotes(String fullText) {
        List<int[]> ranges = new ArrayList<>();
        if (fullText == null || fullText.isEmpty()) return ranges;
        int len = fullText.length();
        int sentenceStart = 0;
        int quoteLevel = 0;
        int i = 0;
        while (i < len) {
            char c = fullText.charAt(i);
            if (OPENING_QUOTES.indexOf(c) >= 0) {
                quoteLevel++;
                i++;
                continue;
            }
            if (CLOSING_QUOTES.indexOf(c) >= 0) {
                if (quoteLevel > 0) {
                    quoteLevel--;
                    if (quoteLevel == 0) {
                        int end = i + 1;
                        if (sentenceStart < end && fullText.substring(sentenceStart, end).trim().length() > 0)
                            ranges.add(new int[] { sentenceStart, end });
                        sentenceStart = end;
                    }
                }
                i++;
                if (quoteLevel == 0) {
                    sentenceStart = skipSpacesAndComma(fullText, len, sentenceStart);
                }
                continue;
            }
            if (AMBIGUOUS_QUOTES.indexOf(c) >= 0) {
                if (quoteLevel > 0) {
                    quoteLevel--;
                    if (quoteLevel == 0) {
                        int end = i + 1;
                        if (sentenceStart < end && fullText.substring(sentenceStart, end).trim().length() > 0)
                            ranges.add(new int[] { sentenceStart, end });
                        sentenceStart = end;
                    }
                } else {
                    quoteLevel = 1;
                }
                i++;
                if (quoteLevel == 0) {
                    sentenceStart = skipSpacesAndComma(fullText, len, sentenceStart);
                }
                continue;
            }
            if (c == ASCII_QUOTE) {
                if (quoteLevel > 0) {
                    quoteLevel--;
                    if (quoteLevel == 0) {
                        int end = i + 1;
                        if (sentenceStart < end && fullText.substring(sentenceStart, end).trim().length() > 0)
                            ranges.add(new int[] { sentenceStart, end });
                        sentenceStart = end;
                    }
                } else {
                    quoteLevel = 1;
                }
                i++;
                if (quoteLevel == 0) {
                    sentenceStart = skipSpacesAndComma(fullText, len, sentenceStart);
                }
                continue;
            }
            if (quoteLevel == 0 && (c == '.' || c == '!' || c == '?')) {
                int end = i + 1;
                while (end < len && (fullText.charAt(end) == ' ' || fullText.charAt(end) == '\t')) end++;
                if (sentenceStart < end && fullText.substring(sentenceStart, end).trim().length() > 0)
                    ranges.add(new int[] { sentenceStart, end });
                sentenceStart = end;
                i = end;
                continue;
            }
            i++;
        }
        if (sentenceStart < len && fullText.substring(sentenceStart).trim().length() > 0)
            ranges.add(new int[] { sentenceStart, len });
        return ranges;
    }

    /**
     * Findet den Satz- oder Absatz-Bereich, der die gegebene Offset-Position enthält.
     * @return int[] { start, end } oder null, wenn offset außerhalb des Textes oder in keiner Einheit
     */
    private int[] findRangeContainingOffset(String fullText, int offset, boolean byParagraph) {
        if (fullText == null || offset < 0 || offset > fullText.length()) return null;
        List<int[]> ranges = splitTextIntoRanges(fullText, byParagraph);
        for (int[] r : ranges) {
            if (offset >= r[0] && offset < r[1]) return r;
        }
        return null;
    }

    /** Filtert Bereiche heraus, die mit einem bestehenden Segment überlappen (bereits markiert). */
    private List<int[]> getUnmarkedRanges(List<int[]> ranges) {
        List<int[]> unmarked = new ArrayList<>();
        for (int[] r : ranges) {
            int start = r[0], end = r[1];
            boolean overlaps = false;
            for (TtsSegment s : segments) {
                if (s.start < end && s.end > start) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) unmarked.add(r);
        }
        return unmarked;
    }

    /** Kopiert generierte Audio nach block_XXX.mp3 und fügt das Segment hinzu (Blocknummer + Copy im FX-Thread, damit keine Doppelvergabe). Temp-Datei wird nach Copy gelöscht. */
    private void saveBatchSegment(int start, int end, Path generatedAudioPath, String voiceName, double temp, double topP, int topK, double repPen, boolean hq, ComfyUIClient.SavedVoice voice) {
        Path tempPath = generatedAudioPath;
        Platform.runLater(() -> {
            try {
                if (audioDirPath != null) Files.createDirectories(audioDirPath);
                int n = getNextFreeBlockNumber();
                String baseName = "block_" + String.format("%03d", n) + ".mp3";
                Path target = audioDirPath != null ? audioDirPath.resolve(baseName) : tempPath;
                if (audioDirPath != null) Files.copy(tempPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try { Files.deleteIfExists(tempPath); } catch (IOException ignored) { }
                String audioPathStr = target.toAbsolutePath().toString();
                String voiceDesc = getEffectiveVoiceDescription();
                TtsSegment seg = new TtsSegment(start, end, audioPathStr, voiceName, temp, topP, topK, repPen, hq, voiceDesc);
                seg.seed = currentSeedForGeneration;
                seg.speakerId = currentSpeakerIdForGeneration != null ? currentSpeakerIdForGeneration : "";
                if (voice != null && "elevenlabs".equalsIgnoreCase(voice.getProvider())) {
                    seg.hasElevenLabsParams = true;
                    seg.elevenLabsModelId = voice.getElevenLabsModelId() != null ? voice.getElevenLabsModelId() : "";
                    seg.elevenLabsStability = voice.getElevenLabsStability();
                    seg.elevenLabsSimilarityBoost = voice.getElevenLabsSimilarityBoost();
                    seg.elevenLabsSpeed = voice.getElevenLabsSpeed();
                    seg.elevenLabsUseSpeakerBoost = voice.isElevenLabsUseSpeakerBoost();
                    seg.elevenLabsStyle = voice.getElevenLabsStyle();
                }
                segments.add(seg);
                saveSegments();
                refreshHighlight();
            } catch (IOException e) {
                logger.warn("Batch-Segment speichern fehlgeschlagen: {}", e.getMessage());
            }
        });
    }

    private void startBatch() {
        ComfyUIClient.SavedVoice voice = voiceCombo.getSelectionModel().getSelectedItem();
        if (voice == null) {
            setStatus("Bitte eine Stimme wählen.");
            return;
        }
        String fullText = codeArea.getText();
        if (fullText == null || fullText.isEmpty()) {
            setStatus("Kein Text vorhanden.");
            return;
        }
        boolean byParagraph = batchModeCombo.getSelectionModel().getSelectedIndex() == 1;
        List<int[]> allRanges = splitTextIntoRanges(fullText, byParagraph);
        List<int[]> unmarked = getUnmarkedRanges(allRanges);
        if (unmarked.isEmpty()) {
            setStatus("Keine unmarkierten " + (byParagraph ? "Absätze" : "Sätze") + " mehr.");
            return;
        }
        batchCancelled = false;
        btnBatchToggle.setText("Batch beenden");
        btnBatchToggle.setOnAction(ev -> batchCancelled = true);
        batchModeCombo.setDisable(true);
        setStatus("Batch: " + unmarked.size() + " " + (byParagraph ? "Absatz/Absätze" : "Satz/Sätze") + " werden nacheinander gerendert. „Batch beenden“ zum Unterbrechen.");
        TTS_EXECUTOR.execute(() -> runBatch(unmarked, fullText, voice, byParagraph));
    }

    private void runBatch(List<int[]> unmarked, String fullText, ComfyUIClient.SavedVoice voice, boolean byParagraph) {
        final int[] doneRef = new int[] { 0 };
        int total = unmarked.size();
        double temp = temperatureSlider.getValue();
        double topP = topPSlider.getValue();
        int topK = (int) Math.round(topKSlider.getValue());
        double repPen = Math.max(1.0, Math.min(2.0, repetitionPenaltySlider.getValue()));
        boolean hq = highQualityCheck.isSelected();
        long seed = (currentSeedForGeneration != 0) ? currentSeedForGeneration : ComfyUIClient.DEFAULT_SEED;
        String speakerId = (currentSpeakerIdForGeneration != null && !currentSpeakerIdForGeneration.isBlank()) ? currentSpeakerIdForGeneration : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER;
        String voiceDesc = getEffectiveVoiceDescription();
        ComfyUIClient.SavedVoice effectiveVoice;
        if ("elevenlabs".equalsIgnoreCase(voice.getProvider()) && voice.getElevenLabsVoiceId() != null && !voice.getElevenLabsVoiceId().isBlank()) {
            effectiveVoice = new ComfyUIClient.SavedVoice(voice.getName(), ComfyUIClient.DEFAULT_SEED, ComfyUIClient.DEFAULT_TEMPERATURE, "", true, false);
            effectiveVoice.setProvider("elevenlabs");
            effectiveVoice.setElevenLabsVoiceId(voice.getElevenLabsVoiceId());
            String modelId = (elevenLabsModelCombo != null && elevenLabsModelCombo.getSelectionModel().getSelectedItem() != null)
                ? elevenLabsModelCombo.getSelectionModel().getSelectedItem() : voice.getElevenLabsModelId();
            effectiveVoice.setElevenLabsModelId(modelId != null ? modelId : "");
            effectiveVoice.setElevenLabsStability(voice.getElevenLabsStability());
            effectiveVoice.setElevenLabsSimilarityBoost(voice.getElevenLabsSimilarityBoost());
            effectiveVoice.setElevenLabsSpeed(voice.getElevenLabsSpeed());
            effectiveVoice.setElevenLabsUseSpeakerBoost(voice.isElevenLabsUseSpeakerBoost());
            effectiveVoice.setElevenLabsStyle(voice.getElevenLabsStyle());
        } else if (voice.isVoiceClone()) {
            effectiveVoice = new ComfyUIClient.SavedVoice(voice.getName(), seed, temp, voiceDesc, hq, true, topP, topK, repPen, speakerId, voice.getRefAudioPath(), voice.getVoiceCloneTranscript(), true);
        } else {
            effectiveVoice = new ComfyUIClient.SavedVoice(voice.getName(), seed, temp, voiceDesc, hq, true, topP, topK, repPen, speakerId);
        }
        String voiceName = voice.getName();
        java.util.Map<String, String> lexicon = getLexiconFromTable();
        for (int[] r : unmarked) {
            if (batchCancelled) break;
            int start = r[0], end = r[1];
            String text = fullText.substring(start, end).trim();
            if (text.isEmpty()) continue;
            try {
                Path tempPath = Files.createTempFile("manuskript-batch-", ".mp3");
                TtsBackend.generateTtsToFile(text, effectiveVoice, lexicon, tempPath, true, null);
                saveBatchSegment(start, end, tempPath, voiceName, temp, topP, topK, repPen, hq, effectiveVoice);
                doneRef[0]++;
                final int d = doneRef[0];
                Platform.runLater(() -> setStatus("Batch: " + d + "/" + total + " " + (byParagraph ? "Absätze" : "Sätze") + " erledigt."));
            } catch (Exception e) {
                logger.warn("Batch-Einheit fehlgeschlagen ({}–{}): {}", start, end, e.getMessage());
                final int nextNum = doneRef[0] + 1;
                Platform.runLater(() -> setStatus("Batch: Fehler bei Einheit " + nextNum + " – " + e.getMessage() + ". Weitere werden trotzdem versucht."));
            }
        }
        final int finalDone = doneRef[0];
        final boolean wasElevenLabs = "elevenlabs".equalsIgnoreCase(voice.getProvider());
        Platform.runLater(() -> {
            btnBatchToggle.setText("Batch starten");
            btnBatchToggle.setOnAction(ev -> startBatch());
            batchModeCombo.setDisable(false);
            setStatus(batchCancelled ? "Batch unterbrochen. " + finalDone + "/" + total + " erledigt." : "Batch fertig. " + finalDone + "/" + total + " " + (byParagraph ? "Absätze" : "Sätze") + " gerendert.");
            if (wasElevenLabs) refreshElevenLabsBalance();
        });
    }

    /** Ermittelt die nächste freie block_XXX-Nummer (aus Segmenten + Dateien im Audio-Ordner), damit keine bestehende Datei überschrieben wird. */
    private int getNextFreeBlockNumber() {
        Set<Integer> used = new HashSet<>();
        for (TtsSegment s : segments) {
            if (s.audioPath != null && !s.audioPath.isEmpty()) {
                String name = Paths.get(s.audioPath).getFileName().toString();
                if (name != null && name.startsWith("block_") && name.endsWith(".mp3") && name.length() > 10) {
                    try {
                        int num = Integer.parseInt(name.substring(6, name.length() - 4));
                        if (num > 0) used.add(num);
                    } catch (NumberFormatException ignored) { /* ignore */ }
                }
            }
        }
        if (audioDirPath != null && Files.isDirectory(audioDirPath)) {
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(audioDirPath, "block_*.mp3")) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    if (name != null && name.length() > 10) {
                        try {
                            int num = Integer.parseInt(name.substring(6, name.length() - 4));
                            if (num > 0) used.add(num);
                        } catch (NumberFormatException ignored) { /* ignore */ }
                    }
                }
            } catch (IOException ignored) { /* ignore */ }
        }
        int n = 1;
        while (used.contains(n)) n++;
        return n;
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
        String currentSig = buildTtsRequestSignature(sel != null ? sel : "", voice, temp, topPSlider.getValue(), (int) Math.round(topKSlider.getValue()), Math.max(1.0, Math.min(2.0, repetitionPenaltySlider.getValue())), highQualityCheck.isSelected());
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
            // Überlappende Segmente zuerst entfernen (und deren Audiodateien löschen)
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
            // Nächste freie Block-Nummer (niemals bestehende Datei überschreiben)
            int n = getNextFreeBlockNumber();
            String baseName = "block_" + String.format("%03d", n) + ".mp3";
            Path target = audioDirPath != null ? audioDirPath.resolve(baseName) : sourcePath;
            if (audioDirPath != null) Files.copy(sourcePath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String audioPathStr = target.toAbsolutePath().toString();
            String voiceDesc = getEffectiveVoiceDescription();
            TtsSegment seg = new TtsSegment(start, end, audioPathStr, voiceName, temp, topP, topK, repPen, hq, voiceDesc);
            seg.seed = currentSeedForGeneration;
            seg.speakerId = currentSpeakerIdForGeneration != null ? currentSpeakerIdForGeneration : "";
            if (voice != null && "elevenlabs".equalsIgnoreCase(voice.getProvider())) {
                seg.hasElevenLabsParams = true;
                seg.elevenLabsModelId = voice.getElevenLabsModelId() != null ? voice.getElevenLabsModelId() : "";
                seg.elevenLabsStability = voice.getElevenLabsStability();
                seg.elevenLabsSimilarityBoost = voice.getElevenLabsSimilarityBoost();
                seg.elevenLabsSpeed = voice.getElevenLabsSpeed();
                seg.elevenLabsUseSpeakerBoost = voice.isElevenLabsUseSpeakerBoost();
                seg.elevenLabsStyle = voice.getElevenLabsStyle();
            }
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
        int caretOrSelStart = codeArea.getSelection().getStart();
        TtsSegment fromSegment = segmentAtOffset(caretOrSelStart);
        if (fromSegment != null) {
            for (int i = 0; i < segmentsPlayOrder.size(); i++) {
                if (segmentsPlayOrder.get(i) == fromSegment) {
                    playingSegmentIndex = i;
                    break;
                }
            }
        }
        if (btnAlleAbspielen != null) {
            btnAlleAbspielen.setText("Abbrechen");
            btnAlleAbspielen.setOnAction(ev -> abortPlayAllSequence());
        }
        playCurrentSegmentInEmbeddedPlayer();
    }

    /** Bricht „Alle abspielen“ ab und setzt den Button zurück auf „Alle abspielen“. */
    private void abortPlayAllSequence() {
        if (!isPlayingAllSequence) return;
        isPlayingAllSequence = false;
        playingSegmentIndex = -1;
        segmentsPlayOrder = null;
        if (playAllAdvanceTransition != null) {
            playAllAdvanceTransition.stop();
            playAllAdvanceTransition = null;
        }
        try {
            if (embeddedPlayer != null) embeddedPlayer.stop();
        } catch (Exception e) {
            logger.trace("Player stop beim Abbrechen", e);
        }
        if (btnAlleAbspielen != null) {
            btnAlleAbspielen.setText("Ab hier abspielen");
            btnAlleAbspielen.setOnAction(e -> playAllSegments());
        }
        refreshHighlight();
        setStatus("Wiedergabe abgebrochen.");
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
            btnAlleAbspielen.setText("Ab hier abspielen");
            btnAlleAbspielen.setOnAction(e -> playAllSegments());
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
        setStatus("Füge Audiodateien zusammen (mit " + (int) FULL_AUDIO_PAUSE_SECONDS + " s Pause zwischen Segmenten)…");
        CompletableFuture.runAsync(() -> {
            try {
                List<TtsSegment> byTextOrder = new ArrayList<>(segments);
                byTextOrder.sort(Comparator.comparingInt(s -> s.start));
                List<Path> files = new ArrayList<>();
                for (TtsSegment s : byTextOrder) {
                    Path p = Paths.get(s.audioPath);
                    if (Files.isRegularFile(p)) files.add(p);
                }
                if (files.isEmpty()) {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        setStatus("Keine gültigen Audiodateien gefunden.");
                    });
                    return;
                }
                if (files.size() == 1) {
                    Files.copy(files.get(0), out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        setStatus("Gespeichert: " + out.getFileName());
                    });
                } else {
                    String ffmpegError = runFfmpegConcatWithPause(files, out);
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        if (ffmpegError == null) {
                            setStatus("Gespeichert: " + out.getFileName() + " (mit " + (int) FULL_AUDIO_PAUSE_SECONDS + " s Pause zwischen Segmenten)");
                        } else {
                            setStatus("Gesamt-Audiodatei fehlgeschlagen.");
                            String detail = ffmpegError.length() > 400 ? ffmpegError.substring(0, 400) + "…" : ffmpegError;
                            CustomAlert errAlert = DialogFactory.createErrorAlert("Gesamt-Audiodatei",
                                "FFmpeg wird für mehrere Segmente benötigt",
                                "Zum Zusammenfügen mehrerer Audiodateien wird FFmpeg benötigt. Bitte FFmpeg installieren oder ins Projektverzeichnis \"ffmpeg\" legen (ffmpeg.exe unter Windows).\n\n" + detail,
                                stage);
                            errAlert.applyTheme(themeIndex);
                            errAlert.showAndWait();
                        }
                    });
                }
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

    /**
     * Liefert den Pfad zu FFmpeg: zuerst aus dem Projektverzeichnis {@value #FFMPEG_DIR} (analog zu Pandoc),
     * sonst null (dann wird „ffmpeg“ aus dem System-PATH verwendet).
     */
    private static String getFfmpegExePath() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        String exeName = isWindows ? "ffmpeg.exe" : "ffmpeg";
        java.io.File dir = new java.io.File(FFMPEG_DIR);
        java.io.File exe = new java.io.File(dir, exeName);
        if (exe.isFile()) return exe.getAbsolutePath();
        java.io.File binExe = new java.io.File(dir, "bin" + java.io.File.separator + exeName);
        if (binExe.isFile()) return binExe.getAbsolutePath();
        return null;
    }

    /** Formatiert eine Kommandozeile für CMD: ein Argument pro Stelle, Pfade mit Leerzeichen in Anführungszeichen. Zum Kopieren in cmd. */
    private static String formatCommandForCmd(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(' ');
            String a = args.get(i);
            if (a.isEmpty() || a.contains(" ") || a.contains("\t") || a.contains("\"")) {
                sb.append('"').append(a.replace("\\", "\\\\").replace("\"", "\"\"")).append('"');
            } else {
                sb.append(a);
            }
        }
        return sb.toString();
    }

    /**
     * Fügt mehrere MP3-Dateien per FFmpeg mit Pause zwischen je zwei Dateien zusammen (Concat-Demuxer).
     * Öffentlich für Hörbuch-Erstellung. Einzelne Datei: wird kopiert.
     *
     * @param audioFiles Liste der MP3-Pfade in Reihenfolge
     * @param outputPath Ausgabedatei
     * @param pauseSeconds Pause in Sekunden zwischen den Dateien
     * @param mp3Quality FFmpeg -q:a (0 = beste Qualität, 9 = geringste; typisch 2–6)
     * @param logCommandsToConsole true = FFmpeg-Befehle auf System.out (zum Kopieren in cmd)
     * @return null bei Erfolg, sonst Fehlermeldung
     */
    public static String concatMp3FilesWithPause(List<Path> audioFiles, Path outputPath, double pauseSeconds, int mp3Quality, boolean logCommandsToConsole) {
        int n = audioFiles.size();
        if (n == 0) return "Keine Dateien";
        if (n == 1) {
            try {
                Files.copy(audioFiles.get(0), outputPath, StandardCopyOption.REPLACE_EXISTING);
                return null;
            } catch (IOException e) {
                return e.getMessage();
            }
        }
        String ffmpegExe = getFfmpegExePath();
        if (ffmpegExe == null) ffmpegExe = "ffmpeg";
        Path listPath = null;
        Path silencePath = null;
        try {
            Path tempDir = Files.createTempDirectory("manuskript-ffmpeg");
            silencePath = tempDir.resolve("silence.mp3");
            listPath = tempDir.resolve("concatlist.txt");
            int q = Math.max(0, Math.min(9, mp3Quality));
            List<String> silenceCmd = List.of(
                ffmpegExe, "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "anullsrc=r=24000:cl=mono",
                "-t", String.valueOf(pauseSeconds),
                "-c:a", "libmp3lame", "-q:a", String.valueOf(q),
                silencePath.toAbsolutePath().toString()
            );
            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (Stille), zum Einfügen in cmd ---");
                System.out.println(formatCommandForCmd(silenceCmd));
            }
            ProcessBuilder pbSilence = new ProcessBuilder(silenceCmd);
            pbSilence.redirectError(java.io.File.createTempFile("ffmpeg-silence-", ".log"));
            Process procSilence = pbSilence.start();
            if (procSilence.waitFor() != 0) return "Stille-Datei konnte nicht erzeugt werden";
            if (!Files.isRegularFile(silencePath)) return "Stille-Datei fehlt";
            String silenceAbs = silencePath.toAbsolutePath().toString().replace("\\", "/");
            if (silenceAbs.contains("'")) silenceAbs = silenceAbs.replace("'", "'\\''");
            StringBuilder listContent = new StringBuilder();
            for (int i = 0; i < audioFiles.size(); i++) {
                String segAbs = audioFiles.get(i).toAbsolutePath().toString().replace("\\", "/");
                if (segAbs.contains("'")) segAbs = segAbs.replace("'", "'\\''");
                listContent.append("file '").append(segAbs).append("'\n");
                if (i < audioFiles.size() - 1) listContent.append("file '").append(silenceAbs).append("'\n");
            }
            Files.writeString(listPath, listContent.toString(), StandardCharsets.UTF_8);
            List<String> concatCmd = List.of(
                ffmpegExe, "-y", "-loglevel", "warning",
                "-f", "concat", "-safe", "0", "-i", listPath.toAbsolutePath().toString(),
                "-c:a", "libmp3lame", "-q:a", String.valueOf(q),
                outputPath.toAbsolutePath().toString()
            );
            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (Concat), zum Einfügen in cmd ---");
                System.out.println(formatCommandForCmd(concatCmd));
            }
            ProcessBuilder pbConcat = new ProcessBuilder(concatCmd);
            java.io.File errLog = java.io.File.createTempFile("ffmpeg-concat-", ".log");
            try {
                pbConcat.redirectError(errLog);
                Process proc = pbConcat.start();
                int exit = proc.waitFor();
                String err = Files.readString(errLog.toPath(), StandardCharsets.UTF_8).trim();
                if (exit != 0) return err.isEmpty() ? "FFmpeg beendet mit Code " + exit : err;
                return null;
            } finally {
                try { errLog.delete(); } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            return "FFmpeg: " + e.getMessage();
        } finally {
            try {
                if (listPath != null) Files.deleteIfExists(listPath);
                if (silencePath != null) Files.deleteIfExists(silencePath);
                if (silencePath != null && silencePath.getParent() != null) Files.deleteIfExists(silencePath.getParent());
            } catch (IOException ignored) { }
        }
    }

    /** Wie {@link #concatMp3FilesWithPause(List, Path, double, int, boolean)} mit FULL_AUDIO_PAUSE_SECONDS und aktueller Qualitätsauswahl. */
    private String runFfmpegConcatWithPause(List<Path> files, Path out) {
        return concatMp3FilesWithPause(files, out, FULL_AUDIO_PAUSE_SECONDS, getFullAudioMp3Quality(), true);
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
                removeOrphanedSegments();
            }
        } catch (Exception e) {
            logger.warn("Segmente konnten nicht geladen werden: {}", segmentsPath, e);
        }
    }

    /**
     * Entfernt Segmente, die außerhalb des aktuellen Textes liegen oder leere Bereiche haben („Zombies“),
     * und löscht deren Audiodateien. Wird nach dem Laden und kann bei Bedarf manuell aufgerufen werden.
     */
    private void removeOrphanedSegments() {
        if (codeArea == null) return;
        String text = codeArea.getText();
        int textLen = text != null ? text.length() : 0;
        List<TtsSegment> toRemove = new ArrayList<>();
        for (TtsSegment seg : segments) {
            if (seg.start >= seg.end || seg.start >= textLen || seg.end > textLen || seg.start < 0) {
                toRemove.add(seg);
            }
        }
        for (TtsSegment seg : toRemove) {
            if (selectedSegmentForColor == seg) selectedSegmentForColor = null;
            if (seg.audioPath != null && !seg.audioPath.isEmpty()) {
                try {
                    Path p = Paths.get(seg.audioPath);
                    if (Files.isRegularFile(p)) Files.delete(p);
                } catch (IOException e) {
                    logger.warn("Audiodatei konnte nicht gelöscht werden: {}", seg.audioPath, e);
                }
            }
            segments.remove(seg);
        }
        if (!toRemove.isEmpty()) {
            saveSegments();
            refreshHighlight();
            setStatus(toRemove.size() + " ungültige Segment(e) entfernt (Textbereich passte nicht mehr).");
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

    /**
     * Beim ersten Öffnen: Hash des Original-MD speichern.
     * Bei erneutem Öffnen: wenn sich der Originaltext geändert hat, Warnung anzeigen;
     * „Änderungen anerkennen“ speichert den neuen Hash.
     */
    private void checkAndUpdateOriginalHash(String currentOriginalContent) {
        if (originalHashPath == null || currentOriginalContent == null) return;
        try {
            String currentHash = computeSha256(currentOriginalContent);
            if (!Files.isRegularFile(originalHashPath)) {
                Files.createDirectories(originalHashPath.getParent());
                Files.writeString(originalHashPath, currentHash, StandardCharsets.UTF_8);
                return;
            }
            String savedHash = Files.readString(originalHashPath, StandardCharsets.UTF_8).trim();
            if (currentHash.equals(savedHash)) return;
            // Originaltext wurde geändert – Warnung mit Option „Änderungen anerkennen“
            CustomAlert alert = DialogFactory.createConfirmationAlert(
                "Kapiteltext geändert",
                "Der Originaltext dieses Kapitels wurde seit der letzten Nutzung geändert.",
                "Möchten Sie die Änderungen anerkennen? Dann wird der aktuelle Stand als Referenz gespeichert und die Warnung verschwindet beim nächsten Öffnen.",
                stage
            );
            alert.applyTheme(themeIndex);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                Files.writeString(originalHashPath, currentHash, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Hash-Prüfung für TTS-Original konnte nicht durchgeführt werden: {}", originalHashPath, e);
        }
    }

    private static String computeSha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 nicht verfügbar", e);
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
