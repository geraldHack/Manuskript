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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.manuskript.GatewayHttpRetry;
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
    private double temperature;

    public OpenAIBackend() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
        this.currentModel = null;
        this.temperature = AgentSamplingParams.defaultTemperature("OpenAI");
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
        this.currentModel = AgentModelIds.apiModelId(model);
    }

    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @Override
    public CompletableFuture<String> chat(String systemPrompt, String userMessage, int maxTokens) {
        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        return CompletableFuture.supplyAsync(() -> executeChatCompletion(messages, maxTokens, userMessage.length()));
    }

    @Override
    public CompletableFuture<String> chatMultiTurn(String systemPrompt, String contextBlock,
                                                    List<ChatTurn> history, String newUserMessage,
                                                    int maxTokens) {
        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);
        if (contextBlock != null && !contextBlock.isBlank()) {
            JsonObject ctxMsg = new JsonObject();
            ctxMsg.addProperty("role", "user");
            ctxMsg.addProperty("content", "=== KONTEXT ===\n" + contextBlock.trim());
            messages.add(ctxMsg);
        }
        if (history != null) {
            for (ChatTurn turn : history) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", turn.role());
                msg.addProperty("content", turn.content());
                messages.add(msg);
            }
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", newUserMessage);
        messages.add(userMsg);
        int logLen = newUserMessage != null ? newUserMessage.length() : 0;
        return CompletableFuture.supplyAsync(() -> executeChatCompletion(messages, maxTokens, logLen));
    }

    private String executeChatCompletion(JsonArray messages, int maxTokens, int userMessageLength) {
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
            body.add("messages", messages);

            String requestBody = gson.toJson(body);
            logger.info("OpenAI Request: {} Zeichen, max_tokens: {}", requestBody.length(), maxTokens);

            int timeoutSec = requestTimeoutSeconds();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = sendWithGatewayRetry(request);
            String responseBody = response.body();

            JsonElement root;
            try {
                root = OpenAIChatCompletionParser.parseRoot(responseBody);
            } catch (JsonSyntaxException e) {
                logger.error("OpenAI API (Modell={}): Antwort ist kein gültiges JSON. HTTP {}. Anfang:\n{}",
                        currentModel, response.statusCode(), preview(responseBody, 2500), e);
                throw new RuntimeException("API-Antwort ist kein gültiges JSON (Modell "
                        + currentModel + "): " + e.getMessage(), e);
            }
            JsonObject json = OpenAIChatCompletionParser.toCompletionEnvelope(root);
            if (json == null) {
                String rootKind = root == null ? "null"
                        : root.isJsonArray() ? "array[" + root.getAsJsonArray().size() + "]"
                        : root.isJsonObject() ? "object keys=" + root.getAsJsonObject().keySet()
                        : "other";
                logger.error("OpenAI API (Modell={}): Antwort-Format nicht erkannt ({}). HTTP {}. Anfang:\n{}",
                        currentModel, rootKind, response.statusCode(), preview(responseBody, 2500));
                throw new RuntimeException("API-Antwortformat nicht erkannt (Modell " + currentModel
                        + ", " + rootKind + "). Details im Log.");
            }
            if (root != null && root.isJsonArray()) {
                logger.info("OpenAI API (Modell={}): Wurzel-Array mit {} Element(en) normalisiert",
                        currentModel, root.getAsJsonArray().size());
            }

            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                String errorMsg = error.has("message") ? error.get("message").getAsString() : error.toString();
                logger.error("OpenAI API (Modell={}) Fehler im JSON (HTTP 200): {} — Body:\n{}",
                        currentModel, errorMsg, preview(responseBody, 2500));
                throw new RuntimeException("API-Fehler (Modell " + currentModel + "): " + errorMsg);
            }

            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                logger.error("OpenAI API (Modell={}): kein choices-Array. Body:\n{}",
                        currentModel, preview(responseBody, 2500));
                throw new RuntimeException("Keine Antwort von der API erhalten (keine choices, Modell "
                        + currentModel + ")");
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            if (message == null) {
                logger.error("OpenAI API (Modell={}): choice ohne message. Body:\n{}",
                        currentModel, preview(responseBody, 2500));
                throw new RuntimeException("Keine Antwort von der API erhalten (kein message, Modell "
                        + currentModel + ")");
            }

            String content = extractMessageContent(message, choice, responseBody);

            if (content == null || content.trim().isEmpty()) {
                String contentShape = message.has("content")
                        ? OpenAIMessageContentExtractor.describe(message.get("content"))
                        : "fehlt";
                logger.error("OpenAI API (Modell={}): kein Text in message.content ({}). "
                        + "Antwort-Anfang:\n{}", currentModel, contentShape, preview(responseBody, 2500));
                throw new RuntimeException("Keine lesbare Text-Antwort von der API (Modell " + currentModel
                        + ", content=" + contentShape + "). Details im Log.");
            }

            return content;

        } catch (RuntimeException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            int timeoutSec = requestTimeoutSeconds();
            logger.error("OpenAI API Timeout nach {}s (Modell={}). User-Message {} Zeichen.",
                    timeoutSec, currentModel, userMessageLength, e);
            throw new RuntimeException("API-Timeout nach " + timeoutSec + " s (Modell " + currentModel
                    + "). Kontext zu groß oder Modell zu langsam — siehe Log.", e);
        } catch (Exception e) {
            logger.error("OpenAI chat Fehler (Modell={}): {}", currentModel, e.getMessage(), e);
            throw new RuntimeException("OpenAI Fehler (Modell " + currentModel + "): " + e.getMessage(), e);
        }
    }

    private String extractMessageContent(JsonObject message, JsonObject choice, String responseBody) {
        String content = null;
        if (message.has("content") && !message.get("content").isJsonNull()) {
            JsonElement contentElement = message.get("content");
            content = OpenAIMessageContentExtractor.extractText(contentElement);
            if (content == null || content.isBlank()) {
                logger.warn("OpenAI API (Modell={}): content nicht als Text extrahierbar: {}",
                        currentModel, OpenAIMessageContentExtractor.describe(contentElement));
            }
        }

        if (content == null || content.trim().isEmpty()) {
            int messageStart = responseBody.indexOf("\"message\":");
            if (messageStart != -1) {
                int braceStart = messageStart + "\"message\":".length();
                while (braceStart < responseBody.length() && Character.isWhitespace(responseBody.charAt(braceStart))) {
                    braceStart++;
                }
                if (braceStart < responseBody.length() && responseBody.charAt(braceStart) == '{') {
                    int braceEnd = findMatchingBrace(responseBody, braceStart);
                    if (braceEnd != -1) {
                        String messageStr = responseBody.substring(braceStart, braceEnd + 1);
                        int contentPos = 0;
                        while (true) {
                            int idx = messageStr.indexOf("\"content\"", contentPos);
                            if (idx == -1) {
                                break;
                            }
                            int colonIdx = idx + "\"content\"".length();
                            while (colonIdx < messageStr.length() && Character.isWhitespace(messageStr.charAt(colonIdx))) {
                                colonIdx++;
                            }
                            if (colonIdx < messageStr.length() && messageStr.charAt(colonIdx) == ':') {
                                colonIdx++;
                                while (colonIdx < messageStr.length() && Character.isWhitespace(messageStr.charAt(colonIdx))) {
                                    colonIdx++;
                                }
                                if (colonIdx + 3 < messageStr.length()
                                        && messageStr.substring(colonIdx, colonIdx + 4).equals("null")) {
                                    contentPos = colonIdx + 4;
                                } else if (colonIdx < messageStr.length() && messageStr.charAt(colonIdx) == '"') {
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
                                    logger.warn("Workaround verwendet: content aus message-Objekt extrahiert");
                                    break;
                                }
                            }
                            contentPos = idx + 1;
                        }
                    }
                }
            }
        }

        if (content == null || content.trim().isEmpty()) {
            if (message.has("reasoning_content")) {
                JsonElement rc = message.get("reasoning_content");
                if (!rc.isJsonNull() && rc.isJsonPrimitive() && rc.getAsJsonPrimitive().isString()) {
                    String reasoning = rc.getAsString();
                    if (reasoning != null && !reasoning.trim().isEmpty()) {
                        String finishReason = "";
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                            finishReason = choice.get("finish_reason").getAsString();
                        }
                        logger.warn("Fallback auf 'reasoning_content' (finish_reason={}, content=null).", finishReason);
                        content = reasoning;
                    }
                }
            }
        }
        return content;
    }

    /** Liest konfigurierbares Anfrage-Timeout (Sekunden). */
    public static int requestTimeoutSeconds() {
        int fromAgent = ResourceManager.getIntParameter("agent.openai.request_timeout_sec", -1);
        if (fromAgent >= 60) {
            return Math.min(900, fromAgent);
        }
        int fromLektorat = ResourceManager.getIntParameter("api.lektorat.request_timeout_sec", 300);
        return Math.max(60, Math.min(900, fromLektorat));
    }

    private HttpResponse<String> sendWithGatewayRetry(HttpRequest request) throws java.io.IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 || !GatewayHttpRetry.isRetryableStatus(response.statusCode())) {
            if (response.statusCode() != 200) {
                throw httpError(response);
            }
            return response;
        }
        logger.info("OpenAI Agent: HTTP {} – ein Wiederholungsversuch nach {} ms…",
                response.statusCode(), 1500);
        GatewayHttpRetry.sleepBeforeRetry();
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw httpError(response);
        }
        return response;
    }

    private static String preview(String body, int maxChars) {
        if (body == null) {
            return "(null)";
        }
        String trimmed = body.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "\n… [" + trimmed.length() + " Zeichen gesamt]";
    }

    private RuntimeException httpError(HttpResponse<String> response) {
        logger.error("OpenAI API Fehler {} (Modell={}): {}", response.statusCode(), currentModel,
                preview(response.body(), 2500));
        if (response.statusCode() == 413) {
            return new RuntimeException("OpenAI API Fehler 413: Request body zu groß. "
                    + "Der gesendete Text ist zu lang. Bitte Kontext reduzieren oder Projekt aufteilen.");
        }
        return new RuntimeException("OpenAI API Fehler " + response.statusCode() + ": " + response.body());
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
