package com.manuskript.novelwizard;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NovelWizardSession {
    /** Praefix fuer verbindliche Autoren-Korrekturen im Chat-Verlauf */
    public static final String CORRECTION_PREFIX = "[Korrektur] ";

    private int version = 1;
    private String status = "ACTIVE";
    private NovelWizardPhase currentPhase = NovelWizardPhase.BRAINSTORM;
    private String currentSubStep = "";
    private Map<NovelWizardPhase, NovelWizardPhaseStatus> phaseStatus = new EnumMap<>(NovelWizardPhase.class);
    private String mode = "NEW";
    private NovelWizardTurn pendingTurn;
    private Map<String, String> collected = new LinkedHashMap<>();
    private String projectSummary = "";
    private List<ChatEntry> chatHistory = new ArrayList<>();
    private List<PromptLogEntry> promptLog = new ArrayList<>();
    private String startedAt = now();
    private String updatedAt = now();

    public NovelWizardSession() {
        ensurePhaseStatus();
    }

    public static NovelWizardSession create(String mode) {
        NovelWizardSession session = new NovelWizardSession();
        session.mode = mode == null || mode.isBlank() ? "NEW" : mode;
        session.setCurrentPhase(NovelWizardPhase.BRAINSTORM);
        return session;
    }

    public void ensurePhaseStatus() {
        if (phaseStatus == null) {
            phaseStatus = new EnumMap<>(NovelWizardPhase.class);
        }
        for (NovelWizardPhase phase : NovelWizardPhase.values()) {
            phaseStatus.putIfAbsent(phase, NovelWizardPhaseStatus.NOT_STARTED);
        }
    }

    /**
     * Stellt fehlende Phasen-Marker in der Chat-Historie her (z. B. nach JSON-Laden).
     */
    public void normalizeChatPhases() {
        if (chatHistory == null) {
            chatHistory = new ArrayList<>();
            return;
        }
        NovelWizardPhase running = currentPhase == null || currentPhase == NovelWizardPhase.BOOTSTRAP
                ? NovelWizardPhase.BRAINSTORM
                : currentPhase;
        for (ChatEntry entry : chatHistory) {
            if (entry == null) {
                continue;
            }
            if (entry.phase != null && entry.phase != NovelWizardPhase.BOOTSTRAP) {
                running = entry.phase;
            } else {
                entry.phase = running;
            }
        }
    }

    public void touch() {
        updatedAt = now();
    }

    public void addAssistantTurn(NovelWizardPhase phase, String raw, NovelWizardTurn parsed) {
        chatHistory.add(ChatEntry.assistant(phase, raw, parsed));
        pendingTurn = parsed;
        touch();
    }

    public void addUserTurn(NovelWizardPhase phase, String answer, boolean custom) {
        chatHistory.add(ChatEntry.user(phase, answer, custom));
        pendingTurn = null;
        touch();
    }

    public void addPromptLog(NovelWizardPhase phase, String systemPrompt, String userPrompt) {
        promptLog.add(new PromptLogEntry(phase, systemPrompt, userPrompt, now()));
        touch();
    }

    public int getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public NovelWizardPhase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(NovelWizardPhase currentPhase) {
        this.currentPhase = currentPhase == null ? NovelWizardPhase.BRAINSTORM : currentPhase;
        ensurePhaseStatus();
        NovelWizardPhaseStatus previous = phaseStatus.get(this.currentPhase);
        if (previous == NovelWizardPhaseStatus.NOT_STARTED) {
            phaseStatus.put(this.currentPhase, NovelWizardPhaseStatus.IN_PROGRESS);
        }
        touch();
    }

    public String getCurrentSubStep() {
        return currentSubStep;
    }

    public void setCurrentSubStep(String currentSubStep) {
        this.currentSubStep = currentSubStep == null ? "" : currentSubStep;
    }

    public Map<NovelWizardPhase, NovelWizardPhaseStatus> getPhaseStatus() {
        ensurePhaseStatus();
        return phaseStatus;
    }

    public String getMode() {
        return mode;
    }

    public NovelWizardTurn getPendingTurn() {
        return pendingTurn;
    }

    public void setPendingTurn(NovelWizardTurn pendingTurn) {
        this.pendingTurn = pendingTurn;
    }

    public Map<String, String> getCollected() {
        return collected;
    }

    public String getProjectSummary() {
        return projectSummary;
    }

    public void setProjectSummary(String projectSummary) {
        this.projectSummary = projectSummary == null ? "" : projectSummary;
    }

    public List<ChatEntry> getChatHistory() {
        return chatHistory;
    }

    public List<PromptLogEntry> getPromptLog() {
        return promptLog;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static class ChatEntry {
        public String role;
        public NovelWizardPhase phase;
        public String raw;
        public NovelWizardTurn parsed;
        public String choice;
        public boolean custom;
        public String at;

        public static ChatEntry assistant(NovelWizardPhase phase, String raw, NovelWizardTurn parsed) {
            ChatEntry entry = new ChatEntry();
            entry.role = "assistant";
            entry.phase = phase;
            entry.raw = raw;
            entry.parsed = parsed;
            entry.at = now();
            return entry;
        }

        public static ChatEntry user(NovelWizardPhase phase, String answer, boolean custom) {
            ChatEntry entry = new ChatEntry();
            entry.role = "user";
            entry.phase = phase;
            entry.choice = answer;
            entry.custom = custom;
            entry.at = now();
            return entry;
        }
    }

    public static class PromptLogEntry {
        public NovelWizardPhase phase;
        public String system;
        public String user;
        public String at;

        public PromptLogEntry(NovelWizardPhase phase, String system, String user, String at) {
            this.phase = phase;
            this.system = system;
            this.user = user;
            this.at = at;
        }
    }
}
