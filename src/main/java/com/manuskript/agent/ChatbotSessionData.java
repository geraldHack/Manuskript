package com.manuskript.agent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.manuskript.CustomChatArea;
import com.manuskript.ResourceManager;

/**
 * Persistierte Chat-Session (pro Projekt unter data/chatbot/sessions/).
 */
public class ChatbotSessionData {

    private String name;
    private List<CustomChatArea.QAPair> qaPairs = new ArrayList<>();
    private List<String> contextSources = new ArrayList<>();
    private int chaptersBefore = 3;
    private int chaptersAfter = 3;
    private String contextSize = ChatbotContextSize.COMPACT.name();
    private double temperature = 0.7;
    private String model;
    private boolean useParameterModel = true;
    private int maxHistoryTurns = 10;

    public ChatbotSessionData() {
    }

    public ChatbotSessionData(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CustomChatArea.QAPair> getQaPairs() {
        return qaPairs;
    }

    public void setQaPairs(List<CustomChatArea.QAPair> qaPairs) {
        this.qaPairs = qaPairs != null ? new ArrayList<>(qaPairs) : new ArrayList<>();
    }

    public ChatbotContextConfig toContextConfig() {
        Set<ChatbotContextSource> sources = EnumSet.noneOf(ChatbotContextSource.class);
        if (contextSources != null) {
            for (String s : contextSources) {
                try {
                    sources.add(ChatbotContextSource.valueOf(s));
                } catch (IllegalArgumentException ignored) {
                    // unbekannte Quelle ignorieren
                }
            }
        }
        ChatbotContextConfig cfg = new ChatbotContextConfig(sources, chaptersBefore, chaptersAfter,
                ChatbotContextSize.fromName(contextSize));
        return cfg;
    }

    public void applyContextConfig(ChatbotContextConfig config) {
        if (config == null) {
            return;
        }
        contextSources = config.getSources().stream()
                .map(Enum::name)
                .collect(Collectors.toList());
        chaptersBefore = config.getChaptersBefore();
        chaptersAfter = config.getChaptersAfter();
        contextSize = config.getContextSize().name();
    }

    public List<String> getContextSources() {
        return contextSources;
    }

    public void setContextSources(List<String> contextSources) {
        this.contextSources = contextSources != null ? new ArrayList<>(contextSources) : new ArrayList<>();
    }

    public int getChaptersBefore() {
        return chaptersBefore;
    }

    public void setChaptersBefore(int chaptersBefore) {
        this.chaptersBefore = chaptersBefore;
    }

    public int getChaptersAfter() {
        return chaptersAfter;
    }

    public void setChaptersAfter(int chaptersAfter) {
        this.chaptersAfter = chaptersAfter;
    }

    public String getContextSize() {
        return contextSize;
    }

    public void setContextSize(String contextSize) {
        this.contextSize = contextSize;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isUseParameterModel() {
        return useParameterModel;
    }

    public void setUseParameterModel(boolean useParameterModel) {
        this.useParameterModel = useParameterModel;
    }

    public int getMaxHistoryTurns() {
        return maxHistoryTurns;
    }

    public void setMaxHistoryTurns(int maxHistoryTurns) {
        this.maxHistoryTurns = maxHistoryTurns;
    }

    public static int defaultMaxHistoryTurns() {
        return Integer.parseInt(ResourceManager.getParameter("agent.chatbot.max_history_turns", "10"));
    }
}
