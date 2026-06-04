package com.manuskript;

import javafx.stage.Stage;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Kontextmenü-Aktionen „Sprechantwort korrigieren“, „Phrase korrigieren“, „Selektion überarbeiten“.
 */
public class ChapterRewriteContextActions {

    private final ChapterEditorHost host;
    private final Stage ownerStage;
    private final Preferences preferences;
    private final File chapterDocx;
    private final IntSupplier themeIndexSupplier;

    @FunctionalInterface
    public interface IntSupplier {
        int getAsInt();
    }

    public ChapterRewriteContextActions(ChapterEditorHost host, Stage ownerStage, int themeIndex,
                                        Preferences preferences, File chapterDocx) {
        this(host, ownerStage, () -> themeIndex, preferences, chapterDocx);
    }

    public ChapterRewriteContextActions(ChapterEditorHost host, Stage ownerStage, IntSupplier themeIndexSupplier,
                                        Preferences preferences, File chapterDocx) {
        this.host = host;
        this.ownerStage = ownerStage;
        this.themeIndexSupplier = themeIndexSupplier;
        this.preferences = preferences;
        this.chapterDocx = chapterDocx;
    }

    public void handleSprechantwortKorrektur() {
        int caretPos = host.getCaretPosition();
        int[] bounds = ChapterRewriteSentenceUtils.findCurrentSentenceBounds(host.getText(), caretPos);
        if (bounds == null) {
            host.updateStatus("Kein Satz an der Cursorposition gefunden.");
            return;
        }
        String sentence = host.getText().substring(bounds[0], bounds[1]);
        if (!ChapterRewriteSentenceUtils.containsSpeechTag(sentence)) {
            host.updateStatus("Kein Sprechantwort im aktuellen Satz gefunden.");
            return;
        }
        host.selectRange(bounds[0], bounds[1]);
        openRewriteDialog("Sprechantwort korrigieren", sentence, bounds[0], bounds[1], "speech");
    }

    public void handlePhraseKorrektur() {
        int startPos;
        int endPos;
        if (host.hasTextSelection()) {
            startPos = host.getSelectionStart();
            endPos = host.getSelectionEnd();
        } else {
            int caretPos = host.getCaretPosition();
            int[] bounds = ChapterRewriteSentenceUtils.findCurrentSentenceBounds(host.getText(), caretPos);
            if (bounds == null) {
                host.updateStatus("Bitte zuerst eine Phrase auswählen oder den Cursor in einen Satz setzen.");
                return;
            }
            startPos = bounds[0];
            endPos = bounds[1];
            host.selectRange(startPos, endPos);
        }
        String text = host.getText().substring(startPos, endPos);
        if (text.trim().isEmpty()) {
            host.updateStatus("Die Auswahl ist leer.");
            return;
        }
        openRewriteDialog("Phrase korrigieren", text, startPos, endPos, "phrase");
    }

    public void handleSelektionUeberarbeitung() {
        if (!host.hasTextSelection()) {
            host.updateStatus("Bitte zuerst Text auswählen.");
            return;
        }
        int startPos = host.getSelectionStart();
        int endPos = host.getSelectionEnd();
        String selected = host.getText().substring(startPos, endPos);
        if (selected.trim().isEmpty()) {
            host.updateStatus("Die Auswahl ist leer.");
            return;
        }
        openRewriteDialog("Selektion überarbeiten", selected, startPos, endPos, "selection");
    }

    private void openRewriteDialog(String title, String originalText, int startPos, int endPos, String mode) {
        int themeIndex = themeIndexSupplier.getAsInt();
        if (useOpenAiBackend()) {
            ChapterApiRewriteDialog.show(host, ownerStage, themeIndex, preferences, title, originalText, startPos, endPos, mode);
            return;
        }
        ChapterOllamaRewriteDialog.Mode ollamaMode = switch (mode) {
            case "speech" -> ChapterOllamaRewriteDialog.Mode.SPEECH;
            case "phrase" -> ChapterOllamaRewriteDialog.Mode.PHRASE;
            default -> ChapterOllamaRewriteDialog.Mode.SELECTION;
        };
        ChapterOllamaRewriteDialog.show(host, ownerStage, themeIndex, preferences, chapterDocx,
                title, originalText, startPos, endPos, ollamaMode);
    }

    private static boolean useOpenAiBackend() {
        return "OpenAI".equalsIgnoreCase(ResourceManager.getParameter("agent.backend", "Ollama").trim());
    }

}
