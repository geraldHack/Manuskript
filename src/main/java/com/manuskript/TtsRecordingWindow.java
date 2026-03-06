package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
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
    /** Native Selection (Path) ausblenden, Markierung nur per StyleSpans (tts-selection). */
    private static final String HIDE_MAIN_SELECTION_CSS = "data:text/css,.main-selection{-fx-fill:transparent%20!important;}";

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
    private CodeArea codeArea;
    /** Eigenes Markierungsintervall für tts-selection-Spans; unabhängig von CodeArea-Selektion, damit Linksklick sie nicht löscht. -1 = keine Markierung. */
    private int recordingMarkStart = -1;
    private int recordingMarkEnd = -1;
    
    /** Datenstruktur für Mehrfachmarkierungen */
    private static class TextMarkierung {
        int start;
        int end;
        String farbe;
        String beschreibung;
        
        TextMarkierung(int start, int end, String farbe) {
            this(start, end, farbe, "");
        }
        
        TextMarkierung(int start, int end, String farbe, String beschreibung) {
            this.start = start;
            this.end = end;
            this.farbe = farbe;
            this.beschreibung = beschreibung;
        }
        
        boolean ueberlapptMit(int otherStart, int otherEnd) {
            return !(otherEnd <= start || otherStart >= end);
        }
    }
    
    /** Liste aller Markierungen */
    private List<TextMarkierung> markierungen = new ArrayList<>();
    /** Aktuell ausgewählte Farbe für neue Markierungen */
    private String aktuelleMarkierungsfarbe = "gelb";
    /** Farbwähler-Container */
    private VBox colorPicker;
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
    /** true = Leertaste startet/stoppt Aufnahme; false = Leertaste zum Bearbeiten (Leerzeichen einfügen). */
    private boolean aufnahmeModus = true;

    /** true = nächste Leertasten-Session nimmt den Einschwingtext auf (eine Aufnahme, dann Modus Ende). */
    private boolean einschwingRecordingMode;
    private Path einschwingAudioPath;
    private double einschwingDurationSeconds;
    private final MicrophoneRecorder einschwingRecorder = new MicrophoneRecorder();
    private Button btnEinschwingAufnehmen;
    private Label einschwingStatusLabel;
    private Button btnEinschwingPlay;

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
            if (einschwingRecorder.isRecording() || einschwingRecorder.isPaused()) einschwingRecorder.stopRecording();
            if (einschwingAudioPath != null) {
                try { Files.deleteIfExists(einschwingAudioPath); } catch (Exception ignored) { }
            }
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
        
        // Mouse-Listener nach UI-Initialisierung registrieren
        setupMouseSelectionHandling();
    }
    
    /** Mouse-Selektion und Drag-Selektion aktivieren */
    private void setupMouseSelectionHandling() {
        codeArea.selectionProperty().addListener((o, a, b) -> refreshRecordingHighlight());
        codeArea.textProperty().addListener((o, a, b) -> refreshRecordingHighlight());

        codeArea.setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (codeArea.getSelection().getLength() <= 0) return;

            int len = codeArea.getLength();
            int start = Math.max(0, Math.min(codeArea.getSelection().getStart(), len));
            int end = Math.max(start, Math.min(codeArea.getSelection().getEnd(), len));

            if (kannMarkieren(start, end)) {
                markierungen.add(new TextMarkierung(start, end, aktuelleMarkierungsfarbe));
                refreshRecordingHighlight();
                setStatus("Markierung hinzugefügt (" + aktuelleMarkierungsfarbe + ")");
            } else {
                setStatus("Bereich bereits markiert - keine Überlappung erlaubt");
            }
        });

        // Rechtsklick: Markierung unter Cursor löschen
        codeArea.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                Point2D localPoint = codeArea.screenToLocal(e.getScreenX(), e.getScreenY());
                int pos = codeArea.hit(localPoint.getX(), localPoint.getY()).getInsertionIndex();
                TextMarkierung toRemove = findMarkierungAtPosition(pos);
                if (toRemove != null) {
                    markierungen.remove(toRemove);
                    refreshRecordingHighlight();
                    setStatus("Markierung gelöscht");
                } else {
                    setStatus("Keine Markierung unter Cursor");
                }
                e.consume();
            }
        });
    }

    /** Erzeugt die Farbwähler-UI mit Icons */
    private VBox createColorPicker() {
        VBox picker = new VBox(3);
        picker.setPrefWidth(50);
        picker.setMinWidth(50);
        picker.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        picker.getStyleClass().add("color-picker");
        
        // Farben definieren: klickbare Farbfelder (wie im ChapterTtsEditorWindow mit Inline-Style)
        String[] colors = {"gelb", "blau", "gruen", "rot", "orange", "lila"};
        String[] hex = {"#fff3cd", "#cfe2ff", "#d1e7dd", "#f8d7da", "#ffe5cc", "#e2d9f3"};
        
        for (int i = 0; i < colors.length; i++) {
            final String color = colors[i];
            final String bg = hex[i];

            javafx.scene.layout.Region swatch = new javafx.scene.layout.Region();
            swatch.setPrefSize(44, 44);
            swatch.setMinSize(44, 44);
            swatch.setMaxSize(44, 44);
            swatch.setUserData(color);
            Tooltip.install(swatch, new Tooltip("Markierung in " + color));
            swatch.getStyleClass().add("color-picker-swatch");
            
            swatch.setOnMouseClicked(e -> {
                aktuelleMarkierungsfarbe = color;
                updateColorPickerUI();
                setStatus("Farbe gewählt: " + color);
            });

            String base = "-fx-cursor: hand; -fx-border-color: #666; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;";
            swatch.setStyle(base + " -fx-background-color: " + bg + ";");
            
            picker.getChildren().add(swatch);
        }
        
        // Button für automatische Markierung von direkter Rede
        Button btnAutoMark = new Button();
        btnAutoMark.setGraphic(new Label("❛"));
        btnAutoMark.setTooltip(new Tooltip("Automatisch Bereiche in Anführungszeichen markieren"));
        btnAutoMark.setOnAction(e -> autoMarkiereDirekteRede());
        btnAutoMark.setMaxWidth(Double.MAX_VALUE);
        picker.getChildren().add(btnAutoMark);
        
        // Erste Farbe als ausgewählt markieren
        updateColorPickerUI();
        
        return picker;
    }
    
    /** Aktualisiert die UI des Farbwählers */
    private void updateColorPickerUI() {
        if (colorPicker == null) return;
        
        for (Node child : colorPicker.getChildren()) {
            if (!(child instanceof javafx.scene.layout.Region)) continue;
            javafx.scene.layout.Region swatch = (javafx.scene.layout.Region) child;
            String color = swatch.getUserData() != null ? String.valueOf(swatch.getUserData()) : "";
            boolean isSelected = color.equals(aktuelleMarkierungsfarbe);
            // Border bei ausgewählter Farbe dicker
            String style = swatch.getStyle();
            if (style == null) style = "";
            style = style.replace("-fx-border-width: 3px;", "-fx-border-width: 1px;");
            if (isSelected) style = style.replace("-fx-border-width: 1px;", "-fx-border-width: 3px;");
            swatch.setStyle(style);
        }
    }

    private VBox buildContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("param-card");

        Label textLabel = new Label("Segment-Text (einsprochen aufnehmen):");
        codeArea = new CodeArea();
        codeArea.setWrapText(true);
        codeArea.replaceText(segmentText != null ? segmentText : "");
        codeArea.getStyleClass().add("code-area");
        codeArea.getStyleClass().add("tts-editor-code-area");
        codeArea.setPrefHeight(200);
        codeArea.setMaxWidth(Double.MAX_VALUE);
        applyCodeAreaTheme(codeArea, themeIndex);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) codeArea.getStylesheets().add(cssPath);

        // Wichtig: Standard-Selektion sichtbar lassen; Markierung wird in setupMouseSelectionHandling()
        // erst bei Mouse-Release aus der aktuellen Selektion erzeugt.
        codeArea.setEditable(!aufnahmeModus);
        refreshRecordingHighlight();
        
        // Layout mit Farbwähler links und Text rechts
        HBox textContainer = new HBox(5);
        colorPicker = createColorPicker();
        VirtualizedScrollPane<CodeArea> textScroll = new VirtualizedScrollPane<>(codeArea);
        textScroll.setStyle("-fx-padding: 2px;");
        HBox.setHgrow(textScroll, Priority.ALWAYS);
        textContainer.getChildren().addAll(colorPicker, textScroll);
        VBox.setVgrow(textContainer, Priority.ALWAYS);

        stateIndicatorButton = new Button("Starte Aufnahme mit Leertaste und halte sie bis zum Ende der Aufnahme.");
        stateIndicatorButton.setTooltip(new Tooltip("Nur Anzeige: Leertaste gedrückt = aufnehmen, loslassen = Pause. Mit „Speichern“ beenden und übernehmen."));
        stateIndicatorButton.setDisable(true);
        stateIndicatorButton.setMaxWidth(Double.MAX_VALUE);
        stateIndicatorButton.setWrapText(true);

        btnEinschwingAufnehmen = new Button("Einschwingtext aufnehmen");
        btnEinschwingAufnehmen.setTooltip(new Tooltip("In den Einschwingtext-Modus wechseln. Dann Leertaste halten, Einschwingtext sprechen, loslassen = Ende. Wird bei Übernahme vorangestellt und aus dem Ergebnis wieder abgeschnitten."));
        btnEinschwingAufnehmen.setOnAction(e -> enterEinschwingMode());
        einschwingStatusLabel = new Label("Kein Einschwingtext vorhanden");
        einschwingStatusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(einschwingStatusLabel, Priority.ALWAYS);
        btnEinschwingPlay = new Button("Abspielen");
        btnEinschwingPlay.setTooltip(new Tooltip("Eingesprochenen Einschwingtext abspielen."));
        btnEinschwingPlay.setDisable(true);
        btnEinschwingPlay.setOnAction(e -> playEinschwing());
        HBox einschwingRow = new HBox(10);
        einschwingRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        einschwingRow.getChildren().addAll(btnEinschwingAufnehmen, einschwingStatusLabel, btnEinschwingPlay);

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
        hintergrundDaempfenCheck.setSelected(true);
        hintergrundDaempfenCheck.getStyleClass().add("param-card");

        javafx.scene.control.CheckBox aufnahmeModusCheck = new javafx.scene.control.CheckBox("Aufnahme-Modus (Leertaste startet/stoppt Aufnahme)");
        aufnahmeModusCheck.setTooltip(new Tooltip("An: Nur Aufnahme, keine Texteingabe. Aus: Text bearbeiten (Leerzeichen mit Leertaste)."));
        aufnahmeModusCheck.setSelected(aufnahmeModus);
        aufnahmeModusCheck.getStyleClass().add("param-card");
        aufnahmeModusCheck.selectedProperty().addListener((o, a, b) -> {
            aufnahmeModus = b != null && b;
            if (codeArea != null) codeArea.setEditable(!aufnahmeModus);
            updateStateIndicator();
        });

        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(btnPlay, btnZuruecknehmen, btnAbbrechen, btnSpeichern, btnReset, btnLetzteLoeschen, btnUebernehmen, convertProgress);

        statusLabel = new Label(" ");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(textLabel, textContainer, einschwingRow, stateIndicatorButton, aufnahmeModusCheck, hintergrundDaempfenCheck, buttonRow, statusLabel);
        return content;
    }
    
    private TextMarkierung findMarkierungAtPosition(int pos) {
        for (TextMarkierung markierung : markierungen) {
            if (pos >= markierung.start && pos < markierung.end) {
                return markierung;
            }
        }
        return null;
    }
    
    private void autoMarkiereDirekteRede() {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            setStatus("Kein Text vorhanden.");
            return;
        }
        
        // Anführungszeichen-Paare: open, close
        String[][] quotePairs = {
            {"„", "“"}, // Deutsch/Guillemets
            {"»", "«"}, // Umgekehrte Guillemets
            {"\"", "\""} // Englisch
        };
        
        int added = 0;
        for (String[] pair : quotePairs) {
            String open = pair[0];
            String close = pair[1];
            int start = 0;
            while ((start = text.indexOf(open, start)) != -1) {
                int end = text.indexOf(close, start + open.length());
                if (end != -1) {
                    end += close.length(); // inklusive schließendes Zeichen
                    if (kannMarkieren(start, end)) {
                        markierungen.add(new TextMarkierung(start, end, aktuelleMarkierungsfarbe));
                        added++;
                    }
                    start = end;
                } else {
                    break;
                }
            }
        }
        
        if (added > 0) {
            refreshRecordingHighlight();
            setStatus("Direkte Rede markiert: " + added + " Bereiche in " + aktuelleMarkierungsfarbe);
        } else {
            setStatus("Keine direkte Rede gefunden oder Bereiche bereits markiert.");
        }
    }
    
    /** Prüft ob ein Bereich markiert werden kann (keine Überlappungen) */
    private boolean kannMarkieren(int start, int end) {
        
        for (int i = 0; i < markierungen.size(); i++) {
            TextMarkierung markierung = markierungen.get(i);
            if (markierung.ueberlapptMit(start, end)) {
                return false;
            }
        }
        return true;
    }

    private void updateStateIndicator() {
        if (stateIndicatorButton == null) return;
        if (!aufnahmeModus) {
            stateIndicatorButton.setText("Bearbeiten: Leertaste fügt Leerzeichen ein. Für Aufnahme „Aufnahme-Modus“ aktivieren.");
            updatePlayButtonState();
            return;
        }
        if (einschwingRecordingMode) {
            if (einschwingRecorder.isRecording()) {
                stateIndicatorButton.setText("● Einschwingtext-Aufnahme läuft – Leertaste loslassen zum Beenden");
            } else {
                stateIndicatorButton.setText("Einschwingtext: Leertaste halten zum Aufnehmen, loslassen beendet die Einschwing-Aufnahme.");
            }
        } else if (recorder.isRecording()) {
            stateIndicatorButton.setText("● Aufnahme läuft");
        } else if (recorder.isPaused()) {
            stateIndicatorButton.setText("⏸ Pausiert – Leertaste zum Fortsetzen");
        } else {
            stateIndicatorButton.setText("Starte Aufnahme mit Leertaste und halte sie bis zum Ende der Aufnahme.");
        }
        updatePlayButtonState();
    }

    private void enterEinschwingMode() {
        if (recorder.isRecording() || recorder.isPaused()) {
            setStatus("Zuerst Speichern oder Reset der Hauptaufnahme, dann Einschwingtext.");
            return;
        }
        einschwingRecordingMode = true;
        updateStateIndicator();
        setStatus("Einschwingtext-Modus: Leertaste halten, Text sprechen, loslassen = Ende.");
    }

    private void playEinschwing() {
        if (einschwingAudioPath == null || !Files.isRegularFile(einschwingAudioPath)) {
            setStatus("Kein Einschwingtext vorhanden.");
            return;
        }
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.dispose();
            currentPlayer = null;
        }
        try {
            javafx.scene.media.Media media = new javafx.scene.media.Media(einschwingAudioPath.toUri().toString());
            javafx.scene.media.MediaPlayer player = new javafx.scene.media.MediaPlayer(media);
            currentPlayer = player;
            player.setOnReady(() -> player.play());
            player.setOnEndOfMedia(() -> {
                if (currentPlayer == player) currentPlayer = null;
                player.dispose();
                Platform.runLater(() -> setStatus("Einschwingtext abgespielt."));
            });
            player.setOnError(() -> {
                if (currentPlayer == player) currentPlayer = null;
                player.dispose();
                Platform.runLater(() -> setStatus("Abspielfehler: " + player.getError().getMessage()));
            });
            setStatus("Spielt Einschwingtext ab…");
        } catch (Exception e) {
            currentPlayer = null;
            setStatus("Abspielen fehlgeschlagen: " + e.getMessage());
        }
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
        if (einschwingRecordingMode || einschwingAudioPath != null) {
            if (einschwingRecorder.isRecording() || einschwingRecorder.isPaused()) {
                einschwingRecorder.stopRecording();
            }
            if (einschwingAudioPath != null) {
                try { Files.deleteIfExists(einschwingAudioPath); } catch (Exception ignored) { }
                einschwingAudioPath = null;
            }
            einschwingDurationSeconds = 0;
            einschwingRecordingMode = false;
            if (einschwingStatusLabel != null) einschwingStatusLabel.setText("Kein Einschwingtext vorhanden");
            if (btnEinschwingPlay != null) btnEinschwingPlay.setDisable(true);
            updateStateIndicator();
        }
        updatePlayButtonState();
        btnUebernehmen.setDisable(true);
        setStatus("Zurückgesetzt. Leertaste zum erneuten Aufnehmen.");
    }

    private void setupSpaceKeyControl(javafx.scene.Node contentRoot) {
        contentRoot.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE) return;
            if (!aufnahmeModus) return;
            if (spaceKeyPressed) return;
            spaceKeyPressed = true;
            e.consume();
            if (einschwingRecordingMode) {
                if (einschwingRecorder.isRecording()) return;
                if (!MicrophoneRecorder.isMicrophoneAvailable()) {
                    setStatus("Kein Mikrofon verfügbar.");
                    return;
                }
                setStatus("Piep – Einschwingtext aufnehmen…");
                playStartBeep(() -> Platform.runLater(() -> {
                    if (einschwingRecorder.startRecording()) {
                        updateStateIndicator();
                        setStatus("Einschwingtext-Aufnahme läuft – Leertaste loslassen zum Beenden.");
                    } else {
                        setStatus(einschwingRecorder.getLastError() != null ? einschwingRecorder.getLastError().getMessage() : "Mikrofon fehlgeschlagen.");
                    }
                }));
                return;
            }
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
            if (!aufnahmeModus) return;
            spaceKeyPressed = false;
            e.consume();
            if (einschwingRecordingMode) {
                if (einschwingRecorder.isRecording()) {
                    einschwingRecorder.pauseRecording();
                    Path wav = einschwingRecorder.stopRecording();
                    try {
                        if (wav != null && Files.size(wav) > 44) {
                            long dataBytes = Files.size(wav) - 44;
                            double sec = dataBytes / (MicrophoneRecorder.SAMPLE_RATE * 2.0);
                            if (einschwingAudioPath != null) Files.deleteIfExists(einschwingAudioPath);
                            einschwingAudioPath = wav;
                            einschwingDurationSeconds = sec;
                            if (einschwingStatusLabel != null) einschwingStatusLabel.setText(String.format(Locale.ROOT, "Einschwingtext eingesprochen (%.1f Sekunden)", sec));
                            if (btnEinschwingPlay != null) btnEinschwingPlay.setDisable(false);
                            setStatus("Einschwingtext gespeichert. Jetzt Hauptaufnahme mit Leertaste.");
                        } else {
                            if (wav != null) Files.deleteIfExists(wav);
                            setStatus("Einschwingtext zu kurz oder fehlgeschlagen.");
                        }
                    } catch (IOException ex) {
                        if (wav != null) try { Files.deleteIfExists(wav); } catch (Exception ignored) { }
                        setStatus("Einschwingtext: " + ex.getMessage());
                    }
                    einschwingRecordingMode = false;
                    updateStateIndicator();
                }
                return;
            }
            if (recorder.isRecording()) {
                recorder.pauseRecording();
                setStatus("Pausiert. Leertaste zum Fortsetzen, „Speichern“ zum Beenden.");
                updateStateIndicator();
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

    /** Markierung per StyleSpans für Mehrfachmarkierungen: jede Markierung erhält ihre eigene CSS-Klasse (tts-gelb, tts-blau, etc.). */
    private void refreshRecordingHighlight() {
        if (codeArea == null) return;
        String text = codeArea.getText();
        if (text == null) text = "";
        int len = text.length();
        if (len == 0) {
            try {
                codeArea.setStyleSpans(0, StyleSpans.singleton(Collections.emptyList(), 0));
            } catch (Exception e) {
                logger.trace("StyleSpans leer", e);
            }
            return;
        }
        
        // Markierungen nach Startposition sortieren
        markierungen.sort(Comparator.comparingInt(m -> m.start));
        
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;
        
        for (TextMarkierung markierung : markierungen) {
            // Unmarkierter Bereich vor dieser Markierung
            if (pos < markierung.start) {
                builder.add(Collections.emptyList(), markierung.start - pos);
                pos = markierung.start;
            }
            
            // Markierter Bereich mit farbspezifischer CSS-Klasse
            List<String> styles = Collections.singletonList("tts-" + markierung.farbe);
            builder.add(styles, markierung.end - markierung.start);
            pos = markierung.end;
        }
        
        // Rest des Textes unmarkiert
        if (pos < len) {
            builder.add(Collections.emptyList(), len - pos);
        }
        
        try {
            codeArea.setStyleSpans(0, builder.create());
        } catch (Exception e) {
            logger.trace("StyleSpans Mehrfachmarkierungen", e);
        }
    }

    /** RichTextFX CodeArea: Hintergrund und Textfarbe passend zum Fenster-Theme. */
    private void applyCodeAreaTheme(CodeArea area, int themeIndex) {
        String bg, fg;
        switch (themeIndex) {
            case 0: bg = "#ffffff"; fg = "#000000"; break;
            case 1: bg = "#1a1a1a"; fg = "#ffffff"; break;
            case 2: bg = "#f3e5f5"; fg = "#000000"; break;
            case 3: bg = "#1e3a8a"; fg = "#ffffff"; break;
            case 4: bg = "#064e3b"; fg = "#ffffff"; break;
            case 5: bg = "#581c87"; fg = "#ffffff"; break;
            default: bg = "#ffffff"; fg = "#000000"; break;
        }
        area.setStyle(String.format(
            "-fx-font-family: 'Segoe UI', system-ui, sans-serif; -fx-font-size: 15px; " +
            "-rtfx-background-color: %s; -fx-text-fill: %s; -fx-caret-color: %s;",
            bg, fg, fg));
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

        final Path einschwingPath = einschwingAudioPath;
        final double einschwingSec = einschwingDurationSeconds;
        final boolean hasEinschwing = einschwingPath != null && Files.isRegularFile(einschwingPath) && einschwingSec > 0;

        executor.submit(() -> {
            try {
                Path audioForS2S = currentRecordingPath;
                Path combinedTemp = null;
                if (hasEinschwing) {
                    combinedTemp = concatWavs(einschwingPath, currentRecordingPath);
                    if (combinedTemp == null) {
                        Platform.runLater(() -> {
                            convertProgress.setVisible(false);
                            updatePlayButtonState();
                            btnSpeichern.setDisable(false);
                            btnReset.setDisable(false);
                            setStatus("Einschwing + Aufnahme zusammenfügen fehlgeschlagen.");
                        });
                        return;
                    }
                    audioForS2S = combinedTemp;
                }
                if (useIsolation) {
                    Path isolatedTemp = Files.createTempFile("manuskript-isolated-", ".wav");
                    try {
                        Platform.runLater(() -> setStatus("Hintergrundgeräusche dämpfen…"));
                        ElevenLabsClient client = new ElevenLabsClient();
                        client.setApiKey(apiKey);
                        client.isolateAudio(audioForS2S, isolatedTemp);
                        if (combinedTemp != null) { try { Files.deleteIfExists(combinedTemp); } catch (Exception ignored) { } combinedTemp = null; }
                        audioForS2S = isolatedTemp;
                        Platform.runLater(() -> setStatus("Konvertiere mit ElevenLabs (multilingual v2)…"));
                    } finally {
                        if (audioForS2S.equals(isolatedTemp)) { }
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
                if (combinedTemp != null) try { Files.deleteIfExists(combinedTemp); } catch (Exception ignored) { }
                if (hasEinschwing && einschwingSec > 0) {
                    Path trimmed = Files.createTempFile("manuskript-trimmed-", ".mp3");
                    String err = trimAudioFromStart(outputPath, trimmed, einschwingSec);
                    if (err == null) {
                        Files.delete(outputPath);
                        Files.move(trimmed, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        logger.warn("Einschwing-Trim fehlgeschlagen: {}", err);
                        try { Files.deleteIfExists(trimmed); } catch (Exception ignored) { }
                    }
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
                    btnUebernehmen.setDisable(false);
                    setStatus("Fehler: " + e.getMessage());
                });
            }
        });
    }

    /** Hängt zwei WAVs (22050 Hz, 16-bit mono) aneinander: [first][second]. Gibt Temp-Pfad zurück oder null bei Fehler. */
    private static Path concatWavs(Path first, Path second) {
        try {
            byte[] a = Files.readAllBytes(first);
            byte[] b = Files.readAllBytes(second);
            if (a.length < 44 || b.length < 44) return null;
            int dataLenA = a.length - 44;
            int dataLenB = b.length - 44;
            int totalData = dataLenA + dataLenB;
            int totalLen = 36 + totalData;
            Path out = Files.createTempFile("manuskript-einschwing-combined-", ".wav");
            try (java.io.DataOutputStream os = new java.io.DataOutputStream(Files.newOutputStream(out))) {
                os.writeBytes("RIFF");
                os.write(new byte[] { (byte) (totalLen & 0xff), (byte) ((totalLen >> 8) & 0xff), (byte) ((totalLen >> 16) & 0xff), (byte) ((totalLen >> 24) & 0xff) });
                os.writeBytes("WAVE");
                os.writeBytes("fmt ");
                os.write(new byte[] { 16, 0, 0, 0 });
                os.write(new byte[] { 1, 0 }); // PCM
                os.write(new byte[] { 1, 0 }); // mono
                os.write(new byte[] { (byte) 0x22, (byte) 0x56, 0, 0 }); // 22050
                os.write(new byte[] { (byte) 0x44, (byte) 0xac, 0, 0 }); // byte rate
                os.write(new byte[] { 2, 0 }); // block align
                os.write(new byte[] { 16, 0 }); // bits
                os.writeBytes("data");
                os.write(new byte[] { (byte) (totalData & 0xff), (byte) ((totalData >> 8) & 0xff), (byte) ((totalData >> 16) & 0xff), (byte) ((totalData >> 24) & 0xff) });
                os.write(a, 44, dataLenA);
                os.write(b, 44, dataLenB);
            }
            return out;
        } catch (Exception e) {
            logger.warn("WAV-Konkatenation fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /** Schneidet den Anfang einer Audiodatei ab (FFmpeg). @return null bei Erfolg, sonst Fehlermeldung */
    private static String trimAudioFromStart(Path source, Path target, double trimStartSec) {
        String ffmpeg = getFfmpegPath();
        List<String> cmd = List.of(
            ffmpeg, "-y", "-loglevel", "error",
            "-ss", String.format(Locale.ROOT, "%.3f", trimStartSec),
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

    private static String getFfmpegPath() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String exeName = isWindows ? "ffmpeg.exe" : "ffmpeg";
        java.io.File dir = new java.io.File("ffmpeg");
        java.io.File exe = new java.io.File(dir, exeName);
        if (exe.canExecute()) return exe.getAbsolutePath();
        java.io.File inBin = new java.io.File(dir, "bin/" + exeName);
        if (inBin.canExecute()) return inBin.getAbsolutePath();
        return "ffmpeg";
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
