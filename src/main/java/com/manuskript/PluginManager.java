package com.manuskript;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Verwaltet Plugins für den KI-Assistenten
 */
public class PluginManager {
    private static final Logger logger = Logger.getLogger(PluginManager.class.getName());
    private static final String PLUGIN_DIR = "config/plugins";
    private static final String PLUGIN_SETTINGS_FILE = "config/plugin-settings.json";
    
    private List<Plugin> plugins = new ArrayList<>();
    private Map<String, Plugin> pluginMap = new HashMap<>();
    private Gson gson;
    
    public PluginManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadPlugins();
    }
    
    /**
     * Lädt alle Plugins aus dem Plugin-Verzeichnis
     */
    public void loadPlugins() {
        plugins.clear();
        pluginMap.clear();
        
        try {
            // Plugin-Verzeichnis erstellen falls nicht vorhanden
            Path pluginPath = Paths.get(PLUGIN_DIR);
            if (!Files.exists(pluginPath)) {
                Files.createDirectories(pluginPath);
                createDefaultPlugins();
            }
            
            // Alle .json Dateien im Plugin-Verzeichnis laden
            File pluginDir = new File(PLUGIN_DIR);
            File[] pluginFiles = pluginDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            
            if (pluginFiles != null) {
                for (File pluginFile : pluginFiles) {
                    try {
                        Plugin plugin = loadPluginFromFile(pluginFile);
                        if (plugin != null && plugin.isEnabled()) {
                            plugins.add(plugin);
                            pluginMap.put(plugin.getName(), plugin);
                            logger.info("Plugin geladen: " + plugin.getName());
                        }
                    } catch (Exception e) {
                        logger.warning("Fehler beim Laden von Plugin " + pluginFile.getName() + ": " + e.getMessage());
                    }
                }
            }
            
            logger.info("Plugins geladen: " + plugins.size() + " aktiv");
            
        } catch (Exception e) {
            logger.severe("Fehler beim Laden der Plugins: " + e.getMessage());
        }
    }
    
    /**
     * Lädt ein einzelnes Plugin aus einer JSON-Datei
     */
    private Plugin loadPluginFromFile(File pluginFile) throws IOException {
        try (FileReader reader = new FileReader(pluginFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            
            Plugin plugin = new Plugin();
            plugin.setName(getStringOrDefault(json, "name", "Unbekanntes Plugin"));
            plugin.setDescription(getStringOrDefault(json, "description", ""));
            plugin.setCategory(getStringOrDefault(json, "category", "Allgemein"));
            plugin.setPrompt(getStringOrDefault(json, "prompt", ""));
            plugin.setTemperature(getDoubleOrDefault(json, "temperature", 0.7));
            plugin.setMaxTokens(getIntOrDefault(json, "maxTokens", 2048));
            plugin.setEnabled(getBooleanOrDefault(json, "enabled", true));
            
            // Lade Variablen-Definitionen falls vorhanden
            if (json.has("variables") && json.get("variables").isJsonArray()) {
                List<PluginVariable> variables = new ArrayList<>();
                for (var variableJson : json.getAsJsonArray("variables")) {
                    if (variableJson.isJsonObject()) {
                        PluginVariable variable = parsePluginVariable(variableJson.getAsJsonObject());
                        if (variable != null) {
                            variables.add(variable);
                        }
                    }
                }
                plugin.setVariableDefinitions(variables);
            }
            
            return plugin;
        }
    }
    
    /**
     * Parst eine Plugin-Variable aus JSON
     */
    private PluginVariable parsePluginVariable(JsonObject variableJson) {
        try {
            String name = getStringOrDefault(variableJson, "name", "");
            String typeStr = getStringOrDefault(variableJson, "type", "text").toLowerCase();
            String defaultValue = getStringOrDefault(variableJson, "default", "");
            String description = getStringOrDefault(variableJson, "description", "");
            
            PluginVariable.Type type;
            switch (typeStr) {
                case "choice":
                    type = PluginVariable.Type.CHOICE;
                    break;
                case "number":
                    type = PluginVariable.Type.NUMBER;
                    break;
                case "boolean":
                case "bool":
                    type = PluginVariable.Type.BOOLEAN;
                    break;
                case "area":
                case "multiline":
                    type = PluginVariable.Type.MULTI_LINE;
                    break;
                default:
                    type = PluginVariable.Type.SINGLE_LINE;
                    break;
            }
            
            PluginVariable variable = new PluginVariable(name, type, defaultValue, description);
            
            // Lade Optionen für Choice-Variablen
            if (type == PluginVariable.Type.CHOICE && variableJson.has("options")) {
                List<PluginVariable.Option> options = new ArrayList<>();
                for (var optionJson : variableJson.getAsJsonArray("options")) {
                    if (optionJson.isJsonObject()) {
                        String value = getStringOrDefault(optionJson.getAsJsonObject(), "value", "");
                        String label = getStringOrDefault(optionJson.getAsJsonObject(), "label", value);
                        options.add(new PluginVariable.Option(value, label));
                    }
                }
                variable.setOptions(options);
            }
            
            // Lade Min/Max für Number-Variablen
            if (type == PluginVariable.Type.NUMBER) {
                if (variableJson.has("min")) {
                    variable.setMinValue(variableJson.get("min").getAsDouble());
                }
                if (variableJson.has("max")) {
                    variable.setMaxValue(variableJson.get("max").getAsDouble());
                }
            }
            
            return variable;
        } catch (Exception e) {
            logger.warning("Fehler beim Parsen der Plugin-Variable: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Speichert ein Plugin in eine JSON-Datei
     */
    public void savePlugin(Plugin plugin) {
        try {
            String filename = plugin.getName().toLowerCase().replaceAll("[^a-z0-9]", "-") + ".json";
            File pluginFile = new File(PLUGIN_DIR, filename);
            
            JsonObject json = new JsonObject();
            json.addProperty("name", plugin.getName());
            json.addProperty("description", plugin.getDescription());
            json.addProperty("category", plugin.getCategory());
            json.addProperty("prompt", plugin.getPrompt());
            json.addProperty("temperature", plugin.getTemperature());
            json.addProperty("maxTokens", plugin.getMaxTokens());
            json.addProperty("enabled", plugin.isEnabled());
            
            try (FileWriter writer = new FileWriter(pluginFile)) {
                gson.toJson(json, writer);
            }
            
            logger.info("Plugin gespeichert: " + plugin.getName());
            
        } catch (Exception e) {
            logger.severe("Fehler beim Speichern von Plugin " + plugin.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Erstellt Standard-Plugins
     */
    private void createDefaultPlugins() {
        // Grammatik-/Stilprüfung Plugin
        Plugin grammatikPlugin = new Plugin(
            "Grammatik-/Stilprüfung",
            "Analysiert und verbessert Texte sprachlich und literarisch",
            "Text-Verbesserung",
            "Du bist ein erfahrener deutscher Lektor und Sprachanalyst. Deine Aufgabe ist es, einen gegebenen Text sprachlich und literarisch zu analysieren und ihn anschließend in mehreren Varianten zu verbessern.\n\n**Anleitung:**\n\n1. **Originaltext:** Gib den Originaltext an.\n2. **Varianten:** Formuliere mindestens zwei alternative Versionen des Textes um, die sprachlich eleganter, präziser und natürlicher klingen.\n3. **Erläuterung:** Erkläre detailliert, welche sprachlichen und literarischen Verbesserungen du vorgenommen hast.\n\n**Text zum Analysieren:** {Hier den Text einfügen, den du analysieren möchtest}",
            0.7,
            2048,
            true
        );
        savePlugin(grammatikPlugin);
        
        // Charakter-Entwicklung Plugin
        Plugin charakterPlugin = new Plugin(
            "Charakter-Entwicklung",
            "Entwickelt detaillierte Charakterprofile basierend auf Grundideen",
            "Charakter-Entwicklung",
            "Du bist ein erfahrener Autor und Charakterentwickler. Deine Aufgabe ist es, basierend auf einer Grundidee einen vollständigen, dreidimensionalen Charakter zu entwickeln.\n\n**Anleitung:**\n\n1. **Charakter-Name:** {Charakter-Name}\n2. **Grundidee:** {Grundidee}\n\nEntwickle einen detaillierten Charakter mit:\n- Persönlichkeit und Motivation\n- Hintergrund und Geschichte\n- Stärken und Schwächen\n- Beziehungen zu anderen Charakteren\n- Entwicklungsmöglichkeiten\n\n**Format:** Strukturierte, übersichtliche Darstellung",
            0.8,
            2048,
            true
        );
        savePlugin(charakterPlugin);
    }
    
    /**
     * Gibt alle aktiven Plugins zurück
     */
    public List<Plugin> getActivePlugins() {
        return new ArrayList<>(plugins);
    }
    
    /**
     * Gibt ein Plugin nach Namen zurück
     */
    public Plugin getPlugin(String name) {
        return pluginMap.get(name);
    }
    
    /**
     * Gibt alle Plugin-Kategorien zurück
     */
    public Set<String> getCategories() {
        Set<String> categories = new HashSet<>();
        for (Plugin plugin : plugins) {
            categories.add(plugin.getCategory());
        }
        return categories;
    }
    
    /**
     * Gibt Plugins nach Kategorie zurück
     */
    public List<Plugin> getPluginsByCategory(String category) {
        List<Plugin> categoryPlugins = new ArrayList<>();
        for (Plugin plugin : plugins) {
            if (category.equals(plugin.getCategory())) {
                categoryPlugins.add(plugin);
            }
        }
        return categoryPlugins;
    }
    
    // Hilfsmethoden für JSON-Parsing
    private String getStringOrDefault(JsonObject json, String key, String defaultValue) {
        return json.has(key) ? json.get(key).getAsString() : defaultValue;
    }
    
    private double getDoubleOrDefault(JsonObject json, String key, double defaultValue) {
        return json.has(key) ? json.get(key).getAsDouble() : defaultValue;
    }
    
    private int getIntOrDefault(JsonObject json, String key, int defaultValue) {
        return json.has(key) ? json.get(key).getAsInt() : defaultValue;
    }
    
    private boolean getBooleanOrDefault(JsonObject json, String key, boolean defaultValue) {
        return json.has(key) ? json.get(key).getAsBoolean() : defaultValue;
    }
}
