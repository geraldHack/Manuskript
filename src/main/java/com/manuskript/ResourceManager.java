package com.manuskript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.Objects;

/**
 * Verwaltet den Zugriff auf Ressourcen mit Priorität für externe Config-Dateien
 */
public class ResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    private static final String CONFIG_DIR = "config";
    private static final String SESSIONS_DIR = "sessions";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Lädt eine CSS-Datei aus dem Config-Ordner
     */
    public static String getCssResource(String resourcePath) {
        // Externe Datei im Config-Ordner
        String externalPath = CONFIG_DIR + "/" + resourcePath;
        File externalFile = new File(externalPath);
        
        if (externalFile.exists() && externalFile.isFile()) {
            return externalFile.toURI().toString();
        }
        

        
        // Standard-Datei erstellen
        createDefaultCssFile(resourcePath);
        
        // Erneut versuchen zu laden
        if (externalFile.exists() && externalFile.isFile()) {
            return externalFile.toURI().toString();
        }
        
        return null;
    }
    
    /**
     * Lädt eine Properties-Datei aus dem Config-Ordner
     */
    public static InputStream getPropertiesResource(String resourcePath) {
        // Externe Datei im Config-Ordner
        String externalPath = CONFIG_DIR + "/" + resourcePath;
        File externalFile = new File(externalPath);
        
        if (externalFile.exists() && externalFile.isFile()) {
            try {
                return Files.newInputStream(externalFile.toPath());
            } catch (IOException e) {
                logger.warn("Fehler beim Laden der externen Properties-Datei", e);
            }
        }
        
        logger.warn("Properties-Datei nicht gefunden: {} - Erstelle Standard-Datei", externalPath);
        
        // Standard-Datei erstellen
        createDefaultPropertiesFile(resourcePath);
        
        // Erneut versuchen zu laden
        if (externalFile.exists() && externalFile.isFile()) {
            try {
                return Files.newInputStream(externalFile.toPath());
            } catch (IOException e) {
                logger.warn("Fehler beim Laden der erstellten Properties-Datei", e);
            }
        }
        
        return null;
    }
    
    /**
     * Erstellt den Config-Ordner und erstellt Standard-Dateien
     */
    public static void initializeConfigDirectory() {
        try {
            Path configPath = Paths.get(CONFIG_DIR);
            
            // Config-Ordner erstellen falls nicht vorhanden
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
            }
            
            // CSS-Unterordner erstellen
            Path cssPath = configPath.resolve("css");
            if (!Files.exists(cssPath)) {
                Files.createDirectories(cssPath);
            }
            
            // Sessions-Unterordner erstellen
            Path sessionsPath = configPath.resolve(SESSIONS_DIR);
            if (!Files.exists(sessionsPath)) {
                Files.createDirectories(sessionsPath);
            }
            
            // Standard-Dateien erstellen falls nicht vorhanden
            // createDefaultCssFile("css/styles.css"); // Deaktiviert - verursacht CSS-Konflikte
            // createDefaultCssFile("css/editor.css"); // Deaktiviert - verursacht CSS-Konflikte
            createDefaultPropertiesFile("textanalysis.properties");
            createDefaultPropertiesFile("parameters.properties");
            
        } catch (IOException e) {
            logger.warn("Fehler beim Initialisieren des Config-Ordners", e);
        }
    }
    
    /**
     * Speichert eine Session als JSON-Datei
     */
    public static void saveSession(String sessionName, List<CustomChatArea.QAPair> qaPairs) {
        try {
            Path sessionsPath = Paths.get(CONFIG_DIR, SESSIONS_DIR);
            Path sessionFile = sessionsPath.resolve(sessionName + ".json");
            
            // Session als JSON serialisieren
            String json = gson.toJson(qaPairs);
            Files.write(sessionFile, json.getBytes());
            
        } catch (IOException e) {
            logger.warn("Fehler beim Speichern der Session {}", sessionName, e);
        }
    }
    
    /**
     * Lädt eine Session aus JSON-Datei
     */
    public static List<CustomChatArea.QAPair> loadSession(String sessionName) {
        try {
            Path sessionsPath = Paths.get(CONFIG_DIR, SESSIONS_DIR);
            Path sessionFile = sessionsPath.resolve(sessionName + ".json");
            
            if (Files.exists(sessionFile)) {
                String json = new String(Files.readAllBytes(sessionFile));
                List<CustomChatArea.QAPair> qaPairs = gson.fromJson(json, 
                    new TypeToken<List<CustomChatArea.QAPair>>(){}.getType());
                
                return qaPairs != null ? qaPairs : new ArrayList<>();
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Laden der Session {}", sessionName, e);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Löscht eine Session-Datei
     */
    public static void deleteSession(String sessionName) {
        try {
            Path sessionsPath = Paths.get(CONFIG_DIR, SESSIONS_DIR);
            Path sessionFile = sessionsPath.resolve(sessionName + ".json");
            
            if (Files.exists(sessionFile)) {
                Files.delete(sessionFile);
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Löschen der Session {}", sessionName, e);
        }
    }
    
    /**
     * Lädt alle verfügbaren Session-Namen
     */
    public static List<String> getAvailableSessions() {
        List<String> sessions = new ArrayList<>();
        try {
            Path sessionsPath = Paths.get(CONFIG_DIR, SESSIONS_DIR);
            
            if (Files.exists(sessionsPath)) {
                Files.list(sessionsPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String sessionName = fileName.substring(0, fileName.length() - 5); // .json entfernen
                        sessions.add(sessionName);
                    });
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Laden der verfügbaren Sessions", e);
        }
        
        return sessions;
    }
    
    /**
     * Erstellt eine Standard-CSS-Datei falls sie nicht existiert
     */
    private static void createDefaultCssFile(String configPath) {
        try {
            Path targetPath = Paths.get(CONFIG_DIR, configPath);
            
            // Nur erstellen falls Ziel-Datei nicht existiert
            if (!Files.exists(targetPath)) {
                // Ziel-Ordner erstellen falls nötig
                Path targetDir = targetPath.getParent();
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                
                // Standard-CSS-Inhalt erstellen
                String cssContent = getDefaultCssContent(configPath);
                Files.write(targetPath, cssContent.getBytes());
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Erstellen der CSS-Datei {}", configPath, e);
        }
    }
    
    /**
     * Erstellt eine Standard-Properties-Datei falls sie nicht existiert
     */
    private static void createDefaultPropertiesFile(String configPath) {
        try {
            Path filePath = Paths.get(CONFIG_DIR, configPath);
            if (!Files.exists(filePath)) {
                Files.copy(Objects.requireNonNull(ResourceManager.class.getResourceAsStream("/" + configPath)), filePath);
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Erstellen der Properties-Datei {}", configPath, e);
        }
    }
    
    /**
     * Gibt den Standard-CSS-Inhalt für eine Datei zurück
     */
    private static String getDefaultCssContent(String configPath) {
        if (configPath.equals("css/styles.css")) {
            return "/* Standard Styles für Manuskript */\n" +
                   ".root {\n" +
                   "    -fx-background-color: #ffffff;\n" +
                   "    -fx-text-fill: #000000;\n" +
                   "}\n" +
                   "\n" +
                   ".button {\n" +
                   "    -fx-background-color: #f0f0f0;\n" +
                   "    -fx-text-fill: #000000;\n" +
                   "    -fx-border-color: #cccccc;\n" +
                   "    -fx-border-width: 1px;\n" +
                   "    -fx-border-radius: 3px;\n" +
                   "    -fx-background-radius: 3px;\n" +
                   "}\n" +
                   "\n" +
                   ".button:hover {\n" +
                   "    -fx-background-color: #e0e0e0;\n" +
                   "}\n";
        } else if (configPath.equals("css/editor.css")) {
            return "/* Standard Editor Styles für Manuskript */\n" +
                   ".code-area {\n" +
                   "    -fx-font-family: 'Consolas', 'Monaco', monospace;\n" +
                   "    -fx-font-size: 12px;\n" +
                   "    -fx-background-color: #ffffff;\n" +
                   "    -fx-text-fill: #000000;\n" +
                   "}\n" +
                   "\n" +
                   ".text-area {\n" +
                   "    -fx-font-family: 'Consolas', 'Monaco', monospace;\n" +
                   "    -fx-font-size: 12px;\n" +
                   "    -fx-background-color: #ffffff;\n" +
                   "    -fx-text-fill: #000000;\n" +
                   "}\n";
        }
        return "/* Standard CSS */\n";
    }
    
    /**
     * Gibt den Standard-Properties-Inhalt für eine Datei zurück
     */
    private static String getDefaultPropertiesContent(String configPath) {
        if (configPath.equals("textanalysis.properties")) {
            return "# Textanalyse-Konfiguration\n" +
                   "# Standard-Einstellungen für die Textanalyse\n" +
                   "\n" +
                   "# Füllwörter (durch Kommas getrennt)\n" +
                   "fuellwoerter=und,oder,aber,auch,noch,schon,erst,denn,dann,so,wie,als,daß,dass,da,wo,was,wer,welche,welcher,welches\n" +
                   "\n" +
                   "# Sprechwörter (durch Kommas getrennt)\n" +
                   "sprechwoerter=sagte,sprach,erzählte,berichtete,erklärte,antwortete,fragte,fragte nach,meinte,denkt,dachte\n" +
                   "\n" +
                   "# Sprechantworten (durch Kommas getrennt)\n" +
                   "sprechantworten=ja,nein,okay,verstehe,klar,genau,richtig,falsch,stimmt,nicht,doch\n" +
                   "\n" +
                   "# Phrasen (durch Kommas getrennt)\n" +
                   "phrasen=es war einmal,in der tat,wie gesagt,wie bereits erwähnt,wie du weißt,wie du siehst\n";
        } else if (configPath.equals("parameters.properties")) {
            return "# Session-Management Parameter\n" +
                   "# Maximale Anzahl von QAPairs pro Session bevor automatische Aufteilung erfolgt\n" +
                   "session.max_qapairs_per_session=20\n" +
                   "\n" +
                   "# Ollama-Parameter\n" +
                   "ollama.temperature=0.3\n" +
                   "ollama.max_tokens=2048\n" +
                   "ollama.top_p=0.7\n" +
                   "ollama.repeat_penalty=1.3\n" +
                   "\n" +
                   "# UI-Parameter\n" +
                   "ui.default_theme=4\n" +
                   "ui.editor_font_size=16\n";
        }
        return "# Standard Properties\n";
    }
    
    /**
     * Gibt den absoluten Pfad zum Config-Ordner zurück
     */
    public static String getConfigDirectory() {
        return Paths.get(CONFIG_DIR).toAbsolutePath().toString();
    }
    
    /**
     * Lädt einen Parameter - zuerst aus User Preferences, dann aus parameters.properties
     */
    public static String getParameter(String key, String defaultValue) {
        try {
            Preferences preferences = Preferences.userNodeForPackage(ResourceManager.class);
            String value = preferences.get(key, null);
            
            // Wenn in User Preferences gefunden, verwende diesen Wert
            if (value != null) {
                return value;
            }
            
            // Fallback: Aus parameters.properties laden
            Properties props = new Properties();
            File configFile = new File(CONFIG_DIR + File.separator + "parameters.properties");
            
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                    String propValue = props.getProperty(key);
                    if (propValue != null) {
                        // Automatisch zu User Preferences migrieren
                        preferences.put(key, propValue);
                        return propValue;
                    }
                } catch (IOException e) {
                    logger.warn("Fehler beim Laden der parameters.properties", e);
                }
            }
            
            return defaultValue;
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der User Preference {}", key, e);
            return defaultValue;
        }
    }
    
    /**
     * Lädt einen Integer-Parameter - zuerst aus User Preferences, dann aus parameters.properties
     */
    public static int getIntParameter(String key, int defaultValue) {
        try {
            Preferences preferences = Preferences.userNodeForPackage(ResourceManager.class);
            int value = preferences.getInt(key, Integer.MIN_VALUE);
            
            // Wenn in User Preferences gefunden, verwende diesen Wert
            if (value != Integer.MIN_VALUE) {
                return value;
            }
            
            // Fallback: Aus parameters.properties laden
            String stringValue = getParameter(key, String.valueOf(defaultValue));
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                logger.warn("Ungültiger Integer-Wert für Parameter {}: {} - verwende Standard {}", key, stringValue, defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der User Preference {}", key, e);
            return defaultValue;
        }
    }
    
    /**
     * Lädt einen Double-Parameter aus User Preferences
     */
    public static double getDoubleParameter(String key, double defaultValue) {
        try {
            Preferences preferences = Preferences.userNodeForPackage(ResourceManager.class);
            return preferences.getDouble(key, defaultValue);
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der User Preference {}", key, e);
            return defaultValue;
        }
    }
    
    /**
     * Speichert einen Parameter in User Preferences (vereinheitlichte Konfiguration)
     */
    public static void saveParameter(String key, String value) {
        try {
            Preferences preferences = Preferences.userNodeForPackage(ResourceManager.class);
            preferences.put(key, value);
        } catch (Exception e) {
            logger.warn("Fehler beim Speichern der User Preference {}", key, e);
        }
    }

    private static final String TEXTANALYSIS_PROPERTIES = "textanalysis.properties";

    /**
     * Liest einen Parameter aus config/textanalysis.properties (UTF-8).
     */
    public static String getTextanalysisParameter(String key, String defaultValue) {
        File file = new File(CONFIG_DIR + File.separator + TEXTANALYSIS_PROPERTIES);
        if (!file.exists()) {
            return defaultValue;
        }
        try {
            Properties props = new Properties();
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                props.load(r);
            }
            return props.getProperty(key, defaultValue);
        } catch (IOException e) {
            logger.warn("Fehler beim Lesen von " + TEXTANALYSIS_PROPERTIES + ": " + e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Speichert einen Parameter in config/textanalysis.properties (UTF-8).
     * Lädt die Datei, setzt den Key und schreibt sie zurück.
     */
    public static void saveTextanalysisParameter(String key, String value) {
        File file = new File(CONFIG_DIR + File.separator + TEXTANALYSIS_PROPERTIES);
        Properties props = new Properties();
        if (file.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                logger.warn("Fehler beim Laden von " + TEXTANALYSIS_PROPERTIES + " zum Speichern: " + e.getMessage());
            }
        }
        props.setProperty(key, value != null ? value : "");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            props.store(w, "# Textanalyse-Konfiguration");
        } catch (IOException e) {
            logger.warn("Fehler beim Speichern in " + TEXTANALYSIS_PROPERTIES + ": " + e.getMessage());
        }
    }

    /**
     * Speichert alle Ollama-Parameter in User Preferences
     */
    public static void saveOllamaParameters(double temperature, int maxTokens, double topP, double repeatPenalty) {
        saveParameter("ollama.temperature", String.valueOf(temperature));
        saveParameter("ollama.max_tokens", String.valueOf(maxTokens));
        saveParameter("ollama.top_p", String.valueOf(topP));
        saveParameter("ollama.repeat_penalty", String.valueOf(repeatPenalty));
    }
    
    /**
     * Migriert alle parameters.properties zu User Preferences (einmalig beim Start)
     */
    public static void migrateParametersToPreferences() {
        try {
            File configFile = new File(CONFIG_DIR + File.separator + "parameters.properties");
            if (!configFile.exists()) {
                return; // Keine Migration nötig
            }
            
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                logger.warn("Fehler beim Laden der parameters.properties für Migration: " + e.getMessage());
                return;
            }
            
            Preferences preferences = Preferences.userNodeForPackage(ResourceManager.class);
            int migratedCount = 0;
            
            // Alle Properties zu User Preferences migrieren
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                if (value != null && !value.trim().isEmpty()) {
                    // Prüfen ob bereits in User Preferences vorhanden
                    String existingValue = preferences.get(key, null);
                    if (existingValue == null) {
                        preferences.put(key, value);
                        migratedCount++;
                        logger.debug("Migriert: " + key + " = " + value);
                    } else {
                        logger.debug("Bereits vorhanden: " + key + " = " + existingValue);
                    }
                }
            }
            
            // Zusätzlich: Prüfe spezifisch session.max_qapairs_per_session
            String sessionMaxQaPairs = props.getProperty("session.max_qapairs_per_session");
            if (sessionMaxQaPairs != null && !sessionMaxQaPairs.trim().isEmpty()) {
                String existingSessionValue = preferences.get("session.max_qapairs_per_session", null);
                if (existingSessionValue == null) {
                    preferences.put("session.max_qapairs_per_session", sessionMaxQaPairs);
                    migratedCount++;
                    logger.debug("Spezifisch migriert: session.max_qapairs_per_session = " + sessionMaxQaPairs);
                } else {
                    logger.debug("session.max_qapairs_per_session bereits vorhanden: " + existingSessionValue);
                }
            }
            
            if (migratedCount > 0) {
                logger.debug("Migration abgeschlossen: " + migratedCount + " Parameter zu User Preferences migriert");
                
                // Backup der alten parameters.properties erstellen
                File backupFile = new File(CONFIG_DIR + File.separator + "parameters.properties.backup");
                try {
                    Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Backup erstellt: " + backupFile.getAbsolutePath());
                } catch (IOException e) {
                    logger.warn("Fehler beim Erstellen des Backups: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.warn("Fehler bei der Migration von parameters.properties: " + e.getMessage());
        }
    }
    
    /**
     * Liest einen Parameter direkt aus der parameters.properties Datei
     */
    private static String getParameterFromProperties(String key, String defaultValue) {
        try {
            File configFile = new File(CONFIG_DIR + File.separator + "parameters.properties");
            if (!configFile.exists()) {
                return defaultValue;
            }
            
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                return props.getProperty(key, defaultValue);
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Lesen der parameters.properties: " + e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Stellt Standardwerte für wichtige Parameter wieder her, falls sie fehlen
     * Wird bei jedem Start aufgerufen, um sicherzustellen, dass wichtige Parameter vorhanden sind
     */
    public static void restoreDefaultParameters() {
        try {
            // Wichtige Parameter mit Standardwerten wiederherstellen
            String[][] defaultParams = {
                {"project.root.directory", System.getProperty("user.home") + File.separator + "Manuskripte"},
                {"session.max_qapairs_per_session", "20"},
                {"ollama.temperature", "0.17"},
                {"ollama.max_tokens", "8192"},
                {"ollama.top_p", "0.83"},
                {"ollama.repeat_penalty", "1.85"},
                {"ollama.http_connect_timeout_secs", "30"},
                {"ollama.http_request_timeout_secs", "360"},
                {"ui.selected_session", "default"},
                {"main_window_theme", "0"},
                {"editor_theme", "0"},
                {"help_enabled", "true"},
                {"paragraph_marking_enabled", "false"}
            };
            
            int restoredCount = 0;
            for (String[] param : defaultParams) {
                String key = param[0];
                String defaultValue = param[1];
                
                // Prüfe in ResourceManager Preferences
                Preferences resourcePrefs = Preferences.userNodeForPackage(ResourceManager.class);
                String existingValue = resourcePrefs.get(key, null);
                if (existingValue == null || existingValue.trim().isEmpty()) {
                    // Prüfe zuerst in parameters.properties, bevor Standardwert gesetzt wird
                    String paramValue = getParameterFromProperties(key, null);
                    if (paramValue != null && !paramValue.trim().isEmpty()) {
                        resourcePrefs.put(key, paramValue);
                        logger.debug("Wert aus parameters.properties übernommen (ResourceManager): " + key + " = " + paramValue);
                    } else {
                        resourcePrefs.put(key, defaultValue);
                        logger.debug("Standardwert wiederhergestellt (ResourceManager): " + key + " = " + defaultValue);
                    }
                    restoredCount++;
                }
                
                // Prüfe auch in MainController Preferences (für UI-spezifische Parameter)
                if (key.startsWith("ui.") || key.startsWith("project.") || key.startsWith("session.") || 
                    key.startsWith("main_") || key.startsWith("editor_") || key.startsWith("help_") || 
                    key.startsWith("paragraph_")) {
                    Preferences mainPrefs = Preferences.userNodeForPackage(com.manuskript.MainController.class);
                    String mainValue = mainPrefs.get(key, null);
                    if (mainValue == null || mainValue.trim().isEmpty()) {
                        // Prüfe zuerst in parameters.properties, bevor Standardwert gesetzt wird
                        String paramValue = getParameterFromProperties(key, null);
                        if (paramValue != null && !paramValue.trim().isEmpty()) {
                            mainPrefs.put(key, paramValue);
                            logger.debug("Wert aus parameters.properties übernommen (MainController): " + key + " = " + paramValue);
                        } else {
                            mainPrefs.put(key, defaultValue);
                            logger.debug("Standardwert wiederhergestellt (MainController): " + key + " = " + defaultValue);
                        }
                        restoredCount++;
                    }
                }
            }
            
            if (restoredCount > 0) {
                logger.debug("Standardwerte wiederhergestellt: " + restoredCount + " Parameter");
            } else {
                logger.debug("Alle Standardwerte sind bereits vorhanden");
            }
            
        } catch (Exception e) {
            logger.warn("Fehler beim Wiederherstellen der Standardwerte: " + e.getMessage());
        }
    }
    
    /**
     * Lädt den letzten verwendeten Datei-Pfad
     */
    public static String getLastFilePath() {
        return getParameter("ui.last_file_path", "");
    }
    
    /**
     * Speichert den letzten verwendeten Datei-Pfad
     */
    public static void setLastFilePath(String path) {
        saveParameter("ui.last_file_path", path);
    }
    
    /**
     * Lädt den letzten verwendeten Ausgabe-Pfad
     */
    public static String getLastOutputPath() {
        return getParameter("ui.last_output_path", "");
    }
    
    /**
     * Speichert den letzten verwendeten Ausgabe-Pfad
     */
    public static void setLastOutputPath(String path) {
        saveParameter("ui.last_output_path", path);
    }
} 