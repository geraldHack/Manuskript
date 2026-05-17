package com.manuskript.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.manuskript.ResourceManager;

/**
 * Verwaltet Agenten-Konfigurationen global in config/agents.json.
 */
public class AgentConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(AgentConfigManager.class);

    private static final String CONFIG_DIR = "config";
    private static final String AGENTS_FILE = "agents.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static List<AgentConfig> cachedConfigs;

    public static String getDefaultPlotholePrompt() {
        return "Du bist ein Analysemodul zur Erkennung von Plotlöchern und logischen Widersprüchen in Manuskripten.\n\n" +
            "AUSGABEREGELN:\n\n" +
            "Du erzeugst ausschließlich eine der folgenden zwei Antworten:\n\n" +
            "VARIANTE A:\n" +
            "KEINE_PROBLEME\n\n" +
            "VARIANTE B:\n" +
            "Eine oder mehrere Problemblöcke im EXAKTEN Format:\n\n" +
            "<PROBLEM> SCHWEREGRAD: [1-5] ZITAT: \"[EXAKTES ZITAT AUS DEM TEXT]\" PROBLEM: [KURZE BESCHREIBUNG] VORSCHLAG: [KONKRETE VERBESSERUNG] </PROBLEM>\n\n" +
            "WICHTIGE FORMATREGELN:\n\n" +
            "Verwende niemals Markdown.\n" +
            "Verwende niemals Aufzählungen.\n" +
            "Verwende niemals Nummerierungen.\n" +
            "Verwende niemals zusätzlichen Fließtext.\n" +
            "Verwende niemals Erklärungen vor oder nach der Ausgabe.\n" +
            "Gib ausschließlich gültige Problemblöcke oder KEINE_PROBLEME aus.\n" +
            "Jeder Problemblock MUSS mit <PROBLEM> beginnen und mit </PROBLEM> enden.\n" +
            "Zwischen zwei Problemblöcken steht genau eine Leerzeile.\n" +
            "Das Feld ZITAT muss exakt aus dem Manuskript übernommen werden.\n" +
            "Verändere niemals den Wortlaut eines Zitats.\n" +
            "Wenn keine relevanten Probleme existieren, gib ausschließlich KEINE_PROBLEME aus.\n" +
            "Antworte niemals mit Höflichkeitsfloskeln.\n" +
            "Antworte niemals mit Einleitungen.\n" +
            "Antworte niemals mit Zusammenfassungen.\n\n" +
            "ANALYSEREGELN:\n\n" +
            "Suche aktiv nach:\n\n" +
            "logischen Widersprüchen\n" +
            "Plotlöchern\n" +
            "unstimmigen Motivationen\n" +
            "unmöglichen Abläufen\n" +
            "verletzten Weltregeln\n" +
            "zeitlichen Inkonsistenzen\n" +
            "Figurenwissen ohne Grundlage\n" +
            "physikalischen Unmöglichkeiten innerhalb der Weltlogik\n\n" +
            "Ignoriere:\n\n" +
            "Stilfragen\n" +
            "Grammatik\n" +
            "reine Geschmacksfragen\n" +
            "absichtliche Mysterien ohne Widerspruch";
    }

    public static List<AgentConfig> getDefaults() {
        String backend = ResourceManager.getParameter("agent.backend", "Ollama");
        String model;
        if ("OpenAI".equals(backend)) {
            model = ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini");
        } else {
            model = ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
        }

        List<AgentConfig> defaults = new ArrayList<>();
        defaults.add(new AgentConfig(
            "Plothole-Agent",
            backend,
            getDefaultPlotholePrompt(),
            model,
            0.3, 2048, 0.7, 1.3
        ));
        return defaults;
    }

    public static synchronized List<AgentConfig> loadConfigs() {
        if (cachedConfigs != null) {
            return new ArrayList<>(cachedConfigs);
        }

        Path filePath = Paths.get(CONFIG_DIR, AGENTS_FILE);
        File file = filePath.toFile();

        if (!file.exists()) {
            cachedConfigs = getDefaults();
            saveConfigs(cachedConfigs);
            return new ArrayList<>(cachedConfigs);
        }

        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            List<AgentConfig> configs = gson.fromJson(json,
                new TypeToken<List<AgentConfig>>() {}.getType());
            if (configs == null || configs.isEmpty()) {
                cachedConfigs = getDefaults();
                saveConfigs(cachedConfigs);
            } else {
                cachedConfigs = configs;
            }
        } catch (IOException e) {
            logger.error("Fehler beim Laden von agents.json: {}", e.getMessage());
            cachedConfigs = getDefaults();
        }

        return new ArrayList<>(cachedConfigs);
    }

    public static synchronized void saveConfigs(List<AgentConfig> configs) {
        cachedConfigs = new ArrayList<>(configs);

        Path filePath = Paths.get(CONFIG_DIR, AGENTS_FILE);
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            String json = gson.toJson(configs);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Fehler beim Speichern von agents.json: {}", e.getMessage());
        }
    }

    public static synchronized void invalidateCache() {
        cachedConfigs = null;
    }
}
