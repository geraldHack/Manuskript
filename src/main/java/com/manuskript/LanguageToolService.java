package com.manuskript;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import java.lang.Process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Service-Klasse für die Kommunikation mit dem lokalen LanguageTool Server
 */
public class LanguageToolService {
    private static final Logger logger = LoggerFactory.getLogger(LanguageToolService.class);
    
    private static final String DEFAULT_SERVER_URL = "http://localhost:8081";
    private static final String DEFAULT_SERVER_JAR_PATH = "language tool/languagetool-server.jar";
    private static final String CHECK_ENDPOINT = "/v2/check";
    
    private final HttpClient httpClient;
    private final Preferences preferences;
    
    private static final Object SERVER_LOCK = new Object();
    private static final Object CHECK_LOCK = new Object();
    private static Process sharedServerProcess;
    private static boolean sharedServerStartedByUs = false;

    private String serverUrl;
    private String serverJarPath;
    private boolean autoStartEnabled;
    
    /**
     * Repräsentiert einen gefundenen Fehler von LanguageTool
     */
    public static class Match {
        private int offset;
        private int length;
        private String message;
        private String shortMessage;
        private List<Replacement> replacements;
        private String ruleId;
        private String ruleDescription;
        
        public Match() {
            this.replacements = new ArrayList<>();
        }
        
        public int getOffset() { return offset; }
        public void setOffset(int offset) { this.offset = offset; }
        
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getShortMessage() { return shortMessage; }
        public void setShortMessage(String shortMessage) { this.shortMessage = shortMessage; }
        
        public List<Replacement> getReplacements() { return replacements; }
        public void setReplacements(List<Replacement> replacements) { this.replacements = replacements; }
        
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        
        public String getRuleDescription() { return ruleDescription; }
        public void setRuleDescription(String ruleDescription) { this.ruleDescription = ruleDescription; }
    }
    
    /**
     * Repräsentiert einen Korrekturvorschlag
     */
    public static class Replacement {
        private String value;
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
    
    /**
     * Ergebnis einer Textprüfung.
     * {@link #isSuccessful()} unterscheidet echte „0 Fehler“ von fehlgeschlagenen Prüfungen
     * (Timeout, HTTP-Fehler, Server tot) – sonst zeigt die UI fälschlich einen Haken.
     */
    public static class CheckResult {
        private List<Match> matches;
        private String language;
        private String detectedLanguage;
        private boolean successful = true;
        private String errorMessage;
        
        public CheckResult() {
            this.matches = new ArrayList<>();
        }

        public static CheckResult failure(String message) {
            CheckResult result = new CheckResult();
            result.successful = false;
            result.errorMessage = message != null ? message : "LanguageTool-Prüfung fehlgeschlagen";
            return result;
        }
        
        public List<Match> getMatches() { return matches; }
        public void setMatches(List<Match> matches) { this.matches = matches; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getDetectedLanguage() { return detectedLanguage; }
        public void setDetectedLanguage(String detectedLanguage) { this.detectedLanguage = detectedLanguage; }

        public boolean isSuccessful() { return successful; }

        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Konstruktor
     */
    public LanguageToolService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.preferences = Preferences.userNodeForPackage(LanguageToolService.class);
        
        // Lade Konfiguration aus Preferences
        loadConfiguration();
    }
    
    /**
     * Lädt Konfiguration aus Preferences
     */
    private void loadConfiguration() {
        this.serverUrl = preferences.get("languagetool_server_url", DEFAULT_SERVER_URL);
        this.serverJarPath = preferences.get("languagetool_server_jar_path", DEFAULT_SERVER_JAR_PATH);
        this.autoStartEnabled = preferences.getBoolean("languagetool_auto_start", true);
    }
    
    /**
     * Speichert Konfiguration in Preferences
     */
    public void saveConfiguration() {
        preferences.put("languagetool_server_url", serverUrl);
        preferences.put("languagetool_server_jar_path", serverJarPath);
        preferences.putBoolean("languagetool_auto_start", autoStartEnabled);
    }
    
    /**
     * Prüft ob der Server erreichbar ist
     */
    public CompletableFuture<Boolean> checkServerStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + CHECK_ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("text=test&language=de-DE"))
                    .timeout(Duration.ofSeconds(10)) // Erhöht von 5 auf 10 Sekunden
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    /**
     * Startet den Server-Prozess, falls nicht bereits gestartet
     */
    public CompletableFuture<Boolean> startServerIfNeeded() {
        return checkServerStatus().thenCompose(isRunning -> {
            if (isRunning) {
                return CompletableFuture.completedFuture(true);
            }
            
            if (!autoStartEnabled) {
                return CompletableFuture.completedFuture(false);
            }
            
            return startServerProcess();
        });
    }

    /**
     * Startet den gebündelten LanguageTool-Server unabhängig von der Auto-Start-Preference
     * (z. B. Setup-Assistent).
     */
    public CompletableFuture<Boolean> ensureServerRunning() {
        return checkServerStatus().thenCompose(isRunning -> {
            if (isRunning) {
                return CompletableFuture.completedFuture(true);
            }
            return startServerProcess();
        });
    }
    
    /**
     * Startet den Server-Prozess (prozessweit geteilt über alle Editor-Instanzen).
     */
    private CompletableFuture<Boolean> startServerProcess() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (SERVER_LOCK) {
                try {
                    if (sharedServerProcess != null && sharedServerProcess.isAlive()) {
                        try {
                            if (Boolean.TRUE.equals(checkServerStatus().get())) {
                                return true;
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    File jarFile = resolveJarPath(serverJarPath);
                    if (jarFile == null || !jarFile.exists()) {
                        logger.error("LanguageTool Server JAR nicht gefunden: " + serverJarPath);
                        logger.error("Geprüfte Pfade:");
                        logger.error("  1. " + new File(serverJarPath).getAbsolutePath());
                        logger.error("  2. " + new File(System.getProperty("user.dir"), serverJarPath).getAbsolutePath());
                        return false;
                    }

                    logger.info("LanguageTool Server JAR gefunden: " + jarFile.getAbsolutePath());

                    int port = extractPortFromUrl(serverUrl);
                    String javaExe = resolveJavaExecutable();
                    ProcessBuilder pb = new ProcessBuilder(
                        javaExe, "-jar", jarFile.getAbsolutePath(), "--port", String.valueOf(port)
                    );
                    pb.directory(jarFile.getParentFile());
                    pb.redirectErrorStream(true);

                    logger.info("Starte LanguageTool Server mit: java -jar " + jarFile.getAbsolutePath() + " --port " + port);

                    sharedServerProcess = pb.start();
                    sharedServerStartedByUs = true;

                    logger.info("LanguageTool Server gestartet (PID: " + sharedServerProcess.pid() + ")");

                    int maxRetries = 10;
                    int retryDelay = 1500;

                    for (int i = 0; i < maxRetries; i++) {
                        Thread.sleep(retryDelay);

                        try {
                            boolean isRunning = checkServerStatus().get();
                            if (isRunning) {
                                logger.info("LanguageTool Server ist bereit (nach " + ((i + 1) * retryDelay / 1000.0) + " Sekunden)");
                                return true;
                            }
                        } catch (Exception e) {
                        }

                        if (sharedServerProcess != null && !sharedServerProcess.isAlive()) {
                            int exitCode = sharedServerProcess.exitValue();
                            logger.error("LanguageTool Server-Prozess beendet mit Exit-Code: " + exitCode);
                            sharedServerProcess = null;
                            sharedServerStartedByUs = false;
                            return false;
                        }
                    }

                    logger.warn("LanguageTool Server antwortet nach " + (maxRetries * retryDelay / 1000.0) + " Sekunden nicht");
                    return false;
                } catch (Exception e) {
                    logger.error("Fehler beim Starten des LanguageTool Servers", e);
                    return false;
                }
            }
        });
    }

    /**
     * Stoppt den LanguageTool-Server und startet ihn neu (z. B. nach Timeout/Verklemmung).
     */
    public CompletableFuture<Boolean> restartServer() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (SERVER_LOCK) {
                logger.warn("LanguageTool Server wird neu gestartet...");
                stopSharedServerUnlocked();
                killProcessOnPort(extractPortFromUrl(serverUrl));
            }
            return null;
        }).thenCompose(ignored -> startServerProcess());
    }
    
    /**
     * Extrahiert Port aus URL
     */
    private int extractPortFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            int port = uri.getPort();
            return port > 0 ? port : 8081; // Default Port
        } catch (Exception e) {
            return 8081;
        }
    }
    
    /**
     * Stoppt den Server-Prozess (falls von uns gestartet)
     */
    public void stopServerProcess() {
        synchronized (SERVER_LOCK) {
            stopSharedServerUnlocked();
        }
    }

    private static void stopSharedServerUnlocked() {
        if (sharedServerProcess != null && sharedServerStartedByUs) {
            try {
                sharedServerProcess.destroy();
                if (sharedServerProcess.isAlive()) {
                    sharedServerProcess.destroyForcibly();
                }
                logger.info("LanguageTool Server gestoppt");
            } catch (Exception e) {
                logger.error("Fehler beim Stoppen des LanguageTool Servers", e);
            }
        }
        sharedServerProcess = null;
        sharedServerStartedByUs = false;
    }

    private void killProcessOnPort(int port) {
        if (port <= 0) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c",
                        "for /f \"tokens=5\" %a in ('netstat -ano ^| findstr :" + port + "') do taskkill /F /PID %a");
            } else {
                pb = new ProcessBuilder("bash", "-lc",
                        "pids=$(lsof -tiTCP:" + port + " -sTCP:LISTEN 2>/dev/null); "
                                + "if [ -n \"$pids\" ]; then kill -9 $pids; fi");
            }
            pb.redirectErrorStream(true);
            Process killer = pb.start();
            killer.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            logger.info("Versuche LanguageTool-Prozess auf Port {} zu beenden", port);
        } catch (Exception e) {
            logger.debug("Konnte Prozess auf Port {} nicht beenden: {}", port, e.getMessage());
        }
    }
    
    /**
     * Prüft einen Text auf Fehler
     * Prüft alle Fehlertypen: Rechtschreibung, Grammatik, Kommasetzung, Stil, etc.
     * Keine Kategorien werden deaktiviert, damit alle Fehlertypen erkannt werden.
     */
    public CompletableFuture<CheckResult> checkText(String text, String language) {
        return CompletableFuture.supplyAsync(() -> {
            // Nur eine Prüfung gleichzeitig – parallele Requests verklemmen den lokalen LT-Server leicht
            synchronized (CHECK_LOCK) {
                try {
                    // URL-encoded Body erstellen
                    // WICHTIG: Keine disabledCategories setzen, damit alle Fehlertypen geprüft werden:
                    // - Rechtschreibung (SPELLING)
                    // - Grammatik (GRAMMAR)
                    // - Kommasetzung (PUNCTUATION)
                    // - Stil (STYLE)
                    // - Typografie (TYPOGRAPHY)
                    String body = "text=" + urlEncode(text) + "&language=" + language;

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(serverUrl + CHECK_ENDPOINT))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(30))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        logger.error("LanguageTool API Fehler: HTTP " + response.statusCode());
                        return CheckResult.failure("HTTP " + response.statusCode());
                    }

                    return parseCheckResponse(response.body());
                } catch (Exception e) {
                    logger.error("Fehler bei LanguageTool Textprüfung", e);
                    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return CheckResult.failure(message);
                }
            }
        });
    }
    
    /**
     * Parst die JSON-Antwort von LanguageTool
     */
    private CheckResult parseCheckResponse(String json) {
        CheckResult result = new CheckResult();
        
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            // Sprache
            if (root.has("language")) {
                JsonObject langObj = root.getAsJsonObject("language");
                if (langObj.has("code")) {
                    result.setLanguage(langObj.get("code").getAsString());
                }
            }
            
            // Erkannte Sprache
            if (root.has("language") && root.getAsJsonObject("language").has("detectedLanguage")) {
                JsonObject detectedLang = root.getAsJsonObject("language").getAsJsonObject("detectedLanguage");
                if (detectedLang.has("code")) {
                    result.setDetectedLanguage(detectedLang.get("code").getAsString());
                }
            }
            
            // Matches (Fehler)
            if (root.has("matches")) {
                JsonArray matchesArray = root.getAsJsonArray("matches");
                for (int i = 0; i < matchesArray.size(); i++) {
                    JsonObject matchObj = matchesArray.get(i).getAsJsonObject();
                    Match match = new Match();
                    
                    if (matchObj.has("offset")) {
                        match.setOffset(matchObj.get("offset").getAsInt());
                    }
                    if (matchObj.has("length")) {
                        match.setLength(matchObj.get("length").getAsInt());
                    }
                    if (matchObj.has("message")) {
                        match.setMessage(matchObj.get("message").getAsString());
                    }
                    if (matchObj.has("shortMessage")) {
                        match.setShortMessage(matchObj.get("shortMessage").getAsString());
                    }
                    if (matchObj.has("rule")) {
                        JsonObject ruleObj = matchObj.getAsJsonObject("rule");
                        if (ruleObj.has("id")) {
                            match.setRuleId(ruleObj.get("id").getAsString());
                        }
                        if (ruleObj.has("description")) {
                            match.setRuleDescription(ruleObj.get("description").getAsString());
                        }
                    }
                    
                    // Replacements (Korrekturvorschläge)
                    if (matchObj.has("replacements")) {
                        JsonArray replacementsArray = matchObj.getAsJsonArray("replacements");
                        for (int j = 0; j < replacementsArray.size(); j++) {
                            JsonObject replObj = replacementsArray.get(j).getAsJsonObject();
                            Replacement replacement = new Replacement();
                            if (replObj.has("value")) {
                                replacement.setValue(replObj.get("value").getAsString());
                            }
                            match.getReplacements().add(replacement);
                        }
                    }
                    
                    result.getMatches().add(match);
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Parsen der LanguageTool-Antwort", e);
            return CheckResult.failure("Ungültige LanguageTool-Antwort");
        }
        
        return result;
    }
    
    /**
     * Löst den JAR-Pfad auf (prüft mehrere mögliche Orte)
     */
    private File resolveJarPath(String jarPath) {
        // 1. Versuche direkt (falls absoluter Pfad)
        File jarFile = new File(jarPath);
        if (jarFile.exists()) {
            return jarFile;
        }

        // 2. App-Bundle / Dev-Home (jpackage Contents/app)
        jarFile = ApplicationPaths.resolveBundledPath(jarPath);
        if (jarFile.exists()) {
            return jarFile;
        }
        
        // 3. Versuche relativ zum Arbeitsverzeichnis (user.dir)
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            jarFile = new File(userDir, jarPath);
            if (jarFile.exists()) {
                return jarFile;
            }
        }
        
        // 3. Versuche relativ zum JAR-Verzeichnis der Anwendung
        try {
            String appPath = LanguageToolService.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            if (appPath != null && appPath.endsWith(".jar")) {
                File appJar = new File(appPath);
                File appDir = appJar.getParentFile();
                if (appDir != null) {
                    jarFile = new File(appDir, jarPath);
                    if (jarFile.exists()) {
                        return jarFile;
                    }
                }
            }
        } catch (Exception e) {
        }
        
        // 4. Versuche relativ zum Projekt-Root (ein Verzeichnis höher, falls wir in target/classes sind)
        if (userDir != null) {
            File projectRoot = new File(userDir);
            // Prüfe ob wir in target/classes sind
            if (projectRoot.getName().equals("classes") && projectRoot.getParentFile() != null 
                && projectRoot.getParentFile().getName().equals("target")) {
                File rootDir = projectRoot.getParentFile().getParentFile();
                if (rootDir != null) {
                    jarFile = new File(rootDir, jarPath);
                    if (jarFile.exists()) {
                        return jarFile;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Findet die Java-Executable: zuerst im eingebetteten Runtime (jpackage),
     * dann JAVA_HOME, zuletzt Fallback auf "java" im System-PATH.
     */
    private static String resolveJavaExecutable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        String exeName = isWindows ? "java.exe" : "java";

        // 1. Eingebettete Runtime neben der App (jpackage app-image: runtime/bin/java)
        File runtimeJava = new File("runtime" + File.separator + "bin" + File.separator + exeName);
        if (runtimeJava.isFile()) return runtimeJava.getAbsolutePath();

        // 2. JAVA_HOME
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File homeJava = new File(javaHome, "bin" + File.separator + exeName);
            if (homeJava.isFile()) return homeJava.getAbsolutePath();
        }

        // 3. Fallback: System-PATH
        return "java";
    }

    /**
     * URL-Encoding für Text
     */
    private String urlEncode(String text) {
        try {
            return java.net.URLEncoder.encode(text, "UTF-8");
        } catch (Exception e) {
            return text.replace(" ", "%20");
        }
    }
    
    // Getter und Setter
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { 
        this.serverUrl = serverUrl;
        saveConfiguration();
    }
    
    public String getServerJarPath() { return serverJarPath; }
    public void setServerJarPath(String serverJarPath) { 
        this.serverJarPath = serverJarPath;
        saveConfiguration();
    }
    
    public boolean isAutoStartEnabled() { return autoStartEnabled; }
    public void setAutoStartEnabled(boolean autoStartEnabled) { 
        this.autoStartEnabled = autoStartEnabled;
        saveConfiguration();
    }
    
    /**
     * Cleanup beim Beenden
     */
    public void shutdown() {
        stopServerProcess();
    }
}

