package com.manuskript;

import java.util.List;

/**
 * Umfang einer Welt-Editor-Extraktion aus Kapitel-MDs (optional mit Figurenfokus).
 */
public record WorldEditorExtractScope(List<String> selectedMdFileNames, String characterFocus) {

    public boolean hasChapterSelection() {
        return selectedMdFileNames != null && !selectedMdFileNames.isEmpty();
    }

    public boolean hasCharacterFocus() {
        return characterFocus != null && !characterFocus.isBlank();
    }
}
