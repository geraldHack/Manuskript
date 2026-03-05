package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

/**
 * Kleines Fenster zum Einsprechen des Segment-Texts per Mikrofon und Konvertierung
 * über ElevenLabs Speech-to-Speech (multilingual v2). Mehrfach aufnehmen, abspielen, dann konvertieren.
 */
public class TtsRecordingWindow {

    private static final Logger logger = LoggerFactory.getLogger(TtsRecordingWindow.class);
    private static final String PREF_PREFIX = "ttsRecordingWindow_";
    private static final Preferences PREFS = Preferences.userNodeForPackage(TtsRecordingWindow.class);

    private final CustomStage stage;
    private final String segmentText;
    private final int start;
    private final int end;
    private final Path audioDirPath;
    /** Nächste freie block_XXX-Nummer (vom Parent), damit keine Kollision mit bestehenden Segmenten. */
    private final int nextBlockNumber;
    private final String elevenLabsVoiceId;
    private final String voiceName;
    /** Voice-Settings aus dem linken Fenster (Stabilität, Similarity, Speed, …); null = API-Defaults. */
    private final ElevenLabsClient.VoiceSettings elevenLabsVoiceSettings;
    private final int themeIndex;
    private final OnConvertDoneCallback onConvertDone;

    private final MicrophoneRecorder recorder = new MicrophoneRecorder();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TtsRecordingConvert");
        t.setDaemon(true);
        return t;
    });

    private Path currentRecordingPath;
    private TextArea textArea;
    /** Zeigt nur den Aufnahme-Status (Leertaste / Aufnahme / Pause), nicht klickbar. */
    private Button stateIndicatorButton;
    private Button btnPlay;
    private Button btnZuruecknehmen;
    private Button btnAbbrechen;
    private Button btnSpeichern;
    private Button btnReset;
    private Button btnLetzteLoeschen;
    private Button btnUebernehmen;
    private ProgressIndicator convertProgress;
    private Label statusLabel;
    private javafx.scene.media.MediaPlayer currentPlayer;
    private javafx.scene.control.CheckBox hintergrundDaempfenCheck;
    private boolean spaceKeyPressed;

    /**
     * Callback nach erfolgreicher Konvertierung: Parent fügt Segment hinzu.
     */
    public interface OnConvertDoneCallback {
        void onConvertDone(Path outputMp3Path, int start, int end, String voiceName);
    }

    public TtsRecordingWindow(Window owner, int themeIndex, String segmentText, int start, int end,
                             Path audioDirPath, int nextBlockNumber, String elevenLabsVoiceId, String voiceName,
                             ElevenLabsClient.VoiceSettings elevenLabsVoiceSettings,
                             OnConvertDoneCallback onConvertDone) {
        this.segmentText = segmentText != null ? segmentText : "";
        this.start = start;
        this.end = end;
        this.audioDirPath = audioDirPath;
        this.nextBlockNumber = nextBlockNumber <= 0 ? 1 : nextBlockNumber;
        this.elevenLabsVoiceId = elevenLabsVoiceId != null ? elevenLabsVoiceId : "";
        this.voiceName = voiceName != null ? voiceName : "";
        this.elevenLabsVoiceSettings = elevenLabsVoiceSettings;
        this.themeIndex = themeIndex;
        this.onConvertDone = onConvertDone;

        stage = StageManager.createStage("Aufnahme", owner, false);
        stage.setMinWidth(520);
        stage.setMinHeight(420);
        stage.setWidth(600);
        stage.setHeight(500);
        loadWindowPreferences();
        stage.setOnCloseRequest(e -> {
            if (currentPlayer != null) {
                currentPlayer.stop();
                currentPlayer.dispose();
                currentPlayer = null;
            }
            if (recorder.isRecording() || recorder.isPaused()) recorder.stopRecording();
            executor.shutdown();
        });

        VBox root = buildContent();
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        stage.setSceneWithTitleBar(scene);
        setupSpaceKeyControl(root);
        stage.setFullTheme(themeIndex);
        applyThemeToNode(root, themeIndex);
        applyThemeToAll(root);
        saveWindowPreferencesOnShow();
        root.setFocusTraversable(true);
    }

    private VBox buildContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("param-card");

        Label textLabel = new Label("Segment-Text (einsprochen aufnehmen):");
        textArea = new TextArea(segmentText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(10);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setStyle("-fx-font-size: 17px;");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        stateIndicatorButton = new Button("⌨ Leertaste halten: Aufnahme läuft");
        stateIndicatorButton.setTooltip(new Tooltip("Nur Anzeige: Leertaste gedrückt = aufnehmen, loslassen = Pause. Mit „Speichern“ beenden und übernehmen."));
        stateIndicatorButton.setDisable(true);
        stateIndicatorButton.setMaxWidth(Double.MAX_VALUE);

        btnPlay = new Button("Abspielen");
        btnPlay.setTooltip(new Tooltip("Aufnahme abspielen (nach Speichern oder in Pause)."));
        btnPlay.setDisable(true);
        btnPlay.setOnAction(e -> playCurrentRecording());

        btnZuruecknehmen = new Button("Zurücknehmen");
        btnZuruecknehmen.setTooltip(new Tooltip("Letzte Space-Aufnahme (letztes Segment) rückgängig machen. Nur in Pause."));
        btnZuruecknehmen.setDisable(true);
        btnZuruecknehmen.setOnAction(e -> undoLastSegment());

        btnAbbrechen = new Button("Abbrechen");
        btnAbbrechen.setTooltip(new Tooltip("Fenster schließen ohne zu übernehmen."));
        btnAbbrechen.setOnAction(e -> stage.close());

        btnSpeichern = new Button("Speichern");
        btnSpeichern.setTooltip(new Tooltip("Aufnahme beenden und als Datei übernehmen. Danach Abspielen oder Übernehmen."));
        btnSpeichern.setOnAction(e -> finalizeRecording());

        btnReset = new Button("Reset");
        btnReset.setTooltip(new Tooltip("Puffer verwerfen und von vorn beginnen."));
        btnReset.setOnAction(e -> resetRecording());

        btnLetzteLoeschen = new Button("Letztes Stück löschen");
        btnLetzteLoeschen.setTooltip(new Tooltip("Nur das letzte Stück (letztes Leertasten-Segment) entfernen. Nur in Pause."));
        btnLetzteLoeschen.setDisable(true);
        btnLetzteLoeschen.setOnAction(e -> deleteLastRecording());

        btnUebernehmen = new Button("Übernehmen");
        btnUebernehmen.setTooltip(new Tooltip("Aufnahme an ElevenLabs senden (multilingual v2) und als Segment übernehmen."));
        btnUebernehmen.setDisable(true);
        btnUebernehmen.setOnAction(e -> convertAndDone());

        convertProgress = new ProgressIndicator(-1);
        convertProgress.setVisible(false);
        convertProgress.setPrefSize(24, 24);
        convertProgress.setMaxSize(24, 24);

        hintergrundDaempfenCheck = new javafx.scene.control.CheckBox("Hintergrundgeräusche dämpfen");
        hintergrundDaempfenCheck.setTooltip(new Tooltip("Vor der Konvertierung ElevenLabs Audio Isolation anwenden (ca. 1000 Credits/Min.)."));
        hintergrundDaempfenCheck.getStyleClass().add("param-card");

        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(btnPlay, btnZuruecknehmen, btnAbbrechen, btnSpeichern, btnReset, btnLetzteLoeschen, btnUebernehmen, convertProgress);

        statusLabel = new Label(" ");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(textLabel, textArea, stateIndicatorButton, hintergrundDaempfenCheck, buttonRow, statusLabel);
        return content;
    }

    private void updateStateIndicator() {
        if (stateIndicatorButton == null) return;
        if (recorder.isRecording()) {
            stateIndicatorButton.setText("● Aufnahme läuft");
        } else if (recorder.isPaused()) {
            stateIndicatorButton.setText("⏸ Pausiert – Leertaste zum Fortsetzen");
        } else {
            stateIndicatorButton.setText("⌨ Leertaste halten: Aufnahme läuft");
        }
        updatePlayButtonState();
    }

    private void updatePlayButtonState() {
        if (btnPlay != null) {
            btnPlay.setDisable(currentRecordingPath == null && !recorder.isPaused());
        }
        if (btnZuruecknehmen != null) {
            btnZuruecknehmen.setDisable(!recorder.canUndoLastSegment());
        }
        if (btnLetzteLoeschen != null) {
            btnLetzteLoeschen.setDisable(!recorder.canUndoLastSegment());
        }
    }

    private void finalizeRecording() {
        if (recorder.isRecording() || recorder.isPaused()) {
            // Wenn noch am Aufnehmen (Leertaste gehalten): zuerst pausieren, damit der Record-Thread
            // den letzten Puffer vollständig draint – sonst fehlt die letzte Aufnahme in der WAV.
            if (recorder.isRecording()) {
                recorder.pauseRecording();
            }
            Path wav = recorder.stopRecording();
            updateStateIndicator();
            if (wav != null) {
                if (currentRecordingPath != null && Files.exists(currentRecordingPath)) {
                    try { Files.deleteIfExists(currentRecordingPath); } catch (Exception ignored) { }
                }
                currentRecordingPath = wav;
                btnUebernehmen.setDisable(false);
                setStatus("Aufnahme gespeichert. Abspielen oder Übernehmen.");
                updatePlayButtonState();
                javafx.application.Platform.runLater(this::updatePlayButtonState);
            } else {
                setStatus("Aufnahme zu kurz oder Fehler.");
            }
        } else {
            setStatus("Zuerst mit Leertaste aufnehmen.");
        }
    }

    private void undoLastSegment() {
        if (recorder.undoLastSegment()) {
            updatePlayButtonState();
            setStatus("Letzte Space-Aufnahme zurückgenommen. Leertaste zum Fortsetzen.");
        } else {
            setStatus("Nichts zum Zurücknehmen (nur in Pause, mind. ein Segment).");
        }
    }

    private void deleteLastRecording() {
        if (currentRecordingPath != null && Files.exists(currentRecordingPath)) {
            try {
                Files.deleteIfExists(currentRecordingPath);
            } catch (Exception e) {
                setStatus("Löschen fehlgeschlagen: " + e.getMessage());
                return;
            }
            currentRecordingPath = null;
        }
        if (recorder.undoLastSegment()) {
            updateStateIndicator();
            updatePlayButtonState();
            btnUebernehmen.setDisable(true);
            setStatus("Letztes Stück zurückgenommen. Leertaste zum Fortsetzen.");
        } else {
            setStatus("Nichts zum Löschen (nur in Pause, mind. ein Segment).");
        }
    }

    private void resetRecording() {
        if (recorder.isRecording() || recorder.isPaused()) {
            recorder.stopRecording();
            updateStateIndicator();
        }
        if (currentRecordingPath != null && Files.exists(currentRecordingPath)) {
            try { Files.deleteIfExists(currentRecordingPath); } catch (Exception ignored) { }
            currentRecordingPath = null;
        }
        updatePlayButtonState();
        btnUebernehmen.setDisable(true);
        setStatus("Zurückgesetzt. Leertaste zum erneuten Aufnehmen.");
    }

    private void setupSpaceKeyControl(javafx.scene.Node contentRoot) {
        contentRoot.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE) return;
            if (spaceKeyPressed) return;
            spaceKeyPressed = true;
            e.consume();
            if (recorder.isPaused()) {
                setStatus("Piep – gleich geht’s los…");
                playStartBeep(() -> Platform.runLater(() -> {
                    if (recorder.resumeRecording()) {
                        updateStateIndicator();
                        setStatus("Aufnahme läuft (Leertaste loslassen = Pause)…");
                    } else {
                        setStatus("Fortsetzen fehlgeschlagen.");
                    }
                }));
            } else if (!recorder.isRecording()) {
                if (!MicrophoneRecorder.isMicrophoneAvailable()) {
                    setStatus("Kein Mikrofon verfügbar.");
                    return;
                }
                setStatus("Piep – gleich geht’s los…");
                playStartBeep(() -> Platform.runLater(() -> {
                    if (recorder.startRecording()) {
                        updateStateIndicator();
                        setStatus("Aufnahme läuft (Leertaste loslassen = Pause)…");
                    } else {
                        Exception err = recorder.getLastError();
                        setStatus(err != null ? err.getMessage() : "Mikrofon konnte nicht gestartet werden.");
                    }
                }));
            }
        });
        contentRoot.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() != KeyCode.SPACE) return;
            spaceKeyPressed = false;
            e.consume();
            if (recorder.isRecording()) {
                recorder.pauseRecording();
                setStatus("Pausiert. Leertaste zum Fortsetzen, „Speichern“ zum Beenden.");
                updateStateIndicator();
                // Button-Zustand sicher nach Pause aktualisieren (Zurücknehmen aktivieren)
                javafx.application.Platform.runLater(this::updatePlayButtonState);
            }
        });
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

    private void applyThemeToAll(Node root) {
        applyThemeToNode(root, themeIndex);
        if (root instanceof Parent) {
            for (Node child : ((Parent) root).getChildrenUnmodifiable()) {
                applyThemeToNode(child, themeIndex);
                if (child instanceof Parent) {
                    for (Node grand : ((Parent) child).getChildrenUnmodifiable()) {
                        applyThemeToNode(grand, themeIndex);
                    }
                }
            }
        }
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg != null ? msg : " ");
    }

    private static final float BEEP_SR = 44100f;
    private static final int BEEP_FREQ = 640;
    private static final double BEEP_AMP = 6000;

    /** Dauer der Pieps in ms (fest, damit keine getMicrosecondLength()-Abweichung). */
    private static final int BEEP_SHORT_MS = 95;
    private static final int BEEP_LONG_MS = 595;
    private static final int BEEP_PAUSE_MS = 520;

    /** Spielt „piep piep pieeeep“. Clips einmal öffnen, dann nur start() – kein wiederholtes open() zwischen den Tönen. */
    private static void playStartBeep(Runnable onDone) {
        Thread beepThread = new Thread(() -> {
            Clip clipShort = null;
            Clip clipLong = null;
            try {
                byte[] wavShort = buildBeepWav(BEEP_FREQ, BEEP_AMP, 0.09, BEEP_SR);
                byte[] wavLong = buildBeepWav(BEEP_FREQ, BEEP_AMP, 0.28, BEEP_SR);
                try (AudioInputStream aisShort = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavShort));
                     AudioInputStream aisLong = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavLong))) {
                    clipShort = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, aisShort.getFormat()));
                    clipShort.open(aisShort);
                    clipLong = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, aisLong.getFormat()));
                    clipLong.open(aisLong);
                }
                clipShort.start();
                Thread.sleep(BEEP_SHORT_MS + BEEP_PAUSE_MS);
                clipShort.setFramePosition(0);
                clipShort.start();
                Thread.sleep(BEEP_SHORT_MS + BEEP_PAUSE_MS);
                clipLong.start();
                Thread.sleep(BEEP_LONG_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.trace("Start-Beep: {}", e.getMessage());
            } finally {
                if (clipShort != null) try { clipShort.close(); } catch (Exception ignored) { }
                if (clipLong != null) try { clipLong.close(); } catch (Exception ignored) { }
            }
            if (onDone != null) onDone.run();
        }, "TtsRecordingStartBeep");
        beepThread.setDaemon(true);
        beepThread.start();
    }

    /** Erzeugt eine minimale WAV-Datei (16-bit mono) mit einem Sinus-Ton inkl. kurzem Fade. */
    private static byte[] buildBeepWav(int freqHz, double amp, double durationSec, float sampleRate) throws IOException {
        int samples = (int) Math.round(sampleRate * durationSec);
        int fade = Math.min((int) (sampleRate * 0.006), samples / 4);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream(samples * 2);
        for (int i = 0; i < samples; i++) {
            double t = i / sampleRate;
            double g = (i < fade) ? (double) i / fade : (i >= samples - fade) ? (double) (samples - 1 - i) / fade : 1.0;
            short s = (short) (amp * g * Math.sin(2 * Math.PI * freqHz * t));
            pcm.write(s & 0xff);
            pcm.write((s >> 8) & 0xff);
        }
        byte[] pcmArr = pcm.toByteArray();
        int dataLen = pcmArr.length;
        int totalLen = 36 + dataLen;
        ByteArrayOutputStream wav = new ByteArrayOutputStream(44 + dataLen);
        wav.write(new byte[] { 'R', 'I', 'F', 'F' });
        wav.write(intLe(totalLen));
        wav.write(new byte[] { 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ' });
        wav.write(intLe(16));
        wav.write(shortLe((short) 1));
        wav.write(shortLe((short) 1));
        wav.write(intLe((int) sampleRate));
        wav.write(intLe((int) (sampleRate * 2)));
        wav.write(shortLe((short) 2));
        wav.write(shortLe((short) 16));
        wav.write(new byte[] { 'd', 'a', 't', 'a' });
        wav.write(intLe(dataLen));
        wav.write(pcmArr, 0, dataLen);
        return wav.toByteArray();
    }

    private static byte[] intLe(int v) {
        return new byte[] { (byte) (v & 0xff), (byte) ((v >> 8) & 0xff), (byte) ((v >> 16) & 0xff), (byte) ((v >> 24) & 0xff) };
    }

    private static byte[] shortLe(short v) {
        return new byte[] { (byte) (v & 0xff), (byte) ((v >> 8) & 0xff) };
    }

    private void playCurrentRecording() {
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.dispose();
            currentPlayer = null;
        }
        Path toPlay = null;
        boolean deleteAfterPlay = false;
        if (currentRecordingPath != null && Files.isRegularFile(currentRecordingPath)) {
            toPlay = currentRecordingPath;
        } else if (recorder.isPaused()) {
            toPlay = recorder.writeCurrentBufferToTempFile();
            deleteAfterPlay = (toPlay != null);
        }
        if (toPlay == null || !Files.isRegularFile(toPlay)) {
            setStatus("Keine Aufnahme vorhanden.");
            return;
        }
        try {
            final Path finalToPlay = toPlay;
            final boolean finalDeleteAfterPlay = deleteAfterPlay;
            javafx.scene.media.Media media = new javafx.scene.media.Media(toPlay.toUri().toString());
            javafx.scene.media.MediaPlayer player = new javafx.scene.media.MediaPlayer(media);
            currentPlayer = player;
            player.setOnReady(() -> player.play());
            player.setOnEndOfMedia(() -> {
                if (currentPlayer == player) currentPlayer = null;
                player.dispose();
                if (finalDeleteAfterPlay) {
                    try { Files.deleteIfExists(finalToPlay); } catch (Exception ignored) { }
                }
                Platform.runLater(() -> setStatus("Abspielen beendet."));
            });
            player.setOnError(() -> {
                if (currentPlayer == player) currentPlayer = null;
                player.dispose();
                if (finalDeleteAfterPlay) {
                    try { Files.deleteIfExists(finalToPlay); } catch (Exception ignored) { }
                }
                Platform.runLater(() -> setStatus("Abspielfehler: " + player.getError().getMessage()));
            });
            setStatus("Spielt ab…");
        } catch (Exception e) {
            currentPlayer = null;
            setStatus("Abspielen fehlgeschlagen: " + e.getMessage());
        }
    }

    private void convertAndDone() {
        if (currentRecordingPath == null || !Files.isRegularFile(currentRecordingPath)) {
            setStatus("Keine Aufnahme vorhanden.");
            return;
        }
        if (elevenLabsVoiceId.isBlank()) {
            setStatus("Bitte im TTS-Editor eine ElevenLabs-Stimme wählen.");
            return;
        }
        if (audioDirPath == null || !Files.isDirectory(audioDirPath)) {
            setStatus("Audio-Verzeichnis fehlt.");
            return;
        }
        String apiKey = ResourceManager.getParameter("tts.elevenlabs_api_key", "");
        if (apiKey == null || apiKey.isBlank()) {
            setStatus("ElevenLabs API-Key fehlt (Parameter-Verwaltung).");
            return;
        }

        btnUebernehmen.setDisable(true);
        btnPlay.setDisable(true);
        btnSpeichern.setDisable(true);
        btnReset.setDisable(true);
        convertProgress.setVisible(true);
        boolean useIsolation = hintergrundDaempfenCheck != null && hintergrundDaempfenCheck.isSelected();
        setStatus(useIsolation ? "Hintergrundgeräusche dämpfen…" : "Konvertiere mit ElevenLabs (multilingual v2)…");

        executor.submit(() -> {
            try {
                Path audioForS2S = currentRecordingPath;
                if (useIsolation) {
                    Path isolatedTemp = Files.createTempFile("manuskript-isolated-", ".wav");
                    try {
                        Platform.runLater(() -> setStatus("Hintergrundgeräusche dämpfen…"));
                        ElevenLabsClient client = new ElevenLabsClient();
                        client.setApiKey(apiKey);
                        client.isolateAudio(currentRecordingPath, isolatedTemp);
                        audioForS2S = isolatedTemp;
                        Platform.runLater(() -> setStatus("Konvertiere mit ElevenLabs (multilingual v2)…"));
                    } finally {
                        if (audioForS2S.equals(isolatedTemp)) {
                            // Nutze isolierte Datei für S2S; wird nach S2S gelöscht
                        }
                    }
                }
                String baseName = "block_" + String.format(Locale.ROOT, "%03d", nextBlockNumber) + ".mp3";
                Path outputPath = audioDirPath.resolve(baseName);
                ElevenLabsClient client = new ElevenLabsClient();
                client.setApiKey(apiKey);
                client.convertSpeechToSpeech(audioForS2S, elevenLabsVoiceId, ElevenLabsClient.SPEECH_TO_SPEECH_MODEL_ID, outputPath, elevenLabsVoiceSettings);
                if (useIsolation && audioForS2S != currentRecordingPath) {
                    try { Files.deleteIfExists(audioForS2S); } catch (Exception ignored) { }
                }
                Path result = outputPath;
                Platform.runLater(() -> {
                    convertProgress.setVisible(false);
                    if (onConvertDone != null) {
                        onConvertDone.onConvertDone(result, start, end, voiceName);
                    }
                    setStatus("Konvertierung erfolgreich. Segment wurde übernommen.");
                    stage.close();
                });
            } catch (Exception e) {
                logger.warn("Speech-to-Speech fehlgeschlagen", e);
                Platform.runLater(() -> {
                    convertProgress.setVisible(false);
                    updatePlayButtonState();
                    btnSpeichern.setDisable(false);
                    btnReset.setDisable(false);
                    setStatus("Fehler: " + e.getMessage());
                });
            }
        });
    }

    private void loadWindowPreferences() {
        double x = PREFS.getDouble(PREF_PREFIX + "x", Double.NaN);
        double y = PREFS.getDouble(PREF_PREFIX + "y", Double.NaN);
        double w = PREFS.getDouble(PREF_PREFIX + "width", 600);
        double h = PREFS.getDouble(PREF_PREFIX + "height", 500);
        if (!Double.isNaN(w) && w >= 520) stage.setWidth(w);
        if (!Double.isNaN(h) && h >= 420) stage.setHeight(h);
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            stage.setX(x);
            stage.setY(y);
        }
    }

    private void saveWindowPreferencesOnShow() {
        stage.showingProperty().addListener((o, wasShowing, nowShowing) -> {
            if (nowShowing) return;
            try {
                PREFS.putDouble(PREF_PREFIX + "x", stage.getX());
                PREFS.putDouble(PREF_PREFIX + "y", stage.getY());
                PREFS.putDouble(PREF_PREFIX + "width", stage.getWidth());
                PREFS.putDouble(PREF_PREFIX + "height", stage.getHeight());
            } catch (Exception ignored) { }
        });
    }

    public void show() {
        stage.show();
    }
}
