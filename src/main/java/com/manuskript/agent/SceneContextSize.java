package com.manuskript.agent;

/**
 * Kontext-Umfang für den Agenten „Szene Schreiben“.
 */
public enum SceneContextSize {

    COMPACT(
            "Gekürzt",
            "Wenig Kontext — schneller und günstiger.",
            2_000,
            16_000,
            8_000,
            10,
            4_000
    ),
    EXTENDED(
            "Mehr",
            "Erweiterter Kontext für längere Kapitel und mehr Vorgeschichte.",
            6_000,
            32_000,
            24_000,
            15,
            12_000
    ),
    FULL(
            "Alles (teuer!)",
            "Voller Kontext — langsam/teuer; bei Ollama oft trotzdem gekürzt (num_ctx).",
            -1,
            -1,
            -1,
            -1,
            -1
    );

    private final String label;
    private final String tooltip;
    private final int maxBlockChars;
    private final int maxSceneOutlineChars;
    private final int maxChapterChars;
    private final int maxRecentChapters;
    private final int maxCharsPerRecentChapter;

    SceneContextSize(String label, String tooltip,
                       int maxBlockChars, int maxSceneOutlineChars, int maxChapterChars,
                       int maxRecentChapters, int maxCharsPerRecentChapter) {
        this.label = label;
        this.tooltip = tooltip;
        this.maxBlockChars = maxBlockChars;
        this.maxSceneOutlineChars = maxSceneOutlineChars;
        this.maxChapterChars = maxChapterChars;
        this.maxRecentChapters = maxRecentChapters;
        this.maxCharsPerRecentChapter = maxCharsPerRecentChapter;
    }

    public String getLabel() {
        return label;
    }

    public String getTooltip() {
        return tooltip;
    }

    int maxBlockChars() {
        return maxBlockChars;
    }

    int maxSceneOutlineChars() {
        return maxSceneOutlineChars;
    }

    int maxChapterChars() {
        return maxChapterChars;
    }

    int maxRecentChapters() {
        return maxRecentChapters;
    }

    int maxCharsPerRecentChapter() {
        return maxCharsPerRecentChapter;
    }

    boolean unlimited(int limit) {
        return limit < 0;
    }

    public static SceneContextSize fromName(String name) {
        if (name == null || name.isBlank()) {
            return COMPACT;
        }
        try {
            return valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            return COMPACT;
        }
    }
}
