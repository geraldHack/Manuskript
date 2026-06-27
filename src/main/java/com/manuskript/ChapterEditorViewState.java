package com.manuskript;

import java.util.HashMap;
import java.util.Map;

/**
 * Merkt sich Caret- und Scroll-Position pro Kapitel ({@code kapitel.md}) für die laufende App-Sitzung.
 */
public final class ChapterEditorViewState {

    private static final Map<String, Integer> CARET_BY_KEY = new HashMap<>();
    private static final Map<String, Double> SCROLL_RATIO_BY_KEY = new HashMap<>();

    public record ViewState(int caret, Double scrollRatio) {
    }

    private ChapterEditorViewState() {
    }

    public static void save(String editorKey, int caret, double scrollRatio) {
        if (editorKey == null || editorKey.isBlank()) {
            return;
        }
        CARET_BY_KEY.put(editorKey, Math.max(0, caret));
        SCROLL_RATIO_BY_KEY.put(editorKey, Math.max(0.0, Math.min(1.0, scrollRatio)));
    }

    public static ViewState load(String editorKey) {
        if (editorKey == null || !CARET_BY_KEY.containsKey(editorKey)) {
            return null;
        }
        return new ViewState(CARET_BY_KEY.get(editorKey), SCROLL_RATIO_BY_KEY.get(editorKey));
    }
}
