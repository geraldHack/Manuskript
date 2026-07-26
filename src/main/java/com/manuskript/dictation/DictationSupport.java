package com.manuskript.dictation;

import com.manuskript.ChapterEditorHost;
import com.manuskript.EditingShortcuts;
import com.manuskript.ManuskriptTextEditor;
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
                        + "Kommando: mit „Anweisung:“, „Befehl:“ oder „Kommando:“ beginnen — "
                        + "z. B. „Anweisung: Schreibe, dass Luna wacklige Knie hat nach der Ankunft“.\n"
                        + "Format: „kursiv“, „fett“, „Absatz“; "
                        + DictationSpokenMarkup.spokenMarkupHint() + ".\n"
                        + "Glossar: dictation-glossary.txt im Buchordner.\n"
                        + "STT: lokal (whisper.cpp). LLM: agent.backend."));
        dictationButton.getStyleClass().add("dictation-btn");
        dictationButton.setSelected(dictationModeEnabled);
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
                host.setStatusBusyBarActive(false);
            }
            dictationModeEnabled = selected;
            preferences.putBoolean(PREF_DICTATION_MODE, selected);
            applyModeAppearance();
            host.updateStatus(selected ? "Diktat-Modus an (" + hotkeyHint + " halten)" : "Bereit");
        });

        return dictationButton;
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
        if (!dictationModeEnabled || dictationService.isProcessing()) {
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
        host.updateStatus("Diktat: Aufnahme… (" + pushToTalkHint() + " halten)");
        host.setStatusBusyBarActive(true);
    }

    private void finishRecording() {
        setRecordingAppearance(false);
        host.updateStatus("Diktat: Verarbeite…");

        String editorContext = DictationPromptBuilder.extractEditorContext(
                host.getText(), host.getCaretPosition());
        DictationVocabulary vocabulary = DictationVocabulary.fromHost(host);

        int quoteStyleIndex = host.getQuoteStyleIndex();
        dictationService.stopAndProcess(editorContext, vocabulary, analysis -> Platform.runLater(() -> {
                    if (analysis.mode() == DictationMode.INSTRUCTION) {
                        setInstructionAppearance(true);
                        String vocabHint = vocabularyHint(vocabulary);
                        host.updateStatus("Anweisung erkannt: "
                                + summarizeInstruction(analysis.instructionText())
                                + vocabHint);
                    } else {
                        String vocabHint = vocabularyHint(vocabulary);
                        host.updateStatus(vocabHint.isEmpty()
                                ? "Diktat: Verarbeite…"
                                : "Diktat: Verarbeite…" + vocabHint);
                    }
                }), quoteStyleIndex)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    host.setStatusBusyBarActive(false);
                    setInstructionAppearance(false);
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        String message = cause.getMessage();
                        logger.warn("Diktat fehlgeschlagen: {}", message);
                        showError(resolveErrorHeader(message), message);
                        if (dictationModeEnabled) {
                            host.updateStatus("Diktat-Modus an (" + pushToTalkHint() + " halten)");
                        }
                        return;
                    }
                    applyResult(result);
                }));
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
            host.updateStatus(result.mode() == DictationMode.INSTRUCTION
                    ? "Anweisung: Vorschau angezeigt."
                    : "Diktat: Vorschau angezeigt.");
        } else {
            insertProcessedText(result.processedText());
            if (dictationModeEnabled) {
                host.updateStatus(result.mode() == DictationMode.INSTRUCTION
                        ? "Anweisung umgesetzt — Diktat-Modus an (" + pushToTalkHint() + " halten)"
                        : "Diktat-Modus an (" + pushToTalkHint() + " halten)");
            } else {
                host.updateStatus(result.mode() == DictationMode.INSTRUCTION
                        ? "Anweisung umgesetzt."
                        : "Diktat eingefügt.");
            }
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
            return " (Glossar leer — dictation-glossary.txt anlegen)";
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
