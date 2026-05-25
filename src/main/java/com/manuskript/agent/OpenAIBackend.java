package com.manuskript.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.manuskript.ResourceManager;

/**
 * OpenAI-kompatibles Backend (OpenAI, OpenRouter, etc.).
 * API-Key und Modell werden aus dem Parameter-Fenster geladen.
 */
public class OpenAIBackend implements AIBackend {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIBackend.class);

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final HttpClient httpClient;
    private final Gson gson;
    private String currentModel;
    private double temperature = 0.3;

    public OpenAIBackend() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
        this.currentModel = null; // Wird über setCurrentModel gesetzt
    }

    @Override
    public String getName() {
        return "OpenAI";
    }

    @Override
    public List<String> getAvailableModels() {
        return Arrays.asList("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo", "gpt-4-turbo");
    }

    @Override
    public String getCurrentModel() {
        return currentModel;
    }

    @Override
    public void setCurrentModel(String model) {
        this.currentModel = model;
    }

    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @Override
    public CompletableFuture<String> chat(String systemPrompt, String userMessage, int maxTokens) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = ResourceManager.getParameter("agent.openai.api_key", "");
                if (apiKey.isEmpty()) {
                    apiKey = ResourceManager.getParameter("api.lektorat.api_key", "");
                }
                if (apiKey.isEmpty()) {
                    throw new RuntimeException("Kein OpenAI API-Key konfiguriert. Bitte im Parameter-Fenster unter 'Agenten' setzen.");
                }

                String baseUrl = ResourceManager.getParameter("agent.openai.api_url", DEFAULT_BASE_URL);
                String url = baseUrl + "/chat/completions";

                JsonObject body = new JsonObject();
                body.addProperty("model", currentModel);
                body.addProperty("max_tokens", maxTokens);
                body.addProperty("temperature", temperature);

                JsonArray messages = new JsonArray();

                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userMessage);
                messages.add(userMsg);

                body.add("messages", messages);

                String requestBody = gson.toJson(body);
                logger.info("OpenAI Request: {} Zeichen, System-Prompt: {} Zeichen, User-Message: {} Zeichen, max_tokens: {}",
                        requestBody.length(), systemPrompt.length(), userMessage.length(), maxTokens);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.error("OpenAI API Fehler {}: {}", response.statusCode(), response.body());
                    String errorMsg;
                    if (response.statusCode() == 413) {
                        errorMsg = "OpenAI API Fehler 413: Request body zu groß. " +
                                "Der gesendete Text ist zu lang. Bitte Kontext reduzieren oder Projekt aufteilen.";
                    } else {
                        errorMsg = "OpenAI API Fehler " + response.statusCode() + ": " + response.body();
                    }
                    throw new RuntimeException(errorMsg);
                }

                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                
                // Prüfen, ob die Antwort ein Fehler enthält (einige APIs geben 200 mit Fehler zurück)
                if (json.has("error")) {
                    JsonObject error = json.getAsJsonObject("error");
                    String errorMsg = error.has("message") ? error.get("message").getAsString() : error.toString();
                    logger.error("OpenAI API gab Fehler zurück (HTTP 200): {}", errorMsg);
                    throw new RuntimeException("OpenAI API Fehler: " + errorMsg);
                }
                
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices == null || choices.size() == 0) {
                    logger.error("OpenAI API Antwort enthält kein 'choices' Array oder ist leer: {}", response.body());
                    throw new RuntimeException("Keine Antwort von OpenAI erhalten (keine choices)");
                }
                
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                if (message == null) {
                    logger.error("OpenAI API Antwort enthält kein 'message' Objekt: {}", response.body());
                    throw new RuntimeException("Keine Antwort von OpenAI erhalten (kein message)");
                }
                
                // Manche APIs (z.B. Minimax) geben zwei content-Felder zurück, wobei das zweite null ist
                // Wir suchen das erste nicht-null content-Feld
                String content = null;
                if (message.has("content")) {
                    com.google.gson.JsonElement contentElement = message.get("content");
                    if (!contentElement.isJsonNull()) {
                        if (contentElement.isJsonPrimitive() && contentElement.getAsJsonPrimitive().isString()) {
                            content = contentElement.getAsString();
                        }
                    }
                }
                
                // Wenn das content-Feld null ist, prüfen wir, ob es im JSON-String ein zweites content gibt
                // (Workaround für fehlerhafte APIs wie Minimax)
                if (content == null || content.trim().isEmpty()) {
                    String responseBody = response.body();
                    
                    // Extrahiere das message-Objekt als String
                    int messageStart = responseBody.indexOf("\"message\":");
                    if (messageStart != -1) {
                        // Finde die öffnende Klammer nach "message":
                        int braceStart = messageStart + "\"message\":".length();
                        while (braceStart < responseBody.length() && Character.isWhitespace(responseBody.charAt(braceStart))) {
                            braceStart++;
                        }
                        if (braceStart < responseBody.length() && responseBody.charAt(braceStart) == '{') {
                            // Finde das schließende Brace
                            int braceEnd = findMatchingBrace(responseBody, braceStart);
                            if (braceEnd != -1) {
                                String messageStr = responseBody.substring(braceStart, braceEnd + 1);
                                
                                // Suche im message-Objekt nach content-Feldern
                                int contentPos = 0;
                                while (true) {
                                    int idx = messageStr.indexOf("\"content\"", contentPos);
                                    if (idx == -1) break;
                                    
                                    // Prüfen, ob danach ein : kommt
                                    int colonIdx = idx + "\"content\"".length();
                                    while (colonIdx < messageStr.length() && Character.isWhitespace(messageStr.charAt(colonIdx))) {
                                        colonIdx++;
                                    }
                                    if (colonIdx < messageStr.length() && messageStr.charAt(colonIdx) == ':') {
                                        colonIdx++;
                                        while (colonIdx < messageStr.length() && Character.isWhitespace(messageStr.charAt(colonIdx))) {
                                            colonIdx++;
                                        }
                                        
                                        // Prüfen, ob der Wert null ist
                                        if (colonIdx + 3 < messageStr.length() && 
                                            messageStr.substring(colonIdx, colonIdx + 4).equals("null")) {
                                            // Das ist das null-Feld, suche weiter
                                            contentPos = colonIdx + 4;
                                        } else if (colonIdx < messageStr.length() && messageStr.charAt(colonIdx) == '"') {
                                            // Das ist ein String-Wert, extrahiere ihn
                                            StringBuilder sb = new StringBuilder();
                                            int i = colonIdx + 1;
                                            boolean escaped = false;
                                            while (i < messageStr.length()) {
                                                char c = messageStr.charAt(i);
                                                if (escaped) {
                                                    sb.append(c);
                                                    escaped = false;
                                                } else if (c == '\\') {
                                                    escaped = true;
                                                    sb.append(c);
                                                } else if (c == '"') {
                                                    break;
                                                } else {
                                                    sb.append(c);
                                                }
                                                i++;
                                            }
                                            content = sb.toString();
                                            logger.warn("Workaround verwendet: content aus message-Objekt extrahiert (API gab doppeltes content-Feld zurück)");
                                            break;
                                        }
                                    }
                                    contentPos = idx + 1;
                                }
                            }
                        }
                    }
                }
                
                // Fallback: Manche Reasoning-Modelle (z.B. DeepSeek v4, o1) liefern die Antwort
                // in 'reasoning_content', wenn das Token-Budget vom Reasoning aufgebraucht wurde
                // und 'content' null bleibt (finish_reason=length).
                if (content == null || content.trim().isEmpty()) {
                    if (message.has("reasoning_content")) {
                        com.google.gson.JsonElement rc = message.get("reasoning_content");
                        if (!rc.isJsonNull() && rc.isJsonPrimitive() && rc.getAsJsonPrimitive().isString()) {
                            String reasoning = rc.getAsString();
                            if (reasoning != null && !reasoning.trim().isEmpty()) {
                                String finishReason = "";
                                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                                    finishReason = choice.get("finish_reason").getAsString();
                                }
                                logger.warn("Fallback auf 'reasoning_content' (finish_reason={}, content=null). " +
                                        "Token-Budget evtl. zu klein für Reasoning-Modell.", finishReason);
                                content = reasoning;
                            }
                        }
                    }
                }

                if (content == null || content.trim().isEmpty()) {
                    logger.error("OpenAI API Antwort enthält keinen gültigen content: {}", response.body());
                    throw new RuntimeException("Keine Antwort von OpenAI erhalten (kein gültiger content)");
                }
                
                return content;

            } catch (Exception e) {
                logger.error("OpenAI chat Fehler", e);
                throw new RuntimeException("OpenAI Fehler: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Findet das schließende Brace, das zum öffnenden Brace an der gegebenen Position gehört.
     * Berücksichtigt verschachtelte Braces und Strings.
     */
    private static int findMatchingBrace(String str, int openPos) {
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = openPos + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        
        return -1; // Kein passendes Brace gefunden
    }
}
