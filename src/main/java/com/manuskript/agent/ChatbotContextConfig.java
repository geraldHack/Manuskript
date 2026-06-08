package com.manuskript.agent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Aktive Kontext-Pills und Nachbar-Kapitel-Zaehler pro Chat-Session.
 */
public class ChatbotContextConfig {

    private final Set<ChatbotContextSource> sources;
    private int chaptersBefore;
    private int chaptersAfter;
    private ChatbotContextSize contextSize;

    public ChatbotContextConfig() {
        this.sources = EnumSet.noneOf(ChatbotContextSource.class);
        this.contextSize = ChatbotContextSize.defaultFromParameters();
        this.chaptersBefore = contextSize.defaultChaptersBefore();
        this.chaptersAfter = contextSize.defaultChaptersAfter();
    }

    public ChatbotContextConfig(Set<ChatbotContextSource> sources, int chaptersBefore, int chaptersAfter,
                                ChatbotContextSize contextSize) {
        this.sources = sources == null ? EnumSet.noneOf(ChatbotContextSource.class)
                : EnumSet.copyOf(sources);
        this.chaptersBefore = chaptersBefore;
        this.chaptersAfter = chaptersAfter;
        this.contextSize = contextSize == null ? ChatbotContextSize.defaultFromParameters() : contextSize;
    }

    public Set<ChatbotContextSource> getSources() {
        return EnumSet.copyOf(sources);
    }

    public void setSources(Set<ChatbotContextSource> newSources) {
        sources.clear();
        if (newSources != null) {
            sources.addAll(newSources);
        }
    }

    public boolean hasSource(ChatbotContextSource source) {
        return sources.contains(source);
    }

    public void addSource(ChatbotContextSource source) {
        sources.add(source);
    }

    public void removeSource(ChatbotContextSource source) {
        sources.remove(source);
    }

    public int getChaptersBefore() {
        return chaptersBefore;
    }

    public void setChaptersBefore(int chaptersBefore) {
        this.chaptersBefore = Math.max(0, chaptersBefore);
    }

    public int getChaptersAfter() {
        return chaptersAfter;
    }

    public void setChaptersAfter(int chaptersAfter) {
        this.chaptersAfter = Math.max(0, chaptersAfter);
    }

    public ChatbotContextSize getContextSize() {
        return contextSize;
    }

    public void setContextSize(ChatbotContextSize contextSize) {
        this.contextSize = contextSize == null ? ChatbotContextSize.defaultFromParameters() : contextSize;
    }
}
