package com.manuskript.dictation;

import com.manuskript.ChapterEditorHost;
import com.manuskript.EditingShortcuts;
import com.manuskript.ManuskriptTextEditor;
import com.manuskript.NovelManager;
import com.manuskript.ResourceManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
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
    private boolean spacePushToTalkActive;
    private boolean altModifierHeld;
    private boolean keyHandlersInstalled;
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
                "Diktat-Modus ein/aus.\n"
                        + "Im Modus: " + hotkeyHint + " gedrückt halten zum Sprechen, loslassen zum Einfügen.\n"
                        + "Während der Verarbeitung kannst du sofort weiter diktieren; "
                        + "Texte werden in Aufnahme-Reihenfolge eingefügt.\n"
                        + "Kommando: mit „Anweisung:“, „Befehl:“ oder „Kommando:“ beginnen — "
                        + "z. B. „Anweisung: Schreibe, dass Luna wacklige Knie hat nach der Ankunft“.\n"
                        + "Format: „kursiv“, „fett“, „Absatz“; "
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
                dictationService.cancelRecording();
                setRecordingAppearance(false);
                refreshBusyBar();
            }
            dictationModeEnabled = selected;
            preferences.putBoolean(PREF_DICTATION_MODE, selected);
            applyModeAppearance();
            host.updateStatus(selected ? "Diktat-Modus an (" + hotkeyHint + " halten)" : "Bereit");
        });

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
        keyHandlersInstalled = scene != null || editor != null;
        logger.debug("Diktat-Hotkeys installiert (scene={}, editor={})", scene != null, editor != null);
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.isConsumed()) {
            return;
        }
        trackModifiers(event, true);
        if (!dictationModeEnabled) {
            return;
        }
        if (!isPushToTalkKey(event) || dictationService.isRecording()) {
            return;
        }
        event.consume();
        recordingViaHotkey = true;
        spacePushToTalkActive = isOptionSpacePushToTalk(event);
        startRecording();
    }

    private void onKeyReleased(KeyEvent event) {
        if (event.isConsumed()) {
            return;
        }
        if (!recordingViaHotkey) {
            trackModifiers(event, false);
            return;
        }
        boolean release = isPushToTalkReleaseKey(event);
        trackModifiers(event, false);
        if (!release) {
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

    private void trackModifiers(KeyEvent event, boolean pressed) {
        if (event.getCode() == KeyCode.ALT) {
            altModifierHeld = pressed;
        } else if (pressed && event.isAltDown()) {
            altModifierHeld = true;
        }
    }

    private boolean shouldSuppressTypedCharacter(KeyEvent event) {
        if (!dictationModeEnabled) {
            return false;
        }
        if (recordingViaHotkey && spacePushToTalkActive) {
            String character = event.getCharacter();
            return character != null && !character.isEmpty();
        }
        return EditingShortcuts.isMac()
                && (altModifierHeld || event.isAltDown())
                && isSpaceTypedCharacter(event);
    }

    private static boolean isSpaceTypedCharacter(KeyEvent event) {
        String character = event.getCharacter();
        if (character == null || character.isEmpty()) {
            return false;
        }
        char ch = character.charAt(0);
        return ch == ' ' || ch == '\u00a0';
    }

    private boolean isPushToTalkKey(KeyEvent event) {
        if (event.getCode() == KeyCode.F9 || event.getCode() == KeyCode.F10) {
            return true;
        }
        return isOptionSpacePushToTalk(event);
    }

    private boolean isOptionSpacePushToTalk(KeyEvent event) {
        if (!EditingShortcuts.isMac() || event.getCode() != KeyCode.SPACE) {
            return false;
        }
        return event.isAltDown() || altModifierHeld;
    }

    private boolean isPushToTalkReleaseKey(KeyEvent event) {
        if (event.getCode() == KeyCode.F9 || event.getCode() == KeyCode.F10) {
            return true;
        }
        if (!spacePushToTalkActive) {
            return false;
        }
        if (event.getCode() == KeyCode.SPACE) {
            return true;
        }
        return event.getCode() == KeyCode.ALT;
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

        String editorContext = DictationPromptBuilder.extractEditorContext(
                host.getText(), host.getCaretPosition());
        DictationVocabulary vocabulary = DictationVocabulary.fromHost(host);
        int quoteStyleIndex = host.getQuoteStyleIndex();

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
                        readyOutcomes.put(jobId, PendingOutcome.error(cause.getMessage()));
                    } else {
                        readyOutcomes.put(jobId, PendingOutcome.success(result));
                    }
                    drainReadyOutcomes();
                }));
    }

    /**
     * Fügt fertige Diktate in Aufnahme-Reihenfolge ein, auch wenn ein späteres Job früher fertig ist.
     */
    private void drainReadyOutcomes() {
        while (readyOutcomes.containsKey(nextApplyJobId)) {
            PendingOutcome outcome = readyOutcomes.remove(nextApplyJobId);
            nextApplyJobId++;
            uiPendingJobs = Math.max(0, uiPendingJobs - 1);
            setInstructionAppearance(false);

            if (outcome.errorMessage != null) {
                logger.warn("Diktat fehlgeschlagen: {}", outcome.errorMessage);
                showError(resolveErrorHeader(outcome.errorMessage), outcome.errorMessage);
            } else if (outcome.result != null) {
                applyResult(outcome.result);
            }
        }
        refreshBusyBar();
        if (dictationService.isRecording()) {
            host.updateStatus(statusWhileRecording());
        } else if (uiPendingJobs > 0) {
            host.updateStatus(statusWhileProcessing());
        } else if (dictationModeEnabled) {
            host.updateStatus("Diktat-Modus an (" + pushToTalkHint() + " halten)");
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
            return "Diktat: Aufnahme… (" + uiPendingJobs + " in Verarbeitung, "
                    + pushToTalkHint() + " halten)";
        }
        return "Diktat: Aufnahme… (" + pushToTalkHint() + " halten)";
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

    private static final class PendingOutcome {
        final DictationResult result;
        final String errorMessage;

        private PendingOutcome(DictationResult result, String errorMessage) {
            this.result = result;
            this.errorMessage = errorMessage;
        }

        static PendingOutcome success(DictationResult result) {
            return new PendingOutcome(result, null);
        }

        static PendingOutcome error(String message) {
            return new PendingOutcome(null, message != null ? message : "Unbekannter Fehler");
        }
    }

    private void showError(String header, String detail) {
        DictationErrorDialog.show(ownerStage, themeIndex, header, detail);
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
        return "Diktat fehlgeschlagen";
    }

    private void applyResult(DictationResult result) {
        boolean preview = Boolean.parseBoolean(
                ResourceManager.getParameter("dictation.enable_preview_before_insert", "false"));
        if (preview) {
            DictationPreviewDialog.show(host, ownerStage, themeIndex, result, text -> insertProcessedText(text));
        } else {
            insertProcessedText(result.processedText());
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

    private void insertProcessedText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String toInsert = text;
        if (!toInsert.endsWith(" ") && !toInsert.endsWith("\n")) {
            toInsert = toInsert + " ";
        }
        // Kein revealRange: das würde den Viewport auf den Caret zentrieren und die Leseposition verschieben.
        // insertTextAtCaret erhält die Viewport-Verankerung des Canvas-Editors.
        host.insertTextAtCaret(toInsert);
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
