package com.manuskript;

import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

/**
 * {@link ChapterEditorHost}-Adapter für den RichTextFX-{@link EditorWindow}.
 */
public class EditorWindowHostAdapter implements ChapterEditorHost {

    private final EditorWindow editor;

    public EditorWindowHostAdapter(EditorWindow editor) {
        this.editor = editor;
    }

    public EditorWindow getEditorWindow() {
        return editor;
    }

    @Override
    public EditorKind getEditorKind() {
        return EditorKind.LEGACY_CODE_AREA;
    }

    @Override
    public Stage getStage() {
        return editor.getStage();
    }

    @Override
    public File getOriginalDocxFile() {
        return editor.getOriginalDocxFile();
    }

    @Override
    public void setOriginalDocxFile(File file) {
        editor.setOriginalDocxFile(file);
    }

    @Override
    public String getEditorKey() {
        File docx = editor.getOriginalDocxFile();
        if (docx == null) {
            return null;
        }
        String name = docx.getName();
        if (name.toLowerCase().endsWith(".docx")) {
            name = name.substring(0, name.length() - 5);
        }
        return name + ".md";
    }

    @Override
    public String getText() {
        return editor.getText();
    }

    @Override
    public void setText(String text) {
        editor.setText(text);
    }

    @Override
    public void replaceRange(int start, int end, String replacement) {
        editor.replaceTextRange(start, end, replacement);
    }

    @Override
    public void revealRange(int start, int end) {
        editor.revealTextRange(start, end);
    }

    @Override
    public int getCaretPosition() {
        return editor.getCaretPosition();
    }

    @Override
    public void selectRange(int start, int end) {
        editor.selectTextRange(start, end);
    }

    @Override
    public void requestEditorFocus() {
        editor.requestEditorFocus();
    }

    @Override
    public boolean isDirty() {
        return editor.hasUnsavedChanges();
    }

    @Override
    public void markSaved() {
        editor.markAsSavedPublic();
    }

    @Override
    public void insertTextAtCaret(String text) {
        editor.insertTextFromAI(text);
    }

    @Override
    public void jumpToQuote(String quote) {
        editor.jumpToQuoteFromHost(quote);
    }

    @Override
    public void setOnlineLektoratMode(boolean enabled) {
        editor.setOnlineLektoratMode(enabled);
    }

    @Override
    public void startOnlineLektorat() {
        editor.startOnlineLektorat();
    }

    @Override
    public void startOnlineLektorat(boolean enableAssessment) {
        editor.startOnlineLektorat(enableAssessment);
    }

    @Override
    public void updateStatus(String message) {
        editor.updateStatus(message);
    }

    @Override
    public void updateStatusError(String message) {
        editor.updateStatusError(message);
    }

    @Override
    public int getThemeIndex() {
        return editor.getCurrentThemeIndex();
    }

    @Override
    public MainController getMainController() {
        return editor.getMainController();
    }

    @Override
    public boolean saveChapter() throws IOException {
        return editor.saveCurrentChapterFromHost();
    }
}
