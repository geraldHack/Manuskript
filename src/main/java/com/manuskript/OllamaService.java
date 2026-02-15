package com.manuskript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.Closeable;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * Service-Klasse für die Kommunikation mit dem lokalen Ollama-Server
 */
public class OllamaService {
    private static final Logger logger = LoggerFactory.getLogger(OllamaService.class);
    
    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String GENERATE_ENDPOINT = "/api/generate";
    private static final String CHAT_ENDPOINT = "/api/chat";
    private static final String MODELS_ENDPOINT = "/api/tags";
    private static final String CREATE_ENDPOINT = "/api/create";
    
    private final HttpClient httpClient;
    private String currentModel = "gemma3:4b";
    
    // Konfigurierbare Parameter - OPTIMIERT FÜR PERFORMANCE
    private double temperature = 0.1;  // Sehr niedrig für schnellere, deterministischere Antworten
    private int maxTokens = 1024;      // Reduziert für schnellere Generierung
    private double topP = 0.5;         // Reduziert für fokussiertere Antworten
    private double repeatPenalty = 1.1; // Reduziert für weniger Rechenaufwand
    private int httpConnectTimeoutSeconds = 10;  // Schnellere Verbindung
    private int httpRequestTimeoutSeconds = 60;  // Reduziertes Timeout
    
    // Chat-Session-Management
    private Map<String, ChatSession> chatSessions = new HashMap<>();
    private String currentSessionId = "default";

    // Debug: Letzte Anfrage
    private volatile String lastRequestJson;
    private volatile String lastEndpoint; // "/api/generate" oder "/api/chat"
    private volatile String lastFullPrompt;
    private volatile String lastContext;
    
    /** Streaming-Handle zum Abbrechen laufender Streams */
    public static class StreamHandle {
        private volatile Closeable closeable;
        private volatile CompletableFuture<?> future;

        public void bind(Closeable c) { this.closeable = c; }
        public void bindFuture(CompletableFuture<?> f) { this.future = f; }
        public void cancel() {
            try { if (closeable != null) closeable.close(); } catch (Exception ignored) {}
            if (future != null) { future.cancel(true); }
        }
    }

    /**
     * Chat-Session für Kontext-Speicherung
     */
    public static class ChatSession {
        private String sessionId;
        private List<ChatMessage> messages;
        private Map<String, String> context; // Zusätzlicher Kontext (Charaktere, Setting, etc.)
        private long lastActivity;
        
        public ChatSession(String sessionId) {
            this.sessionId = sessionId;
            this.messages = new ArrayList<>();
            this.context = new HashMap<>();
            this.lastActivity = System.currentTimeMillis();
        }
        
        public void addMessage(String role, String content) {
            messages.add(new ChatMessage(role, content));
            lastActivity = System.currentTimeMillis();
        }
        
        public void addContext(String key, String value) {
            context.put(key, value);
        }
        
        public String getContext(String key) {
            return context.get(key);
        }
        
        public void clearContext() {
            context.clear();
        }
        
        public void clearMessages() {
            messages.clear();
        }
        
        public List<ChatMessage> getMessages() {
            return new ArrayList<>(messages);
        }
        
        public Map<String, String> getContext() {
            return new HashMap<>(context);
        }
        
        public long getLastActivity() {
            return lastActivity;
        }
        
        public boolean isStale(long maxAgeMs) {
            return System.currentTimeMillis() - lastActivity > maxAgeMs;
        }
    }
    
    /**
     * Chat-Nachricht
     */
    public static class ChatMessage {
        private String role; // "user" oder "assistant"
        private String content;
        private long timestamp;
        
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRole() { return role; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
    }
    
    public OllamaService() {
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(httpConnectTimeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        
        // Parameter aus der properties-Datei laden und in Instanzvariablen speichern
        loadParametersFromProperties();
    }
    
    /**
     * Lädt alle Parameter aus der parameters.properties und speichert sie in den Instanzvariablen
     */
    private void loadParametersFromProperties() {
        
        double temp = ResourceManager.getDoubleParameter("ollama.temperature", 0.3);
        int tokens = ResourceManager.getIntParameter("ollama.max_tokens", 2048);
        double topP = ResourceManager.getDoubleParameter("ollama.top_p", 0.7);
        double penalty = ResourceManager.getDoubleParameter("ollama.repeat_penalty", 1.3);
        int connectTimeout = ResourceManager.getIntParameter("ollama.http_connect_timeout_secs", 30);
        int requestTimeout = ResourceManager.getIntParameter("ollama.http_request_timeout_secs", 180);
        
        
        this.temperature = temp;
        this.maxTokens = tokens;
        this.topP = topP;
        this.repeatPenalty = penalty;
        this.httpConnectTimeoutSeconds = Math.max(1, connectTimeout);
        this.httpRequestTimeoutSeconds = Math.max(1, requestTimeout);
        
    }
    
    /**
     * Setzt das zu verwendende Modell
     */
    public void setModel(String model) {
        this.currentModel = model;
    }
    
    /**
     * Gibt die aktuelle Temperatur zurück
     */
    public double getTemperature() {
        return this.temperature;
    }
    
    /**
     * Gibt die maximale Anzahl von Tokens zurück
     */
    public int getMaxTokens() {
        return this.maxTokens;
    }
    
    /**
     * Gibt den Top-P Parameter zurück
     */
    public double getTopP() {
        return this.topP;
    }
    
    /**
     * Gibt die Repeat Penalty zurück
     */
    public double getRepeatPenalty() {
        return this.repeatPenalty;
    }
    
    /**
     * Setzt die Temperatur (Kreativität) - 0.0 = deterministisch, 1.0 = sehr kreativ
     */
    public void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(2.0, temperature));
        
        // In properties-Datei speichern
        ResourceManager.saveParameter("ollama.temperature", String.valueOf(this.temperature));
    }
    
    /**
     * Setzt die maximale Anzahl von Tokens
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = Math.max(1, Math.min(8192, maxTokens));
        
        // In properties-Datei speichern
        ResourceManager.saveParameter("ollama.max_tokens", String.valueOf(this.maxTokens));
    }
    
    /**
     * Setzt Top-P Parameter (Nukleus-Sampling)
     */
    public void setTopP(double topP) {
        this.topP = Math.max(0.0, Math.min(1.0, topP));
        
        // In properties-Datei speichern
        ResourceManager.saveParameter("ollama.top_p", String.valueOf(this.topP));
    }
    
    /**
     * Setzt Repeat Penalty (Wiederholungsstrafe)
     */
    public void setRepeatPenalty(double repeatPenalty) {
        this.repeatPenalty = Math.max(0.0, Math.min(2.0, repeatPenalty));
        
        // In properties-Datei speichern
        ResourceManager.saveParameter("ollama.repeat_penalty", String.valueOf(this.repeatPenalty));
    }
    
    /**
     * Gibt aktuelle Parameter zurück
     */
    public String getCurrentParameters() {
        return String.format("Modell: %s, Temperatur: %.2f, Max Tokens: %d, Top-P: %.2f, Repeat Penalty: %.2f",
                currentModel, temperature, maxTokens, topP, repeatPenalty);
    }
    
    /**
     * Setzt alle Parameter auf einmal und speichert sie in der properties-Datei
     */
    public void setAllParameters(double temperature, int maxTokens, double topP, double repeatPenalty) {
        this.temperature = Math.max(0.0, Math.min(2.0, temperature));
        this.maxTokens = Math.max(1, Math.min(8192, maxTokens));
        this.topP = Math.max(0.0, Math.min(1.0, topP));
        this.repeatPenalty = Math.max(0.0, Math.min(2.0, repeatPenalty));
        
        
        // Alle Parameter in properties-Datei speichern
        ResourceManager.saveOllamaParameters(this.temperature, this.maxTokens, this.topP, this.repeatPenalty);
    }
    
    /**
     * Generiert Text basierend auf einem Prompt mit aktuellen Parametern
     */
    public CompletableFuture<String> generateText(String prompt) {
        return generateText(prompt, null);
    }
    
    /**
     * Chat-Funktion mit optimierten Parametern für trainierte Modelle
     * Verwendet niedrigere Temperatur und höhere Repeat Penalty um Schleifen zu vermeiden
     */
    public CompletableFuture<String> chatWithTrainedModel(String message, String context) {
        // Parameter aus der properties-Datei laden
        double originalTemp = temperature;
        double originalTopP = topP;
        double originalRepeatPenalty = repeatPenalty;
        
        // Optimierte Parameter für trainierte Modelle (aus properties oder Standard)
        double trainedTemp = ResourceManager.getDoubleParameter("ollama.temperature", 0.3);
        double trainedTopP = ResourceManager.getDoubleParameter("ollama.top_p", 0.7);
        double trainedRepeatPenalty = ResourceManager.getDoubleParameter("ollama.repeat_penalty", 1.4);
        
        setTemperature(trainedTemp);
        setTopP(trainedTopP);
        setRepeatPenalty(trainedRepeatPenalty);
        
        CompletableFuture<String> result = chat(message, context);
        
        // Parameter zurücksetzen
        result = result.whenComplete((response, throwable) -> {
            setTemperature(originalTemp);
            setTopP(originalTopP);
            setRepeatPenalty(originalRepeatPenalty);
        });
        
        return result;
    }
    
    /**
     * Generiert Text mit Kontext und aktuellen Parametern
     */
    public CompletableFuture<String> generateText(String prompt, String context) {
        return generateText(prompt, context, this.temperature, this.maxTokens, this.topP, this.repeatPenalty);
    }
    
    /**
     * Generiert Text mit Kontext und spezifischen Parametern (ohne sie zu speichern)
     */
    public CompletableFuture<String> generateText(String prompt, String context, double temperature, int maxTokens, double topP, double repeatPenalty) {
        // Erstelle vollständigen Prompt mit Kontext
        String fullPrompt = prompt;
        if (context != null && !context.trim().isEmpty()) {
            String rules = "Regeln: Du erhältst zuerst 'Kontext', dann 'Anweisung'. Der Kontext dient NUR dem Verständnis. " +
                           "Gib NIEMALS den Kontext wieder. Antworte ausschließlich mit dem Ergebnis aus der Anweisung. " +
                           "Keine Erklärungen, kein Vor-/Nachtext, keine Anführungszeichen.";
            fullPrompt = rules + "\n\nKontext:\n" + context + "\n\nAnweisung:\n" + prompt;
        }
        
        // Korrekte JSON-Formatierung mit Escaping
        String escapedPrompt = escapeJson(fullPrompt);
        // Verwende Locale.US für konsistente Dezimalpunkt-Formatierung (z.B. 0.5 statt 0,5)
        String json = String.format(java.util.Locale.US,
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false,\"options\":{\"num_predict\":%d,\"temperature\":%.2f,\"top_p\":%.2f,\"repeat_penalty\":%.2f}}",
            currentModel, escapedPrompt, maxTokens, 
            temperature, topP, repeatPenalty
        );
        
        // Debug: Logge die verwendete Temperatur
        logger.debug("Ollama Request - Temperatur: {}", temperature);
        // Merken für UI
        this.lastEndpoint = GENERATE_ENDPOINT;
        this.lastRequestJson = json;
        this.lastFullPrompt = fullPrompt;
        this.lastContext = context;
        
        // Debug-Logging für JSON
        return sendRequest(GENERATE_ENDPOINT, json)
                .thenApply(this::parseGenerateResponse);
    }

    /**
     * Generate-API mit explizitem System-Prompt, der den Modelfile-SYSTEM garantiert überschreibt.
     * Nützlich für Spezialaufgaben (z. B. Audio-Tagging), bei denen ein trainiertes Modell
     * seinen eigenen System-Prompt nicht verwenden soll.
     *
     * @param systemPrompt System-Anweisungen (überschreiben Modelfile-SYSTEM)
     * @param prompt       User-Prompt (z. B. der zu verarbeitende Text)
     * @param numPredict   maximale Token-Anzahl (≤0 = Standard)
     * @param temperature  Temperatur (NaN = Standard)
     * @param topP         Top-P (NaN = Standard)
     * @param repeatPenalty Repeat-Penalty (NaN = Standard)
     * @return CompletableFuture mit der Modellantwort
     */
    public CompletableFuture<String> generateWithSystem(String systemPrompt, String prompt,
                                                         int numPredict, double temperature,
                                                         double topP, double repeatPenalty) {
        int tokens = numPredict > 0 ? numPredict : this.maxTokens;
        double temp = Double.isNaN(temperature) ? this.temperature : temperature;
        double tp = Double.isNaN(topP) ? this.topP : topP;
        double rp = Double.isNaN(repeatPenalty) ? this.repeatPenalty : repeatPenalty;

        String escapedSystem = escapeJson(systemPrompt);
        String escapedPrompt = escapeJson(prompt);

        String json = String.format(java.util.Locale.US,
            "{\"model\":\"%s\",\"system\":\"%s\",\"prompt\":\"%s\",\"stream\":false,\"options\":{\"num_predict\":%d,\"temperature\":%.2f,\"top_p\":%.2f,\"repeat_penalty\":%.2f}}",
            currentModel, escapedSystem, escapedPrompt, tokens, temp, tp, rp);

        this.lastEndpoint = GENERATE_ENDPOINT;
        this.lastRequestJson = json;
        this.lastFullPrompt = prompt;
        this.lastContext = null;

        return sendRequest(GENERATE_ENDPOINT, json)
                .thenApply(this::parseGenerateResponse);
    }

    // Für UI-Vorschau: baue JSON genau wie generateText, ohne Request abzuschicken
    public String buildGenerateJsonForPreview(String prompt, String context) {
        int maxTokens = this.maxTokens;
        double temperature = this.temperature;
        double topP = this.topP;
        double repeatPenalty = this.repeatPenalty;

        String fullPrompt = prompt;
        if (context != null && !context.trim().isEmpty()) {
            String rules = "Regeln: Du erhältst zuerst 'Kontext', dann 'Anweisung'. Der Kontext dient NUR dem Verständnis. " +
                           "Gib NIEMALS den Kontext wieder. Antworte ausschließlich mit dem Ergebnis aus der Anweisung. " +
                           "Keine Erklärungen, kein Vor-/Nachtext, keine Anführungszeichen.";
            fullPrompt = rules + "\n\nKontext:\n" + context + "\n\nAnweisung:\n" + prompt;
        }
        String escapedPrompt = escapeJson(fullPrompt);
        return String.format(
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false,\"options\":{\"num_predict\":%d,\"temperature\":%s,\"top_p\":%s,\"repeat_penalty\":%s}}",
            currentModel, escapedPrompt, maxTokens, 
            String.valueOf(temperature), String.valueOf(topP), String.valueOf(repeatPenalty)
        );
    }
    
    /**
     * Generiert Text mit spezifischen Parametern (Legacy-Methode)
     */
    public CompletableFuture<String> generateText(String prompt, int maxTokens, double temperature) {
        // Zusätzliche Parameter aus der properties-Datei laden
        double topP = ResourceManager.getDoubleParameter("ollama.top_p", 0.7);
        double repeatPenalty = ResourceManager.getDoubleParameter("ollama.repeat_penalty", 1.3);
        
        // Temporär Parameter setzen
        int originalMaxTokens = this.maxTokens;
        double originalTemperature = this.temperature;
        double originalTopP = this.topP;
        double originalRepeatPenalty = this.repeatPenalty;
        
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.repeatPenalty = repeatPenalty;
        
        CompletableFuture<String> result = generateText(prompt, null);
        
        // Parameter zurücksetzen
        this.maxTokens = originalMaxTokens;
        this.temperature = originalTemperature;
        this.topP = originalTopP;
        this.repeatPenalty = originalRepeatPenalty;
        
        return result;
    }
    
    /**
     * Chat-Funktion für kreatives Schreiben
     */
    public CompletableFuture<String> chat(String message, String context) {
        // Parameter aus den Instanzvariablen verwenden (die bereits aus properties geladen wurden)
        int maxTokens = this.maxTokens;
        double temperature = this.temperature;
        double topP = this.topP;
        double repeatPenalty = this.repeatPenalty;
        
        String systemPrompt = "Du bist ein kreativer Schreibassistent. Antworte auf Deutsch und sei hilfreich für das kreative Schreiben.";
        
        // Erstelle vollständigen Kontext
        String fullContext = context;
        if (context != null && !context.trim().isEmpty()) {
            fullContext = "Kontext: " + context + "\n\nFrage: " + message;
        } else {
            fullContext = message;
        }
        
        // Korrekte JSON-Formatierung mit Escaping
        String escapedSystemPrompt = escapeJson(systemPrompt);
        String escapedUserContent = escapeJson(fullContext);
        
        String json = String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false,\"options\":{\"num_predict\":%d,\"temperature\":%s,\"top_p\":%s,\"repeat_penalty\":%s}}",
            currentModel, escapedSystemPrompt, escapedUserContent, maxTokens, 
            String.valueOf(temperature), String.valueOf(topP), String.valueOf(repeatPenalty)
        );
        this.lastEndpoint = CHAT_ENDPOINT;
        this.lastRequestJson = json;
        this.lastFullPrompt = fullContext;
        this.lastContext = context;
        
        // Debug-Logging für JSON
        
        return sendRequest(CHAT_ENDPOINT, json)
                .thenApply(this::parseChatResponse);
    }

    /**
     * Chat mit benutzerdefiniertem System-Prompt (z. B. für Audio-Tagging oder andere Spezialaufgaben).
     * Verwendet das aktuell konfigurierte Modell und die Parameter-Einstellungen.
     *
     * @param systemPrompt der System-Prompt (Anweisungen für das Modell)
     * @param userMessage die Benutzernachricht (z. B. der zu taggende Text)
     * @param numPredict maximale Token-Anzahl für die Antwort (≤0 = Standard aus Konfiguration)
     * @return CompletableFuture mit der Modellantwort
     */
    public CompletableFuture<String> chatWithSystemPrompt(String systemPrompt, String userMessage, int numPredict) {
        int tokens = numPredict > 0 ? numPredict : this.maxTokens;
        double temp = this.temperature;
        double tp = this.topP;
        double rp = this.repeatPenalty;

        String escapedSys = escapeJson(systemPrompt);
        String escapedUser = escapeJson(userMessage);

        String json = String.format(java.util.Locale.US,
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false,\"options\":{\"num_predict\":%d,\"temperature\":%.2f,\"top_p\":%.2f,\"repeat_penalty\":%.2f}}",
            currentModel, escapedSys, escapedUser, tokens, temp, tp, rp);

        this.lastEndpoint = CHAT_ENDPOINT;
        this.lastRequestJson = json;
        this.lastFullPrompt = userMessage;
        this.lastContext = null;

        return sendRequest(CHAT_ENDPOINT, json)
                .thenApply(this::parseChatResponse);
    }

    /**
     * Sendet eine einzelne User-Nachricht an die Chat-API (ohne System-Prompt),
     * mit expliziten Parametern. Nützlich wenn das Modell einen eigenen System-Prompt
     * im Modelfile hat und der Chat-System-Prompt ignoriert wird.
     *
     * @param userMessage die vollständige Nachricht (inkl. aller Anweisungen)
     * @param numPredict maximale Token-Anzahl (≤0 = Standard)
     * @param temperature Temperatur (NaN = Standard aus Konfiguration)
     * @param topP Top-P (NaN = Standard)
     * @param repeatPenalty Repeat-Penalty (NaN = Standard)
     * @return CompletableFuture mit der Modellantwort
     */
    public CompletableFuture<String> chatUserOnly(String userMessage, int numPredict,
                                                   double temperature, double topP, double repeatPenalty) {
        int tokens = numPredict > 0 ? numPredict : this.maxTokens;
        double temp = Double.isNaN(temperature) ? this.temperature : temperature;
        double tp = Double.isNaN(topP) ? this.topP : topP;
        double rp = Double.isNaN(repeatPenalty) ? this.repeatPenalty : repeatPenalty;

        String escapedUser = escapeJson(userMessage);

        String json = String.format(java.util.Locale.US,
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false,\"options\":{\"num_predict\":%d,\"temperature\":%.2f,\"top_p\":%.2f,\"repeat_penalty\":%.2f}}",
            currentModel, escapedUser, tokens, temp, tp, rp);

        this.lastEndpoint = CHAT_ENDPOINT;
        this.lastRequestJson = json;
        this.lastFullPrompt = userMessage;
        this.lastContext = null;

        return sendRequest(CHAT_ENDPOINT, json)
                .thenApply(this::parseChatResponse);
    }

    // Für UI-Vorschau: baut das Chat-JSON ohne es zu senden
    public String buildChatJsonForPreview(String message, String context) {
        int maxTokens = this.maxTokens;
        double temperature = this.temperature;
        double topP = this.topP;
        double repeatPenalty = this.repeatPenalty;

        String systemPrompt = "Du bist ein kreativer Schreibassistent. Antworte auf Deutsch und sei hilfreich für das kreative Schreiben.";

        String fullContext = context;
        if (context != null && !context.trim().isEmpty()) {
            fullContext = "Kontext: " + context + "\n\nFrage: " + message;
        } else {
            fullContext = message;
        }

        String escapedSystemPrompt = escapeJson(systemPrompt);
        String escapedUserContent = escapeJson(fullContext);

        return String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false,\"options\":{\"num_predict\":%d,\"temperature\":%s,\"top_p\":%s,\"repeat_penalty\":%s}}",
            currentModel, escapedSystemPrompt, escapedUserContent, maxTokens,
            String.valueOf(temperature), String.valueOf(topP), String.valueOf(repeatPenalty)
        );
    }

    // Getter für Debug-Anzeige in UI
    public String getLastRequestJson() { return lastRequestJson; }
    public String getLastEndpoint() { return lastEndpoint; }
    public String getLastFullPrompt() { return lastFullPrompt; }
    public String getLastContext() { return lastContext; }
    
    /**
     * Spezielle Funktionen für kreatives Schreiben
     */
    
    /**
     * Generiert Dialoge für Charaktere
     */
    public CompletableFuture<String> generateDialogue(String character, String situation, String emotion) {
        String prompt = String.format("""
            Schreibe einen authentischen Dialog für den Charakter '%s' in folgender Situation:
            %s
            
            Emotion des Charakters: %s
            
            Schreibe nur den Dialog, ohne Erzählertext. Verwende natürliche, deutsche Sprache.
            """, character, situation, emotion);
        
        // Temporär höhere Temperatur für kreativere Dialoge
        double originalTemp = temperature;
        setTemperature(0.8);
        
        CompletableFuture<String> result = generateText(prompt, null);
        
        // Temperatur zurücksetzen
        setTemperature(originalTemp);
        
        return result;
    }

    /**
     * Streaming-Variante: sendet stream:true und liefert Chunks über Callback. Gibt ein Handle zurück, mit dem abgebrochen werden kann.
     */
    public StreamHandle generateTextStreaming(String prompt, String context,
                                              Consumer<String> onChunk,
                                              Runnable onDone,
                                              Consumer<Throwable> onError) {
        // Vollständigen Prompt wie in generateText aufbauen
        String fullPrompt = prompt;
        if (context != null && !context.trim().isEmpty()) {
            String rules = "Regeln: Du erhältst zuerst 'Kontext', dann 'Anweisung'. Der Kontext dient NUR dem Verständnis. " +
                           "Gib NIEMALS den Kontext wieder. Antworte ausschließlich mit dem Ergebnis aus der Anweisung. " +
                           "Keine Erklärungen, kein Vor-/Nachtext, keine Anführungszeichen.";
            fullPrompt = rules + "\n\nKontext:\n" + context + "\n\nAnweisung:\n" + prompt;
        }

        int maxTokens = this.maxTokens;
        double temperature = this.temperature;
        double topP = this.topP;
        double repeatPenalty = this.repeatPenalty;

        String escapedPrompt = escapeJson(fullPrompt);
        String json = String.format(java.util.Locale.US,
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":true,\"options\":{\"num_predict\":%d,\"temperature\":%s,\"top_p\":%s,\"repeat_penalty\":%s,\"repeat_last_n\":512,\"penalize_newline\":true,\"num_gpu\":-1}}",
            currentModel, escapedPrompt, maxTokens,
            String.valueOf(temperature), String.valueOf(topP), String.valueOf(repeatPenalty)
        );

        this.lastEndpoint = GENERATE_ENDPOINT;
        this.lastRequestJson = json;
        this.lastFullPrompt = fullPrompt;
        this.lastContext = context;

        StreamHandle handle = new StreamHandle();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL + GENERATE_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(httpRequestTimeoutSeconds))
                    .build();

            CompletableFuture<HttpResponse<InputStream>> fut = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            handle.bindFuture(fut);
            fut.whenComplete((resp, err) -> {
                if (err != null) {
                    if (onError != null) onError.accept(err);
                    return;
                }
                if (resp.statusCode() != 200) {
                    if (onError != null) onError.accept(new RuntimeException("HTTP " + resp.statusCode()));
                    return;
                }
                InputStream in = resp.body();
                handle.bind(in);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        if (line.contains("\"response\":")) {
                            String chunk = extractJsonValue(line, "response");
                            if (chunk != null && !chunk.isEmpty() && onChunk != null) {
                                onChunk.accept(chunk);
                            }
                        }
                        if (line.contains("\"done\":true")) {
                            if (onDone != null) onDone.run();
                            break;
                        }
                    }
                } catch (Exception ex) {
                    if (onError != null) onError.accept(ex);
                }
            });
        } catch (Exception e) {
            if (onError != null) onError.accept(e);
        }

        return handle;
    }

    // Einfache JSON-Extraktion für Streaming-Zeilen: zieht den Stringwert eines Schlüssels heraus
    private String extractJsonValue(String json, String key) {
        if (json == null || key == null) return null;
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return null;
        int i = start + needle.length();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (escaped) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(c); break;
                }
                escaped = false;
                continue;
            }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') break; // Ende
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Generiert Dialoge für Charaktere (mit Kontext)
     */
    public CompletableFuture<String> generateDialogue(String character, String situation, String emotion, String context) {
        String prompt = String.format("""
            Schreibe einen authentischen Dialog für den Charakter '%s' in folgender Situation:
            %s
            
            Emotion des Charakters: %s
            
            Schreibe nur den Dialog, ohne Erzählertext. Verwende natürliche, deutsche Sprache.
            """, character, situation, emotion);
        
        double originalTemp = temperature;
        setTemperature(0.8);
        
        CompletableFuture<String> result = generateText(prompt, context);
        
        setTemperature(originalTemp);
        return result;
    }
    
    /**
     * Erweitert Beschreibungen
     */
    public CompletableFuture<String> expandDescription(String shortDescription, String style) {
        String prompt = String.format("""
            Erweitere diese kurze Beschreibung im Stil '%s':
            
            %s
            
            Schreibe eine detaillierte, atmosphärische Beschreibung (max. 3 Sätze).
            """, style, shortDescription);
        
        // Temporär niedrigere Temperatur für konsistentere Beschreibungen
        double originalTemp = temperature;
        setTemperature(0.6);
        
        CompletableFuture<String> result = generateText(prompt, null);
        
        // Temperatur zurücksetzen
        setTemperature(originalTemp);
        
        return result;
    }

    /**
     * Erweitert Beschreibungen (mit Kontext)
     */
    public CompletableFuture<String> expandDescription(String shortDescription, String style, String context) {
        String prompt = String.format("""
            Erweitere diese kurze Beschreibung im Stil '%s':
            
            %s
            
            Schreibe eine detaillierte, atmosphärische Beschreibung (max. 3 Sätze).
            """, style, shortDescription);
        
        double originalTemp = temperature;
        setTemperature(0.6);
        CompletableFuture<String> result = generateText(prompt, context);
        setTemperature(originalTemp);
        return result;
    }
    
    /**
     * Entwickelt Plot-Ideen
     */
    public CompletableFuture<String> developPlotIdeas(String genre, String basicIdea) {
        String prompt = String.format("""
            Entwickle Plot-Ideen für eine %s-Geschichte basierend auf dieser Grundidee:
            %s
            
            Gib 3-5 konkrete Plot-Entwicklungen an, die die Geschichte vorantreiben könnten.
            """, genre, basicIdea);
        
        // Temporär höhere Temperatur für kreativere Plot-Ideen
        double originalTemp = temperature;
        setTemperature(0.9);
        
        CompletableFuture<String> result = generateText(prompt, null);
        
        // Temperatur zurücksetzen
        setTemperature(originalTemp);
        
        return result;
    }

    /**
     * Entwickelt Plot-Ideen (mit Kontext)
     */
    public CompletableFuture<String> developPlotIdeas(String genre, String basicIdea, String context) {
        String prompt = String.format("""
            Entwickle Plot-Ideen für eine %s-Geschichte basierend auf dieser Grundidee:
            %s
            
            Gib 3-5 konkrete Plot-Entwicklungen an, die die Geschichte vorantreiben könnten.
            """, genre, basicIdea);
        
        double originalTemp = temperature;
        setTemperature(0.9);
        CompletableFuture<String> result = generateText(prompt, context);
        setTemperature(originalTemp);
        return result;
    }
    
    /**
     * Entwickelt Charaktere
     */
    public CompletableFuture<String> developCharacter(String characterName, String basicTraits) {
        String prompt = String.format("""
            Entwickle den Charakter '%s' weiter:
            
            Grundmerkmale: %s
            
            Erstelle ein detailliertes Charakterprofil mit:
            - Hintergrund/Geschichte
            - Motivationen und Ziele
            - Stärken und Schwächen
            - Beziehungen zu anderen Charakteren
            """, characterName, basicTraits);
        
        // Temporär mittlere Temperatur für ausgewogene Charakterentwicklung
        double originalTemp = temperature;
        setTemperature(0.8);
        
        CompletableFuture<String> result = generateText(prompt, null);
        
        // Temperatur zurücksetzen
        setTemperature(originalTemp);
        
        return result;
    }

    /**
     * Entwickelt Charaktere (mit Kontext)
     */
    public CompletableFuture<String> developCharacter(String characterName, String basicTraits, String context) {
        String prompt = String.format("""
            Entwickle den Charakter '%s' weiter:
            
            Grundmerkmale: %s
            
            Erstelle ein detailliertes Charakterprofil mit:
            - Hintergrund/Geschichte
            - Motivationen und Ziele
            - Stärken und Schwächen
            - Beziehungen zu anderen Charakteren
            """, characterName, basicTraits);
        
        double originalTemp = temperature;
        setTemperature(0.8);
        CompletableFuture<String> result = generateText(prompt, context);
        setTemperature(originalTemp);
        return result;
    }
    
    /**
     * Analysiert den Schreibstil eines Textes
     */
    public CompletableFuture<String> analyzeWritingStyle(String text) {
        String prompt = String.format("""
            DU BIST EIN EXPERTE FÜR SCHREIBSTIL-ANALYSE!
            
            AUFGABE: Analysiere NUR den Schreibstil, NICHT den Inhalt!
            
            Text zur Analyse:
            %s
            
            FOKUSSIERE DICH AUSSCHLIESSLICH AUF:
            1. Satzlänge (kurz/mittel/lang) und Satzstruktur (einfach/komplex)
            2. Wortwahl (einfach/gehoben/technisch/emotional)
            3. Erzählperspektive (Ich-Erzähler/Er-Erzähler/Allwissend)
            4. Tonfall (ernst/humorvoll/dramatisch/nüchtern)
            5. Atmosphäre (düster/hell/spannend/ruhig)
            6. Besondere stilistische Merkmale (Metaphern, Wiederholungen, etc.)
            7. Vergleich mit bekannten Autoren oder Stilen
            
            VERBOTEN: 
            - Keine Inhaltszusammenfassung!
            - Keine Handlungsanalyse!
            - Keine Charakterbeschreibung!
            
            Antworte nur mit der Stil-Analyse!
            """, text);
        
        // Temporär niedrigere Temperatur für präzisere Analyse
        double originalTemp = temperature;
        setTemperature(0.5);
        
        CompletableFuture<String> result = generateText(prompt, null);
        
        // Temperatur zurücksetzen
        setTemperature(originalTemp);
        
        return result;
    }

    /**
     * Analysiert den Schreibstil eines Textes (mit Kontext)
     */
    public CompletableFuture<String> analyzeWritingStyle(String text, String context) {
        String prompt = String.format("""
            DU BIST EIN EXPERTE FÜR SCHREIBSTIL-ANALYSE!
            
            AUFGABE: Analysiere NUR den Schreibstil, NICHT den Inhalt!
            
            Text zur Analyse:
            %s
            
            FOKUSSIERE DICH AUSSCHLIESSLICH AUF:
            1. Satzlänge (kurz/mittel/lang) und Satzstruktur (einfach/komplex)
            2. Wortwahl (einfach/gehoben/technisch/emotional)
            3. Erzählperspektive (Ich-Erzähler/Er-Erzähler/Allwissend)
            4. Tonfall (ernst/humorvoll/dramatisch/nüchtern)
            5. Atmosphäre (düster/hell/spannend/ruhig)
            6. Besondere stilistische Merkmale (Metaphern, Wiederholungen, etc.)
            7. Vergleich mit bekannten Autoren oder Stilen
            
            VERBOTEN: 
            - Keine Inhaltszusammenfassung!
            - Keine Handlungsanalyse!
            - Keine Charakterbeschreibung!
            
            Antworte nur mit der Stil-Analyse!
            """, text);
        
        double originalTemp = temperature;
        setTemperature(0.5);
        CompletableFuture<String> result = generateText(prompt, context);
        setTemperature(originalTemp);
        return result;
    }
    
    /**
     * Umschreibt Text nach verschiedenen Kriterien
     */
    public CompletableFuture<String> rewriteText(String originalText, String rewriteType, String additionalInstructions) {
        String prompt = String.format("""
            DU BIST EIN EXPERTE FÜR TEXT-UMFORMULIERUNG!
            
            ORIGINALTEXT (nur dieser Abschnitt darf verändert werden):
            %s
            
            UMSCHREIBUNGSART: %s
            
            ZUSÄTZLICHE ANWEISUNGEN: %s
            
            AUFGABE: Umschreibe NUR den oben angegebenen ORIGINALTEXT entsprechend der gewählten Art. Behalte die Grundaussage bei, aber ändere die Darstellung. Nutze den Kontext nur zum Verständnis, nicht zum Ergänzen.
            
            WICHTIG: 
            - Verwende natürliche, flüssige deutsche Sprache
            - Behalte den ursprünglichen Ton und Stil bei
            - Ändere nur die Darstellung, nicht die Kernaussage
            - Antworte ausschließlich mit der umgeschriebenen Version des ORIGINALTEXTES – ohne Ergänzungen vor oder nach dem Text, keine Kontextzitate.
            """, originalText, rewriteType, additionalInstructions.isEmpty() ? "Keine" : additionalInstructions);
        
        // Temporär mittlere Temperatur für ausgewogene Umschreibung
        double originalTemp = temperature;
        setTemperature(0.7);
        
        CompletableFuture<String> result = generateText(prompt, null);
        
        // Temperatur zurücksetzen
        setTemperature(originalTemp);
        
        return result;
    }

    /**
     * Umschreibt Text nach verschiedenen Kriterien (mit Kontext)
     */
    public CompletableFuture<String> rewriteText(String originalText, String rewriteType, String additionalInstructions, String context) {
        String prompt = String.format("""
            DU BIST EIN EXPERTE FÜR TEXT-UMFORMULIERUNG!
            
            ORIGINALTEXT (nur dieser Abschnitt darf verändert werden):
            %s
            
            UMSCHREIBUNGSART: %s
            
            ZUSÄTZLICHE ANWEISUNGEN: %s
            
            AUFGABE: Umschreibe NUR den oben angegebenen ORIGINALTEXT entsprechend der gewählten Art. Behalte die Grundaussage bei, aber ändere die Darstellung. Nutze den Kontext nur zum Verständnis, nicht zum Ergänzen.
            
            WICHTIG: 
            - Verwende natürliche, flüssige deutsche Sprache
            - Behalte den ursprünglichen Ton und Stil bei
            - Ändere nur die Darstellung, nicht die Kernaussage
            - Antworte ausschließlich mit der umgeschriebenen Version des ORIGINALTEXTES – ohne Ergänzungen vor oder nach dem Text, keine Kontextzitate.
            """, originalText, rewriteType, additionalInstructions.isEmpty() ? "Keine" : additionalInstructions);
        
        double originalTemp = temperature;
        setTemperature(0.7);
        CompletableFuture<String> result = generateText(prompt, context);
        setTemperature(originalTemp);
        return result;
    }
    
    /**
     * Überprüft, ob Ollama läuft
     */
        public CompletableFuture<Boolean> isOllamaRunning() {
        return sendGetRequest("/api/tags")
                .thenApply(response -> !response.contains("error"))
                .exceptionally(ex -> {
                    logger.warn("Ollama-Server nicht erreichbar: {}", ex.getMessage());
                    return false;
                });
    }
    
    /**
     * Ruft die verfügbaren Modelle von Ollama ab
     */
    public CompletableFuture<String[]> getAvailableModels() {
        return sendGetRequest(MODELS_ENDPOINT)
                .thenApply(this::parseModelsResponse)
                .exceptionally(ex -> {
                    logger.warn("Fehler beim Abrufen der Modelle: {}", ex.getMessage());
                    ex.printStackTrace();
                    return new String[]{"mistral:7b-instruct"}; // Fallback
                });
    }
    
    /**
     * Parst die Antwort der Models-API
     */
    private String[] parseModelsResponse(String response) {
        try {
            
            // Einfache JSON-Parsing für "models" Array
            // Erwartetes Format: {"models":[{"name":"model1"},{"name":"model2"}]}
            String[] models = new String[0];
            
            if (response != null && response.contains("\"models\"")) {
                // Extrahiere alle "name" Felder
                String[] parts = response.split("\"name\":\"");
                models = new String[parts.length - 1];
                
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i];
                    int endQuote = part.indexOf("\"");
                    if (endQuote > 0) {
                        models[i - 1] = part.substring(0, endQuote);
                    }
                }
            } else {
                logger.warn("Keine 'models' in der Response gefunden oder Response ist null");
            }
            
            return models;
        } catch (Exception e) {
            logger.warn("Fehler beim Parsen der Models-Antwort: {}", e.getMessage());
            e.printStackTrace();
            return new String[]{"mistral:7b-instruct"}; // Fallback
        }
    }
    
    /**
     * Sendet GET-Request an Ollama
     */
    private CompletableFuture<String> sendGetRequest(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_BASE_URL + endpoint))
                        .GET()
                        .timeout(Duration.ofSeconds(httpRequestTimeoutSeconds))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                logger.error("Fehler beim Ollama-GET-Request", e);
                throw new RuntimeException("Ollama-GET-Request fehlgeschlagen: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Sendet POST-Request an Ollama
     */
    private CompletableFuture<String> sendRequest(String endpoint, String requestBody) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_BASE_URL + endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(httpRequestTimeoutSeconds))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                logger.error("Fehler beim Ollama-Request", e);
                throw new RuntimeException("Ollama-Request fehlgeschlagen: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Parst die Antwort der Generate-API
     */
    private String parseGenerateResponse(String response) {
        try {
            // Robustes JSON-Parsing mit besserer Fehlerbehandlung
            if (response == null || response.isEmpty()) {
                return "Leere Antwort erhalten";
            }
            
            // Suche nach dem "response" Feld
            int responseStart = response.indexOf("\"response\":\"");
            if (responseStart == -1) {
                return "Kein 'response' Feld in der Antwort gefunden";
            }
            
            // Start-Position nach dem "response":" Teil
            int contentStart = responseStart + 12;
            
            // Suche das Ende des Response-Strings
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            
            for (int i = contentStart; i < response.length(); i++) {
                char c = response.charAt(i);
                
                if (escaped) {
                    switch (c) {
                        case '\\':
                            result.append('\\');
                            break;
                        case '"':
                            result.append('"');
                            break;
                        case 'n':
                            result.append('\n');
                            break;
                        case 'r':
                            result.append('\r');
                            break;
                        case 't':
                            result.append('\t');
                            break;
                        default:
                            result.append('\\').append(c);
                            break;
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    // Ende des Response-Strings gefunden
                    break;
                } else {
                    result.append(c);
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            logger.warn("Fehler beim Parsen der Generate-Antwort: {}", e.getMessage());
            return "Fehler beim Parsen der Antwort: " + e.getMessage();
        }
    }
    
    /**
     * Parst die Antwort der Chat-API
     */
    private String parseChatResponse(String response) {
        try {
            // Robustes JSON-Parsing mit besserer Fehlerbehandlung
            if (response == null || response.isEmpty()) {
                return "Leere Antwort erhalten";
            }
            
            // Suche nach dem "content" Feld
            int contentStart = response.indexOf("\"content\":\"");
            if (contentStart == -1) {
                return "Kein 'content' Feld in der Antwort gefunden";
            }
            
            // Start-Position nach dem "content":" Teil
            int textStart = contentStart + 11;
            
            // Suche das Ende des Content-Strings
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            
            for (int i = textStart; i < response.length(); i++) {
                char c = response.charAt(i);
                
                if (escaped) {
                    switch (c) {
                        case '\\':
                            result.append('\\');
                            break;
                        case '"':
                            result.append('"');
                            break;
                        case 'n':
                            result.append('\n');
                            break;
                        case 'r':
                            result.append('\r');
                            break;
                        case 't':
                            result.append('\t');
                            break;
                        default:
                            result.append('\\').append(c);
                            break;
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    // Ende des Content-Strings gefunden
                    break;
                } else {
                    result.append(c);
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            logger.warn("Fehler beim Parsen der Chat-Antwort: {}", e.getMessage());
            return "Fehler beim Parsen der Chat-Antwort: " + e.getMessage();
        }
    }
    
    /**
     * Escaped JSON-Strings
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Startet das Training eines neuen Modells mit den eigenen Romanen
     */
    public CompletableFuture<String> startModelTraining(String modelName, String baseModel, String trainingData, int epochs, String additionalInstructions) {
        // Epochen-Parameter wird ignoriert, da Ollama Modelfiles keine Training-Parameter unterstützen
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Erstelle Modelfile für das Training
                String modelfile = createModelfile(modelName, baseModel, trainingData, epochs, additionalInstructions);
                
                // Debug: Zeige das Modelfile
                
                // Erstelle temporäre Modelfile-Datei
                String modelfilePath = createTempModelfile(modelfile);
                
                // Führe ollama create Kommando aus
                String response = executeOllamaCreate(modelName, modelfilePath);
                
                // Lösche temporäre Datei
                deleteTempModelfile(modelfilePath);
                
                                 return "✅ Modell-Training erfolgreich gestartet!\n\n" +
                        "📊 Details:\n" +
                        "- Modell-Name: " + modelName + "\n" +
                        "- Basis-Modell: " + baseModel + "\n" +
                        "- Training-Daten: " + trainingData.length() + " Zeichen\n\n" +
                        "🚀 Das Modell wird jetzt erstellt...\n" +
                        "⏱️  Dies dauert normalerweise nur wenige Sekunden.\n\n" +
                        "📝 Antwort vom Server:\n" + response;
                
            } catch (Exception e) {
                logger.error("Fehler beim Modell-Training", e);
                throw new RuntimeException("Modell-Training fehlgeschlagen: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Stoppt das laufende Modell-Training
     */
    public CompletableFuture<String> stopModelTraining() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Hier könnte man einen Stop-Endpoint implementieren
                // Für jetzt geben wir nur eine Bestätigung zurück
                return "✅ Training-Stop-Signal gesendet.\n" +
                       "⚠️  Hinweis: Das Training läuft möglicherweise noch weiter.\n" +
                       "💡 Verwende 'ollama list' um den Status zu prüfen.";
            } catch (Exception e) {
                logger.error("Fehler beim Stoppen des Trainings", e);
                throw new RuntimeException("Training-Stop fehlgeschlagen: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Löscht ein Modell von Ollama
     */
    public CompletableFuture<Boolean> deleteModel(String modelName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("ollama", "rm", modelName);
                processBuilder.redirectErrorStream(true);
                
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    return true;
                } else {
                    logger.warn("Fehler beim Löschen des Modells: {}", modelName);
                    return false;
                }
            } catch (Exception e) {
                logger.warn("Exception beim Löschen des Modells: {}", e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Installiert ein neues Modell von Ollama
     */
    public CompletableFuture<String> installModel(String modelName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("ollama", "pull", modelName);
                processBuilder.redirectErrorStream(true);
                
                Process process = processBuilder.start();
                
                // Sammle die Ausgabe
                StringBuilder output = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    return "✅ Modell erfolgreich installiert: " + modelName;
                } else {
                    logger.warn("Fehler beim Installieren des Modells: {}", modelName);
                    return "❌ Fehler beim Installieren: " + output.toString();
                }
            } catch (Exception e) {
                logger.warn("Exception beim Installieren des Modells: {}", e.getMessage());
                return "❌ Exception: " + e.getMessage();
            }
        });
    }
    
    /**
     * Gibt eine Liste empfohlener Modelle für kreatives Schreiben zurück
     * Kombiniert statische Empfehlungen mit dynamisch geladenen verfügbaren Modellen
     */
    public String[] getRecommendedCreativeWritingModels() {
        // Statische Empfehlungen (bewährte Modelle für kreatives Schreiben)
        String[] staticRecommendations = {
            "llama2:7b",             // Sehr gut für kreatives Schreiben, ausgewogen
            "llama2:13b",            // Bessere Qualität, aber langsamer
            "llama2:70b",            // Beste Qualität, aber sehr langsam
            "qwen2.5:7b",            // Sehr gut für kreatives Schreiben
            "qwen2.5:14b",           // Bessere Qualität
            "phi:2.7b",              // Schnell und gut für kreative Texte
            "phi:3.5:3.8b",          // Schnell und gut für kreative Texte
            "mistral:7b-instruct",   // Klassiker für kreatives Schreiben
            "gemma3:4b",             // Schnell und gut strukturiert
            "codellama:7b"           // Gut für strukturierte Texte
        };
        
        // Dynamisch verfügbare Modelle laden
        String[] availableModels = loadAvailableModelsSync();
        
        // Kombiniere beide Listen, entferne Duplikate
        Set<String> combinedModels = new LinkedHashSet<>();
        
        // Zuerst die statischen Empfehlungen (haben Priorität)
        for (String model : staticRecommendations) {
            combinedModels.add(model);
        }
        
        // Dann die verfügbaren Modelle hinzufügen
        for (String model : availableModels) {
            combinedModels.add(model);
        }
        
        return combinedModels.toArray(new String[0]);
    }
    
    /**
     * Lädt die aktuell verfügbaren Modelle von Ollama (synchron)
     */
    private String[] loadAvailableModelsSync() {
        try {
            // Verwende die bestehende getAvailableModels Methode
            CompletableFuture<String[]> future = getAvailableModels();
            return future.get(5, TimeUnit.SECONDS); // 5 Sekunden Timeout
        } catch (Exception e) {
            logger.warn("Fehler beim Laden verfügbarer Modelle: {}", e.getMessage());
            return new String[0];
        }
    }
    
    /**
     * Erstellt ein Modelfile für das Training
     */
    private String createModelfile(String modelName, String baseModel, String trainingData, int epochs, String additionalInstructions) {
        StringBuilder modelfile = new StringBuilder();
        
        // Basis-Modell (erforderlich) - muss die erste Zeile sein
        modelfile.append("FROM ").append(baseModel).append("\n\n");
        
        // Inferenz-Parameter (nicht Training-Parameter)
        modelfile.append("PARAMETER stop \"<|endoftext|>\"\n\n");
        
        // System-Prompt für den Stil
        modelfile.append("SYSTEM \"\"\"\n");
        modelfile.append("Du bist ein KI-Assistent, der im Stil der folgenden Romane schreibt.\n");
        modelfile.append("Dein Schreibstil ist geprägt von:\n");
        modelfile.append("- Detaillierten Beschreibungen und atmosphärischen Szenen\n");
        modelfile.append("- Charakterentwicklung und emotionaler Tiefe\n");
        modelfile.append("- Spannenden Dialogen und Handlungsverläufen\n");
        modelfile.append("- Konsistenter Welt und Setting\n\n");
        
        if (!additionalInstructions.isEmpty()) {
            modelfile.append("Zusätzliche Anweisungen: ").append(additionalInstructions).append("\n\n");
        }
        
        modelfile.append("Antworte immer in diesem Stil und halte dich an die etablierten Charaktere und die Welt.\n");
        modelfile.append("\"\"\"\n\n");
        
        // Template mit Training-Daten
        modelfile.append("TEMPLATE \"\"\"\n");
        modelfile.append("{{ if .System }}{{ .System }}{{ end }}\n\n");
        modelfile.append("Training-Daten (Schreibstil-Beispiele):\n");
        modelfile.append(trainingData).append("\n\n");
        modelfile.append("{{ .Prompt }}\n");
        modelfile.append("\"\"\"\n");
        
        return modelfile.toString();
    }
     
     /**
      * Erstellt eine temporäre Modelfile-Datei
      */
     private String createTempModelfile(String modelfileContent) throws IOException {
         Path tempFile = Files.createTempFile("ollama-modelfile-", ".modelfile");
         Files.write(tempFile, modelfileContent.getBytes());
         return tempFile.toString();
     }
     
     /**
      * Führt das ollama create Kommando aus
      */
     private String executeOllamaCreate(String modelName, String modelfilePath) throws IOException, InterruptedException {
         ProcessBuilder pb = new ProcessBuilder("ollama", "create", modelName, "-f", modelfilePath);
         pb.redirectErrorStream(true);
         
         Process process = pb.start();
         
         // Lese die Ausgabe
         String output = new String(process.getInputStream().readAllBytes());
         
         int exitCode = process.waitFor();
         
         if (exitCode != 0) {
             throw new IOException("Ollama create fehlgeschlagen mit Exit-Code " + exitCode + ": " + output);
         }
         
         return output;
     }
     
     /**
      * Löscht eine temporäre Modelfile-Datei
      */
     private void deleteTempModelfile(String modelfilePath) {
         try {
             Files.deleteIfExists(Path.of(modelfilePath));
         } catch (IOException e) {
             logger.warn("Konnte temporäre Modelfile-Datei nicht löschen", e);
         }
     }

    /**
     * Erstellt oder lädt eine Chat-Session
     */
    public ChatSession getOrCreateSession(String sessionId) {
        if (!chatSessions.containsKey(sessionId)) {
            chatSessions.put(sessionId, new ChatSession(sessionId));
        }
        return chatSessions.get(sessionId);
    }
    
    /**
     * Setzt die aktuelle Session
     */
    public void setCurrentSession(String sessionId) {
        this.currentSessionId = sessionId;
        getOrCreateSession(sessionId);
    }
    
    /**
     * Gibt die aktuelle Session zurück
     */
    public ChatSession getCurrentSession() {
        return getOrCreateSession(currentSessionId);
    }
    
    /**
     * Fügt Kontext zur aktuellen Session hinzu
     */
    public void addContext(String key, String value) {
        ChatSession session = getCurrentSession();
        session.addContext(key, value);
    }
    
    /**
     * Löscht eine Chat-Session
     */
    public void deleteSession(String sessionId) {
        chatSessions.remove(sessionId);
    }
    
    /**
     * Löscht alle Sessions
     */
    public void clearAllSessions() {
        chatSessions.clear();
    }
    
    /**
     * Gibt alle Session-IDs zurück
     */
    public Set<String> getSessionIds() {
        return new LinkedHashSet<>(chatSessions.keySet());
    }
    
    /**
     * Echte Chat-Funktion mit Kontext-Speicherung
     */
    public CompletableFuture<String> chatWithContext(String message, String additionalContext) {
        StringBuilder contextBuilder = new StringBuilder();
        
        // Einfacher, freundlicher System-Prompt
        contextBuilder.append("Du bist ein hilfreicher deutscher Assistent. ");
        contextBuilder.append("Antworte bitte auf Deutsch.\n\n");
        
        // Zusätzlichen Kontext hinzufügen, falls vorhanden
        if (additionalContext != null && !additionalContext.trim().isEmpty()) {
            contextBuilder.append("=== ZUSÄTZLICHER KONTEXT ===\n");
            contextBuilder.append(additionalContext.trim()).append("\n");
            contextBuilder.append("==========================\n\n");
        }
        
        // Neue Nachricht hinzufügen
        contextBuilder.append("Du: ").append(message).append("\n");
        contextBuilder.append("Assistent: ");
        
        String fullPrompt = contextBuilder.toString();
        
        // Für trainierte Modelle spezielle Parameter verwenden
        CompletableFuture<String> responseFuture;
        if (isTrainedModel(currentModel)) {
            responseFuture = chatWithTrainedModel(message, fullPrompt);
        } else {
            responseFuture = generateText(fullPrompt, maxTokens, temperature);
        }
        
        return responseFuture;
    }
    
    /**
     * Echte Chat-Funktion mit Kontext-Speicherung (ohne zusätzlichen Kontext)
     */
    public CompletableFuture<String> chatWithContext(String message) {
        return chatWithContext(message, null);
    }
    
    // Keine Session-Verwaltung mehr - einfache Lösung
    
    /**
     * Prüft ob das angegebene Modell ein trainiertes Modell ist
     * Trainierte Modelle haben normalerweise einen benutzerdefinierten Namen
     */
    private boolean isTrainedModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return false;
        }
        
        // Standard-Modelle die NICHT trainiert sind
        String[] standardModels = {
            "mistral", "mistral:7b-instruct", "gemma3:4b", 
            "llama2", "llama2:7b", "llama2:13b", "llama2:70b",
            "codellama", "codellama:7b", "codellama:13b", "codellama:34b",
            "phi", "phi:2.7b", "phi:3.5", "phi:3.5:3.8b",
            "qwen2.5", "qwen2.5:7b", "qwen2.5:14b", "qwen2.5:32b"
        };
        
        // Prüfe ob es ein Standard-Modell ist
        for (String standardModel : standardModels) {
            if (modelName.equalsIgnoreCase(standardModel)) {
                return false;
            }
        }
        
        // Wenn es kein Standard-Modell ist, ist es wahrscheinlich ein trainiertes Modell
        return true;
    }
    
    // ===== STREAMING-VARIANTEN DER KI-ASSISTENTEN-FUNKTIONEN =====
    
    /**
     * Streaming-Variante für Dialog-Generierung
     */
    public StreamHandle generateDialogueStreaming(String character, String situation, String emotion, String context,
                                                 Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        String prompt = String.format("""
            Schreibe einen authentischen Dialog für den Charakter '%s' in folgender Situation:
            %s
            
            Emotion des Charakters: %s
            
            Schreibe nur den Dialog, ohne Erzählertext. Verwende natürliche, deutsche Sprache.
            """, character, situation, emotion);
        
        double originalTemp = temperature;
        setTemperature(0.8);
        
        StreamHandle handle = generateTextStreaming(prompt, context, onChunk, onComplete, onError);
        
        setTemperature(originalTemp);
        return handle;
    }
    
    /**
     * Streaming-Variante für Beschreibung erweitern
     */
    public StreamHandle expandDescriptionStreaming(String shortDescription, String style, String context,
                                                  Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        String prompt = String.format("""
            Erweitere diese kurze Beschreibung im Stil '%s':
            
            %s
            
            Schreibe eine detaillierte, atmosphärische Beschreibung (max. 3 Sätze).
            """, style, shortDescription);
        
        double originalTemp = temperature;
        setTemperature(0.6);
        
        StreamHandle handle = generateTextStreaming(prompt, context, onChunk, onComplete, onError);
        
        setTemperature(originalTemp);
        return handle;
    }
    
    /**
     * Streaming-Variante für Plot-Ideen entwickeln
     */
    public StreamHandle developPlotIdeasStreaming(String genre, String basicIdea, String context,
                                                 Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        String prompt = String.format("""
            Entwickle Plot-Ideen für eine %s-Geschichte basierend auf dieser Grundidee:
            %s
            
            Gib 3-5 konkrete Plot-Entwicklungen an, die die Geschichte vorantreiben könnten.
            """, genre, basicIdea);
        
        double originalTemp = temperature;
        setTemperature(0.9);
        
        StreamHandle handle = generateTextStreaming(prompt, context, onChunk, onComplete, onError);
        
        setTemperature(originalTemp);
        return handle;
    }
    
    /**
     * Streaming-Variante für Charakter entwickeln
     */
    public StreamHandle developCharacterStreaming(String characterName, String basicTraits, String context,
                                                 Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        String prompt = String.format("""
            Entwickle einen detaillierten Charakter basierend auf diesen Grundmerkmalen:
            
            Name: %s
            Grundmerkmale: %s
            
            Erstelle eine umfassende Charakterbeschreibung mit:
            - Persönlichkeit und Motivation
            - Hintergrund und Geschichte
            - Stärken und Schwächen
            - Beziehungen zu anderen Charakteren
            - Entwicklungsmöglichkeiten
            
            Schreibe in einem natürlichen, erzählenden Stil.
            """, characterName, basicTraits);
        
        double originalTemp = temperature;
        setTemperature(0.7);
        
        StreamHandle handle = generateTextStreaming(prompt, context, onChunk, onComplete, onError);
        
        setTemperature(originalTemp);
        return handle;
    }
    
    /**
     * Streaming-Variante für Schreibstil analysieren
     */
    public StreamHandle analyzeWritingStyleStreaming(String text, String context,
                                                    Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        String prompt = String.format("""
            Analysiere den Schreibstil des folgenden Textes:
            
            %s
            
            Gib eine detaillierte Analyse mit folgenden Punkten:
            - Ton und Stimmung
            - Satzstruktur und Rhythmus
            - Wortwahl und Vokabular
            - Erzählperspektive
            - Besondere stilistische Merkmale
            - Verbesserungsvorschläge
            
            Sei konstruktiv und hilfreich in deiner Analyse.
            """, text);
        
        double originalTemp = temperature;
        setTemperature(0.5);
        
        StreamHandle handle = generateTextStreaming(prompt, context, onChunk, onComplete, onError);
        
        setTemperature(originalTemp);
        return handle;
    }
    
    /**
     * Streaming-Variante für Text umschreiben
     */
    public StreamHandle rewriteTextStreaming(String originalText, String rewriteType, String additionalInstructions, String context,
                                            Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        String prompt = String.format("""
            Umschreibe den folgenden Text im Stil '%s':
            
            %s
            
            %s
            
            Schreibe nur den umgeschriebenen Text, ohne Erklärungen.
            """, rewriteType, originalText, 
            additionalInstructions != null && !additionalInstructions.trim().isEmpty() ? 
            "Zusätzliche Anweisungen: " + additionalInstructions : "");
        
        double originalTemp = temperature;
        setTemperature(0.7);
        
        StreamHandle handle = generateTextStreaming(prompt, context, onChunk, onComplete, onError);
        
        setTemperature(originalTemp);
        return handle;
    }
    
    /**
     * Streaming-Variante der Chat-API mit echter Konversationshistorie
     */
    public StreamHandle chatStreaming(List<ChatMessage> messages, String additionalContext,
                                     Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        int maxTokens = this.maxTokens;
        double temperature = this.temperature;
        double topP = this.topP;
        double repeatPenalty = this.repeatPenalty;
        
        String systemPrompt = "Du bist ein hilfreicher deutscher Assistent. Antworte bitte auf Deutsch.";
        
        // Erstelle Messages-Array mit System-Prompt und Kontext
        List<ChatMessage> fullMessages = new ArrayList<>();
        fullMessages.add(new ChatMessage("system", systemPrompt));
        
        // Zusätzlichen Kontext als separate Nachricht hinzufügen
        if (additionalContext != null && !additionalContext.trim().isEmpty()) {
            fullMessages.add(new ChatMessage("user", "Kontext: " + additionalContext));
        }
        
        // Chat-Historie hinzufügen
        fullMessages.addAll(messages);
        
        // JSON für Chat-API erstellen
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"model\":\"").append(currentModel).append("\",\"messages\":[");
        
        for (int i = 0; i < fullMessages.size(); i++) {
            ChatMessage msg = fullMessages.get(i);
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append("{\"role\":\"").append(msg.getRole()).append("\",\"content\":\"")
                      .append(escapeJson(msg.getContent())).append("\"}");
        }
        
        jsonBuilder.append("],\"stream\":true,\"options\":{\"num_predict\":").append(maxTokens)
                  .append(",\"temperature\":").append(temperature)
                  .append(",\"top_p\":").append(topP)
                  .append(",\"repeat_penalty\":").append(repeatPenalty)
                  .append(",\"repeat_last_n\":512,\"penalize_newline\":true,\"num_gpu\":-1}}");
        
        String json = jsonBuilder.toString();
        
        this.lastEndpoint = CHAT_ENDPOINT;
        this.lastRequestJson = json;
        this.lastFullPrompt = "Chat mit " + messages.size() + " Nachrichten";
        this.lastContext = additionalContext;
        
        StreamHandle handle = new StreamHandle();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL + CHAT_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(httpRequestTimeoutSeconds))
                    .build();

            CompletableFuture<HttpResponse<InputStream>> fut = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            handle.bindFuture(fut);
            fut.whenComplete((resp, err) -> {
                if (err != null) {
                    if (onError != null) onError.accept(err);
                    return;
                }
                if (resp.statusCode() != 200) {
                    if (onError != null) onError.accept(new RuntimeException("HTTP " + resp.statusCode()));
                    return;
                }
                InputStream in = resp.body();
                handle.bind(in);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        if (line.contains("\"message\":")) {
                            // Chat-API verwendet "message" statt "response"
                            String chunk = extractJsonValue(line, "content");
                            if (chunk != null && !chunk.isEmpty() && onChunk != null) {
                                onChunk.accept(chunk);
                            }
                        }
                        if (line.contains("\"done\":true")) {
                            if (onComplete != null) onComplete.run();
                            break;
                        }
                    }
                } catch (Exception ex) {
                    if (onError != null) onError.accept(ex);
                }
            });
        } catch (Exception e) {
            if (onError != null) onError.accept(e);
        }

        return handle;
    }
    
} 