package com.manuskript.agent;

import java.util.prefs.Preferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verwaltet die Historie der zuletzt verwendeten Modelle.
 * Persistiert die letzten 10 Modelle global.
 */
public class ModelHistory {
    private static final Logger logger = LoggerFactory.getLogger(ModelHistory.class);
    private static final int MAX_HISTORY = 10;
    private static final String PREFS_KEY = "agent_model_history";

    private static List<String> cachedHistory;

    /**
     * Gibt die Historie der zuletzt verwendeten Modelle zurück.
     * Die Liste ist sortiert nach neuestem Modell zuerst.
     */
    public static synchronized List<String> getHistory() {
        if (cachedHistory != null) {
            return new ArrayList<>(cachedHistory);
        }

        Preferences prefs = Preferences.userNodeForPackage(ModelHistory.class);
        String historyStr = prefs.get(PREFS_KEY, "");
        
        logger.info("Lade Modell-Historie aus Preferences: '{}'", historyStr);

        if (historyStr.isEmpty()) {
            cachedHistory = new ArrayList<>();
            return new ArrayList<>();
        }

        String[] models = historyStr.split("\\|");
        cachedHistory = new ArrayList<>(Arrays.asList(models));
        logger.info("Geladene Modell-Historie: {}", cachedHistory);
        return new ArrayList<>(cachedHistory);
    }

    /**
     * Fügt ein Modell zur Historie hinzu.
     * Duplikate werden entfernt, das Modell wird an den Anfang gestellt.
     * Nur die letzten 10 Modelle werden gespeichert.
     */
    public static synchronized void addModel(String model) {
        if (model == null || model.trim().isEmpty()) return;

        List<String> history = getHistory();
        history.remove(model); // Duplikat entfernen
        history.add(0, model); // An den Anfang stellen

        // Auf MAX_HISTORY beschränken
        if (history.size() > MAX_HISTORY) {
            history = history.subList(0, MAX_HISTORY);
        }

        cachedHistory = history;
        logger.info("Speichere Modell-Historie: {}", history);
        saveHistory(history);
    }

    /**
     * Gibt die Historie zurück, ergänzt um die verfügbaren Modelle.
     * Duplikate werden entfernt, Historie-Modelle stehen zuerst.
     */
    public static List<String> getHistoryWithAvailableModels(List<String> availableModels) {
        List<String> history = getHistory();
        Set<String> combined = new LinkedHashSet<>();

        // Zuerst Historie (nur Modelle, die auch verfügbar sind)
        for (String model : history) {
            if (availableModels.contains(model)) {
                combined.add(model);
            }
        }

        // Dann alle verfügbaren Modelle
        combined.addAll(availableModels);

        return new ArrayList<>(combined);
    }

    private static void saveHistory(List<String> history) {
        Preferences prefs = Preferences.userNodeForPackage(ModelHistory.class);
        String historyStr = String.join("|", history);
        prefs.put(PREFS_KEY, historyStr);
        logger.info("Gespeicherter String in Preferences: '{}'", historyStr);
        try {
            prefs.flush();
        } catch (Exception e) {
            logger.error("Konnte Modell-Historie nicht speichern", e);
        }
    }

    /**
     * Löscht die Historie.
     */
    public static synchronized void clearHistory() {
        Preferences prefs = Preferences.userNodeForPackage(ModelHistory.class);
        prefs.remove(PREFS_KEY);
        cachedHistory = new ArrayList<>();
        logger.info("Modell-Historie gelöscht");
    }
}
