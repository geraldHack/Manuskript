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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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

    /** Bitrate-Optionen für Hörbuch-Export (kbps). */
    private static final String[] BITRATE_OPTIONS = { "128 kbps", "192 kbps", "256 kbps", "320 kbps" };
    /** Tatsächliche kbps-Werte zu BITRATE_OPTIONS. */
    private static final int[] BITRATE_VALUES = { 128, 192, 256, 320 };
    /** Default-Index in BITRATE_OPTIONS (320 kbps). */
    private static final int DEFAULT_BITRATE_INDEX = 3;

    /** Stille am Anfang jeder Kapitel-MP3 in Sekunden (wird nach Trimming eingefügt). */
    private static final double LEAD_SILENCE_SECONDS = 0.8;
    /** Stille am Ende jeder Kapitel-MP3 in Sekunden (wird nach Trimming eingefügt). */
    private static final double TRAIL_SILENCE_SECONDS = 1.5;
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

    /**
     * System-Prompt fuer ElevenLabs v3 Audio-Tagging via Ollama Generate-API.
     * Wird ueber das "system"-Feld der /api/generate gesendet, das den Modelfile-SYSTEM
     * garantiert ueberschreibt – auch bei trainierten Modellen.
     */
    private static final String V3_AUDIO_TAG_SYSTEM = """
            Du bist ein Audio-Tagging-Werkzeug. Deine einzige Aufgabe ist es, Text mit Audio-Tags fuer ElevenLabs v3 zu versehen.
            Du bist KEIN Schreibassistent. Gib KEIN Feedback, KEINE Kommentare, KEINE Erklaerungen.
            Gib AUSSCHLIESSLICH den getaggten Text zurueck – sonst nichts.""";

    /**
     * User-Prompt (Anweisungen + Platzhalter) fuer v3 Audio-Tagging.
     * Der eigentliche Text wird direkt angehaengt.
     */
    private static final String V3_AUDIO_TAG_PROMPT = """
            Fuege Audio-Tags in eckigen Klammern in den folgenden Text ein.
            Diese Tags steuern die Sprachausgabe in ElevenLabs v3.

            Regeln:
            - Veraendere KEINE Woerter des Originaltexts. Fuege NUR Audio-Tags in eckigen Klammern ein.
            - Platziere Emotions-/Delivery-Tags (z.B. [angry], [whisper], [sad]) VOR dem Textsegment.
            - Platziere non-verbale Aktionen (z.B. [sighs], [laughing], [clears throat]) NACH dem Textsegment.
            - Verwende NUR auditive Tags – KEINE visuellen wie [grinning] oder [standing].
            - Setze Tags sparsam ein – nicht jeden Satz taggen.
            - Behalte Absaetze und Zeilenumbrueche exakt bei.
            - Gib NUR den getaggten Text zurueck.

            Erlaubte Tags (Beispiele):
            Emotionen: [happy] [sad] [excited] [angry] [annoyed] [surprised] [worried] [desperate]
            Delivery: [whisper] [shouting] [softly] [firmly] [sarcastically] [mockingly]
            Non-verbal: [laughing] [chuckles] [sighs] [clears throat] [exhales sharply] [inhales deeply]

            Beispiel:
            Eingabe: Bist du wahnsinnig? Ich kann nicht glauben, dass du das getan hast!
            Ausgabe: [appalled] Bist du wahnsinnig? Ich kann nicht glauben, dass du das getan hast! [sighs]

            Jetzt tagge den folgenden Text:

            """;

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

    /** Preferences für Fenster-, Separator- und FileChooser-Persistenz (gesetzt in setupTtsWindowPersistence). */
    private Preferences ttsPreferences;

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
    private Slider trimStartSlider;
    private Label trimStartLabel;
    private ProgressBar playerProgress;
    private MediaPlayer embeddedPlayer;
    /** Pfad, der gerade geladen werden soll; verhindert, dass veraltete asynchrone Loads den Player überschreiben. */
    private Path pendingAudioPath;
    private Label selectionWordCountLabel;
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
    /** Busy-Indicator für v3 Audio-Tagging. */
    private ProgressIndicator v3TagProgressIndicator;
    private Label v3TagCheckmark;
    private Label statusLabel;
    private int playingSegmentIndex = -1;
    /** Verhindert Doppelstart und Doppelklick bei „Alle abspielen“. */
    private boolean isPlayingAllSequence = false;
    /** Bearbeitungsmodus: per Doppelklick auf Segment aktiviert – Text kann während Abspielens/Renderens bearbeitet werden. */
    private boolean editModeActive = false;
    private Label editModeLabel;
    /** Dynamisches Stylesheet: blendet RichTextFX-Selektion (main-selection) im Bearbeitungsmodus aus. */
    private static final String TTS_EDIT_MODE_HIDE_SELECTION_CSS = "data:text/css,.main-selection{-fx-fill:transparent%20!important;}";
    /** Während „Alle abspielen“: Segmente nach Position im Text (start) sortiert, damit in Textreihenfolge abgespielt wird. */
    private List<TtsSegment> segmentsPlayOrder;
    /** Timer für „Alle abspielen“: wechselt nach Ablauf der Stücklänge genau einmal zum nächsten Segment (vermeidet Doppelauslösung von setOnEndOfMedia). */
    private PauseTransition playAllAdvanceTransition;
    /** Nach Seek in der Fortschrittsanzeige: Settle-Timer; nach Ablauf ggf. Wiedergabe fortsetzen. */
    private PauseTransition seekSettleTransition;
    /** Ob nach dem laufenden Seek-Settle wieder abgespielt werden soll (war vor Seek am Laufen). */
    private boolean pendingSeekResume;
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

    /** Projektglobale Regieanweisungen (Text in eckigen Klammern). lastUsed für Sortierung „nach letzter Nutzung“. */
    public static class RegieanweisungEntry {
        public String text = "";
        public long lastUsed = 0;
        public RegieanweisungEntry() {}
        public RegieanweisungEntry(String text, long lastUsed) {
            this.text = text != null ? text : "";
            this.lastUsed = lastUsed;
        }
        public javafx.beans.property.StringProperty textProperty() {
            javafx.beans.property.SimpleStringProperty p = new javafx.beans.property.SimpleStringProperty(text);
            p.addListener((o, oldV, newV) -> text = newV != null ? newV : "");
            return p;
        }
    }
    private final Path regieanweisungenPath;
    private final ObservableList<RegieanweisungEntry> regieanweisungenItems = FXCollections.observableArrayList();
    /** true = nach letzter Nutzung, false = alphabetisch. */
    private boolean regieanweisungenSortByLastUsed = false;
    private TableView<RegieanweisungEntry> regieanweisungenTableView;
    private ComboBox<String> regieanweisungenSortCombo;

    /** Einschwingtext: wird bei TTS-Generierung dem eigentlichen Text vorangestellt, damit die Stimme konsistenter einsetzt. */
    private TextArea einschwingTextArea;
    private CheckBox einschwingCheckBox;

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
        this.regieanweisungenPath = dataDir != null ? Paths.get(dataDir.getPath(), "tts-regieanweisungen.json") : null;
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
        // Einschwingtext aus Preferences laden (nach buildRoot, da die UI-Elemente dort erstellt werden)
        if (w.einschwingTextArea != null) {
            w.einschwingTextArea.setText(ttsPrefs.get("einschwing_text", ""));
        }
        if (w.einschwingCheckBox != null) {
            w.einschwingCheckBox.setSelected(ttsPrefs.getBoolean("einschwing_enabled", false));
        }
        w.loadSegments();
        w.refreshHighlight();
        w.stage.setOnCloseRequest(ev -> {
            String text = w.codeArea != null ? w.codeArea.getText() : null;
            boolean textEmpty = text == null || text.trim().isEmpty();
            if (textEmpty) {
                w.segments.clear();
                w.selectedSegmentForColor = null;
                w.saveSegments();
                logger.warn("TTS-Editor geschlossen bei leerem Text – Inhalt nicht überschrieben, Segmente zurückgesetzt.");
            } else {
                w.saveSegments();
                w.saveEditorContentToSeparateFile();
            }
            // Einschwingtext in Preferences speichern
            if (ttsPrefs != null) {
                ttsPrefs.put("einschwing_text", w.einschwingTextArea != null ? w.einschwingTextArea.getText() : "");
                ttsPrefs.putBoolean("einschwing_enabled", w.einschwingCheckBox != null && w.einschwingCheckBox.isSelected());
            }
        });
        w.stage.show();
        // Hash-Pruefung NACH stage.show(), damit der Diff-Dialog (showAndWait) korrekt angezeigt werden kann
        final String originalContent = content;
        Platform.runLater(() -> {
            w.checkAndUpdateOriginalHash(originalContent);
            w.scrollEditorToTop();
        });
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
        this.ttsPreferences = prefs;
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
        final double divPos;
        if (ttsPreferences != null) {
            double p = ttsPreferences.getDouble("tts_split_divider", 0.42);
            divPos = Math.max(0.15, Math.min(0.85, p));
        } else {
            divPos = 0.42;
        }
        split.getStyleClass().add("tts-editor-split");

        Node left = buildLeftPanel();
        Node right = buildRightPanel(content);
        split.getItems().addAll(left, right);
        split.setDividerPositions(divPos);
        // Beide Seiten duerfen mit dem Fenster mitwachsen
        SplitPane.setResizableWithParent(left, true);
        SplitPane.setResizableWithParent(right, true);
        if (ttsPreferences != null && !split.getDividers().isEmpty()) {
            split.getDividers().get(0).positionProperty().addListener((o, oldVal, pos) -> {
                if (pos != null) {
                    try { ttsPreferences.putDouble("tts_split_divider", pos.doubleValue()); } catch (Exception ignored) {}
                }
            });
        }
        Platform.runLater(() -> split.setDividerPositions(divPos));

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

        // Einschwingtext: wird dem TTS-Text vorangestellt, damit die Stimme konsistenter einsetzt
        einschwingCheckBox = new CheckBox("Einschwingtext benutzen");
        einschwingCheckBox.setSelected(false);
        einschwingCheckBox.setTooltip(new Tooltip("Wenn aktiv, wird dieser Text vor jedem generierten Segment eingefügt, damit die Stimme \"einschwingt\"."));
        einschwingTextArea = new TextArea();
        einschwingTextArea.setPromptText("Text, der vor jedem Segment vorangestellt wird…");
        einschwingTextArea.setPrefRowCount(3);
        einschwingTextArea.setWrapText(true);
        einschwingTextArea.setMaxWidth(Double.MAX_VALUE);
        einschwingTextArea.disableProperty().bind(einschwingCheckBox.selectedProperty().not());
        VBox einschwingBox = new VBox(4);
        einschwingBox.getChildren().addAll(einschwingCheckBox, einschwingTextArea);
        einschwingBox.setMaxWidth(Double.MAX_VALUE);

        // Aussprache-Lexikon: Tabelle (direkte Änderungen werden bei Erstellen/Batch verwendet)
        Label lexiconLabel = new Label("Aussprache-Lexikon (Wort → Ersetzung)");
        lexiconItems = FXCollections.observableArrayList();
        loadLexiconIntoTable();
        lexiconTable = new TableView<>(lexiconItems);
        lexiconTable.getStyleClass().add("lexicon-table");
        lexiconTable.setEditable(true);
        lexiconTable.setPrefHeight(180);
        lexiconTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        lexiconTable.setMinWidth(0);
        lexiconTable.setMaxWidth(Double.MAX_VALUE);
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
        Button btnLexiconLoad = new Button("Aus Datei laden");
        btnLexiconLoad.setMinWidth(120);
        btnLexiconLoad.setOnAction(e -> loadLexiconIntoTable());
        Button btnLexiconAdd = new Button("+ Eintrag");
        btnLexiconAdd.setMinWidth(100);
        btnLexiconAdd.setOnAction(e -> {
            lexiconItems.add(new ComfyUITTSTestWindow.LexiconEntry("", ""));
            int idx = lexiconItems.size() - 1;
            lexiconTable.getSelectionModel().clearAndSelect(idx);
            lexiconTable.scrollTo(idx);
            Platform.runLater(() -> lexiconTable.edit(idx, colWord));
        });
        Button btnLexiconRemove = new Button("− Entfernen");
        btnLexiconRemove.setMinWidth(100);
        btnLexiconRemove.setOnAction(e -> {
            ComfyUITTSTestWindow.LexiconEntry sel = lexiconTable.getSelectionModel().getSelectedItem();
            if (sel != null) lexiconItems.remove(sel);
        });
        Button btnLexiconSave = new Button("Lexikon speichern");
        btnLexiconSave.setMinWidth(140);
        btnLexiconSave.setOnAction(e -> saveLexiconToFile());
        Button btnLexiconApplyToText = new Button("Lexikon in Text setzen");
        btnLexiconApplyToText.setMinWidth(170);
        btnLexiconApplyToText.setTooltip(new Tooltip("Alle Wörter aus dem Lexikon im Editortext durch ihre Ersetzung ersetzen (ganze Wörter)."));
        btnLexiconApplyToText.setOnAction(e -> applyLexiconToText());
        Button btnLexiconRemoveFromText = new Button("Lexikon aus Text entfernen");
        btnLexiconRemoveFromText.setMinWidth(200);
        btnLexiconRemoveFromText.setTooltip(new Tooltip("Ersetzungen im Text wieder durch die ursprünglichen Lexikon-Wörter zurücksetzen."));
        btnLexiconRemoveFromText.setOnAction(e -> removeLexiconFromText());
        FlowPane lexiconButtons = new FlowPane(6, 4);
        lexiconButtons.getChildren().addAll(btnLexiconLoad, btnLexiconAdd, btnLexiconRemove, btnLexiconSave,
                btnLexiconApplyToText, btnLexiconRemoveFromText);
        VBox lexiconBox = new VBox(4);
        lexiconBox.getChildren().addAll(lexiconLabel, lexiconTable, lexiconButtons);

        // Regieanweisungen (projektglobal): TableView analog zum Lexikon, Inline-Editing
        Label regieLabel = new Label("Regieanweisungen [Text] (Modellabhaengig)");
        regieLabel.setTooltip(new Tooltip("Texte in eckigen Klammern z. B. [unterw\u00fcrfig, tiefe Stimme]. Mehrere Tags pro Zeile m\u00f6glich: [lacht][fl\u00fcstert]. Doppelklick f\u00fcgt am Cursor ein."));
        regieanweisungenTableView = new TableView<>(regieanweisungenItems);
        regieanweisungenTableView.getStyleClass().add("lexicon-table");
        regieanweisungenTableView.setEditable(true);
        regieanweisungenTableView.setPrefHeight(120);
        regieanweisungenTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        regieanweisungenTableView.setMinWidth(0);
        regieanweisungenTableView.setMaxWidth(Double.MAX_VALUE);
        TableColumn<RegieanweisungEntry, String> colRegieText = new TableColumn<>("Anweisung z.B. [lacht] oder [lacht][fluestert]");
        colRegieText.setCellValueFactory(c -> c.getValue().textProperty());
        colRegieText.setCellFactory(column -> createRegieTableCell());
        colRegieText.setOnEditCommit(e -> {
            int row = e.getTablePosition().getRow();
            if (row >= 0 && row < regieanweisungenItems.size()) {
                regieanweisungenItems.get(row).text = e.getNewValue() != null ? e.getNewValue().trim() : "";
                saveRegieanweisungen();
            }
        });
        regieanweisungenTableView.getColumns().add(colRegieText);
        regieanweisungenTableView.setOnMouseClicked(me -> {
            if (me.getClickCount() == 2 && regieanweisungenTableView.getSelectionModel().getSelectedItem() != null) {
                // Doppelklick fuegt ein, AUSSER die Zelle ist gerade im Edit-Modus
                if (regieanweisungenTableView.getEditingCell() == null)
                    insertRegieanweisungAtCursor(regieanweisungenTableView.getSelectionModel().getSelectedItem());
            }
        });
        regieanweisungenSortCombo = new ComboBox<>();
        regieanweisungenSortCombo.getItems().addAll("Alphabetisch", "Nach letzter Nutzung");
        regieanweisungenSortCombo.getSelectionModel().select(regieanweisungenSortByLastUsed ? 1 : 0);
        regieanweisungenSortCombo.setMaxWidth(Double.MAX_VALUE);
        regieanweisungenSortCombo.setOnAction(e -> {
            regieanweisungenSortByLastUsed = regieanweisungenSortCombo.getSelectionModel().getSelectedIndex() == 1;
            applyRegieanweisungenSort();
            saveRegieanweisungen();
        });
        Button btnRegieAdd = new Button("+ Eintrag");
        btnRegieAdd.setMinWidth(100);
        btnRegieAdd.setOnAction(e -> {
            RegieanweisungEntry neu = new RegieanweisungEntry("", 0);
            regieanweisungenItems.add(neu);
            int idx = regieanweisungenItems.size() - 1;
            regieanweisungenTableView.getSelectionModel().clearAndSelect(idx);
            regieanweisungenTableView.scrollTo(idx);
            Platform.runLater(() -> regieanweisungenTableView.edit(idx, colRegieText));
        });
        Button btnRegieRemove = new Button("\u2212 Entfernen");
        btnRegieRemove.setMinWidth(100);
        btnRegieRemove.setOnAction(e -> {
            RegieanweisungEntry sel = regieanweisungenTableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                regieanweisungenItems.remove(sel);
                saveRegieanweisungen();
            }
        });
        Button btnRegieCollect = new Button("Aus Text sammeln");
        btnRegieCollect.setMinWidth(130);
        btnRegieCollect.setTooltip(new Tooltip("Alle [ ... ] im aktuellen Kapiteltext finden und zur Liste hinzuf\u00fcgen. Aufeinanderfolgende Tags werden als ein Eintrag \u00fcbernommen."));
        btnRegieCollect.setOnAction(e -> {
            if (codeArea != null) collectRegieanweisungenFromText();
        });
        Button btnRegieInsert = new Button("Am Cursor einf\u00fcgen");
        btnRegieInsert.setMinWidth(140);
        btnRegieInsert.setTooltip(new Tooltip("Gew\u00e4hlten Eintrag an der Cursorposition einf\u00fcgen. Tastenk\u00fcrzel: Strg+Shift+R"));
        btnRegieInsert.setOnAction(e -> {
            RegieanweisungEntry sel = regieanweisungenTableView.getSelectionModel().getSelectedItem();
            if (sel != null) insertRegieanweisungAtCursor(sel);
            else setStatus("Bitte eine Regieanweisung ausw\u00e4hlen.");
        });
        Button btnRegieSave = new Button("Speichern");
        btnRegieSave.setMinWidth(100);
        btnRegieSave.setOnAction(e -> { saveRegieanweisungen(); setStatus("Regieanweisungen gespeichert."); });
        FlowPane regieButtons = new FlowPane(6, 4);
        regieButtons.getChildren().addAll(btnRegieAdd, btnRegieRemove, btnRegieCollect, btnRegieInsert, btnRegieSave);
        VBox regieanweisungenBox = new VBox(4);
        regieanweisungenBox.getChildren().addAll(regieLabel, regieanweisungenTableView, regieanweisungenSortCombo, regieButtons);

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
        playerProgress.setCursor(javafx.scene.Cursor.HAND);
        playerProgress.setOnMouseClicked(ev -> seekPlaybackFromProgressBarMouse(ev));
        playerProgress.setOnMouseDragged(ev -> seekPlaybackFromProgressBarMouse(ev));

        trimStartSlider = new Slider(0, 20, 0);
        trimStartSlider.setPrefWidth(Double.MAX_VALUE);
        trimStartSlider.setBlockIncrement(0.5);
        trimStartSlider.setMajorTickUnit(5);
        trimStartSlider.setMinorTickCount(4);
        trimStartLabel = new Label("Anfang abschneiden: 0,0 s");
        trimStartSlider.valueProperty().addListener((o, a, b) -> {
            double sec = b.doubleValue();
            if (trimStartLabel != null) trimStartLabel.setText(String.format(java.util.Locale.ROOT, "Anfang abschneiden: %.1f s", sec));
        });

        VBox playerBox = new VBox(6);
        playerBox.getChildren().addAll(playerLabel, playerButtons, playerProgress, new Label("Beim Speichern Anfang weglassen (z. B. Einschwingen):"), trimStartSlider, trimStartLabel);

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

        selectionWordCountLabel = new Label("");
        selectionWordCountLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: bold;",
            THEMES[themeIndex][1]));

        editModeLabel = new Label("");
        editModeLabel.setWrapText(true);
        editModeLabel.setVisible(false);
        editModeLabel.setStyle("-fx-text-fill: #0a7c0a; -fx-font-weight: bold;");

        HBox batchRow = new HBox(8);
        batchRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        batchRow.getChildren().addAll(new Label("Batch:"), batchModeCombo, btnBatchToggle);

        Button btnV3AudioTag = new Button("v3 Text taggen");
        btnV3AudioTag.setTooltip(new Tooltip("Text über Ollama mit ElevenLabs v3 Audio-Tags anreichern ([laughing], [sighs], [whisper] etc.)"));
        btnV3AudioTag.setOnAction(e -> tagTextWithV3AudioTags());
        v3TagProgressIndicator = new ProgressIndicator(-1);
        v3TagProgressIndicator.setVisible(false);
        v3TagProgressIndicator.setPrefSize(20, 20);
        v3TagProgressIndicator.setMaxSize(20, 20);
        v3TagProgressIndicator.setMinSize(20, 20);
        v3TagCheckmark = new Label("\u2713");
        v3TagCheckmark.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 16px; -fx-font-weight: bold;");
        v3TagCheckmark.setVisible(false);
        v3TagCheckmark.setMinSize(20, 20);
        v3TagCheckmark.setPrefSize(20, 20);
        v3TagCheckmark.setMaxSize(20, 20);
        v3TagCheckmark.setAlignment(javafx.geometry.Pos.CENTER);
        StackPane v3TagIconStack = new StackPane();
        v3TagIconStack.setMinSize(20, 20);
        v3TagIconStack.setPrefSize(20, 20);
        v3TagIconStack.setMaxSize(20, 20);
        v3TagIconStack.getChildren().addAll(v3TagProgressIndicator, v3TagCheckmark);
        HBox v3TagRow = new HBox(8);
        v3TagRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        v3TagRow.getChildren().addAll(btnV3AudioTag, v3TagIconStack);

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
            selectionWordCountLabel,
            editModeLabel,
            erstellenRow, btnSpeichern,
            new Separator(),
            btnAlleAbspielen,
            buildFullAudioQualityRow(),
            btnGesamtAudiodatei,
            new Separator(),
            batchRow,
            v3TagRow,
            statusLabel
        );
        VBox.setVgrow(statusLabel, Priority.ALWAYS);

        // Rechte Spalte: Aussprache-Lexikon, Regieanweisungen (Hgrow damit sie Platz im HBox bekommt)
        VBox rightColumn = new VBox(12);
        rightColumn.setMinWidth(180);
        rightColumn.setPrefWidth(280);
        rightColumn.setMaxWidth(Double.MAX_VALUE);
        lexiconBox.setMaxWidth(Double.MAX_VALUE);
        regieanweisungenBox.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(lexiconBox, Priority.ALWAYS);
        einschwingBox.setMaxWidth(Double.MAX_VALUE);
        rightColumn.getChildren().addAll(einschwingBox, lexiconBox, regieanweisungenBox);
        loadRegieanweisungen();

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
        wrapper.setMaxWidth(Double.MAX_VALUE);
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
        applyThemeToNode(regieanweisungenBox, themeIndex);
        applyThemeToNode(regieanweisungenTableView, themeIndex);
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
        applyThemeToNode(editModeLabel, themeIndex);
        applyThemeToNode(playerProgress, themeIndex);
        applyThemeToNode(trimStartSlider, themeIndex);
        applyThemeToNode(trimStartLabel, themeIndex);
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
            if (ttsPreferences != null) {
                String last = ttsPreferences.get("tts_vc_ref_audio_dir", null);
                if (last != null && !last.isEmpty()) {
                    File lastDir = new File(last);
                    if (lastDir.isDirectory()) fc.setInitialDirectory(lastDir);
                } else if (dataDir != null && dataDir.isDirectory()) {
                    fc.setInitialDirectory(dataDir.getParentFile());
                }
            }
            File f = fc.showOpenDialog(stage);
            if (f != null) {
                vcRefAudioFile = f;
                vcRefAudioLabel.setText(f.getName());
                if (ttsPreferences != null && f.getParent() != null) {
                    try { ttsPreferences.put("tts_vc_ref_audio_dir", f.getParent()); } catch (Exception ignored) {}
                }
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

    /** Erstellt eine editierbare TableCell fuer die Regieanweisungen-Tabelle (analog zu Lexikon). */
    private TableCell<RegieanweisungEntry, String> createRegieTableCell() {
        TableCell<RegieanweisungEntry, String> cell = new TableCell<>() {
            private TextField textField;

            private void applyCommitToModel() {
                if (textField == null) return;
                int row = getIndex();
                if (row >= 0 && row < regieanweisungenItems.size()) {
                    String v = textField.getText();
                    regieanweisungenItems.get(row).text = v != null ? v.trim() : "";
                    saveRegieanweisungen();
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

    /**
     * Sammelt alle nicht überlappenden Ersetzungen [start, end, newLen], sortiert nach start.
     * Bei gleichem Start gewinnt das längere Match (wie bei „Euler“ vs „Eu“).
     */
    private static List<int[]> findNonOverlappingReplacements(String text, List<Map.Entry<String, String>> entries,
            boolean searchWordNotReplacement) {
        List<int[]> matches = new ArrayList<>(); // [start, end, newLen]
        for (Map.Entry<String, String> e : entries) {
            String search = searchWordNotReplacement ? e.getKey().trim() : e.getValue();
            String replacement = searchWordNotReplacement ? e.getValue() : e.getKey().trim();
            if (search.isEmpty()) continue;
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(search) + "(?![\\p{L}\\p{N}])", Pattern.UNICODE_CHARACTER_CLASS);
            Matcher m = p.matcher(text);
            while (m.find()) {
                int s = m.start();
                int end = m.end();
                matches.add(new int[] { s, end, replacement.length() });
            }
        }
        matches.sort(Comparator.comparingInt((int[] a) -> a[0]).thenComparing((int[] a, int[] b) -> Integer.compare((b[1] - b[0]), (a[1] - a[0]))));
        List<int[]> nonOverlapping = new ArrayList<>();
        int lastEnd = 0;
        for (int[] a : matches) {
            if (a[0] >= lastEnd) {
                nonOverlapping.add(a);
                lastEnd = a[1];
            }
        }
        return nonOverlapping;
    }

    /** Baut aus Originaltext und Ersetzungsliste (start, end, newLen) den neuen Text. replacement-Strings aus entries (searchWord -> replacement). */
    private static String buildTextWithReplacements(String text, List<int[]> nonOverlapping, List<Map.Entry<String, String>> entries,
            boolean searchWordNotReplacement) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int[] a : nonOverlapping) {
            int start = a[0], end = a[1];
            sb.append(text, pos, start);
            String replacement = null;
            for (Map.Entry<String, String> e : entries) {
                String s = searchWordNotReplacement ? e.getKey().trim() : e.getValue();
                String r = searchWordNotReplacement ? e.getValue() : e.getKey().trim();
                if (s.isEmpty()) continue;
                if (text.substring(start, end).equals(s)) {
                    replacement = r;
                    break;
                }
            }
            if (replacement != null) sb.append(replacement);
            pos = end;
        }
        sb.append(text, pos, text.length());
        return sb.toString();
    }

    /** Mappt eine Position aus dem alten Text in den neuen Text anhand der Ersetzungsliste [start, end, newLen]. */
    private static int mapPosition(int oldPos, List<int[]> replacements) {
        int delta = 0;
        for (int[] a : replacements) {
            int start = a[0], end = a[1], oldLen = end - start, newLen = a[2];
            if (oldPos >= end) {
                delta += (newLen - oldLen);
            } else if (oldPos > start) {
                return start + delta + (int) Math.round((oldPos - start) * (double) newLen / oldLen);
            } else {
                break;
            }
        }
        return oldPos + delta;
    }

    /** Ersetzt im Editortext jedes Lexikon-Wort (ganze Wörter) durch seine Ersetzung. Segmentgrenzen (start/end) werden mitgeführt, damit keine Segmente zerstört werden. */
    private void applyLexiconToText() {
        if (codeArea == null) return;
        Map<String, String> lexicon = getLexiconFromTable();
        if (lexicon.isEmpty()) {
            setStatus("Lexikon ist leer – nichts zum Anwenden.");
            return;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(lexicon.entrySet());
        entries.removeIf(e -> (e.getKey() == null || e.getKey().trim().isEmpty()) || (e.getValue() == null || e.getValue().isEmpty()));
        if (entries.isEmpty()) {
            setStatus("Keine gültigen Lexikon-Einträge (Wort und Ersetzung nicht leer).");
            return;
        }
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        String text = codeArea.getText();
        List<int[]> replacements = findNonOverlappingReplacements(text, entries, true);
        if (replacements.isEmpty()) {
            setStatus("Keine Lexikon-Treffer im Text.");
            return;
        }
        String newText = buildTextWithReplacements(text, replacements, entries, true);
        for (TtsSegment seg : segments) {
            seg.start = mapPosition(seg.start, replacements);
            seg.end = mapPosition(seg.end, replacements);
        }
        codeArea.replaceText(newText);
        saveSegments();
        refreshHighlight();
        setStatus("Lexikon im Text angewendet (" + entries.size() + " Einträge, " + segments.size() + " Segmente angepasst).");
    }

    /** Setzt Ersetzungen im Editortext wieder auf die ursprünglichen Lexikon-Wörter zurück. Segmentgrenzen werden mitgeführt. */
    private void removeLexiconFromText() {
        if (codeArea == null) return;
        Map<String, String> lexicon = getLexiconFromTable();
        if (lexicon.isEmpty()) {
            setStatus("Lexikon ist leer – nichts zum Zurücksetzen.");
            return;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(lexicon.entrySet());
        entries.removeIf(e -> (e.getKey() == null || e.getKey().trim().isEmpty()) || (e.getValue() == null || e.getValue().isEmpty()));
        if (entries.isEmpty()) {
            setStatus("Keine gültigen Lexikon-Einträge (Wort und Ersetzung nicht leer).");
            return;
        }
        entries.sort((a, b) -> Integer.compare(b.getValue().length(), a.getValue().length()));
        String text = codeArea.getText();
        List<int[]> replacements = findNonOverlappingReplacements(text, entries, false);
        if (replacements.isEmpty()) {
            setStatus("Keine Lexikon-Ersetzungen im Text gefunden.");
            return;
        }
        String newText = buildTextWithReplacements(text, replacements, entries, false);
        for (TtsSegment seg : segments) {
            seg.start = mapPosition(seg.start, replacements);
            seg.end = mapPosition(seg.end, replacements);
        }
        codeArea.replaceText(newText);
        saveSegments();
        refreshHighlight();
        setStatus("Lexikon aus dem Text entfernt (" + entries.size() + " Einträge, " + segments.size() + " Segmente angepasst).");
    }

    /**
     * Sendet den Editortext an Ollama, um ihn mit ElevenLabs v3 Audio-Tags
     * anzureichern ([laughing], [sighs], [whisper] etc.).
     * Der getaggte Text ersetzt anschließend den Editorinhalt.
     */
    private void tagTextWithV3AudioTags() {
        if (codeArea == null) return;
        String text = codeArea.getText();
        if (text == null || text.isBlank()) {
            setStatus("Editor ist leer – nichts zum Taggen.");
            return;
        }
        // Vorhandene Segmente warnen
        if (!segments.isEmpty()) {
            CustomAlert confirm = DialogFactory.createConfirmationAlert(
                    "v3 Audio-Tagging",
                    "Vorhandene Segmente",
                    "Der Text wird verändert. Bestehende TTS-Segmente werden dadurch ungültig und entfernt. Fortfahren?",
                    stage);
            confirm.applyTheme(themeIndex);
            var result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) return;
        }

        setStatus("v3 Audio-Tagging läuft (Ollama: gemma3:4b) …");

        // Busy-Indicator anzeigen
        if (v3TagProgressIndicator != null) v3TagProgressIndicator.setVisible(true);
        if (v3TagCheckmark != null) v3TagCheckmark.setVisible(false);

        // num_predict großzügig: Text + Tags brauchen ca. 30 % mehr Tokens als der Originaltext
        int estimatedTokens = Math.max(4096, text.length());

        CompletableFuture.runAsync(() -> {
            try {
                OllamaService ollama = new OllamaService();
                // Bewusst NICHT das gespeicherte Modell aus OllamaWindow laden –
                // trainierte Modelle ignorieren System-Prompt-Overrides und geben
                // statt Audio-Tags Schreibfeedback. Das Basismodell (gemma3:4b) funktioniert.
                // Generate-API mit explizitem system-Feld verwenden:
                String userPrompt = V3_AUDIO_TAG_PROMPT + text;
                String taggedText = ollama.generateWithSystem(
                        V3_AUDIO_TAG_SYSTEM, userPrompt,
                        estimatedTokens, 0.3, 0.9, 1.1).join();

                if (taggedText == null || taggedText.isBlank()) {
                    Platform.runLater(() -> {
                        if (v3TagProgressIndicator != null) v3TagProgressIndicator.setVisible(false);
                        setStatus("v3 Audio-Tagging: Leere Antwort von Ollama.");
                    });
                    return;
                }
                // Markdown-Code-Fences entfernen, falls das Modell sie zurückgibt
                taggedText = taggedText.strip();
                if (taggedText.startsWith("```")) {
                    int firstNewline = taggedText.indexOf('\n');
                    if (firstNewline > 0) taggedText = taggedText.substring(firstNewline + 1);
                }
                if (taggedText.endsWith("```")) {
                    taggedText = taggedText.substring(0, taggedText.length() - 3);
                }
                taggedText = taggedText.strip();

                final String finalText = taggedText;
                Platform.runLater(() -> {
                    // Spinner aus, Häkchen an
                    if (v3TagProgressIndicator != null) v3TagProgressIndicator.setVisible(false);
                    if (v3TagCheckmark != null) {
                        v3TagCheckmark.setVisible(true);
                        PauseTransition hideCheck = new PauseTransition(Duration.seconds(15));
                        hideCheck.setOnFinished(ev -> v3TagCheckmark.setVisible(false));
                        hideCheck.play();
                    }
                    // Segmente entfernen, da Positionen durch Tags verschoben sind
                    segments.clear();
                    saveSegments();
                    codeArea.replaceText(finalText);
                    refreshHighlight();
                    setStatus("v3 Audio-Tags eingefügt. Bitte Text prüfen.");
                });
            } catch (Exception ex) {
                logger.error("v3 Audio-Tagging fehlgeschlagen", ex);
                Platform.runLater(() -> {
                    if (v3TagProgressIndicator != null) v3TagProgressIndicator.setVisible(false);
                    setStatus("v3 Audio-Tagging Fehler: " + ex.getMessage());
                });
            }
        });
    }

    private void loadRegieanweisungen() {
        if (regieanweisungenPath == null || !Files.isRegularFile(regieanweisungenPath)) return;
        try {
            String json = Files.readString(regieanweisungenPath, StandardCharsets.UTF_8);
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonObject root = gson.fromJson(json, com.google.gson.JsonObject.class);
            if (root != null) {
                if (root.has("sortByLastUsed")) regieanweisungenSortByLastUsed = root.get("sortByLastUsed").getAsBoolean();
                if (root.has("entries") && root.get("entries").isJsonArray()) {
                    regieanweisungenItems.clear();
                    for (com.google.gson.JsonElement el : root.getAsJsonArray("entries")) {
                        if (el.isJsonObject()) {
                            com.google.gson.JsonObject o = el.getAsJsonObject();
                            String text = o.has("text") ? o.get("text").getAsString() : "";
                            long lastUsed = o.has("lastUsed") ? o.get("lastUsed").getAsLong() : 0;
                            regieanweisungenItems.add(new RegieanweisungEntry(text, lastUsed));
                        }
                    }
                }
            }
            applyRegieanweisungenSort();
            if (regieanweisungenSortCombo != null)
                regieanweisungenSortCombo.getSelectionModel().select(regieanweisungenSortByLastUsed ? 1 : 0);
        } catch (Exception e) {
            logger.warn("Regieanweisungen konnten nicht geladen werden: {}", regieanweisungenPath, e);
        }
    }

    private void saveRegieanweisungen() {
        if (regieanweisungenPath == null) return;
        try {
            Files.createDirectories(regieanweisungenPath.getParent());
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("sortByLastUsed", regieanweisungenSortByLastUsed);
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (RegieanweisungEntry e : regieanweisungenItems) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("text", e.text != null ? e.text : "");
                o.addProperty("lastUsed", e.lastUsed);
                arr.add(o);
            }
            root.add("entries", arr);
            Files.writeString(regieanweisungenPath, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Regieanweisungen konnten nicht gespeichert werden: {}", regieanweisungenPath, e);
        }
    }

    private void collectRegieanweisungenFromText() {
        if (codeArea == null) return;
        String text = codeArea.getText();
        if (text == null) return;
        // Aufeinanderfolgende Tags erkennen: [tag1][tag2] oder [tag1] [tag2] als ein Eintrag
        // Gespeichert wird MIT eckigen Klammern, also z. B. "[lacht]" oder "[lacht][fluestert]"
        Pattern groupP = Pattern.compile("(\\[[^\\]]+\\](?:\\s*\\[[^\\]]+\\])*)");
        Matcher gm = groupP.matcher(text);
        Set<String> existing = new HashSet<>();
        for (RegieanweisungEntry e : regieanweisungenItems)
            existing.add(e.text != null ? e.text.trim() : "");
        int added = 0;
        while (gm.find()) {
            String fullMatch = gm.group(1).trim();
            if (!fullMatch.isEmpty() && !existing.contains(fullMatch)) {
                regieanweisungenItems.add(new RegieanweisungEntry(fullMatch, 0));
                existing.add(fullMatch);
                added++;
            }
        }
        applyRegieanweisungenSort();
        saveRegieanweisungen();
        setStatus(added > 0 ? added + " Regieanweisung(en) aus Text uebernommen." : "Keine neuen [ ... ] im Text gefunden.");
    }

    private void applyRegieanweisungenSort() {
        List<RegieanweisungEntry> list = new ArrayList<>(regieanweisungenItems);
        if (regieanweisungenSortByLastUsed)
            list.sort(Comparator.<RegieanweisungEntry>comparingLong(e -> -e.lastUsed).thenComparing(e -> e.text != null ? e.text : "", String.CASE_INSENSITIVE_ORDER));
        else
            list.sort(Comparator.comparing(e -> e.text != null ? e.text : "", String.CASE_INSENSITIVE_ORDER));
        regieanweisungenItems.clear();
        regieanweisungenItems.addAll(list);
    }

    private void insertRegieanweisungAtCursor(RegieanweisungEntry entry) {
        if (codeArea == null || entry == null) return;
        // Laufenden Table-Edit committen, damit entry.text aktuell ist
        if (regieanweisungenTableView != null && regieanweisungenTableView.getEditingCell() != null) {
            regieanweisungenTableView.requestFocus();
        }
        String t = entry.text != null ? entry.text.trim() : "";
        if (t.isEmpty()) {
            setStatus("Eintrag ist leer.");
            return;
        }
        int pos = codeArea.getCaretPosition();
        // Text wird mit Klammern gespeichert (z. B. "[lacht]" oder "[lacht][fluestert]")
        // Falls der User ohne Klammern eingibt, ergaenzen wir sie
        String toInsert = t;
        if (!toInsert.startsWith("[")) toInsert = "[" + toInsert;
        if (!toInsert.endsWith("]")) toInsert = toInsert + "]";
        codeArea.insertText(pos, toInsert);
        entry.lastUsed = System.currentTimeMillis();
        applyRegieanweisungenSort();
        saveRegieanweisungen();
        codeArea.displaceCaret(pos + toInsert.length());
        codeArea.requestFollowCaret();
        setStatus("Regieanweisung eingefuegt: " + toInsert);
    }

    private void bindLeftPanelActions(VBox left) {
        btnPlay.setOnAction(e -> {
            if (embeddedPlayer == null) return;
            MediaPlayer.Status status = embeddedPlayer.getStatus();
            if (status == MediaPlayer.Status.PLAYING) {
                return; // bereits am Abspielen
            }
            if (status == MediaPlayer.Status.PAUSED) {
                embeddedPlayer.play();
                return;
            }
            // STOPPED / READY / unbekannt: play() starten, dann per onPlaying-Callback an Trim-Position seekieren
            double trimSec = (trimStartSlider != null) ? trimStartSlider.getValue() : 0;
            if (trimSec > 0) {
                final MediaPlayer player = embeddedPlayer;
                player.setOnPlaying(() -> {
                    player.setOnPlaying(null); // nur einmal
                    player.seek(Duration.seconds(trimSec));
                });
            }
            embeddedPlayer.play();
        });
        btnPause.setOnAction(e -> {
            if (embeddedPlayer == null) return;
            if (embeddedPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                embeddedPlayer.pause();
            } else if (embeddedPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
                embeddedPlayer.play();
            }
        });
        btnStop.setOnAction(e -> {
            if (seekSettleTransition != null) {
                seekSettleTransition.stop();
                seekSettleTransition = null;
            }
            pendingSeekResume = false;
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

    /**
     * Sucht die Wiedergabe an die Stelle, die durch Klick oder Drag auf der Fortschrittsanzeige gewählt wurde.
     * Berücksichtigt „Anfang abschneiden“ (trim): 0 % = Trim-Start, 100 % = Ende.
     * Pausiert kurz vor dem Seek und wartet danach, damit die asynchrone Seek zuverlässig greift.
     */
    private void seekPlaybackFromProgressBarMouse(MouseEvent ev) {
        if (embeddedPlayer == null || playerProgress == null) return;
        double w = playerProgress.getWidth();
        if (w <= 0) return;
        double frac = ev.getX() / w;
        frac = Math.max(0, Math.min(1, frac));
        seekPlaybackToProgressFraction(frac);
    }

    /** Kurze Verzögerung, damit MediaPlayer.seek() vor dem ggf. folgenden play() angewandt ist. */
    private static final double SEEK_SETTLE_MS = 180;

    /**
     * Sucht die Wiedergabe an den Anteil frac (0…1) der nutzbaren Länge (von Trim-Start bis Ende).
     * Bei laufender Wiedergabe: kurz pausieren, seek ausführen, Verzögerung abwarten, dann wieder abspielen,
     * damit die asynchrone Seek nicht von play() überholt wird. Bei mehreren Seeks (z. B. Drag) wird nur
     * ein Settle-Timer geführt; nach dem letzten Seek wird ggf. wieder abgespielt.
     */
    private void seekPlaybackToProgressFraction(double frac) {
        if (embeddedPlayer == null) return;
        Duration total = embeddedPlayer.getTotalDuration();
        if (total == null || !total.greaterThan(Duration.ZERO)) return;
        double totalSec = total.toSeconds();
        double trimSec = (trimStartSlider != null) ? trimStartSlider.getValue() : 0;
        double playableSec = Math.max(0, totalSec - trimSec);
        double targetSec = trimSec + frac * playableSec;
        targetSec = Math.max(0, Math.min(totalSec, targetSec));
        Duration targetDur = Duration.seconds(targetSec);
        boolean wasPlaying = embeddedPlayer.getStatus() == MediaPlayer.Status.PLAYING;
        if (wasPlaying) embeddedPlayer.pause();
        if (seekSettleTransition != null) {
            seekSettleTransition.stop();
            seekSettleTransition = null;
        }
        pendingSeekResume = wasPlaying;
        embeddedPlayer.seek(targetDur);
        if (playerProgress != null) playerProgress.setProgress(frac);
        seekSettleTransition = new PauseTransition(Duration.millis(SEEK_SETTLE_MS));
        seekSettleTransition.setOnFinished(e -> {
            seekSettleTransition = null;
            if (embeddedPlayer != null && pendingSeekResume) embeddedPlayer.play();
        });
        seekSettleTransition.play();
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

    /** Liefert die Bitrate-Optionen (Anzeige) für Hörbuch-Dialog. */
    public static String[] getBitrateOptions() {
        return BITRATE_OPTIONS.clone();
    }

    /** Liefert den Default-Index für Bitrate-Auswahl. */
    public static int getDefaultBitrateIndex() {
        return DEFAULT_BITRATE_INDEX;
    }

    /** Liefert den kbps-Wert für einen Bitrate-Index. */
    public static int getBitrateByIndex(int index) {
        if (index < 0 || index >= BITRATE_VALUES.length) return 320;
        return BITRATE_VALUES[index];
    }

    /** Liefert die Lead-Silence in Sekunden (Stille am Anfang der Kapitel-MP3). */
    public static double getLeadSilenceSeconds() {
        return LEAD_SILENCE_SECONDS;
    }

    /** Liefert die Trail-Silence in Sekunden (Stille am Ende der Kapitel-MP3). */
    public static double getTrailSilenceSeconds() {
        return TRAIL_SILENCE_SECONDS;
    }

    /** Aktualisiert den Wortzaehler fuer die aktuelle Textselektion. */
    private void updateSelectionWordCount() {
        if (selectionWordCountLabel == null || codeArea == null) return;
        String sel = codeArea.getSelectedText();
        if (sel == null || sel.isBlank()) {
            selectionWordCountLabel.setText("");
            return;
        }
        String[] words = sel.trim().split("\\s+");
        int wordCount = words.length;
        int charCount = sel.length();
        selectionWordCountLabel.setText("Selektion: " + wordCount + (wordCount == 1 ? " Wort" : " W\u00f6rter") + ", " + charCount + " Zeichen");
    }

    /** Liefert die aktuell auf dem Haupt-Tab eingetragene Stimmbeschreibung (wird beim Generieren mitgegeben). */
    private String getEffectiveVoiceDescription() {
        if (voiceDescriptionArea == null || voiceDescriptionArea.getText() == null) return "";
        return voiceDescriptionArea.getText().trim();
    }

    /**
     * Stellt ggf. den Einschwingtext dem übergebenen TTS-Text voran.
     * Liefert den zusammengesetzten Text zurück.  Wenn die Checkbox deaktiviert ist oder
     * der Einschwingtext leer ist, wird der Originaltext unverändert zurückgegeben.
     */
    private String prependEinschwingText(String ttsText) {
        if (einschwingCheckBox == null || !einschwingCheckBox.isSelected()) return ttsText;
        String prefix = einschwingTextArea.getText();
        if (prefix == null || prefix.isBlank()) return ttsText;
        // Leerzeichen als Trenner sicherstellen
        return prefix.trim() + " " + ttsText;
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

        // Bei Auswahländerung Highlight verzögert aktualisieren, damit Drag/Shift+Pfeil die Auswahl nicht sofort überschreiben (v. a. im Bearbeitungsmodus)
        codeArea.selectionProperty().addListener((o, a, b) -> Platform.runLater(() -> {
            refreshHighlight();
            updateSelectionWordCount();
        }));
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
            // Im Bearbeitungsmodus: Release nach Drag-Selektion nicht als „Klick“ durchlassen, sonst löscht die CodeArea die Auswahl
            if (editModeActive && me.getButton() == MouseButton.PRIMARY) {
                int selStart = codeArea.getSelection().getStart();
                int selEnd = codeArea.getSelection().getEnd();
                if (selStart != selEnd && offset >= selStart && offset <= selEnd) {
                    me.consume();
                    return;
                }
            }
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
                    if (me.getClickCount() == 2) {
                        editModeActive = !editModeActive;
                        updateEditModeIndicator();
                        setStatus(editModeActive
                            ? "Bearbeitungsmodus an: Text kann während Abspielens/Renderens bearbeitet werden. Esc zum Beenden."
                            : "Bearbeitungsmodus beendet.");
                        return;
                    }
                    if (editModeActive) return;  // Im Bearbeitungsmodus: Klick nur Cursor setzen, Abspielen läuft weiter
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

        codeArea.setOnKeyReleased(ke -> {
            if (ke.getCode() == KeyCode.ESCAPE && editModeActive) {
                editModeActive = false;
                updateEditModeIndicator();
                setStatus("Bearbeitungsmodus beendet.");
            }
        });
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, ke -> {
            if (ke.isControlDown() && ke.isShiftDown() && ke.getCode() == KeyCode.R) {
                RegieanweisungEntry sel = regieanweisungenTableView != null ? regieanweisungenTableView.getSelectionModel().getSelectedItem() : null;
                if (sel != null) {
                    insertRegieanweisungAtCursor(sel);
                    ke.consume();
                }
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

    private void updateEditModeIndicator() {
        if (editModeLabel != null) {
            editModeLabel.setVisible(editModeActive);
            editModeLabel.setText(editModeActive ? "Bearbeitungsmodus an – Text während Abspielens bearbeitbar. Esc zum Beenden." : "");
        }
        if (codeArea != null) {
            if (editModeActive) {
                if (!codeArea.getStyleClass().contains("tts-edit-mode")) codeArea.getStyleClass().add("tts-edit-mode");
                if (!codeArea.getStylesheets().contains(TTS_EDIT_MODE_HIDE_SELECTION_CSS)) {
                    codeArea.getStylesheets().add(TTS_EDIT_MODE_HIDE_SELECTION_CSS);
                }
            } else {
                codeArea.getStyleClass().remove("tts-edit-mode");
                codeArea.getStylesheets().remove(TTS_EDIT_MODE_HIDE_SELECTION_CSS);
            }
            refreshHighlight();
        }
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
                // Signatur nach ElevenLabs-UI-Update, damit Speichern die geladene Datei findet
                String segText = codeArea.getText(segRef.start, segRef.end);
                lastGeneratedSignature = buildTtsRequestSignature(segText, voiceCombo.getSelectionModel().getSelectedItem(), temperatureSlider.getValue(), topPSlider.getValue(), (int) Math.round(topKSlider.getValue()), repetitionPenaltySlider.getValue(), highQualityCheck.isSelected());
                if (lastGeneratedSignature != null && lastGeneratedAudioPath != null) {
                    generatedAudioBySignature.put(lastGeneratedSignature, lastGeneratedAudioPath);
                }
            });
        }
        // Seed und Speaker aus dem Segment übernehmen, damit neue Segmente dieselbe Stimme bekommen
        currentSeedForGeneration = (seg.seed != 0) ? seg.seed : (selectedVoice != null && selectedVoice.getSeed() != 0 ? selectedVoice.getSeed() : ComfyUIClient.DEFAULT_SEED);
        currentSpeakerIdForGeneration = (seg.speakerId != null && !seg.speakerId.isBlank()) ? seg.speakerId : (selectedVoice != null && selectedVoice.getSpeakerId() != null && !selectedVoice.getSpeakerId().isBlank() ? selectedVoice.getSpeakerId() : ComfyUIClient.DEFAULT_CUSTOM_SPEAKER);
        if (seg.audioPath != null && !seg.audioPath.isEmpty()) {
            Path p = Paths.get(seg.audioPath);
            if (!p.isAbsolute() && audioDirPath != null) {
                p = audioDirPath.resolve(p);
            }
            if (Files.isRegularFile(p)) {
                loadAudioInPlayer(p);
                lastGeneratedAudioPath = p;
            }
        }
        if (trimStartSlider != null) trimStartSlider.setValue(0);
        setStatus("Segment zum Überarbeiten geladen.");
        refreshHighlight();
        // Signatur setzen, damit Speichern (auch mit „Anfang abschneiden“) die geladene Datei findet
        String segmentText = codeArea.getText(seg.start, seg.end);
        ComfyUIClient.SavedVoice v = voiceCombo.getSelectionModel().getSelectedItem();
        lastGeneratedSignature = buildTtsRequestSignature(segmentText, v, temperatureSlider.getValue(), topPSlider.getValue(), (int) Math.round(topKSlider.getValue()), repetitionPenaltySlider.getValue(), highQualityCheck.isSelected());
        if (lastGeneratedSignature != null && lastGeneratedAudioPath != null) {
            generatedAudioBySignature.put(lastGeneratedSignature, lastGeneratedAudioPath);
        }
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
        cleanupOrphanedAudioFiles();
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
        TtsSegment segmentAtCaret = (len > 0 && selStart >= 0 && selStart <= len) ? segmentAtOffset(selStart) : null;
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;
        while (pos < len) {
            boolean inSel = (pos >= selStart && pos < selEnd);
            boolean inSegmentWithCaret = editModeActive && segmentAtCaret != null && (pos >= segmentAtCaret.start && pos < segmentAtCaret.end);
            boolean inHover = inHoverRange && (pos >= hoverRangeStart && pos < hoverRangeEnd);
            String style = getSegmentStyleAt(pos, len, selStart, selEnd, segmentToSortedIndex);
            if (editModeActive && (inSel || inSegmentWithCaret)) style = "tts-selection";  // Im Bearbeitungsmodus: Auswahl + Segment unter Cursor immer Orange
            else if (style == null && inSel) style = "tts-selection";
            if (style == null && inHover) style = "tts-hover-preview";  // Grau; gleiche Logik wie Segmente
            int start = pos;
            while (pos < len) {
                boolean sel = (pos >= selStart && pos < selEnd);
                boolean segCaret = editModeActive && segmentAtCaret != null && (pos >= segmentAtCaret.start && pos < segmentAtCaret.end);
                boolean hov = inHoverRange && (pos >= hoverRangeStart && pos < hoverRangeEnd);
                String nextStyle = getSegmentStyleAt(pos, len, selStart, selEnd, segmentToSortedIndex);
                if (editModeActive && (sel || segCaret)) nextStyle = "tts-selection";
                else if (nextStyle == null && sel) nextStyle = "tts-selection";
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
        StringBuilder sb = new StringBuilder();
        sb.append(selectedText);
        sb.append("|").append(voice != null ? voice.getName() : "");
        sb.append("|").append(String.format(java.util.Locale.ROOT, "%.4f", temp));
        sb.append("|").append(String.format(java.util.Locale.ROOT, "%.4f", topP));
        sb.append("|").append(topK);
        sb.append("|").append(String.format(java.util.Locale.ROOT, "%.4f", repPen));
        sb.append("|").append(hq);
        sb.append("|vd:").append(getEffectiveVoiceDescription());
        sb.append("|seed:").append(currentSeedForGeneration);
        sb.append("|spk:").append(currentSpeakerIdForGeneration != null ? currentSpeakerIdForGeneration : "");
        if (voice != null && "elevenlabs".equalsIgnoreCase(voice.getProvider())) {
            if (elevenLabsModelCombo != null) {
                String m = elevenLabsModelCombo.getSelectionModel().getSelectedItem();
                sb.append("|elm:").append(m != null ? m : "");
            }
            sb.append("|sta:").append(String.format(java.util.Locale.ROOT, "%.4f", voice.getElevenLabsStability()));
            sb.append("|sim:").append(String.format(java.util.Locale.ROOT, "%.4f", voice.getElevenLabsSimilarityBoost()));
            sb.append("|spd:").append(String.format(java.util.Locale.ROOT, "%.4f", voice.getElevenLabsSpeed()));
            sb.append("|spb:").append(voice.isElevenLabsUseSpeakerBoost());
            sb.append("|sty:").append(String.format(java.util.Locale.ROOT, "%.4f", voice.getElevenLabsStyle()));
            sb.append("|vid:").append(voice.getElevenLabsVoiceId() != null ? voice.getElevenLabsVoiceId() : "");
        }
        if (voice != null && voice.isVoiceClone()) {
            sb.append("|ref:").append(voice.getRefAudioPath() != null ? voice.getRefAudioPath() : "");
            sb.append("|trn:").append(voice.getVoiceCloneTranscript() != null ? voice.getVoiceCloneTranscript() : "");
        }
        return sb.toString();
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

        // Kein Signatur-Check mehr: Wenn der User "Erstellen" drückt, wird immer neu generiert.
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
        final long effectiveSeed = seed;
        final String ttsText = prependEinschwingText(sel);
        CompletableFuture.runAsync(() -> {
            try {
                java.util.Map<String, String> lexicon = getLexiconFromTable();
                Path path = Files.createTempFile("manuskript-tts-", ".mp3");
                TtsBackend.generateTtsToFile(ttsText, effectiveVoice, lexicon, path, true, null, effectiveSeed);
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
                    CustomAlert errAlert = DialogFactory.createErrorAlert("TTS-Fehler", "Sprachausgabe fehlgeschlagen", msg, stage);
                    errAlert.applyTheme(themeIndex);
                    errAlert.showAndWait();
                    if (lastGeneratedAudioPath != null && Files.isRegularFile(lastGeneratedAudioPath)) {
                        loadAudioInPlayer(lastGeneratedAudioPath);
                        setStatus("Fehler – letzte Erzeugung wird abgespielt.");
                    } else {
                        setStatus("Fehler: " + msg);
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
        if (seekSettleTransition != null) {
            seekSettleTransition.stop();
            seekSettleTransition = null;
        }
        pendingSeekResume = false;
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
                    double trimSec = (trimStartSlider != null) ? trimStartSlider.getValue() : 0;
                    double totalSec = total.toSeconds();
                    double curSec = b.toSeconds();
                    double p;
                    if (trimSec > 0 && trimSec < totalSec && curSec >= trimSec) {
                        p = (curSec - trimSec) / (totalSec - trimSec);
                    } else {
                        p = totalSec > 0 ? curSec / totalSec : 0;
                    }
                    final double prog = Math.min(1.0, Math.max(0.0, p));
                    Platform.runLater(() -> playerProgress.setProgress(prog));
                }
            });
            player.setOnReady(() -> Platform.runLater(() -> {
                if (embeddedPlayer != player || !requestedPath.equals(pendingAudioPath)) return;
                playerProgress.setProgress(0);
                Duration total = player.getTotalDuration();
                if (total != null && total.toSeconds() > 0 && trimStartSlider != null) {
                    double maxSec = Math.min(20, total.toSeconds());
                    trimStartSlider.setMax(maxSec);
                    if (trimStartSlider.getValue() > maxSec) trimStartSlider.setValue(0);
                }
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
        // Einschwingtext auf FX-Thread lesen, bevor der Hintergrund-Thread startet
        final String einschwingPrefix;
        if (einschwingCheckBox != null && einschwingCheckBox.isSelected()
                && einschwingTextArea.getText() != null && !einschwingTextArea.getText().isBlank()) {
            einschwingPrefix = einschwingTextArea.getText().trim();
        } else {
            einschwingPrefix = null;
        }
        TTS_EXECUTOR.execute(() -> runBatch(unmarked, fullText, voice, byParagraph, einschwingPrefix));
    }

    private void runBatch(List<int[]> unmarked, String fullText, ComfyUIClient.SavedVoice voice, boolean byParagraph, String einschwingPrefix) {
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
            String ttsText = (einschwingPrefix != null) ? (einschwingPrefix + " " + text) : text;
            try {
                Path tempPath = Files.createTempFile("manuskript-batch-", ".mp3");
                TtsBackend.generateTtsToFile(ttsText, effectiveVoice, lexicon, tempPath, true, null, seed);
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
        ComfyUIClient.SavedVoice voice = voiceCombo.getSelectionModel().getSelectedItem();
        String voiceName = voice != null ? voice.getName() : "";
        double temp = temperatureSlider.getValue();
        String sel = codeArea.getSelectedText();
        String currentSig = buildTtsRequestSignature(sel != null ? sel : "", voice, temp, topPSlider.getValue(), (int) Math.round(topKSlider.getValue()), Math.max(1.0, Math.min(2.0, repetitionPenaltySlider.getValue())), highQualityCheck.isSelected());
        Path sourcePath = generatedAudioBySignature.get(currentSig);
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            sourcePath = lastGeneratedSignature != null && currentSig.equals(lastGeneratedSignature) && lastGeneratedAudioPath != null ? lastGeneratedAudioPath : null;
        }
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            setStatus("Zuerst „Erstellen“ ausführen oder ein Segment zum Überarbeiten auswählen.");
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
            double trimStartSec = (trimStartSlider != null && trimStartSlider.getValue() > 0) ? trimStartSlider.getValue() : 0;
            if (audioDirPath != null) {
                if (trimStartSec > 0) {
                    String err = trimAudioFromStart(sourcePath, target, trimStartSec);
                    if (err != null) {
                        setStatus("Beschneiden fehlgeschlagen: " + err + " – speichere unbeschnitten.");
                        Files.copy(sourcePath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.copy(sourcePath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
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
            selectedSegmentForColor = seg;
            saveSegments();
            refreshHighlight();
            codeArea.selectRange(start, end);
            codeArea.requestFollowCaret();
            setStatus("Gespeichert: " + baseName);
            if (trimStartSlider != null) trimStartSlider.setValue(0);
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
        if (ttsPreferences != null) {
            String last = ttsPreferences.get("tts_save_full_audio_dir", null);
            if (last != null && !last.isEmpty()) {
                File lastDir = new File(last);
                if (lastDir.isDirectory()) fc.setInitialDirectory(lastDir);
            } else if (dataDir != null && dataDir.isDirectory()) {
                fc.setInitialDirectory(dataDir.getParentFile());
            }
        }
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        if (ttsPreferences != null && f.getParent() != null) {
            try { ttsPreferences.put("tts_save_full_audio_dir", f.getParent()); } catch (Exception ignored) {}
        }
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

        java.io.File zipFile = new java.io.File(dir, "ffmpeg.zip");
        if (zipFile.isFile()) {
            logger.info("FFmpeg nicht gefunden – entpacke {} ...", zipFile.getAbsolutePath());
            try {
                extractZip(zipFile.toPath(), dir.toPath());
                logger.info("FFmpeg erfolgreich entpackt nach {}", dir.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Fehler beim Entpacken von ffmpeg.zip: {}", e.getMessage(), e);
                return null;
            }
            if (exe.isFile()) return exe.getAbsolutePath();
            if (binExe.isFile()) return binExe.getAbsolutePath();
        }

        return null;
    }

    /**
     * Entpackt eine ZIP-Datei in das angegebene Zielverzeichnis.
     * Vorhandene Dateien werden überschrieben. Schützt gegen Zip-Slip.
     */
    private static void extractZip(Path zipPath, Path targetDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                Files.newInputStream(zipPath))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Ungültiger ZIP-Eintrag: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (java.io.OutputStream os = Files.newOutputStream(entryPath)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Schneidet den Anfang einer Audiodatei ab (z. B. Einschwingen) und schreibt ab trimStartSec bis Ende nach target.
     * @return null bei Erfolg, sonst Fehlermeldung
     */
    private static String trimAudioFromStart(Path source, Path target, double trimStartSec) {
        String ffmpegExe = getFfmpegExePath();
        if (ffmpegExe == null) ffmpegExe = "ffmpeg";
        // -ss VOR -i = Input-Seeking: Ausgabe beginnt exakt ab dieser Position (wichtig für -c copy)
        List<String> cmd = List.of(
            ffmpegExe, "-y", "-loglevel", "error",
            "-ss", String.format(java.util.Locale.ROOT, "%.3f", trimStartSec),
            "-i", source.toAbsolutePath().toString(),
            "-c:a", "copy",
            target.toAbsolutePath().toString()
        );
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int code = p.waitFor();
            if (code != 0) return out.isEmpty() ? "Exit " + code : out.trim();
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
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
     * Legacy-Überladung ohne Hörbuch-Parameter (Bitrate/Stereo/Lead-Trail-Silence).
     * Verwendet VBR-Qualität, Mono, keine Lead/Trail-Silence.
     */
    public static String concatMp3FilesWithPause(List<Path> audioFiles, Path outputPath, double pauseSeconds, int mp3Quality, boolean logCommandsToConsole) {
        return concatMp3FilesWithPause(audioFiles, outputPath, pauseSeconds, mp3Quality, logCommandsToConsole, -1, false, 0, 0, null);
    }

    public static String concatMp3FilesWithPause(List<Path> audioFiles, Path outputPath, double pauseSeconds,
                                                  int mp3Quality, boolean logCommandsToConsole,
                                                  int bitrateKbps, boolean stereo,
                                                  double leadSilenceSec, double trailSilenceSec,
                                                  java.util.function.Consumer<String> logCallback) {
        return concatMp3FilesWithPauseImpl(audioFiles, outputPath, pauseSeconds, mp3Quality,
                logCommandsToConsole, bitrateKbps, stereo, leadSilenceSec, trailSilenceSec, logCallback);
    }

    /**
     * Fügt mehrere MP3-Dateien per FFmpeg mit Pause zwischen je zwei Dateien zusammen (Concat-Demuxer).
     * Öffentlich für Hörbuch-Erstellung. Einzelne Datei wird ebenfalls per FFmpeg verarbeitet (für Trim/Fade/Stereo).
     *
     * @param audioFiles          Liste der MP3-Pfade in Reihenfolge
     * @param outputPath          Ausgabedatei
     * @param pauseSeconds        Pause in Sekunden zwischen den Dateien
     * @param mp3Quality          FFmpeg -q:a (0 = beste, 9 = geringste); wird ignoriert wenn bitrateKbps &gt; 0
     * @param logCommandsToConsole true = FFmpeg-Befehle auf System.out
     * @param bitrateKbps         Bitrate in kbps (z. B. 320); &lt;= 0 = VBR mit mp3Quality
     * @param stereo              true = Stereo-Ausgabe (2 Kanäle), false = Mono
     * @param leadSilenceSec      Stille am Anfang in Sekunden (0 = keine); vorhandene Stille wird vorher getrimmt
     * @param trailSilenceSec     Stille am Ende in Sekunden (0 = keine); vorhandene Stille wird vorher getrimmt
     * @return null bei Erfolg, sonst Fehlermeldung
     */
    public static String concatMp3FilesWithPause(List<Path> audioFiles, Path outputPath, double pauseSeconds,
                                                  int mp3Quality, boolean logCommandsToConsole,
                                                  int bitrateKbps, boolean stereo,
                                                  double leadSilenceSec, double trailSilenceSec) {
        return concatMp3FilesWithPauseImpl(audioFiles, outputPath, pauseSeconds, mp3Quality,
                logCommandsToConsole, bitrateKbps, stereo, leadSilenceSec, trailSilenceSec, null);
    }

    private static void log(java.util.function.Consumer<String> cb, String msg) {
        if (cb != null) cb.accept(msg);
    }

    private static String concatMp3FilesWithPauseImpl(List<Path> audioFiles, Path outputPath, double pauseSeconds,
                                                  int mp3Quality, boolean logCommandsToConsole,
                                                  int bitrateKbps, boolean stereo,
                                                  double leadSilenceSec, double trailSilenceSec,
                                                  java.util.function.Consumer<String> logCallback) {
        int n = audioFiles.size();
        if (n == 0) return "Keine Dateien";

        boolean hasAudiobookProcessing = bitrateKbps > 0 || stereo || leadSilenceSec > 0 || trailSilenceSec > 0;

        String ffmpegExe = getFfmpegExePath();
        if (ffmpegExe == null) ffmpegExe = "ffmpeg";

        if (n == 1 && !hasAudiobookProcessing) {
            log(logCallback, "  Einzelne Datei, kein Processing -> kopiere direkt.");
            try {
                Files.copy(audioFiles.get(0), outputPath, StandardCopyOption.REPLACE_EXISTING);
                return null;
            } catch (IOException e) {
                return e.getMessage();
            }
        }

        Path listPath = null;
        Path silencePath = null;
        Path rawConcatPath = null;
        try {
            Path tempDir = Files.createTempDirectory("manuskript-ffmpeg");
            silencePath = tempDir.resolve("silence.mp3");
            listPath = tempDir.resolve("concatlist.txt");
            int q = Math.max(0, Math.min(9, mp3Quality));

            List<String> qualityArgs = new ArrayList<>();
            qualityArgs.add("-c:a");
            qualityArgs.add("libmp3lame");
            if (bitrateKbps > 0) {
                qualityArgs.add("-b:a");
                qualityArgs.add(bitrateKbps + "k");
            } else {
                qualityArgs.add("-q:a");
                qualityArgs.add(String.valueOf(q));
            }

            // --- Schritt 1: Stille-Datei erzeugen ---
            if (n > 1) {
                log(logCallback, "  [1/4] Erzeuge Stille-Datei (" + pauseSeconds + "s)");
                String channelLayout = stereo ? "stereo" : "mono";
                List<String> silenceCmd = new ArrayList<>(List.of(
                    ffmpegExe, "-y", "-loglevel", "error",
                    "-f", "lavfi", "-i", "anullsrc=r=44100:cl=" + channelLayout,
                    "-t", String.valueOf(pauseSeconds)
                ));
                silenceCmd.addAll(qualityArgs);
                silenceCmd.add(silencePath.toAbsolutePath().toString());
                if (logCommandsToConsole) {
                    System.out.println("--- FFmpeg (Stille) ---");
                    System.out.println(formatCommandForCmd(silenceCmd));
                }
                ProcessBuilder pbSilence = new ProcessBuilder(silenceCmd);
                pbSilence.redirectError(java.io.File.createTempFile("ffmpeg-silence-", ".log"));
                Process procSilence = pbSilence.start();
                if (procSilence.waitFor() != 0) return "Stille-Datei konnte nicht erzeugt werden";
                if (!Files.isRegularFile(silencePath)) return "Stille-Datei fehlt";
            }

            // --- Schritt 2: Concat-Liste schreiben ---
            Path concatSource;
            if (n == 1) {
                concatSource = audioFiles.get(0);
                log(logCallback, "  [1/4] Einzelsegment, ueberspringe Concat.");
            } else {
                log(logCallback, "  [2/4] Concat-Liste: " + n + " Segmente + " + (n - 1) + " Pausen");
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
                concatSource = null;
            }

            // --- Schritt 3: Zusammenfuegen ---
            if (n > 1) {
                log(logCallback, "  [3/4] FFmpeg Concat...");
                Path targetPath;
                if (hasAudiobookProcessing) {
                    rawConcatPath = tempDir.resolve("raw_concat.mp3");
                    targetPath = rawConcatPath;
                } else {
                    targetPath = outputPath;
                }

                List<String> concatCmd = new ArrayList<>(List.of(
                    ffmpegExe, "-y", "-loglevel", "warning",
                    "-f", "concat", "-safe", "0", "-i", listPath.toAbsolutePath().toString()
                ));
                concatCmd.addAll(qualityArgs);
                concatCmd.add(targetPath.toAbsolutePath().toString());
                if (logCommandsToConsole) {
                    System.out.println("--- FFmpeg (Concat) ---");
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
                } finally {
                    try { errLog.delete(); } catch (Exception ignored) { }
                }
                log(logCallback, "  [3/4] Concat abgeschlossen.");
            }

            // --- Schritt 4: Audiobook-Processing ---
            if (hasAudiobookProcessing) {
                log(logCallback, "  [4/4] Audiobook-Processing (Filter + Normalisierung)...");
                Path inputForFilter = (n == 1) ? concatSource : rawConcatPath;
                String filterResult = applyAudiobookFilter(ffmpegExe, inputForFilter, outputPath,
                        bitrateKbps, stereo, leadSilenceSec, trailSilenceSec, logCommandsToConsole, logCallback);
                if (filterResult != null) return filterResult;
            }

            return null;
        } catch (Exception e) {
            return "FFmpeg: " + e.getMessage();
        } finally {
            try {
                if (listPath != null) Files.deleteIfExists(listPath);
                if (silencePath != null) Files.deleteIfExists(silencePath);
                if (rawConcatPath != null) Files.deleteIfExists(rawConcatPath);
                if (silencePath != null && silencePath.getParent() != null) Files.deleteIfExists(silencePath.getParent());
            } catch (IOException ignored) { }
        }
    }

    /** Maximale Pausenlaenge in Sekunden innerhalb eines Kapitels. Laengere Pausen werden automatisch gekuerzt. */
    private static final double MAX_INTERNAL_PAUSE_SECONDS = 2.0;
    /** Auf diese Dauer werden zu lange Pausen gekuerzt. */
    private static final double TRIMMED_PAUSE_SECONDS = 1.5;
    /** Schwellwert in dB fuer die Stille-Erkennung (alles darunter gilt als Stille). */
    private static final double SILENCE_THRESHOLD_DB = -40.0;

    /**
     * Wendet Audiobook-Filterchain auf eine Audiodatei an:
     * 1. Vorhandene Stille am Anfang entfernen (silenceremove)
     * 2. Lead-Silence einfügen + Fade-in
     * 3. Trail-Silence (reine Stille) am Ende anfügen
     * 4. Stereo/Mono + CBR
     * 5. Zu lange interne Pausen kuerzen (> MAX_INTERNAL_PAUSE_SECONDS → TRIMMED_PAUSE_SECONDS)
     * 6. 2-Pass Loudness-Normalisierung (EBU R128 / xinxii-konform):
     *    – Integrated Loudness: –18 LUFS (Zielbereich –15 bis –23 dB RMS)
     *    – True Peak: max. –3 dBFS
     *    – LRA (Loudness Range): 7
     * 7. RMS/Peak-Verifikation: Prüft ob RMS zwischen –23 und –15 dB liegt
     *
     * @return null bei Erfolg, sonst Fehlermeldung
     */
    private static String applyAudiobookFilter(String ffmpegExe, Path input, Path output,
                                                int bitrateKbps, boolean stereo,
                                                double leadSilenceSec, double trailSilenceSec,
                                                boolean logCommandsToConsole,
                                                java.util.function.Consumer<String> logCallback) {
        // Filterchain zusammenbauen
        StringBuilder af = new StringBuilder();

        // Stille am Anfang entfernen (Threshold -50dB).
        if (leadSilenceSec > 0) {
            af.append("silenceremove=start_periods=1:start_threshold=-50dB");
        }

        // Lead-Silence: adelay fuegt exakte Stille am Anfang ein (Millisekunden) + Fade-in
        if (leadSilenceSec > 0) {
            int delayMs = (int) (leadSilenceSec * 1000);
            if (af.length() > 0) af.append(",");
            af.append("adelay=").append(delayMs).append("|").append(delayMs);
            af.append(",afade=t=in:d=").append(String.format(java.util.Locale.ROOT, "%.2f", leadSilenceSec));
        }

        // Trail-Silence: apad fuegt reine Stille am Ende an (kein Fade-out)
        if (trailSilenceSec > 0) {
            if (af.length() > 0) af.append(",");
            af.append("apad=pad_dur=").append(String.format(java.util.Locale.ROOT, "%.2f", trailSilenceSec));
        }

        // --- Schritt 1: Bestehende Filterchain anwenden ---
        log(logCallback, "    Filter: Trim/Fade/Stille/Stereo...");
        Path tempProcessed = null;
        Path tempPauseTrimmed = null;
        try {
            tempProcessed = Files.createTempFile("manuskript-abfilter-", ".mp3");

            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegExe);
            cmd.add("-y");
            cmd.add("-loglevel");
            cmd.add("warning");
            cmd.add("-i");
            cmd.add(input.toAbsolutePath().toString());

            if (af.length() > 0) {
                cmd.add("-af");
                cmd.add(af.toString());
            }

            cmd.add("-ac");
            cmd.add(stereo ? "2" : "1");
            cmd.add("-ar");
            cmd.add("44100");
            cmd.add("-c:a");
            cmd.add("libmp3lame");
            if (bitrateKbps > 0) {
                cmd.add("-b:a");
                cmd.add(bitrateKbps + "k");
            }
            cmd.add(tempProcessed.toAbsolutePath().toString());

            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (Audiobook-Filter: Trim + Fade-in + Stille + Stereo/CBR), zum Einfuegen in cmd ---");
                System.out.println(formatCommandForCmd(cmd));
            }

            java.io.File errLog = java.io.File.createTempFile("ffmpeg-abfilter-", ".log");
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectError(errLog);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process proc = pb.start();
                int exit = proc.waitFor();
                String err = Files.readString(errLog.toPath(), StandardCharsets.UTF_8).trim();
                if (exit != 0) return err.isEmpty() ? "FFmpeg Audiobook-Filter beendet mit Code " + exit : err;
            } finally {
                try { errLog.delete(); } catch (Exception ignored) { }
            }

            // --- Schritt 2: Zu lange Pausen kuerzen ---
            log(logCallback, "    Pausenkuerzung (max " + MAX_INTERNAL_PAUSE_SECONDS + "s -> " + TRIMMED_PAUSE_SECONDS + "s)...");
            tempPauseTrimmed = Files.createTempFile("manuskript-pausetrim-", ".mp3");
            String pauseResult = trimLongPauses(ffmpegExe, tempProcessed, tempPauseTrimmed,
                    MAX_INTERNAL_PAUSE_SECONDS, TRIMMED_PAUSE_SECONDS, SILENCE_THRESHOLD_DB,
                    bitrateKbps, stereo, logCommandsToConsole, logCallback);
            Path inputForLoudnorm;
            if (pauseResult == null) {
                inputForLoudnorm = tempPauseTrimmed;
            } else if ("NO_LONG_PAUSES".equals(pauseResult)) {
                log(logCallback, "    Keine Pausen > " + MAX_INTERNAL_PAUSE_SECONDS + "s gefunden.");
                inputForLoudnorm = tempProcessed;
            } else {
                log(logCallback, "    Pausenkuerzung fehlgeschlagen: " + pauseResult);
                inputForLoudnorm = tempProcessed;
            }

            // --- Schritt 3: 2-Pass Loudness-Normalisierung (xinxii-konform) ---
            log(logCallback, "    Loudness-Normalisierung (2-Pass, I=-18 LUFS, TP=-3 dBFS)...");
            String loudnormResult = applyLoudnessNormalization(ffmpegExe, inputForLoudnorm, output,
                    bitrateKbps, stereo, logCommandsToConsole, logCallback);
            if (loudnormResult != null) return loudnormResult;

            return null;
        } catch (Exception e) {
            return "FFmpeg Audiobook-Filter: " + e.getMessage();
        } finally {
            if (tempProcessed != null) {
                try { Files.deleteIfExists(tempProcessed); } catch (IOException ignored) { }
            }
            if (tempPauseTrimmed != null) {
                try { Files.deleteIfExists(tempPauseTrimmed); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * Erkennt Pausen laenger als {@code maxPauseSec} und kuerzt sie auf {@code targetPauseSec}.
     * <ol>
     *   <li>Lauf 1: {@code silencedetect} findet alle stillen Segmente</li>
     *   <li>Berechnung der Schnitt-Punkte: Fuer jede Pause &gt; maxPauseSec wird ein Trim-Bereich berechnet</li>
     *   <li>Lauf 2: Zusammenbau per {@code atrim + concat} Filterchain</li>
     * </ol>
     *
     * @return null bei Erfolg, "NO_LONG_PAUSES" wenn nichts zu tun war, sonst Fehlermeldung
     */
    private static String trimLongPauses(String ffmpegExe, Path input, Path output,
                                          double maxPauseSec, double targetPauseSec,
                                          double thresholdDb,
                                          int bitrateKbps, boolean stereo,
                                          boolean logCommandsToConsole,
                                          java.util.function.Consumer<String> logCallback) {
        try {
            // --- Schritt 1: Stille-Segmente erkennen ---
            List<String> detectCmd = List.of(
                ffmpegExe, "-i", input.toAbsolutePath().toString(),
                "-af", String.format(java.util.Locale.ROOT,
                    "silencedetect=noise=%ddB:d=%.2f", (int) thresholdDb, maxPauseSec),
                "-f", "null", "-"
            );
            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (Pausen-Erkennung: silencedetect) ---");
                System.out.println(formatCommandForCmd(detectCmd));
            }
            ProcessBuilder pb = new ProcessBuilder(detectCmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String detectOutput = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();

            // silence_start: 12.345
            // silence_end: 16.789 | silence_duration: 4.444
            List<double[]> silenceSegments = new ArrayList<>();
            double lastStart = -1;
            for (String line : detectOutput.split("\n")) {
                line = line.trim();
                if (line.contains("silence_start:")) {
                    String val = line.substring(line.indexOf("silence_start:") + 14).trim();
                    int spaceIdx = val.indexOf(' ');
                    if (spaceIdx > 0) val = val.substring(0, spaceIdx);
                    try { lastStart = Double.parseDouble(val); } catch (NumberFormatException ignored) {}
                }
                if (line.contains("silence_end:") && lastStart >= 0) {
                    String val = line.substring(line.indexOf("silence_end:") + 12).trim();
                    int pipeIdx = val.indexOf('|');
                    if (pipeIdx > 0) val = val.substring(0, pipeIdx).trim();
                    int spaceIdx = val.indexOf(' ');
                    if (spaceIdx > 0) val = val.substring(0, spaceIdx);
                    try {
                        double end = Double.parseDouble(val);
                        double duration = end - lastStart;
                        if (duration > maxPauseSec) {
                            silenceSegments.add(new double[]{lastStart, end, duration});
                        }
                    } catch (NumberFormatException ignored) {}
                    lastStart = -1;
                }
            }

            if (silenceSegments.isEmpty()) {
                logger.info("Keine Pausen laenger als {} s gefunden.", maxPauseSec);
                return "NO_LONG_PAUSES";
            }

            logger.info("{} Pause(n) laenger als {} s gefunden, kuerze auf {} s:",
                    silenceSegments.size(), maxPauseSec, targetPauseSec);
            log(logCallback, "    " + silenceSegments.size() + " Pause(n) > " + maxPauseSec + "s gefunden:");
            for (double[] seg : silenceSegments) {
                String info = String.format("      %.1fs-%.1fs (Dauer: %.1fs)", seg[0], seg[1], seg[2]);
                logger.info("  Pause bei {}-{} s (Dauer: {} s)",
                        String.format("%.2f", seg[0]), String.format("%.2f", seg[1]), String.format("%.2f", seg[2]));
                log(logCallback, info);
            }

            // --- Schritt 2: Schnitt-Punkte berechnen ---
            // Fuer jede Pause: behalte targetPauseSec/2 am Anfang und targetPauseSec/2 am Ende
            // Schneide den mittleren Teil heraus
            double halfTarget = targetPauseSec / 2.0;
            // Sammle die "keep"-Bereiche: [start, end] des zu behaltenden Audios
            List<double[]> keepSegments = new ArrayList<>();
            double currentPos = 0;
            for (double[] seg : silenceSegments) {
                double silStart = seg[0];
                double silEnd = seg[1];
                // Audio bis Mitte der Pause-Anfang behalten
                double keepEnd = silStart + halfTarget;
                if (currentPos < keepEnd) {
                    keepSegments.add(new double[]{currentPos, keepEnd});
                }
                // Naechster Abschnitt startet halfTarget vor dem Pause-Ende
                currentPos = silEnd - halfTarget;
            }
            // Rest der Datei
            keepSegments.add(new double[]{currentPos, Double.MAX_VALUE});

            // --- Schritt 3: Zusammenbauen per atrim + concat ---
            int segCount = keepSegments.size();
            StringBuilder filterComplex = new StringBuilder();
            for (int i = 0; i < segCount; i++) {
                double[] seg = keepSegments.get(i);
                if (seg[1] == Double.MAX_VALUE) {
                    filterComplex.append(String.format(java.util.Locale.ROOT,
                        "[0:a]atrim=start=%.4f,asetpts=PTS-STARTPTS[s%d];", seg[0], i));
                } else {
                    filterComplex.append(String.format(java.util.Locale.ROOT,
                        "[0:a]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS[s%d];", seg[0], seg[1], i));
                }
            }
            // Concat
            for (int i = 0; i < segCount; i++) {
                filterComplex.append(String.format("[s%d]", i));
            }
            filterComplex.append(String.format("concat=n=%d:v=0:a=1[out]", segCount));

            List<String> trimCmd = new ArrayList<>();
            trimCmd.add(ffmpegExe);
            trimCmd.add("-y");
            trimCmd.add("-loglevel");
            trimCmd.add("warning");
            trimCmd.add("-i");
            trimCmd.add(input.toAbsolutePath().toString());
            trimCmd.add("-filter_complex");
            trimCmd.add(filterComplex.toString());
            trimCmd.add("-map");
            trimCmd.add("[out]");
            trimCmd.add("-ac");
            trimCmd.add(stereo ? "2" : "1");
            trimCmd.add("-ar");
            trimCmd.add("44100");
            trimCmd.add("-c:a");
            trimCmd.add("libmp3lame");
            if (bitrateKbps > 0) {
                trimCmd.add("-b:a");
                trimCmd.add(bitrateKbps + "k");
            }
            trimCmd.add(output.toAbsolutePath().toString());

            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (Pausen-Kuerzung: " + silenceSegments.size() + " Pausen) ---");
                System.out.println(formatCommandForCmd(trimCmd));
            }

            java.io.File errLog = java.io.File.createTempFile("ffmpeg-pausetrim-", ".log");
            try {
                ProcessBuilder pb2 = new ProcessBuilder(trimCmd);
                pb2.redirectError(errLog);
                pb2.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process proc2 = pb2.start();
                int exit = proc2.waitFor();
                String err = Files.readString(errLog.toPath(), StandardCharsets.UTF_8).trim();
                if (exit != 0) return err.isEmpty() ? "Pausen-Kuerzung fehlgeschlagen (Code " + exit + ")" : err;
            } finally {
                try { errLog.delete(); } catch (Exception ignored) { }
            }

            logger.info("Pausenkuerzung abgeschlossen: {} Pause(n) auf {} s gekuerzt.", silenceSegments.size(), targetPauseSec);
            log(logCallback, "    Pausenkuerzung: " + silenceSegments.size() + " Pausen auf " + targetPauseSec + "s gekuerzt.");
            return null;
        } catch (Exception e) {
            return "Pausenkuerzung: " + e.getMessage();
        }
    }

    /**
     * 2-Pass Loudness-Normalisierung nach EBU R128 / xinxii-Standard.
     * <ul>
     *   <li>Pass 1: Messung der aktuellen Lautstärke mit {@code loudnorm print_format=json}</li>
     *   <li>Pass 2: Normalisierung mit gemessenen Werten auf Zielwerte:
     *       Integrated Loudness –18 LUFS, True Peak –3 dBFS, LRA 7</li>
     *   <li>Pass 3: RMS/Peak-Verifikation und ggf. Korrektur auf –15 bis –23 dB RMS</li>
     * </ul>
     *
     * @return null bei Erfolg, sonst Fehlermeldung
     */
    private static String applyLoudnessNormalization(String ffmpegExe, Path input, Path output,
                                                      int bitrateKbps, boolean stereo,
                                                      boolean logCommandsToConsole,
                                                      java.util.function.Consumer<String> logCallback) {
        try {
            // --- Pass 1: Messung ---
            List<String> measureCmd = new ArrayList<>(List.of(
                ffmpegExe, "-y", "-loglevel", "info",
                "-i", input.toAbsolutePath().toString(),
                "-af", "loudnorm=I=-18:TP=-3:LRA=7:print_format=json",
                "-f", "null", "-"
            ));

            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (Loudnorm Pass 1: Messung) ---");
                System.out.println(formatCommandForCmd(measureCmd));
            }

            ProcessBuilder pb1 = new ProcessBuilder(measureCmd);
            pb1.redirectErrorStream(true);
            Process proc1 = pb1.start();
            String measureOutput = new String(proc1.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit1 = proc1.waitFor();
            if (exit1 != 0) return "Loudnorm-Messung fehlgeschlagen (Code " + exit1 + ")";

            // JSON-Block aus der ffmpeg-Ausgabe extrahieren (steht am Ende)
            int jsonStart = measureOutput.lastIndexOf('{');
            int jsonEnd = measureOutput.lastIndexOf('}');
            if (jsonStart < 0 || jsonEnd < 0 || jsonEnd <= jsonStart) {
                return "Loudnorm-Messung: JSON nicht in ffmpeg-Ausgabe gefunden";
            }
            String json = measureOutput.substring(jsonStart, jsonEnd + 1);

            String measuredI = extractLoudnormJsonValue(json, "input_i");
            String measuredTP = extractLoudnormJsonValue(json, "input_tp");
            String measuredLRA = extractLoudnormJsonValue(json, "input_lra");
            String measuredThresh = extractLoudnormJsonValue(json, "input_thresh");
            String targetOffset = extractLoudnormJsonValue(json, "target_offset");

            if (measuredI == null || measuredTP == null || measuredLRA == null
                    || measuredThresh == null || targetOffset == null) {
                return "Loudnorm-Messung: Werte konnten nicht aus JSON extrahiert werden";
            }

            logger.info("Loudnorm-Messung: I={} LUFS, TP={} dBFS, LRA={}, Thresh={}, Offset={}",
                    measuredI, measuredTP, measuredLRA, measuredThresh, targetOffset);
            log(logCallback, "    Pass 1 Messung: I=" + measuredI + " LUFS, TP=" + measuredTP + " dBFS, LRA=" + measuredLRA);

            // --- Pass 2: Normalisierung mit gemessenen Werten ---
            String loudnormFilter = String.format(java.util.Locale.ROOT,
                "loudnorm=I=-18:TP=-3:LRA=7:measured_I=%s:measured_TP=%s:measured_LRA=%s:measured_thresh=%s:offset=%s:linear=true",
                measuredI, measuredTP, measuredLRA, measuredThresh, targetOffset);

            List<String> normalizeCmd = new ArrayList<>();
            normalizeCmd.add(ffmpegExe);
            normalizeCmd.add("-y");
            normalizeCmd.add("-loglevel");
            normalizeCmd.add("warning");
            normalizeCmd.add("-i");
            normalizeCmd.add(input.toAbsolutePath().toString());
            normalizeCmd.add("-af");
            normalizeCmd.add(loudnormFilter);
            normalizeCmd.add("-ac");
            normalizeCmd.add(stereo ? "2" : "1");
            normalizeCmd.add("-ar");
            normalizeCmd.add("44100");
            normalizeCmd.add("-c:a");
            normalizeCmd.add("libmp3lame");
            if (bitrateKbps > 0) {
                normalizeCmd.add("-b:a");
                normalizeCmd.add(bitrateKbps + "k");
            }
            normalizeCmd.add(output.toAbsolutePath().toString());

            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (Loudnorm Pass 2: Normalisierung) ---");
                System.out.println(formatCommandForCmd(normalizeCmd));
            }

            java.io.File errLog = java.io.File.createTempFile("ffmpeg-loudnorm-", ".log");
            try {
                ProcessBuilder pb2 = new ProcessBuilder(normalizeCmd);
                pb2.redirectError(errLog);
                pb2.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process proc2 = pb2.start();
                int exit2 = proc2.waitFor();
                String err = Files.readString(errLog.toPath(), StandardCharsets.UTF_8).trim();
                if (exit2 != 0) return err.isEmpty() ? "Loudnorm-Normalisierung fehlgeschlagen (Code " + exit2 + ")" : err;
            } finally {
                try { errLog.delete(); } catch (Exception ignored) { }
            }

            logger.info("Loudness-Normalisierung abgeschlossen → {}", output.getFileName());
            log(logCallback, "    Pass 2 Normalisierung abgeschlossen.");

            // --- Schritt 3: RMS/Peak-Verifikation und ggf. Korrektur ---
            log(logCallback, "    RMS/Peak-Verifikation (xinxii: -23...-15 dB, Peak max -3 dBFS)...");
            String acxResult = verifyAndCorrectAcxLevels(ffmpegExe, output, bitrateKbps, stereo, logCommandsToConsole, logCallback);
            if (acxResult != null) return acxResult;

            return null;
        } catch (Exception e) {
            return "Loudness-Normalisierung: " + e.getMessage();
        }
    }

    /**
     * Misst RMS-Pegel und Peak der Ausgabedatei und korrigiert ggf.:
     * - RMS soll zwischen -23 dB und -15 dB liegen (xinxii-Standard)
     * - Peak soll max. -3 dBFS sein
     * Falls die Werte ausserhalb liegen, wird ein volume-Filter + Limiter angewendet.
     *
     * @return null bei Erfolg, sonst Fehlermeldung
     */
    private static String verifyAndCorrectAcxLevels(String ffmpegExe, Path audioFile,
                                                     int bitrateKbps, boolean stereo,
                                                     boolean logCommandsToConsole,
                                                     java.util.function.Consumer<String> logCallback) {
        try {
            double TARGET_RMS = -19.0; // Sichere Mitte des xinxii-Fensters (-23...-15)
            double RMS_MIN = -23.0;
            double RMS_MAX = -15.0;
            double PEAK_MAX = -3.0;

            // --- Messung: astats liefert RMS_level und Peak_level ---
            List<String> measureCmd = List.of(
                ffmpegExe, "-i", audioFile.toAbsolutePath().toString(),
                "-af", "astats=metadata=1:reset=0,ametadata=print:key=lavfi.astats.Overall.RMS_level:key=lavfi.astats.Overall.Peak_level",
                "-f", "null", "-"
            );
            if (logCommandsToConsole) {
                System.out.println("--- FFmpeg (ACX-Level-Messung) ---");
                System.out.println(formatCommandForCmd(measureCmd));
            }
            ProcessBuilder pb = new ProcessBuilder(measureCmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String measureOutput = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();

            double measuredRms = Double.NaN;
            double measuredPeak = Double.NaN;

            // Letztes Vorkommen der Werte suchen (Gesamtstatistik am Ende)
            for (String line : measureOutput.split("\n")) {
                line = line.trim();
                if (line.contains("lavfi.astats.Overall.RMS_level=")) {
                    String val = line.substring(line.indexOf('=') + 1).trim();
                    try { measuredRms = Double.parseDouble(val); } catch (NumberFormatException ignored) {}
                }
                if (line.contains("lavfi.astats.Overall.Peak_level=")) {
                    String val = line.substring(line.indexOf('=') + 1).trim();
                    try { measuredPeak = Double.parseDouble(val); } catch (NumberFormatException ignored) {}
                }
            }

            String rmsStr = String.format("%.1f", measuredRms);
            String peakStr = String.format("%.1f", measuredPeak);
            log(logCallback, "    Messung: RMS=" + rmsStr + " dB, Peak=" + peakStr + " dBFS");
            logger.info("ACX-Verifikation: RMS={} dB, Peak={} dBFS (Ziel: RMS {}/{}  Peak max {})",
                    rmsStr, peakStr,
                    RMS_MIN, RMS_MAX, PEAK_MAX);

            if (Double.isNaN(measuredRms) || Double.isNaN(measuredPeak)) {
                logger.warn("ACX-Verifikation: Messwerte konnten nicht ermittelt werden, ueberspringe Korrektur.");
                return null;
            }

            boolean rmsOk = measuredRms >= RMS_MIN && measuredRms <= RMS_MAX;
            boolean peakOk = measuredPeak <= PEAK_MAX;

            if (rmsOk && peakOk) {
                logger.info("ACX-Level OK: RMS={} dB, Peak={} dBFS", rmsStr, peakStr);
                log(logCallback, "    Level OK: RMS=" + rmsStr + " dB, Peak=" + peakStr + " dBFS");
                return null;
            }

            // Gain berechnen, um RMS auf Ziel zu bringen
            double gainDb = 0;
            if (!rmsOk) {
                gainDb = TARGET_RMS - measuredRms;
            }

            // Peak nach Gain pruefen: wenn Peak + Gain > PEAK_MAX, Gain reduzieren
            double projectedPeak = measuredPeak + gainDb;
            if (projectedPeak > PEAK_MAX) {
                gainDb = PEAK_MAX - measuredPeak;
            }

            logger.info("ACX-Korrektur: Gain={} dB anwenden + Limiter bei {} dBFS",
                    String.format("%.2f", gainDb), PEAK_MAX);
            log(logCallback, "    Korrektur: Gain=" + String.format("%.2f", gainDb) + " dB + Limiter bei " + PEAK_MAX + " dBFS");

            // Filter: volume + alimiter (True-Peak-Limiter)
            String filter = String.format(java.util.Locale.ROOT,
                "volume=%.2fdB,alimiter=limit=%.1fdB:attack=5:release=50:level=false",
                gainDb, Math.pow(10, PEAK_MAX / 20.0)); // alimiter limit in linear, aber wir nutzen dBFS-Notation

            // alimiter 'limit' ist linear (0..1), berechnen aus dBFS
            double limitLinear = Math.pow(10, PEAK_MAX / 20.0);
            filter = String.format(java.util.Locale.ROOT,
                "volume=%.2fdB,alimiter=limit=%f:attack=5:release=50:level=false",
                gainDb, limitLinear);

            Path tempCorrected = Files.createTempFile("manuskript-acxcorr-", ".mp3");
            try {
                List<String> corrCmd = new ArrayList<>();
                corrCmd.add(ffmpegExe);
                corrCmd.add("-y");
                corrCmd.add("-loglevel");
                corrCmd.add("warning");
                corrCmd.add("-i");
                corrCmd.add(audioFile.toAbsolutePath().toString());
                corrCmd.add("-af");
                corrCmd.add(filter);
                corrCmd.add("-ac");
                corrCmd.add(stereo ? "2" : "1");
                corrCmd.add("-ar");
                corrCmd.add("44100");
                corrCmd.add("-c:a");
                corrCmd.add("libmp3lame");
                if (bitrateKbps > 0) {
                    corrCmd.add("-b:a");
                    corrCmd.add(bitrateKbps + "k");
                }
                corrCmd.add(tempCorrected.toAbsolutePath().toString());

                if (logCommandsToConsole) {
                    System.out.println("--- FFmpeg (ACX-Level-Korrektur) ---");
                    System.out.println(formatCommandForCmd(corrCmd));
                }

                java.io.File errLog = java.io.File.createTempFile("ffmpeg-acxcorr-", ".log");
                try {
                    ProcessBuilder pb2 = new ProcessBuilder(corrCmd);
                    pb2.redirectError(errLog);
                    pb2.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    Process proc2 = pb2.start();
                    int exit = proc2.waitFor();
                    String err = Files.readString(errLog.toPath(), StandardCharsets.UTF_8).trim();
                    if (exit != 0) return err.isEmpty() ? "ACX-Korrektur fehlgeschlagen (Code " + exit + ")" : err;
                } finally {
                    try { errLog.delete(); } catch (Exception ignored) { }
                }

                // Korrigierte Datei ueber das Original kopieren
                Files.move(tempCorrected, audioFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("ACX-Level-Korrektur angewendet auf {}", audioFile.getFileName());
                log(logCallback, "    Level-Korrektur angewendet.");
                return null;
            } finally {
                try { Files.deleteIfExists(tempCorrected); } catch (IOException ignored) { }
            }
        } catch (Exception e) {
            logger.warn("ACX-Verifikation fehlgeschlagen: {}", e.getMessage());
            return null; // Nicht-kritisch: Normalisierung lief bereits
        }
    }

    /**
     * Extrahiert einen String-Wert aus dem loudnorm-JSON-Block.
     * Erwartet Format: {@code "key" : "value"}.
     */
    private static String extractLoudnormJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd).trim();
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
        // Verwaiste Audio-Dateien aufräumen (block_XXX.mp3, die kein Segment mehr referenziert)
        cleanupOrphanedAudioFiles();
    }

    /**
     * Löscht block_XXX.mp3-Dateien im Audio-Verzeichnis, die von keinem Segment referenziert werden.
     * Wird nach removeOrphanedSegments() und nach dem Löschen einzelner Segmente aufgerufen.
     */
    private void cleanupOrphanedAudioFiles() {
        if (audioDirPath == null || !Files.isDirectory(audioDirPath)) {
            logger.info("cleanupOrphanedAudioFiles: audioDirPath={} existiert nicht oder ist null", audioDirPath);
            return;
        }
        // Alle von Segmenten referenzierten Dateien sammeln (normalisierte Dateinamen)
        Set<String> referencedFileNames = new HashSet<>();
        for (TtsSegment seg : segments) {
            if (seg.audioPath != null && !seg.audioPath.isEmpty()) {
                String fileName = Paths.get(seg.audioPath).getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                referencedFileNames.add(fileName);
            }
        }
        logger.info("cleanupOrphanedAudioFiles: {} Segmente, referenzierte Dateien: {}", segments.size(), referencedFileNames);
        // Alle block_*.mp3 im Audio-Verzeichnis prüfen
        int deleted = 0;
        int total = 0;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(audioDirPath, "block_*.mp3")) {
            for (Path file : stream) {
                total++;
                String fileName = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (!referencedFileNames.contains(fileName)) {
                    try {
                        Files.delete(file);
                        deleted++;
                        logger.info("Verwaiste Audio-Datei gelöscht: {}", file.getFileName());
                    } catch (IOException e) {
                        logger.warn("Verwaiste Audio-Datei konnte nicht gelöscht werden: {}", file, e);
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Aufräumen verwaister Audio-Dateien in {}", audioDirPath, e);
        }
        logger.info("cleanupOrphanedAudioFiles: {} Dateien im Ordner, {} gelöscht, {} behalten.", total, deleted, total - deleted);
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
     * Beim ersten Oeffnen: Hash des Original-MD speichern.
     * Bei erneutem Oeffnen: wenn sich der Originaltext geaendert hat, Diff-Dialog mit selektiver Uebernahme zeigen.
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

            // Originaltext wurde geaendert – zuerst fragen, was der Nutzer tun will
            String ttsContent = codeArea != null ? codeArea.getText() : "";
            DiffProcessor.DiffResult diffResult = DiffProcessor.createDiff(ttsContent, currentOriginalContent);
            if (!diffResult.hasChanges()) {
                Files.writeString(originalHashPath, currentHash, StandardCharsets.UTF_8);
                return;
            }

            // Vorab-Dialog: Diff anzeigen oder ignorieren?
            ButtonType btnDiff = new ButtonType("Diff anzeigen");
            ButtonType btnIgnore = new ButtonType("Ignorieren");
            ButtonType btnCancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            CustomAlert ask = DialogFactory.createConfirmationAlert(
                    "Kapiteltext geaendert",
                    "Originaltext wurde geaendert",
                    "Der Originaltext dieses Kapitels wurde seit der letzten Bearbeitung im TTS-Editor geaendert.\nMoechten Sie die Aenderungen im Diff sehen?",
                    stage);
            ask.setButtonTypes(btnDiff, btnIgnore, btnCancel);
            ask.applyTheme(themeIndex);
            Optional<ButtonType> choice = ask.showAndWait();
            if (choice.isPresent() && choice.get() == btnDiff) {
                showOriginalChangedDiffDialog(ttsContent, currentOriginalContent, diffResult, currentHash);
            } else if (choice.isPresent() && choice.get() == btnIgnore) {
                Files.writeString(originalHashPath, currentHash, StandardCharsets.UTF_8);
                setStatus("Aenderungen ignoriert – Hash aktualisiert.");
            }
            // Bei Abbrechen: nichts tun, Dialog kommt beim naechsten Oeffnen wieder
        } catch (IOException e) {
            logger.warn("Hash-Pruefung fuer TTS-Original: {}", originalHashPath, e);
        }
    }

    /**
     * Zeigt einen Diff-Dialog, wenn sich das Original seit dem letzten TTS-Editor-Stand geaendert hat.
     * Links: aktueller TTS-Editor-Text, Rechts: neues Original mit Checkboxen zur selektiven Uebernahme.
     */
    private void showOriginalChangedDiffDialog(String ttsContent, String originalContent,
                                                DiffProcessor.DiffResult diffResult, String newOriginalHash) {
        int ti = Math.max(0, Math.min(themeIndex, THEMES.length - 1));
        String themeBg = THEMES[ti][0];
        String themeFg = THEMES[ti][1];
        String themeAccent = THEMES[ti].length > 2 ? THEMES[ti][2] : themeFg;

        CustomStage diffStage = StageManager.createDiffStage("Original geaendert: " + chapterName, stage);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setPrefWidth(1600);
        root.setPrefHeight(900);
        root.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2px;", themeBg, themeAccent));

        Label title = new Label("Der Originaltext wurde geaendert. Aenderungen in den TTS-Editor uebernehmen?");
        title.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: %s;", themeFg));
        title.setWrapText(true);

        // --- Bloecke bilden ---
        List<DiffBlock> blocks = groupDiffIntoBlocks(diffResult.getDiffLines());
        List<CheckBox> blockCheckBoxes = new ArrayList<>();

        // --- ContentBox ---
        HBox contentBox = new HBox(10);
        contentBox.setPrefHeight(750);
        contentBox.setStyle("-fx-background-color: transparent;");

        // --- Linke Seite: TTS-Editor (aktuell) ---
        VBox leftBox = new VBox(5);
        leftBox.setPrefWidth(750);
        leftBox.setMinWidth(750);
        leftBox.setMaxWidth(750);
        leftBox.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2px;", themeBg, themeAccent));
        leftBox.setPadding(new Insets(10));

        Label leftLabel = new Label("TTS-Editor (aktuell)");
        leftLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: %s;", themeFg));

        ScrollPane leftScrollPane = new ScrollPane();
        leftScrollPane.setStyle("-fx-background-color: transparent;");
        VBox leftContentBox = new VBox(0);
        leftContentBox.setPadding(new Insets(5));
        leftContentBox.setStyle("-fx-background-color: transparent;");

        // --- Rechte Seite: Neues Original mit Checkboxen ---
        VBox rightBox = new VBox(5);
        rightBox.setPrefWidth(750);
        rightBox.setMinWidth(750);
        rightBox.setMaxWidth(750);
        rightBox.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2px;", themeBg, themeAccent));
        rightBox.setPadding(new Insets(10));

        Label rightLabel = new Label("Neues Original - Aenderungen auswaehlen:");
        rightLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: %s;", themeFg));

        ScrollPane rightScrollPane = new ScrollPane();
        rightScrollPane.setStyle("-fx-background-color: transparent;");
        VBox rightContentBox = new VBox(0);
        rightContentBox.setPadding(new Insets(5));
        rightContentBox.setStyle("-fx-background-color: transparent;");

        // --- Bloecke zeilenweise rendern (wie MainController) ---
        for (int bi = 0; bi < blocks.size(); bi++) {
            DiffBlock block = blocks.get(bi);

            // DELETED+ADDED Paar: gepaart rendern (gleiche Zeilen nebeneinander)
            if (block.type == DiffBlockType.DELETED && bi + 1 < blocks.size()
                    && blocks.get(bi + 1).type == DiffBlockType.ADDED) {
                DiffBlock deletedBlock = block;
                DiffBlock addedBlock = blocks.get(bi + 1);
                int dSize = deletedBlock.lines.size();
                int aSize = addedBlock.lines.size();
                int maxSize = Math.max(dSize, aSize);

                CheckBox pairedCheckBox = new CheckBox();
                pairedCheckBox.getStyleClass().add("diff-green-checkbox");
                pairedCheckBox.setStyle("-fx-padding: 0;");
                pairedCheckBox.setPadding(Insets.EMPTY);
                pairedCheckBox.setMinSize(12, 12);
                pairedCheckBox.setPrefSize(12, 12);
                pairedCheckBox.setMaxSize(12, 12);
                pairedCheckBox.setScaleX(0.8);
                pairedCheckBox.setScaleY(0.8);
                pairedCheckBox.setSelected(false);
                blockCheckBoxes.add(pairedCheckBox);
                final CheckBox checkboxForPairing = pairedCheckBox;

                for (int i = 0; i < maxSize; i++) {
                    DiffProcessor.DiffLine dLine = i < dSize ? deletedBlock.lines.get(i) : null;
                    DiffProcessor.DiffLine aLine = i < aSize ? addedBlock.lines.get(i) : null;

                    HBox leftLineBox = new HBox(5);
                    HBox rightLineBox = new HBox(5);

                    Label leftLineNum = new Label(String.format("%3d", dLine != null ? dLine.getLeftLineNumber() : 0));
                    leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");

                    Label rightLineNum = new Label(String.format("%3d", aLine != null ? aLine.getRightLineNumber() : 0));
                    rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");

                    Label leftLineLabel = new Label(dLine != null ? dLine.getOriginalText() : "");
                    leftLineLabel.setWrapText(true);
                    leftLineLabel.setPrefWidth(620);
                    leftLineLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    leftLineLabel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

                    Label rightLineLabel = new Label(aLine != null ? aLine.getNewText() : "");
                    rightLineLabel.setWrapText(true);
                    rightLineLabel.setPrefWidth(620);
                    rightLineLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    rightLineLabel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

                    if (dLine != null) {
                        leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-font-weight: bold;");
                        leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #dc3545; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                    }
                    if (aLine != null) {
                        rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                        rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #28a745; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                    }

                    // Hoehensynchronisation
                    Platform.runLater(() -> {
                        double maxHeight = Math.max(leftLineLabel.getHeight(), rightLineLabel.getHeight());
                        leftLineLabel.setMinHeight(maxHeight);
                        leftLineLabel.setMaxHeight(maxHeight);
                        rightLineLabel.setMinHeight(maxHeight);
                        rightLineLabel.setMaxHeight(maxHeight);
                    });

                    leftLineBox.getChildren().addAll(leftLineNum, leftLineLabel);

                    // Checkbox nur bei der ersten Zeile des ADDED-Blocks
                    if (aLine != null && i == 0 && checkboxForPairing != null) {
                        VBox checkboxContainer = new VBox();
                        checkboxContainer.setAlignment(javafx.geometry.Pos.CENTER);
                        checkboxContainer.setSpacing(0);
                        checkboxContainer.setPadding(Insets.EMPTY);
                        checkboxContainer.setMinWidth(16);
                        checkboxContainer.setMaxWidth(16);
                        checkboxContainer.getChildren().add(checkboxForPairing);
                        rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel, checkboxContainer);
                    } else {
                        rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel);
                    }

                    leftContentBox.getChildren().add(leftLineBox);
                    rightContentBox.getChildren().add(rightLineBox);
                }
                bi++; // ADDED-Block wurde mitverarbeitet
                continue;
            }

            // Standard-Rendering: Zeile fuer Zeile
            CheckBox blockCheckBox = null;
            if (block.type == DiffBlockType.ADDED) {
                blockCheckBox = new CheckBox();
                blockCheckBox.getStyleClass().add("diff-green-checkbox");
                blockCheckBox.setStyle("-fx-padding: 0;");
                blockCheckBox.setPadding(Insets.EMPTY);
                blockCheckBox.setMinSize(12, 12);
                blockCheckBox.setPrefSize(12, 12);
                blockCheckBox.setMaxSize(12, 12);
                blockCheckBox.setScaleX(0.8);
                blockCheckBox.setScaleY(0.8);
                blockCheckBox.setSelected(false);
                blockCheckBoxes.add(blockCheckBox);
            }

            for (int i = 0; i < block.lines.size(); i++) {
                DiffProcessor.DiffLine diffLine = block.lines.get(i);
                HBox leftLineBox = new HBox(5);
                HBox rightLineBox = new HBox(5);

                Label leftLineNum = new Label(String.format("%3d", diffLine.getLeftLineNumber()));
                leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");

                Label rightLineNum = new Label(String.format("%3d", diffLine.getRightLineNumber()));
                rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");

                Label leftLineLabel = new Label(diffLine.getOriginalText());
                leftLineLabel.setWrapText(true);
                leftLineLabel.setPrefWidth(620);
                leftLineLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                leftLineLabel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

                Label rightLineLabel = new Label(diffLine.getNewText());
                rightLineLabel.setWrapText(true);
                rightLineLabel.setPrefWidth(620);
                rightLineLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                rightLineLabel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

                // Hoehensynchronisation
                Platform.runLater(() -> {
                    double maxHeight = Math.max(leftLineLabel.getHeight(), rightLineLabel.getHeight());
                    leftLineLabel.setMinHeight(maxHeight);
                    leftLineLabel.setMaxHeight(maxHeight);
                    rightLineLabel.setMinHeight(maxHeight);
                    rightLineLabel.setMaxHeight(maxHeight);
                });

                switch (block.type) {
                    case ADDED:
                        leftLineLabel.setText("");
                        leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #d4edda; -fx-text-fill: #155724;");
                        rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                        rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #28a745; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                        break;
                    case DELETED:
                        rightLineLabel.setText("");
                        leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-font-weight: bold;");
                        leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #dc3545; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                        rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
                        break;
                    case UNCHANGED:
                        String lightOpacity = "0.4";
                        leftLineLabel.setStyle(String.format("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-text-fill: %s; -fx-background-color: rgba(240,240,240,0.2); -fx-opacity: %s;", themeFg, lightOpacity));
                        rightLineLabel.setStyle(String.format("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-text-fill: %s; -fx-background-color: rgba(240,240,240,0.2); -fx-opacity: %s;", themeFg, lightOpacity));
                        break;
                }

                leftLineBox.getChildren().addAll(leftLineNum, leftLineLabel);

                // Checkbox am Ende des ADDED-Blocks
                if (blockCheckBox != null && i == block.lines.size() - 1) {
                    VBox checkboxContainer = new VBox();
                    checkboxContainer.setAlignment(javafx.geometry.Pos.CENTER);
                    checkboxContainer.setSpacing(0);
                    checkboxContainer.setPadding(Insets.EMPTY);
                    checkboxContainer.setMinWidth(16);
                    checkboxContainer.setMaxWidth(16);
                    checkboxContainer.getChildren().add(blockCheckBox);
                    rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel, checkboxContainer);
                } else {
                    rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel);
                }

                leftContentBox.getChildren().add(leftLineBox);
                rightContentBox.getChildren().add(rightLineBox);
            }
        }

        // ScrollPanes konfigurieren
        leftScrollPane.vvalueProperty().bindBidirectional(rightScrollPane.vvalueProperty());
        leftScrollPane.hvalueProperty().bindBidirectional(rightScrollPane.hvalueProperty());

        leftScrollPane.setContent(leftContentBox);
        leftScrollPane.setFitToWidth(true);
        leftScrollPane.setPrefHeight(600);

        rightScrollPane.setContent(rightContentBox);
        rightScrollPane.setFitToWidth(true);
        rightScrollPane.setPrefHeight(600);

        leftBox.getChildren().addAll(leftLabel, leftScrollPane);
        rightBox.getChildren().addAll(rightLabel, rightScrollPane);

        contentBox.getChildren().addAll(leftBox, rightBox);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        // --- Buttons ---
        Button btnApply = new Button("Ausgewaehlte Aenderungen uebernehmen");
        btnApply.setStyle("-fx-background-color: rgba(40,167,69,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        Button btnAll = new Button("Komplett Original uebernehmen");
        btnAll.setStyle("-fx-background-color: rgba(0,123,255,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        Button btnIgnore = new Button("Ignorieren (Hash aktualisieren)");
        btnIgnore.setStyle("-fx-background-color: rgba(108,117,125,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
        Button btnCancel = new Button("Abbrechen");
        btnCancel.setStyle("-fx-background-color: rgba(220,53,69,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");

        btnApply.setOnAction(e -> {
            String merged = buildMergedText(blocks, blockCheckBoxes);
            codeArea.replaceText(merged);
            refreshHighlight();
            updateOriginalHashSilently(newOriginalHash);
            setStatus("Ausgewaehlte Aenderungen aus dem Original uebernommen.");
            diffStage.close();
        });
        btnAll.setOnAction(e -> {
            codeArea.replaceText(originalContent);
            segments.clear();
            refreshHighlight();
            updateOriginalHashSilently(newOriginalHash);
            setStatus("Komplettes Original uebernommen - Segmente zurueckgesetzt.");
            diffStage.close();
        });
        btnIgnore.setOnAction(e -> {
            updateOriginalHashSilently(newOriginalHash);
            setStatus("Aenderungen ignoriert - Hash aktualisiert.");
            diffStage.close();
        });
        btnCancel.setOnAction(e -> diffStage.close());

        HBox buttons = new HBox(15, btnApply, btnAll, btnIgnore, btnCancel);
        buttons.setAlignment(javafx.geometry.Pos.CENTER);
        buttons.setPadding(new Insets(15, 0, 0, 0));

        root.getChildren().addAll(title, contentBox, buttons);

        Scene diffScene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) diffScene.getStylesheets().add(cssPath);
        diffStage.setSceneWithTitleBar(diffScene);
        diffStage.setFullTheme(themeIndex);
        applyThemeToAll(diffScene.getRoot(), themeIndex);
        diffStage.showAndWait();
    }

    /** Baut den zusammengefuehrten Text aus Diff-Bloecken und Checkbox-Auswahl. */
    private String buildMergedText(List<DiffBlock> blocks, List<CheckBox> checkBoxes) {
        StringBuilder sb = new StringBuilder();
        int cbIdx = 0;
        for (int i = 0; i < blocks.size(); i++) {
            DiffBlock block = blocks.get(i);
            if (block.type == DiffBlockType.DELETED && i + 1 < blocks.size()
                    && blocks.get(i + 1).type == DiffBlockType.ADDED) {
                // DELETED+ADDED Paar: Wenn Checkbox an, nehme ADDED; sonst behalte DELETED
                DiffBlock addedBlock = blocks.get(i + 1);
                boolean selected = cbIdx < checkBoxes.size() && checkBoxes.get(cbIdx).isSelected();
                if (selected) {
                    for (DiffProcessor.DiffLine l : addedBlock.getLines()) {
                        if (l.getNewText() != null) sb.append(l.getNewText()).append("\n");
                    }
                } else {
                    for (DiffProcessor.DiffLine l : block.getLines()) {
                        if (l.getOriginalText() != null) sb.append(l.getOriginalText()).append("\n");
                    }
                }
                cbIdx++;
                i++; // ADDED-Block mitverarbeitet
                continue;
            }
            if (block.type == DiffBlockType.ADDED) {
                boolean selected = cbIdx < checkBoxes.size() && checkBoxes.get(cbIdx).isSelected();
                if (selected) {
                    for (DiffProcessor.DiffLine l : block.getLines()) {
                        if (l.getNewText() != null) sb.append(l.getNewText()).append("\n");
                    }
                }
                cbIdx++;
                continue;
            }
            if (block.type == DiffBlockType.DELETED) {
                for (DiffProcessor.DiffLine l : block.getLines()) {
                    if (l.getOriginalText() != null) sb.append(l.getOriginalText()).append("\n");
                }
                continue;
            }
            // UNCHANGED
            for (DiffProcessor.DiffLine l : block.getLines()) {
                String t = l.getNewText() != null ? l.getNewText() : l.getOriginalText();
                if (t != null) sb.append(t).append("\n");
            }
        }
        // Trailing newlines trimmen
        String result = sb.toString();
        while (result.endsWith("\n\n")) result = result.substring(0, result.length() - 1);
        return result;
    }

    /** Aktualisiert den Hash still (ohne Dialog). */
    private void updateOriginalHashSilently(String hash) {
        if (originalHashPath == null || hash == null) return;
        try {
            Files.createDirectories(originalHashPath.getParent());
            Files.writeString(originalHashPath, hash, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Original-Hash konnte nicht aktualisiert werden: {}", e.getMessage());
        }
    }

    // ---- Diff-Block Hilfsklassen (lokal, analog zu MainController) ----

    private enum DiffBlockType { ADDED, DELETED, UNCHANGED }

    private static class DiffBlock {
        final DiffBlockType type;
        final List<DiffProcessor.DiffLine> lines = new ArrayList<>();
        DiffBlock(DiffBlockType type) { this.type = type; }
        void addLine(DiffProcessor.DiffLine line) { lines.add(line); }
        List<DiffProcessor.DiffLine> getLines() { return lines; }
    }

    private static DiffBlockType toDiffBlockType(DiffProcessor.DiffType t) {
        switch (t) {
            case ADDED: return DiffBlockType.ADDED;
            case DELETED: return DiffBlockType.DELETED;
            default: return DiffBlockType.UNCHANGED;
        }
    }

    private List<DiffBlock> groupDiffIntoBlocks(List<DiffProcessor.DiffLine> lines) {
        List<DiffBlock> blocks = new ArrayList<>();
        if (lines.isEmpty()) return blocks;
        DiffBlock current = new DiffBlock(toDiffBlockType(lines.get(0).getType()));
        current.addLine(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            DiffProcessor.DiffLine line = lines.get(i);
            DiffBlockType bt = toDiffBlockType(line.getType());
            if (bt != current.type) {
                blocks.add(current);
                current = new DiffBlock(bt);
            }
            current.addLine(line);
        }
        blocks.add(current);
        return blocks;
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

    /** Schreibt den Editor-Text in eine getrennte Datei im TTS-Datenordner. Das Original-.md wird nicht angefasst. Leeren Text speichern wir nicht (Schutz vor Undo-Leerung). */
    private void saveEditorContentToSeparateFile() {
        if (dataDir == null || codeArea == null) return;
        String text = codeArea.getText();
        if (text == null || text.trim().isEmpty()) {
            logger.warn("TTS-Editor: Text ist leer – speichere nicht (vorheriger Inhalt bleibt erhalten).");
            return;
        }
        try {
            Path path = Paths.get(dataDir.getPath(), chapterName + "-tts-content.md");
            Files.createDirectories(path.getParent());
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

    /** Liefert [Hintergrundfarbe, Textfarbe] für den angegebenen Theme-Index. */
    public static String[] getThemeColors(int themeIndex) {
        int idx = Math.max(0, Math.min(themeIndex, THEMES.length - 1));
        return THEMES[idx];
    }
}
