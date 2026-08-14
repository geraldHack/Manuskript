package com.manuskript.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.manuskript.ApplicationPaths;
import com.manuskript.ResourceManager;

/**
 * Verwaltet Agenten-Konfigurationen global in config/agents.json.
 */
public class AgentConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(AgentConfigManager.class);

    private static final String AGENTS_RELATIVE = "config/agents.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static List<AgentConfig> cachedConfigs;

    /** Bundle-/Dev-Pfad: App-Home zuerst (jpackage), sonst Arbeitsverzeichnis. */
    private static Path agentsFilePath() {
        return ApplicationPaths.resolveConfigPath(AGENTS_RELATIVE).toPath();
    }

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
        List<AgentConfig> fromClasspath = loadBuiltinAgentsFromClasspath();
        if (fromClasspath.size() > 2) {
            applyBackendAndModel(fromClasspath);
            ensureSceneWritingAgent(fromClasspath);
            ensureChatbotAgent(fromClasspath);
            ensureSelectionRevisionAgent(fromClasspath);
            ensureIdiomReviewAgent(fromClasspath);
            return fromClasspath;
        }

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
            0.3, 4096, 0.7, 1.3
        ));
        AgentConfig sceneAgent = new AgentConfig(
            "Szene Schreiben",
            backend,
            SceneWritingAgent.DEFAULT_SYSTEM_PROMPT,
            model,
            0.8, 16384, 0.9, 1.1
        );
        sceneAgent.setDefaultPrompt(SceneWritingAgent.DEFAULT_SYSTEM_PROMPT);
        sceneAgent.setAgentType("scene-writing");
        defaults.add(sceneAgent);
        ensureSceneWritingAgent(defaults);
        ensureChatbotAgent(defaults);
        ensureSelectionRevisionAgent(defaults);
        ensureIdiomReviewAgent(defaults);
        return defaults;
    }

    private static void applyBackendAndModel(List<AgentConfig> configs) {
        String backend = ResourceManager.getParameter("agent.backend", "Ollama");
        String model = "OpenAI".equals(backend)
                ? ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini")
                : ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
        for (AgentConfig config : configs) {
            if (config.getAgentType() == null || config.getAgentType().isBlank()) {
                config.setAgentType("analysis");
            }
            config.setBackend(backend);
            config.setModel(model);
        }
    }

    public static synchronized List<AgentConfig> loadConfigs() {
        if (cachedConfigs != null) {
            return new ArrayList<>(cachedConfigs);
        }

        Path filePath = agentsFilePath();
        File file = filePath.toFile();
        logger.info("Lade Agenten aus: {}", filePath.toAbsolutePath());

        if (!file.isFile()) {
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
                applyBackendAndModel(configs);
                logger.info("Backend aus Parametern: {}",
                        ResourceManager.getParameter("agent.backend", "Ollama"));
                if (looksLikeIncompleteDefaults(configs)) {
                    List<AgentConfig> seed = loadBuiltinAgentsFromClasspath();
                    logger.warn("agents.json unvollständig ({} Einträge: {}). Stelle Builtins wieder her ({}).",
                            configs.size(), summarizeNames(configs), seed.size());
                    if (seed.size() > configs.size()) {
                        // Fehlende nachziehen; bei extrem verkürztem Set komplett ersetzen
                        if (configs.size() <= 2) {
                            configs.clear();
                            configs.addAll(seed);
                        } else {
                            mergeMissingBuiltinAgents(configs);
                        }
                    }
                }
                int before = configs.size();
                mergeMissingBuiltinAgents(configs);
                ensureSceneWritingAgent(configs);
                ensureChatbotAgent(configs);
                ensureSelectionRevisionAgent(configs);
                ensureIdiomReviewAgent(configs);
                cachedConfigs = configs;
                logger.info("Agenten geladen: {} ({})", configs.size(), summarizeNames(configs));
                if (configs.size() > before) {
                    saveConfigs(configs);
                }
            }
        } catch (IOException e) {
            logger.error("Fehler beim Laden von agents.json: {}", e.getMessage());
            cachedConfigs = getDefaults();
        }

        return new ArrayList<>(cachedConfigs);
    }

    public static synchronized void saveConfigs(List<AgentConfig> configs) {
        List<AgentConfig> toSave = configs != null ? new ArrayList<>(configs) : new ArrayList<>();
        // Verhindert, dass speichern nur der offenen Tabs die Builtins löscht
        mergeMissingBuiltinAgents(toSave);
        cachedConfigs = new ArrayList<>(toSave);

        Path filePath = agentsFilePath();
        try {
            Path configDir = filePath.getParent();
            if (configDir != null && !Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            // Modell aus den Konfigurationen entfernen, da es aus den Parametern gelesen wird
            // Erstelle Kopien, um die ursprünglichen configs nicht zu verändern
            List<AgentConfig> configsToSave = new ArrayList<>();
            for (AgentConfig config : toSave) {
                AgentConfig configCopy = new AgentConfig();
                configCopy.setId(config.getId());
                configCopy.setName(config.getName());
                configCopy.setBackend(config.getBackend());
                configCopy.setSystemPrompt(config.getSystemPrompt());
                configCopy.setDefaultPrompt(config.getDefaultPrompt());
                configCopy.setModel(null); // Modell wird aus Parametern gelesen
                configCopy.setTemperature(config.getTemperature());
                configCopy.setMaxTokens(config.getMaxTokens());
                configCopy.setTopP(config.getTopP());
                configCopy.setRepeatPenalty(config.getRepeatPenalty());
                configCopy.setAgentType(config.getAgentType());
                configCopy.setUserDefined(config.isUserDefined());
                configsToSave.add(configCopy);
            }
            String json = gson.toJson(configsToSave);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
            logger.info("Agenten gespeichert: {} → {}", configsToSave.size(), filePath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Fehler beim Speichern von agents.json: {}", e.getMessage());
        }
    }

    /**
     * Ergänzt fehlende Standard-Agenten aus Bundle/Classpath.
     *
     * @return {@code true} wenn mindestens ein Agent ergänzt wurde
     */
    private static boolean mergeMissingBuiltinAgents(List<AgentConfig> configs) {
        if (configs == null) {
            return false;
        }
        Map<String, AgentConfig> byId = new LinkedHashMap<>();
        Map<String, AgentConfig> byName = new LinkedHashMap<>();
        for (AgentConfig c : configs) {
            if (c.getId() != null) {
                byId.put(c.getId(), c);
            }
            if (c.getName() != null) {
                byName.put(c.getName(), c);
            }
        }

        List<AgentConfig> seed = loadSeedAgentsExcluding(agentsFilePath());
        if (seed.isEmpty()) {
            seed = loadBuiltinAgentsFromClasspath();
        }
        if (seed.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (AgentConfig seedAgent : seed) {
            if (seedAgent == null || seedAgent.isUserDefined()) {
                continue;
            }
            boolean present = (seedAgent.getId() != null && byId.containsKey(seedAgent.getId()))
                    || (seedAgent.getName() != null && byName.containsKey(seedAgent.getName()));
            if (!present) {
                configs.add(seedAgent);
                changed = true;
                logger.info("Builtin-Agent nachgezogen: {}", seedAgent.getName());
            }
        }
        return changed;
    }

    /** Liest agents.json von bekannten Orten außer {@code alreadyLoaded}, sonst Classpath. */
    private static List<AgentConfig> loadSeedAgentsExcluding(Path alreadyLoaded) {
        // Classpath zuerst: unabhängig von CWD/jpackage, immer vollständige Builtins
        List<AgentConfig> fromClasspath = loadBuiltinAgentsFromClasspath();
        if (fromClasspath.size() > 2) {
            logger.info("Seed-Agenten aus Classpath ({} Einträge)", fromClasspath.size());
            return fromClasspath;
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(ApplicationPaths.resolveConfigPath(AGENTS_RELATIVE).toPath());
        candidates.add(new File(ApplicationPaths.getApplicationHomeDirectory(), AGENTS_RELATIVE).toPath());
        candidates.add(Path.of(AGENTS_RELATIVE));
        candidates.add(Path.of("config", "agents.json"));

        Path loadedAbs = alreadyLoaded != null ? alreadyLoaded.toAbsolutePath().normalize() : null;
        for (Path candidate : candidates) {
            try {
                Path abs = candidate.toAbsolutePath().normalize();
                if (loadedAbs != null && abs.equals(loadedAbs)) {
                    continue;
                }
                if (!Files.isRegularFile(abs)) {
                    continue;
                }
                List<AgentConfig> list = parseAgentsJson(Files.readString(abs, StandardCharsets.UTF_8));
                if (list != null && list.size() > 2) {
                    logger.info("Seed-Agenten aus {} ({} Einträge)", abs, list.size());
                    return list;
                }
            } catch (Exception e) {
                logger.debug("Seed agents.json übersprungen ({}): {}", candidate, e.getMessage());
            }
        }
        return List.of();
    }

    private static List<AgentConfig> loadBuiltinAgentsFromClasspath() {
        String[] resourceNames = {"/builtin/agents.json", "builtin/agents.json"};
        ClassLoader[] loaders = {
                AgentConfigManager.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (String name : resourceNames) {
            try (InputStream in = AgentConfigManager.class.getResourceAsStream(name)) {
                List<AgentConfig> list = readAgentsStream(in);
                if (list.size() > 2) {
                    return list;
                }
            } catch (Exception e) {
                logger.debug("Classpath {}: {}", name, e.getMessage());
            }
            for (ClassLoader loader : loaders) {
                if (loader == null) {
                    continue;
                }
                String path = name.startsWith("/") ? name.substring(1) : name;
                try (InputStream in = loader.getResourceAsStream(path)) {
                    List<AgentConfig> list = readAgentsStream(in);
                    if (list.size() > 2) {
                        return list;
                    }
                } catch (Exception e) {
                    logger.debug("Classpath {} via {}: {}", path, loader, e.getMessage());
                }
            }
        }
        // Letzter Fallback: Datei neben der JAR (Contents/app/config/agents.json)
        try {
            Path besideJar = ApplicationPaths.resolveConfigPath(AGENTS_RELATIVE).toPath();
            if (Files.isRegularFile(besideJar)) {
                List<AgentConfig> list = parseAgentsJson(Files.readString(besideJar, StandardCharsets.UTF_8));
                if (list.size() > 2) {
                    return list;
                }
            }
        } catch (Exception e) {
            logger.debug("Builtin neben JAR: {}", e.getMessage());
        }
        return List.of();
    }

    private static List<AgentConfig> readAgentsStream(InputStream in) throws IOException {
        if (in == null) {
            return List.of();
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            List<AgentConfig> list = gson.fromJson(reader, new TypeToken<List<AgentConfig>>() {}.getType());
            return list != null ? list : List.of();
        }
    }

    /** Typisches kaputtes Fallback-Set: nur Plothole + Szene Schreiben. */
    private static boolean looksLikeIncompleteDefaults(List<AgentConfig> configs) {
        if (configs == null || configs.size() < 5) {
            return true;
        }
        boolean hasDialog = false;
        boolean hasTextstruktur = false;
        for (AgentConfig c : configs) {
            if (c == null || c.getName() == null) {
                continue;
            }
            if ("Dialog-Agent".equals(c.getName())) {
                hasDialog = true;
            }
            if ("Textstruktur".equals(c.getName())) {
                hasTextstruktur = true;
            }
        }
        return !hasDialog || !hasTextstruktur;
    }

    private static List<AgentConfig> parseAgentsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<AgentConfig> list = gson.fromJson(json, new TypeToken<List<AgentConfig>>() {}.getType());
        return list != null ? list : List.of();
    }

    private static String summarizeNames(List<AgentConfig> configs) {
        StringBuilder sb = new StringBuilder();
        for (AgentConfig c : configs) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(c.getName());
        }
        return sb.toString();
    }

    public static synchronized void invalidateCache() {
        cachedConfigs = null;
    }

    private static void ensureSceneWritingAgent(List<AgentConfig> configs) {
        boolean hasScene = false;
        for (AgentConfig c : configs) {
            if (c.isSceneWritingAgent()) {
                hasScene = true;
                break;
            }
        }
        if (!hasScene) {
            String backend = ResourceManager.getParameter("agent.backend", "Ollama");
            String model = "OpenAI".equals(backend)
                ? ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini")
                : ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
            AgentConfig sceneAgent = new AgentConfig(
                "Szene Schreiben",
                backend,
                SceneWritingAgent.DEFAULT_SYSTEM_PROMPT,
                model,
                0.8, 16384, 0.9, 1.1
            );
            sceneAgent.setDefaultPrompt(SceneWritingAgent.DEFAULT_SYSTEM_PROMPT);
            sceneAgent.setAgentType("scene-writing");
            configs.add(sceneAgent);
            saveConfigs(configs);
        } else {
            bumpSceneWritingMaxTokensIfNeeded(configs);
        }
    }

    /** Alte Default 4096 ist für Reasoning-Modelle (Kimi) oft zu knapp. */
    private static void bumpSceneWritingMaxTokensIfNeeded(List<AgentConfig> configs) {
        boolean changed = false;
        for (AgentConfig c : configs) {
            if (c.isSceneWritingAgent() && c.getMaxTokens() > 0 && c.getMaxTokens() <= 4096) {
                c.setMaxTokens(16384);
                changed = true;
            }
        }
        if (changed) {
            saveConfigs(configs);
        }
    }

    private static void ensureChatbotAgent(List<AgentConfig> configs) {
        for (AgentConfig c : configs) {
            if (c.isChatbotAgent()) {
                return;
            }
        }
        String backend = ResourceManager.getParameter("agent.backend", "Ollama");
        String model = "OpenAI".equals(backend)
                ? ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini")
                : ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
        AgentConfig chatAgent = new AgentConfig(
                "Chat",
                backend,
                ChatbotAgent.DEFAULT_SYSTEM_PROMPT,
                model,
                0.7, 8192, 0.9, 1.1
        );
        chatAgent.setDefaultPrompt(ChatbotAgent.DEFAULT_SYSTEM_PROMPT);
        chatAgent.setAgentType("chatbot");
        configs.add(chatAgent);
        saveConfigs(configs);
    }

    private static void ensureSelectionRevisionAgent(List<AgentConfig> configs) {
        for (AgentConfig c : configs) {
            if (c.isSelectionRevisionAgent()
                    || SelectionRevisionSupport.DEFAULT_AGENT_ID.equals(c.getId())) {
                return;
            }
        }
        String backend = ResourceManager.getParameter("agent.backend", "Ollama");
        String model = "OpenAI".equals(backend)
                ? ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini")
                : ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
        String prompt = SelectionRevisionSupport.getDefaultSystemPrompt();
        AgentConfig revisionAgent = new AgentConfig(
                "Überarbeiten",
                backend,
                prompt,
                model,
                0.4, 8192, 0.7, 1.2
        );
        revisionAgent.setId(SelectionRevisionSupport.DEFAULT_AGENT_ID);
        revisionAgent.setDefaultPrompt(prompt);
        revisionAgent.setAgentType("selection-revision");
        configs.add(revisionAgent);
        saveConfigs(configs);
    }

    private static void ensureIdiomReviewAgent(List<AgentConfig> configs) {
        for (AgentConfig c : configs) {
            if (c.isIdiomReviewAgent() || IdiomReviewSupport.DEFAULT_AGENT_ID.equals(c.getId())) {
                return;
            }
        }
        String backend = ResourceManager.getParameter("agent.backend", "Ollama");
        String model = "OpenAI".equals(backend)
                ? ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini")
                : ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
        String prompt = IdiomReviewSupport.getDefaultSystemPrompt();
        AgentConfig idiomAgent = new AgentConfig(
                "Sprachentflechtung",
                backend,
                prompt,
                model,
                0.2, 4096, 0.7, 1.2
        );
        idiomAgent.setId(IdiomReviewSupport.DEFAULT_AGENT_ID);
        idiomAgent.setDefaultPrompt(prompt);
        idiomAgent.setAgentType("idiom-review");
        configs.add(idiomAgent);
        saveConfigs(configs);
    }
}
