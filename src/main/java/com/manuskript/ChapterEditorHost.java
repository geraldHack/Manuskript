package com.manuskript;

import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Gemeinsame API für Kapitel-Editoren (RichTextFX und Canvas-Editor).
 * Neue Integrationen (Agenten, Makros, Lektorat) sollen nur diese Schnittstelle nutzen.
 */
public interface ChapterEditorHost {

    enum EditorKind {
        LEGACY_CODE_AREA,
        CANVAS
    }

    EditorKind getEditorKind();

    Stage getStage();

    File getOriginalDocxFile();

    void setOriginalDocxFile(File file);

    /** Schlüssel wie in {@link MainController#registerChapterEditor}: {@code kapitel.md}. */
    String getEditorKey();

    String getText();

    void setText(String text);

    void replaceRange(int start, int end, String replacement);

    /** Ersetzt Text, ohne Caret und Scroll-Position zu verändern (Canvas-Editor). */
    default void replaceRangePreserveView(int start, int end, String replacement) {
        replaceRange(start, end, replacement);
    }

    void revealRange(int start, int end);

    int getCaretPosition();

    default int getSelectionStart() {
        return getCaretPosition();
    }

    default int getSelectionEnd() {
        return getCaretPosition();
    }

    default boolean hasTextSelection() {
        return getSelectionStart() != getSelectionEnd();
    }

    void selectRange(int start, int end);

    void requestEditorFocus();

    boolean isDirty();

    void markSaved();

    void insertTextAtCaret(String text);

    void jumpToQuote(String quote);

    void setOnlineLektoratMode(boolean enabled);

    void startOnlineLektorat();

    void startOnlineLektorat(boolean enableAssessment);

    /** Wie {@link #startOnlineLektorat(boolean)}, mit explizitem Lektorat-Typ für diesen Lauf. */
    default void startOnlineLektorat(boolean enableAssessment, String lektoratType) {
        startOnlineLektorat(enableAssessment);
    }

    /** Lektorat beenden: Markierungen entfernen, Panel schließen, Agenten wieder freigeben. */
    default void exitOnlineLektorat() {
    }

    void updateStatus(String message);

    void updateStatusError(String message);

    /** Indeterminater Busy-Balken in der Statuszeile (z. B. Online-Lektorat). */
    default void setStatusBusyBarActive(boolean active) {
    }

    int getThemeIndex();

    MainController getMainController();

    default EditorWindow asLegacyEditorWindow() {
        return this instanceof EditorWindow editorWindow ? editorWindow : null;
    }

    default ManuskriptEditorTestWindow asCanvasChapterEditor() {
        return this instanceof ManuskriptEditorTestWindow window ? window : null;
    }

    default boolean saveChapterIfPossible() {
        try {
            return saveChapter();
        } catch (IOException e) {
            updateStatusError("Speichern fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    boolean saveChapter() throws IOException;

    default void runMacro(Macro macro, Consumer<String> statusUpdater) {
        MacroExecutor.execute(macro, this, statusUpdater);
    }

    /** Schriftgröße des Editors in Pixel (für Agenten-Panel etc.). */
    default int getEditorFontSizePx() {
        return java.util.prefs.Preferences.userNodeForPackage(EditorWindow.class).getInt("fontSize", 12);
    }

    /** Schriftart des Editors (für Agenten-Panel etc.). */
    default String getEditorFontFamily() {
        return java.util.prefs.Preferences.userNodeForPackage(EditorWindow.class)
                .get("quillFontFamily", "Segoe UI");
    }

    /** Anführungszeichen-Stil (0=deutsch, 1=französisch, 2=englisch, 3=schweizer). */
    default int getQuoteStyleIndex() {
        int style = java.util.prefs.Preferences.userNodeForPackage(EditorWindow.class).getInt("quoteStyle", 0);
        return style >= 0 && style < QuotationMarkSupport.STYLE_COUNT ? style : 0;
    }

    /**
     * Globale Suche aus dem Hauptfenster: Suchfeld befüllen, alle Treffer markieren,
     * zum ersten Treffer scrollen.
     */
    default void applyGlobalSearch(String searchText, boolean regex, boolean caseSensitive, boolean wholeWord) {
    }
}
