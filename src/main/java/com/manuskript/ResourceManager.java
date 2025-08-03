package com.manuskript;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Verwaltet den Zugriff auf Ressourcen mit Priorität für externe Config-Dateien
 */
public class ResourceManager {
    private static final Logger logger = Logger.getLogger(ResourceManager.class.getName());
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
            logger.info("Lade externe CSS-Datei: " + externalPath);
            return externalFile.toURI().toString();
        }
        
        logger.warning("CSS-Datei nicht gefunden: " + externalPath + " - Erstelle Standard-Datei");
        
        // Standard-Datei erstellen
        createDefaultCssFile(resourcePath);
        
        // Erneut versuchen zu laden
        if (externalFile.exists() && externalFile.isFile()) {
            logger.info("Lade erstellte Standard-CSS-Datei: " + externalPath);
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
                logger.info("Lade externe Properties-Datei: " + externalPath);
                return Files.newInputStream(externalFile.toPath());
            } catch (IOException e) {
                logger.warning("Fehler beim Laden der externen Properties-Datei: " + e.getMessage());
            }
        }
        
        logger.warning("Properties-Datei nicht gefunden: " + externalPath + " - Erstelle Standard-Datei");
        
        // Standard-Datei erstellen
        createDefaultPropertiesFile(resourcePath);
        
        // Erneut versuchen zu laden
        if (externalFile.exists() && externalFile.isFile()) {
            try {
                logger.info("Lade erstellte Standard-Properties-Datei: " + externalPath);
                return Files.newInputStream(externalFile.toPath());
            } catch (IOException e) {
                logger.warning("Fehler beim Laden der erstellten Properties-Datei: " + e.getMessage());
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
                logger.info("Config-Ordner erstellt: " + configPath.toAbsolutePath());
            }
            
            // CSS-Unterordner erstellen
            Path cssPath = configPath.resolve("css");
            if (!Files.exists(cssPath)) {
                Files.createDirectories(cssPath);
                logger.info("CSS-Ordner erstellt: " + cssPath.toAbsolutePath());
            }
            
            // Sessions-Unterordner erstellen
            Path sessionsPath = configPath.resolve(SESSIONS_DIR);
            if (!Files.exists(sessionsPath)) {
                Files.createDirectories(sessionsPath);
                logger.info("Sessions-Ordner erstellt: " + sessionsPath.toAbsolutePath());
            }
            
            // Standard-Dateien erstellen falls nicht vorhanden
            createDefaultCssFile("css/styles.css");
            createDefaultCssFile("css/editor.css");
            createDefaultPropertiesFile("textanalysis.properties");
            
        } catch (IOException e) {
            logger.warning("Fehler beim Initialisieren des Config-Ordners: " + e.getMessage());
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
            
            logger.info("Session gespeichert: " + sessionName + " -> " + sessionFile);
        } catch (IOException e) {
            logger.warning("Fehler beim Speichern der Session " + sessionName + ": " + e.getMessage());
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
                
                logger.info("Session geladen: " + sessionName + " <- " + sessionFile);
                return qaPairs != null ? qaPairs : new ArrayList<>();
            }
        } catch (IOException e) {
            logger.warning("Fehler beim Laden der Session " + sessionName + ": " + e.getMessage());
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
                logger.info("Session gelöscht: " + sessionName + " -> " + sessionFile);
            }
        } catch (IOException e) {
            logger.warning("Fehler beim Löschen der Session " + sessionName + ": " + e.getMessage());
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
            logger.warning("Fehler beim Laden der verfügbaren Sessions: " + e.getMessage());
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
                logger.info("Standard-CSS-Datei erstellt: " + targetPath);
            }
        } catch (IOException e) {
            logger.warning("Fehler beim Erstellen der CSS-Datei " + configPath + ": " + e.getMessage());
        }
    }
    
    /**
     * Erstellt eine Standard-Properties-Datei falls sie nicht existiert
     */
    private static void createDefaultPropertiesFile(String configPath) {
        try {
            Path targetPath = Paths.get(CONFIG_DIR, configPath);
            
            // Nur erstellen falls Ziel-Datei nicht existiert
            if (!Files.exists(targetPath)) {
                // Standard-Properties-Inhalt erstellen
                String propertiesContent = getDefaultPropertiesContent(configPath);
                Files.write(targetPath, propertiesContent.getBytes());
                logger.info("Standard-Properties-Datei erstellt: " + targetPath);
            }
        } catch (IOException e) {
            logger.warning("Fehler beim Erstellen der Properties-Datei " + configPath + ": " + e.getMessage());
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
        }
        return "# Standard Properties\n";
    }
    
    /**
     * Gibt den absoluten Pfad zum Config-Ordner zurück
     */
    public static String getConfigDirectory() {
        return Paths.get(CONFIG_DIR).toAbsolutePath().toString();
    }
} 