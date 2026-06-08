package com.manuskript.agent;

import com.manuskript.ResourceManager;

/**
 * Kontext-Budget fuer den Chatbot.
 */
public enum ChatbotContextSize {

    COMPACT("Gekuerzt", "Wenig Kontext — schneller.", 40_000, 2_000, 8_000, 3, 3),
    EXTENDED("Mehr", "Erweiterter Kontext.", 120_000, 6_000, 24_000, 5, 5),
    FULL("Alles", "Voller Kontext — kann langsam/teuer sein.", -1, -1, -1, -1, -1);

    private final String label;
    private final String tooltip;
    private final int maxTotalChars;
    private final int maxBlockChars;
    private final int maxChapterChars;
    private final int defaultChaptersBefore;
    private final int defaultChaptersAfter;

    ChatbotContextSize(String label, String tooltip, int maxTotalChars, int maxBlockChars,
                       int maxChapterChars, int defaultChaptersBefore, int defaultChaptersAfter) {
        this.label = label;
        this.tooltip = tooltip;
        this.maxTotalChars = maxTotalChars;
        this.maxBlockChars = maxBlockChars;
        this.maxChapterChars = maxChapterChars;
        this.defaultChaptersBefore = defaultChaptersBefore;
        this.defaultChaptersAfter = defaultChaptersAfter;
    }

    public String getLabel() {
        return label;
    }

    public String getTooltip() {
        return tooltip;
    }

    int maxTotalChars() {
        return maxTotalChars;
    }

    int maxBlockChars() {
        return maxBlockChars;
    }

    int maxChapterChars() {
        return maxChapterChars;
    }

    int defaultChaptersBefore() {
        return defaultChaptersBefore;
    }

    int defaultChaptersAfter() {
        return defaultChaptersAfter;
    }

    boolean unlimited(int limit) {
        return limit < 0;
    }

    public static ChatbotContextSize fromName(String name) {
        if (name == null || name.isBlank()) {
            return defaultFromParameters();
        }
        try {
            return valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            return defaultFromParameters();
        }
    }

    public static ChatbotContextSize defaultFromParameters() {
        return fromName(ResourceManager.getParameter("agent.chatbot.context_size", "COMPACT"));
    }
}
