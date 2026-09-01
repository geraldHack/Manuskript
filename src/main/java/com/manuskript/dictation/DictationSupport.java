package com.manuskript.dictation;

import com.manuskript.ChapterEditorHost;
import com.manuskript.EditingShortcuts;
import com.manuskript.ManuskriptTextEditor;
import com.manuskript.NovelManager;
import com.manuskript.ResourceManager;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Diktat im Canvas-Kapitel-Editor: Modus per Toolbar-Toggle, Aufnahme per Push-to-talk-Hotkey.
 */
public class DictationSupport {

    private static final Logger logger = LoggerFactory.getLogger(DictationSupport.class);
    private static final String PREF_DICTATION_MODE = "dictation_mode_enabled";

    private final ChapterEditorHost host;
    private final Stage ownerStage;
    private final int themeIndex;
    private final Preferences preferences;
    private final DictationService dictationService = new DictationService();

    private ToggleButton dictationButton;
    private boolean dictationModeEnabled;
    private boolean recordingViaHotkey;
    private boolean recordingViaMouse;
    private boolean spacePushToTalkActive;
    private boolean keyHandlersInstalled;
    private final PauseTransition holdToTalkDelay = new PauseTransition(Duration.millis(280));
    /** Fortlaufende Job-ID für geordnetes Einfügen trotz paralleler Verarbeitung. */
    private int nextJobId;
    private int nextApplyJobId;
    private final Map<Integer, PendingOutcome> readyOutcomes = new HashMap<>();
    private int uiPendingJobs;
    /** Ob wir den Host-Busy-Zähler aktuell halten (API ist ref-counted). */
    private boolean busyBarHeld;

    public DictationSupport(ChapterEditorHost host, Stage ownerStage, int themeIndex) {
        this(host, ownerStage, themeIndex,
                Preferences.userNodeForPackage(DictationSupport.class));
    }

    DictationSupport(ChapterEditorHost host, Stage ownerStage, int themeIndex, Preferences preferences) {
        this.host = host;
        this.ownerStage = ownerStage;
        this.themeIndex = themeIndex;
        this.preferences = preferences;
        this.dictationModeEnabled = preferences.getBoolean(PREF_DICTATION_MODE, false);
    }

    public ToggleButton createToolbarButton() {
        String hotkeyHint = pushToTalkHint();
        dictationButton = new ToggleButton("Diktat");
        dictationButton.setTooltip(new Tooltip(
                "Diktat-Modus ein/aus (erster Klick schaltet ein, kurzer Klick danach aus).\n"
                        + "Sprechen: Taste „Diktat“ gedrückt halten, oder " + hotkeyHint + " halten.\n"
                        + "Beim Loslassen wird die Cursorposition als Markierung ⟦d:…⟧ gespeichert; "
                        + "während der Verarbeitung kannst du dahinter weiter editieren oder erneut diktieren.\n"
                        + "Fertige Texte ersetzen die Markierung in Aufnahme-Reihenfolge.\n"
                        + "Kommando: mit „Anweisung:“, „Befehl:“ oder „Kommando:“ beginnen — "
                        + "z. B. „Anweisung: Schreibe, dass Luna wacklige Knie hat nach der Ankunft“.\n"
                        + "Format: „in kursiv“, „kursiv … kursiv“, „in fett“, „Absatz“; "
                        + DictationSpokenMarkup.spokenMarkupHint() + ".\n"
                        + "Glossar: Rechtsklick auf „Diktat“ → Glossar bearbeiten (data/dictation-glossary.txt).\n"
                        + "STT: lokal (whisper.cpp). LLM: agent.backend."));
        dictationButton.getStyleClass().add("dictation-btn");
        dictationButton.setSelected(dictationModeEnabled);

        javafx.scene.control.ContextMenu glossaryMenu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem openGlossary = new javafx.scene.control.MenuItem("Glossar bearbeiten…");
        openGlossary.setOnAction(e -> openGlossaryEditor());
        glossaryMenu.getItems().add(openGlossary);
        dictationButton.setContextMenu(glossaryMenu);
        if (dictationModeEnabled) {
            String issue = DictationService.checkReadiness();
            if (issue != null) {
                dictationModeEnabled = false;
                preferences.putBoolean(PREF_DICTATION_MODE, false);
                dictationButton.setSelected(false);
            }
        }
        applyModeAppearance();

        dictationButton.selectedProperty().addListener((obs, wasSelected, selected) -> {
            if (selected) {
                String issue = DictationService.checkReadiness();
                if (issue != null) {
                    dictationModeEnabled = false;
                    dictationButton.setSelected(false);
                    showError(resolveErrorHeader(issue), issue);
                    return;
                }
            } else if (dictationService.isRecording()) {
                holdToTalkDelay.stop();
                recordingViaMouse = false;
                dictationService.cancelRecording();
                setRecordingAppearance(false);
                refreshBusyBar();
            }
            dictationModeEnabled = selected;
            preferences.putBoolean(PREF_DICTATION_MODE, selected);
            applyModeAppearance();
            host.updateStatus(selected
                    ? "Diktat-Modus an (Taste halten oder " + hotkeyHint + ")"
                    : "Bereit");
        });

        dictationButton.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onDictationButtonPressed);
        dictationButton.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onDictationButtonReleased);

        return dictationButton;
    }

    public void openGlossaryEditor() {
        File docx = host.getOriginalDocxFile();
        if (docx == null) {
            showError("Glossar", "Kein Kapitel mit DOCX geladen – Glossar liegt unter data/dictation-glossary.txt im Buchordner.");
            return;
        }
        NovelManager.ensureDictationGlossary(docx.getAbsolutePath());
        DictationGlossaryWindow.show(
                ownerStage,
                themeIndex,
                docx.getAbsolutePath(),
                host.getEditorFontFamily(),
                host.getEditorFontSizePx());
    }

    /**
     * Nach {@code stage.setSceneWithTitleBar(...)} aufrufen — nicht auf der ursprünglichen Scene.
     */
    public void installKeyHandlers(Stage stage, ManuskriptTextEditor editor) {
        if (keyHandlersInstalled) {
            return;
        }
        if (stage != null) {
            stage.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
            stage.addEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);
            stage.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        }
        Scene scene = stage != null ? stage.getScene() : null;
        if (scene != null) {
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
            scene.addEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);
            scene.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        }
        if (editor != null) {
            editor.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
            editor.addEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);
            editor.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        }
        keyHandlersInstalled = stage != null || scene != null || editor != null;
        logger.debug("Diktat-Hotkeys installiert (stage={}, scene={}, editor={})",
                stage != null, scene != null, editor != null);
    }

    private void onDictationButtonPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || event.isConsumed() || !dictationModeEnabled) {
            return;
        }
        if (dictationService.isRecording() || recordingViaHotkey) {
            return;
        }
        event.consume();
        holdToTalkDelay.stop();
        holdToTalkDelay.setOnFinished(e -> {
            if (!dictationModeEnabled || dictationService.isRecording()) {
                return;
            }
            recordingViaMouse = true;
            startRecording();
        });
        holdToTalkDelay.playFromStart();
    }

    private void onDictationButtonReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        boolean delayRunning = holdToTalkDelay.getStatus() == Animation.Status.RUNNING;
        holdToTalkDelay.stop();
        if (recordingViaMouse) {
            event.consume();
            recordingViaMouse = false;
            if (dictationService.isRecording()) {
                finishRecording();
            }
            return;
        }
        if (delayRunning && dictationModeEnabled && dictationButton != null) {
            event.consume();
            dictationButton.setSelected(false);
        }
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.isConsumed()) {
            return;
        }
        if (!dictationModeEnabled) {
            return;
        }
        if (!isPushToTalkKey(event) || dictationService.isRecording()) {
            return;
        }
        holdToTalkDelay.stop();
        event.consume();
        recordingViaHotkey = true;
        spacePushToTalkActive = isOptionSpacePushToTalk(event);
        startRecording();
    }

    private void onKeyReleased(KeyEvent event) {
        if (event.isConsumed() || !recordingViaHotkey) {
            return;
        }
        if (!isPushToTalkReleaseKey(event)) {
            return;
        }
        event.consume();
        recordingViaHotkey = false;
        spacePushToTalkActive = false;
        if (dictationService.isRecording()) {
            finishRecording();
        }
    }

    /** Verhindert Leerzeichen in den Editor bei Option+Leertaste (KEY_TYPED kommt nach KEY_PRESSED). */
    private void onKeyTyped(KeyEvent event) {
        if (event.isConsumed() || !shouldSuppressTypedCharacter(event)) {
            return;
        }
        event.consume();
    }

    private boolean shouldSuppressTypedCharacter(KeyEvent event) {
        if (!dictationModeEnabled || !recordingViaHotkey || !spacePushToTalkActive) {
            return false;
        }
        String character = event.getCharacter();
        return character != null && !character.isEmpty();
    }

    private boolean isPushToTalkKey(KeyEvent event) {
        return DictationHotkeys.isPushToTalkPress(
                event.getCode(),
                event.isAltDown(),
                event.isMetaDown(),
                event.isControlDown(),
                EditingShortcuts.isMac());
    }

    private boolean isOptionSpacePushToTalk(KeyEvent event) {
        return DictationHotkeys.isOptionSpace(
                event.getCode(),
                event.isAltDown(),
                event.isMetaDown(),
                event.isControlDown(),
                EditingShortcuts.isMac());
    }

    private boolean isPushToTalkReleaseKey(KeyEvent event) {
        return DictationHotkeys.isPushToTalkRelease(event.getCode(), spacePushToTalkActive);
    }

    private static String pushToTalkHint() {
        if (EditingShortcuts.isMac()) {
            return "F9, F10 oder Option+Leertaste";
        }
        return "F9 oder F10";
    }

    private void startRecording() {
        if (!dictationService.startRecording()) {
            Exception err = dictationService.getRecorder().getLastError();
            String msg = err != null ? err.getMessage() : "Aufnahme konnte nicht gestartet werden.";
            showError("Aufnahme fehlgeschlagen", msg);
            return;
        }
        setRecordingAppearance(true);
        refreshBusyBar();
        host.updateStatus(statusWhileRecording());
    }

    private void finishRecording() {
        setRecordingAppearance(false);
        int jobId = nextJobId++;
        uiPendingJobs++;
        refreshBusyBar();
        host.updateStatus(statusWhileProcessing());

        String marker;
        String editorContext;
        try {
            int insertStart = Math.min(host.getSelectionStart(), host.getSelectionEnd());
            int insertEnd = Math.max(host.getSelectionStart(), host.getSelectionEnd());
            String editorText = host.getText() != null ? host.getText() : "";
            insertStart = Math.max(0, Math.min(editorText.length(), insertStart));
            insertEnd = Math.max(insertStart, Math.min(editorText.length(), insertEnd));

            editorContext = DictationPromptBuilder.extractEditorContext(editorText, insertStart);
            marker = buildPendingMarker(jobId);
            host.replaceRange(insertStart, insertEnd, marker);
            int afterMarker = insertStart + marker.length();
            host.selectRange(afterMarker, afterMarker);
        } catch (RuntimeException e) {
            logger.warn("Diktat-Markierung konnte nicht gesetzt werden: {}", e.getMessage());
            editorContext = "";
            marker = null;
        }

        DictationVocabulary vocabulary = DictationVocabulary.fromHost(host);
        int quoteStyleIndex = host.getQuoteStyleIndex();
        final String pendingMarker = marker;

        dictationService.stopAndProcess(editorContext, vocabulary, analysis -> Platform.runLater(() -> {
                    if (dictationService.isRecording()) {
                        return;
                    }
                    if (analysis.mode() == DictationMode.INSTRUCTION) {
                        setInstructionAppearance(true);
                        String vocabHint = vocabularyHint(vocabulary);
                        host.updateStatus("Anweisung erkannt: "
                                + summarizeInstruction(analysis.instructionText())
                                + vocabHint
                                + pendingSuffix());
                    } else {
                        String vocabHint = vocabularyHint(vocabulary);
                        host.updateStatus(statusWhileProcessing() + vocabHint);
                    }
                }), quoteStyleIndex)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        readyOutcomes.put(jobId, PendingOutcome.error(cause.getMessage(), pendingMarker));
                    } else {
                        readyOutcomes.put(jobId, PendingOutcome.success(result, pendingMarker));
                    }
                    drainReadyOutcomes();
                }));
    }

    /**
     * Fügt fertige Diktate in Aufnahme-Reihenfolge ein, auch wenn ein späteres Job früher fertig ist.
     */
    private void drainReadyOutcomes() {
        boolean lastWasRawFallback = false;
        while (readyOutcomes.containsKey(nextApplyJobId)) {
            PendingOutcome outcome = readyOutcomes.remove(nextApplyJobId);
            nextApplyJobId++;
            uiPendingJobs = Math.max(0, uiPendingJobs - 1);
            setInstructionAppearance(false);

            if (outcome.errorMessage != null) {
                logger.warn("Diktat fehlgeschlagen: {}", outcome.errorMessage);
                removePendingMarker(outcome.marker);
                showError(resolveErrorHeader(outcome.errorMessage), outcome.errorMessage);
                lastWasRawFallback = false;
            } else if (outcome.result != null) {
                lastWasRawFallback = !outcome.result.llmFormatted();
                applyResult(outcome.result, outcome.marker);
            } else {
                removePendingMarker(outcome.marker);
                lastWasRawFallback = false;
            }
        }
        refreshBusyBar();
        if (dictationService.isRecording()) {
            host.updateStatus(statusWhileRecording());
        } else if (uiPendingJobs > 0) {
            host.updateStatus(statusWhileProcessing());
        } else if (lastWasRawFallback) {
            host.updateStatus("Diktat eingefügt (ohne KI-Korrektur — Zeitüberschreitung).");
        } else if (dictationModeEnabled) {
            host.updateStatus("Diktat-Modus an (Taste halten oder " + pushToTalkHint() + ")");
        } else {
            host.updateStatus("Diktat eingefügt.");
        }
    }

    private void refreshBusyBar() {
        boolean shouldBeBusy = dictationService.isRecording() || uiPendingJobs > 0;
        if (shouldBeBusy == busyBarHeld) {
            return;
        }
        busyBarHeld = shouldBeBusy;
        host.setStatusBusyBarActive(shouldBeBusy);
    }

    private String statusWhileRecording() {
        if (uiPendingJobs > 0) {
            return "Diktat: Aufnahme… (" + uiPendingJobs + " in Verarbeitung, Taste oder "
                    + pushToTalkHint() + " halten)";
        }
        return "Diktat: Aufnahme… (Taste oder " + pushToTalkHint() + " halten)";
    }

    private String statusWhileProcessing() {
        if (uiPendingJobs <= 1) {
            return "Diktat: Verarbeite…";
        }
        return "Diktat: Verarbeite… (" + uiPendingJobs + ")";
    }

    private String pendingSuffix() {
        if (uiPendingJobs <= 1) {
            return "";
        }
        return " (" + uiPendingJobs + " in Verarbeitung)";
    }

    /** Sichtbare, job-eindeutige Einfüge-Markierung im Editortext. */
    static String buildPendingMarker(int jobId) {
        return "⟦d:" + Math.max(0, jobId) + "⟧";
    }

    private static final class PendingOutcome {
        final DictationResult result;
        final String errorMessage;
        final String marker;

        private PendingOutcome(DictationResult result, String errorMessage, String marker) {
            this.result = result;
            this.errorMessage = errorMessage;
            this.marker = marker;
        }

        static PendingOutcome success(DictationResult result, String marker) {
            return new PendingOutcome(result, null, marker);
        }

        static PendingOutcome error(String message, String marker) {
            return new PendingOutcome(null, message != null ? message : "Unbekannter Fehler", marker);
        }
    }

    private void showError(String header, String detail) {
        if (DictationWhisperSetup.isWhisperSetupMessage(detail)) {
            DictationWhisperSetup.show(ownerStage, themeIndex, header, detail);
        } else {
            DictationErrorDialog.show(ownerStage, themeIndex, header, detail);
        }
        if (!dictationModeEnabled) {
            host.updateStatus("Bereit");
        }
    }

    private static String resolveErrorHeader(String message) {
        if (message == null || message.isBlank()) {
            return "Diktat fehlgeschlagen";
        }
        String lower = message.toLowerCase();
        if (lower.contains("whisper-cli nicht gefunden") || lower.contains("whisper-modell")) {
            return "Whisper einrichten";
        }
        if (lower.contains("api-key")) {
            return "Spracherkennung konfigurieren";
        }
        if (lower.contains("zu kurz")) {
            return "Aufnahme zu kurz";
        }
        if (lower.contains("stumm") || lower.contains("sprachsignal")) {
            return "Mikrofon ohne Signal";
        }
        if (lower.contains("mikrofon")) {
            return "Mikrofon";
        }
        if (lower.contains("ollama") || lower.contains("connection refused")) {
            return "KI-Verbindung";
        }
        if (lower.contains("timeout") || lower.contains("zeitüberschreitung")
                || lower.contains("timed out")) {
            return "KI antwortet nicht";
        }
        return "Diktat fehlgeschlagen";
    }

    private void applyResult(DictationResult result, String marker) {
        boolean preview = Boolean.parseBoolean(
                ResourceManager.getParameter("dictation.enable_preview_before_insert", "false"));
        if (preview) {
            DictationPreviewDialog.show(
                    host,
                    ownerStage,
                    themeIndex,
                    result,
                    text -> insertProcessedText(text, marker),
                    () -> removePendingMarker(marker));
        } else {
            insertProcessedText(result.processedText(), marker);
        }
    }

    private static String summarizeInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return "";
        }
        String compact = instruction.trim().replaceAll("\\s+", " ");
        if (compact.length() <= 72) {
            return compact;
        }
        return compact.substring(0, 69) + "…";
    }

    private static String vocabularyHint(DictationVocabulary vocabulary) {
        if (vocabulary == null || vocabulary.isEmpty()) {
            return " (Glossar leer — Glossar bearbeiten: Rechtsklick auf Diktat)";
        }
        if (!vocabulary.hasUserGlossary()) {
            return " (" + vocabulary.termCount() + " Begriffe, Glossar-Datei noch leer)";
        }
        return " (" + vocabulary.termCount() + " Glossar-Begriffe)";
    }

    private void insertProcessedText(String text, String marker) {
        if (text == null || text.isBlank()) {
            removePendingMarker(marker);
            return;
        }
        String current = host.getText() != null ? host.getText() : "";
        int insertAt = host.getSelectionStart();
        if (marker != null && !marker.isBlank()) {
            int markerAt = current.indexOf(marker);
            if (markerAt >= 0) {
                insertAt = markerAt;
            }
        }
        insertAt = Math.max(0, Math.min(current.length(), insertAt));
        String toInsert = DictationInsertCapitalization.adjustLeadingCapital(
                text, current.substring(0, insertAt));
        if (!toInsert.endsWith(" ") && !toInsert.endsWith("\n")) {
            toInsert = toInsert + " ";
        }
        if (marker != null && !marker.isBlank()) {
            int markerAt = current.indexOf(marker);
            if (markerAt >= 0) {
                // Viewport/Caret des Nutzers behalten – typisch editiert man hinter der Markierung.
                host.replaceRangePreserveView(markerAt, markerAt + marker.length(), toInsert);
                return;
            }
            logger.warn("Diktat-Markierung {} fehlt – Fallback am Cursor", marker);
            host.updateStatus("Diktat-Markierung fehlt – am Cursor eingefügt");
        }
        host.insertTextAtCaret(toInsert);
    }

    private void removePendingMarker(String marker) {
        if (marker == null || marker.isBlank()) {
            return;
        }
        String current = host.getText() != null ? host.getText() : "";
        int markerAt = current.indexOf(marker);
        if (markerAt >= 0) {
            host.replaceRangePreserveView(markerAt, markerAt + marker.length(), "");
        }
    }

    private void applyModeAppearance() {
        if (dictationButton == null) {
            return;
        }
        if (dictationModeEnabled) {
            dictationButton.getStyleClass().add("dictation-btn-active");
        } else {
            dictationButton.getStyleClass().remove("dictation-btn-active");
            dictationButton.getStyleClass().remove("dictation-btn-recording");
        }
    }

    private void setRecordingAppearance(boolean recording) {
        if (dictationButton == null) {
            return;
        }
        if (recording) {
            dictationButton.getStyleClass().add("dictation-btn-recording");
        } else {
            dictationButton.getStyleClass().remove("dictation-btn-recording");
        }
    }

    private void setInstructionAppearance(boolean active) {
        if (dictationButton == null) {
            return;
        }
        if (active) {
            dictationButton.setText("Anweisung…");
            if (!dictationButton.getStyleClass().contains("dictation-btn-instruction")) {
                dictationButton.getStyleClass().add("dictation-btn-instruction");
            }
        } else {
            dictationButton.setText("Diktat");
            dictationButton.getStyleClass().remove("dictation-btn-instruction");
        }
    }
}
