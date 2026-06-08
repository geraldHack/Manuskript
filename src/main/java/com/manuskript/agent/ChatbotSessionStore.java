package com.manuskript.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.manuskript.CustomChatArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Speichert Chat-Sessions pro Projekt unter {@code data/chatbot/sessions/}.
 */
public final class ChatbotSessionStore {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotSessionStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SESSIONS_DIR = "data/chatbot/sessions";
    private static final String SELECTED_FILE = "data/chatbot/selected_session.txt";
    public static final String STANDARD_SESSION_NAME = "Standard";

    private ChatbotSessionStore() {
    }

    public static File sessionsDir(File projectDir) {
        return new File(projectDir, SESSIONS_DIR);
    }

    public static List<String> listSessionNames(File projectDir) {
        File dir = sessionsDir(projectDir);
        if (!dir.isDirectory()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return names;
        }
        for (File f : files) {
            String base = f.getName();
            names.add(base.substring(0, base.length() - 5));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Legt {@link #STANDARD_SESSION_NAME} an, falls die Datei fehlt (z. B. nach versehentlichem Löschen). */
    public static void ensureStandardSession(File projectDir) {
        if (projectDir == null) {
            return;
        }
        File file = new File(sessionsDir(projectDir), sanitize(STANDARD_SESSION_NAME) + ".json");
        if (!file.exists()) {
            save(projectDir, newSession(STANDARD_SESSION_NAME));
        }
    }

    public static boolean isStandardSession(String sessionName) {
        return sessionName != null
                && STANDARD_SESSION_NAME.equalsIgnoreCase(sessionName.trim());
    }

    /** Bevorzugt die Standard-Session, sonst die erste verfügbare. */
    public static String pickFallbackSessionName(List<String> sessionNames) {
        if (sessionNames == null || sessionNames.isEmpty()) {
            return STANDARD_SESSION_NAME;
        }
        for (String name : sessionNames) {
            if (isStandardSession(name)) {
                return name;
            }
        }
        return sessionNames.get(0);
    }

    public static ChatbotSessionData load(File projectDir, String sessionName) {
        if (projectDir == null || sessionName == null || sessionName.isBlank()) {
            return newSession(STANDARD_SESSION_NAME);
        }
        File file = new File(sessionsDir(projectDir), sanitize(sessionName) + ".json");
        if (!file.exists()) {
            ChatbotSessionData data = newSession(sessionName);
            save(projectDir, data);
            return data;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            ChatbotSessionData data = GSON.fromJson(json, ChatbotSessionData.class);
            if (data == null) {
                return newSession(sessionName);
            }
            if (data.getName() == null || data.getName().isBlank()) {
                data.setName(sessionName);
            }
            if (data.getMaxHistoryTurns() <= 0) {
                data.setMaxHistoryTurns(ChatbotSessionData.defaultMaxHistoryTurns());
            }
            return data;
        } catch (IOException e) {
            logger.warn("Chat-Session {} konnte nicht geladen werden: {}", sessionName, e.getMessage());
            return newSession(sessionName);
        }
    }

    public static void save(File projectDir, ChatbotSessionData data) {
        if (projectDir == null || data == null || data.getName() == null || data.getName().isBlank()) {
            return;
        }
        File dir = sessionsDir(projectDir);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warn("Chat-Sessions-Ordner konnte nicht erstellt werden: {}", dir);
            return;
        }
        File file = new File(dir, sanitize(data.getName()) + ".json");
        try {
            Files.writeString(file.toPath(), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Chat-Session {} konnte nicht gespeichert werden: {}", data.getName(), e.getMessage());
        }
    }

    public static void delete(File projectDir, String sessionName) {
        if (projectDir == null || sessionName == null || sessionName.isBlank()) {
            return;
        }
        File file = new File(sessionsDir(projectDir), sanitize(sessionName) + ".json");
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            logger.warn("Chat-Session {} konnte nicht gelöscht werden: {}", sessionName, e.getMessage());
        }
    }

    public static String loadSelectedSessionName(File projectDir) {
        if (projectDir == null) {
            return STANDARD_SESSION_NAME;
        }
        File file = new File(projectDir, SELECTED_FILE);
        if (!file.exists()) {
            return STANDARD_SESSION_NAME;
        }
        try {
            String name = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            return name.isBlank() ? STANDARD_SESSION_NAME : name;
        } catch (IOException e) {
            return STANDARD_SESSION_NAME;
        }
    }

    public static void saveSelectedSessionName(File projectDir, String sessionName) {
        if (projectDir == null || sessionName == null || sessionName.isBlank()) {
            return;
        }
        File file = new File(projectDir, SELECTED_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            Files.writeString(file.toPath(), sessionName.trim(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Ausgewählte Chat-Session konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    public static ChatbotSessionData newSession(String name) {
        ChatbotSessionData data = new ChatbotSessionData(name);
        data.setMaxHistoryTurns(ChatbotSessionData.defaultMaxHistoryTurns());
        data.setContextSize(ChatbotContextSize.defaultFromParameters().name());
        ChatbotContextConfig cfg = new ChatbotContextConfig();
        cfg.addSource(ChatbotContextSource.CURRENT_CHAPTER);
        data.applyContextConfig(cfg);
        return data;
    }

    public static List<ChatTurn> qaPairsToTurns(List<CustomChatArea.QAPair> pairs) {
        List<ChatTurn> turns = new ArrayList<>();
        if (pairs == null) {
            return turns;
        }
        for (CustomChatArea.QAPair pair : pairs) {
            if (pair.getQuestion() != null && !pair.getQuestion().isBlank()) {
                turns.add(ChatTurn.user(pair.getQuestion()));
            }
            String answer = pair.getAnswer();
            if (answer != null && !answer.isBlank()) {
                turns.add(ChatTurn.assistant(answer));
            }
        }
        return turns;
    }

    static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
