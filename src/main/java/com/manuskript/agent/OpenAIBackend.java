package com.manuskript.agent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
    private static final String LOOPBACK_PLACEHOLDER_API_KEY = "local";
    /** Mindest-HTTP-Timeout für localhost/127.0.0.1 (Sekunden). */
    private static final int LOCAL_DEFAULT_REQUEST_TIMEOUT_SEC = 900;

    private final HttpClient httpClient;
    private final Gson gson;
    private String currentModel;
    private double temperature;
    private Double topP;

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
        return new ArrayList<>(Arrays.asList(
                "gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo", "gpt-4-turbo"));
    }

    /**
     * Setzt Token-Limits für OpenAI und kompatible Server
     * ({@code max_tokens} und {@code max_completion_tokens}).
     */
    static void putMaxTokenLimits(JsonObject body, int maxTokens) {
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("max_completion_tokens", maxTokens);
    }

    static boolean isLoopbackOpenAiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return false;
        }
        String lower = apiUrl.trim().toLowerCase(Locale.ROOT);
        return lower.contains("127.0.0.1") || lower.contains("localhost");
    }

    static String resolveApiKey(String configuredKey, String baseUrl) {
        String key = configuredKey == null ? "" : configuredKey.trim();
        if (!key.isEmpty()) {
            return key;
        }
        if (isLoopbackOpenAiUrl(baseUrl)) {
            return LOOPBACK_PLACEHOLDER_API_KEY;
        }
        return "";
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

    /** Optional; {@code null} = API-Default (nicht mitsenden). */
    public void setTopP(double topP) {
        this.topP = topP;
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
    public CompletableFuture<String> chatStreaming(String systemPrompt, String userMessage, int maxTokens,
                                                   Consumer<String> onDelta) {
        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        return CompletableFuture.supplyAsync(() ->
                executeChatCompletion(messages, maxTokens, userMessage.length(), onDelta));
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
        return executeChatCompletion(messages, maxTokens, userMessageLength, null);
    }

    private String executeChatCompletion(JsonArray messages, int maxTokens, int userMessageLength,
                                         Consumer<String> onDelta) {
        try {
            JsonArray workingMessages = gson.fromJson(gson.toJson(messages), JsonArray.class);
            StringBuilder fullContent = new StringBuilder();
            int maxContinues = Math.max(0, Math.min(8,
                    ResourceManager.getIntParameter("agent.chatbot.max_continuations", 4)));
            // Reasoning-only (content=null) höchstens 1× nachziehen — sonst hängt Kimi minutenlang.
            int emptyContentContinues = 0;
            final int maxEmptyContentContinues = 1;

            for (int attempt = 0; attempt <= maxContinues; attempt++) {
                CompletionChunk chunk = onDelta != null
                        ? executeChatCompletionOnceStreaming(workingMessages, maxTokens, userMessageLength, onDelta)
                        : executeChatCompletionOnce(workingMessages, maxTokens, userMessageLength);
                if (chunk.content() != null && !chunk.content().isEmpty()) {
                    fullContent.append(chunk.content());
                }

                if (!chunk.requestContinuation()) {
                    break;
                }

                boolean needsFinalOutput = fullContent.length() == 0;
                if (needsFinalOutput) {
                    emptyContentContinues++;
                    if (emptyContentContinues > maxEmptyContentContinues) {
                        logger.warn("OpenAI: nach {} leeren Fortsetzung(en) immer noch kein content (finish_reason={})",
                                maxEmptyContentContinues, chunk.finishReason());
                        break;
                    }
                }

                if (attempt >= maxContinues) {
                    logger.warn("OpenAI: Antwort nach {} Fortsetzung(en) immer noch unvollständig (finish_reason={})",
                            maxContinues, chunk.finishReason());
                    if (fullContent.length() > 0) {
                        fullContent.append("\n\n[Hinweis: Die Antwort wurde wegen des Ausgabe-Token-Limits "
                                + "möglicherweise unvollständig abgebrochen. Bitte „weiter“ schreiben "
                                + "oder max. Tokens beim Agenten erhöhen.]");
                    }
                    break;
                }

                logger.info("OpenAI: finish_reason={} – setze Antwort fort ({}/{}, finalOutputNeeded={})",
                        chunk.finishReason(), attempt + 1, maxContinues, needsFinalOutput);
                workingMessages.add(buildContinuationAssistantMessage(chunk, fullContent.toString()));

                JsonObject continueMsg = new JsonObject();
                continueMsg.addProperty("role", "user");
                continueMsg.addProperty("content", needsFinalOutput
                        ? "Deine vorherige Antwort enthielt keinen fertigen Ausgabetext "
                                + "(nur internes Reasoning und/oder Token-Limit). "
                                + "Gib jetzt ausschließlich die fertige Antwort im geforderten Format aus. "
                                + "Kein weiteres Reasoning, keine Einleitung."
                        : "Bitte setze deine Antwort nahtlos fort. Wiederhole nichts, was du bereits geschrieben hast.");
                workingMessages.add(continueMsg);
            }

            String result = com.manuskript.ModelTextNormalizer.normalize(fullContent.toString());
            if (result == null || result.isBlank()) {
                throw new RuntimeException(
                        "Keine lesbare Text-Antwort von der API (Modell " + currentModel
                                + "). Reasoning-Modelle wie Kimi brauchen oft höheres max_tokens "
                                + "oder reasoning_effort=low — siehe Parameter agent.openai.reasoning_effort.");
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("OpenAI chat Fehler (Modell={}): {}", currentModel, e.getMessage(), e);
            throw new RuntimeException("OpenAI Fehler (Modell " + currentModel + "): " + e.getMessage(), e);
        }
    }

    /** Kimi/Moonshot: Assistant-Message inkl. Reasoning-Feld zurückgeben. */
    private static JsonObject buildContinuationAssistantMessage(CompletionChunk chunk, String fullContentSoFar) {
        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");
        String reasoning = chunk.reasoning();
        if (reasoning != null && !reasoning.isBlank()) {
            assistantMsg.addProperty("reasoning", reasoning);
            assistantMsg.addProperty("reasoning_content", reasoning);
        }
        if (chunk.content() != null && !chunk.content().isEmpty()) {
            assistantMsg.addProperty("content", chunk.content());
        } else if (fullContentSoFar != null && !fullContentSoFar.isEmpty()) {
            assistantMsg.addProperty("content", fullContentSoFar);
        } else {
            assistantMsg.add("content", com.google.gson.JsonNull.INSTANCE);
        }
        return assistantMsg;
    }

    private record CompletionChunk(String content, String finishReason, boolean requestContinuation, String reasoning) {}

    private HttpRequest buildChatCompletionRequest(JsonArray messages, int maxTokens, boolean stream) {
        String baseUrl = ResourceManager.getParameter("agent.openai.api_url", DEFAULT_BASE_URL);
        String apiKey = ResourceManager.getParameter("agent.openai.api_key", "");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = ResourceManager.getParameter("api.lektorat.api_key", "");
        }
        apiKey = resolveApiKey(apiKey, baseUrl);
        if (apiKey.isEmpty()) {
            throw new RuntimeException("Kein OpenAI API-Key konfiguriert. Bitte im Parameter-Fenster unter 'Agenten' setzen.");
        }

        String url = baseUrl.endsWith("/")
                ? baseUrl + "chat/completions"
                : baseUrl + "/chat/completions";

        JsonObject body = new JsonObject();
        body.addProperty("model", currentModel);
        putMaxTokenLimits(body, maxTokens);
        body.addProperty("temperature", temperature);
        if (topP != null) {
            body.addProperty("top_p", topP);
        }
        body.add("messages", messages);
        applyReasoningEffort(body);
        if (stream) {
            body.addProperty("stream", true);
        }

        String requestBody = gson.toJson(body);
        logger.info("OpenAI Request{}: {} Zeichen, max_tokens: {}, temperature: {}, top_p: {}, reasoning_effort={}",
                stream ? " (stream)" : "",
                requestBody.length(), maxTokens, temperature,
                topP != null ? topP : "—",
                body.has("reasoning_effort")
                        ? body.get("reasoning_effort").getAsString() : "—");

        int timeoutSec = requestTimeoutSeconds(baseUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSec))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        if (stream) {
            builder.header("Accept", "text/event-stream");
        }
        return builder.build();
    }

    private static boolean isOutputTruncated(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return false;
        }
        String reason = finishReason.trim().toLowerCase(java.util.Locale.ROOT);
        return "length".equals(reason) || "max_tokens".equals(reason);
    }

    private CompletionChunk executeChatCompletionOnce(JsonArray messages, int maxTokens, int userMessageLength) {
        try {
            HttpRequest request = buildChatCompletionRequest(messages, maxTokens, false);
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
            String finishReason = "";
            if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                finishReason = choice.get("finish_reason").getAsString();
            }
            boolean truncated = isOutputTruncated(finishReason);
            String reasoningOnly = extractReasoningText(message);

            if (content == null || content.trim().isEmpty()) {
                String contentShape = message.has("content")
                        ? OpenAIMessageContentExtractor.describe(message.get("content"))
                        : "fehlt";
                // Kimi/Moonshot u. a.: lange Reasoning-Phase verbraucht max_tokens, content bleibt null.
                if (truncated || (reasoningOnly != null && !reasoningOnly.isBlank())) {
                    logger.warn("OpenAI API (Modell={}): content leer ({}), finish_reason={}, "
                                    + "reasoning={} Zeichen — fordere fertige Ausgabe an. Antwort-Anfang:\n{}",
                            currentModel, contentShape, finishReason,
                            reasoningOnly != null ? reasoningOnly.length() : 0,
                            preview(responseBody, 2500));
                    return new CompletionChunk("", finishReason, true, reasoningOnly);
                }
                logger.error("OpenAI API (Modell={}): kein Text in message.content ({}). "
                        + "Antwort-Anfang:\n{}", currentModel, contentShape, preview(responseBody, 2500));
                throw new RuntimeException("Keine lesbare Text-Antwort von der API (Modell " + currentModel
                        + ", content=" + contentShape + "). Details im Log.");
            }

            if (truncated) {
                logger.warn("OpenAI: Antwort abgeschnitten (finish_reason={}, {} Zeichen bisher)",
                        finishReason, content.length());
            }

            return new CompletionChunk(content, finishReason, truncated, reasoningOnly);

        } catch (RuntimeException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            String baseUrl = ResourceManager.getParameter("agent.openai.api_url", DEFAULT_BASE_URL);
            int timeoutSec = requestTimeoutSeconds(baseUrl);
            logger.error("OpenAI API Timeout nach {}s (Modell={}). User-Message {} Zeichen.",
                    timeoutSec, currentModel, userMessageLength, e);
            if (isLoopbackOpenAiUrl(baseUrl)) {
                throw new RuntimeException("Lokales Modell-Timeout nach " + timeoutSec
                        + " s (Modell " + currentModel + "). "
                        + "Lokale Server brauchen für große Kapitel oft mehrere Minuten. "
                        + "Bitte warten oder agent.openai.request_timeout_sec erhöhen "
                        + "(z. B. 900–1800), Kontext verkleinern oder max. Ausgabe-Tokens senken.", e);
            }
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

        // Reasoning-Felder bewusst NICHT als Content übernehmen: bei Reasoning-Modellen
        // (z. B. Kimi) ist das internes Denken; die fertige Ausgabe kommt per Fortsetzung.
        if (content == null || content.trim().isEmpty()) {
            String reasoning = extractReasoningText(message);
            if (reasoning != null && !reasoning.isBlank()) {
                String finishReason = "";
                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                    finishReason = choice.get("finish_reason").getAsString();
                }
                logger.info("OpenAI API (Modell={}): nur Reasoning vorhanden ({} Zeichen, finish_reason={})",
                        currentModel, reasoning.length(), finishReason);
            }
        }
        return content;
    }

    /** Moonshot/Kimi: {@code reasoning}; andere Provider: {@code reasoning_content}. */
    private static String extractReasoningText(JsonObject message) {
        if (message == null) {
            return null;
        }
        for (String key : new String[]{"reasoning_content", "reasoning"}) {
            if (!message.has(key) || message.get(key).isJsonNull()) {
                continue;
            }
            String text = OpenAIMessageContentExtractor.extractText(message.get(key));
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /** Liest konfigurierbares Anfrage-Timeout (Sekunden). */
    public static int requestTimeoutSeconds() {
        String baseUrl = ResourceManager.getParameter("agent.openai.api_url", DEFAULT_BASE_URL);
        return requestTimeoutSeconds(baseUrl);
    }

    /**
     * Timeout in Sekunden. Lokal (localhost/127.0.0.1): mindestens 900 s, Cap 3600.
     * Cloud: Cap 900.
     */
    public static int requestTimeoutSeconds(String baseUrl) {
        boolean local = isLoopbackOpenAiUrl(baseUrl);
        int maxCap = local ? 3600 : 900;
        int fromAgent = ResourceManager.getIntParameter("agent.openai.request_timeout_sec", -1);
        if (local) {
            int configured = fromAgent >= 60 ? fromAgent : 0;
            if (configured <= 0) {
                int fromLektorat = ResourceManager.getIntParameter("api.lektorat.request_timeout_sec", -1);
                configured = fromLektorat >= 60 ? fromLektorat : LOCAL_DEFAULT_REQUEST_TIMEOUT_SEC;
            }
            // 300 s (früherer Cloud-Default) lokal immer auf mind. 900 anheben
            return Math.min(maxCap, Math.max(LOCAL_DEFAULT_REQUEST_TIMEOUT_SEC, configured));
        }
        if (fromAgent >= 60) {
            return Math.min(maxCap, fromAgent);
        }
        int fromLektorat = ResourceManager.getIntParameter("api.lektorat.request_timeout_sec", 300);
        return Math.max(60, Math.min(maxCap, fromLektorat > 0 ? fromLektorat : 300));
    }

    /**
     * Kimi und DeepSeek v4 denken standardmäßig lange (high/max).
     * Default auto: {@code low}. {@code none} schaltet DeepSeek-Thinking aus.
     */
    private void applyReasoningEffort(JsonObject body) {
        applyReasoningEffort(body, currentModel,
                ResourceManager.getParameter("agent.openai.reasoning_effort", "").trim());
    }

    static void applyReasoningEffort(JsonObject body, String model, String configured) {
        String effort = null;
        String cfg = configured == null ? "" : configured.trim();
        if (!cfg.isEmpty() && !"auto".equalsIgnoreCase(cfg)) {
            effort = cfg.toLowerCase(java.util.Locale.ROOT);
        } else if (isKimiOrMoonshotModel(model) || isDeepSeekModel(model)) {
            effort = "low";
        }
        if (effort == null || effort.isBlank()) {
            return;
        }
        if ("none".equals(effort) || "disabled".equals(effort) || "off".equals(effort)) {
            if (isDeepSeekModel(model)) {
                JsonObject thinking = new JsonObject();
                thinking.addProperty("type", "disabled");
                body.add("thinking", thinking);
            }
            return;
        }
        if (!effort.equals("low") && !effort.equals("high") && !effort.equals("max")) {
            logger.warn("Ungültiger agent.openai.reasoning_effort='{}' — ignoriere", cfg);
            return;
        }
        body.addProperty("reasoning_effort", effort);
        if (isDeepSeekModel(model)) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "enabled");
            body.add("thinking", thinking);
        }
    }

    static boolean isKimiOrMoonshotModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String m = model.toLowerCase(java.util.Locale.ROOT);
        return m.contains("kimi") || m.contains("moonshot");
    }

    static boolean isDeepSeekModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        return model.toLowerCase(java.util.Locale.ROOT).contains("deepseek");
    }

    private CompletionChunk executeChatCompletionOnceStreaming(JsonArray messages, int maxTokens,
                                                               int userMessageLength, Consumer<String> onDelta) {
        try {
            HttpRequest request = buildChatCompletionRequest(messages, maxTokens, true);
            HttpResponse<InputStream> response = sendStreamingWithGatewayRetry(request);
            return readSseCompletion(response.body(), onDelta);
        } catch (RuntimeException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            String baseUrl = ResourceManager.getParameter("agent.openai.api_url", DEFAULT_BASE_URL);
            int timeoutSec = requestTimeoutSeconds(baseUrl);
            logger.error("OpenAI API Stream-Timeout nach {}s (Modell={}). User-Message {} Zeichen.",
                    timeoutSec, currentModel, userMessageLength, e);
            throw new RuntimeException("API-Timeout nach " + timeoutSec + " s (Modell " + currentModel
                    + "). Kontext zu groß oder Modell zu langsam — siehe Log.", e);
        } catch (Exception e) {
            logger.error("OpenAI Stream Fehler (Modell={}): {}", currentModel, e.getMessage(), e);
            throw new RuntimeException("OpenAI Fehler (Modell " + currentModel + "): " + e.getMessage(), e);
        }
    }

    private CompletionChunk readSseCompletion(InputStream body, Consumer<String> onDelta) throws java.io.IOException {
        StringBuilder content = new StringBuilder();
        StringBuilder rawFallback = new StringBuilder();
        String finishReason = "";
        boolean sawSse = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawFallback.append(line).append('\n');
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                    continue;
                }
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                sawSse = true;
                String data = trimmed.substring("data:".length()).trim();
                OpenAIChatCompletionParser.StreamEvent event = OpenAIChatCompletionParser.parseSseData(data);
                if (event.errorMessage() != null && !event.errorMessage().isBlank()) {
                    throw new RuntimeException("API-Fehler (Modell " + currentModel + "): " + event.errorMessage());
                }
                if (event.content() != null && !event.content().isEmpty()) {
                    content.append(event.content());
                    if (onDelta != null) {
                        onDelta.accept(event.content());
                    }
                }
                if (event.finishReason() != null && !event.finishReason().isBlank()) {
                    finishReason = event.finishReason();
                }
                if (event.done()) {
                    break;
                }
            }
        }
        if (!sawSse) {
            return parseNonStreamFallback(rawFallback.toString(), onDelta);
        }
        boolean truncated = isOutputTruncated(finishReason);
        if (truncated) {
            logger.warn("OpenAI Stream: Antwort abgeschnitten (finish_reason={}, {} Zeichen bisher)",
                    finishReason, content.length());
        }
        if (content.isEmpty() && truncated) {
            return new CompletionChunk("", finishReason, true, null);
        }
        return new CompletionChunk(content.toString(), finishReason, truncated, null);
    }

    private CompletionChunk parseNonStreamFallback(String responseBody, Consumer<String> onDelta) {
        JsonElement root;
        try {
            root = OpenAIChatCompletionParser.parseRoot(responseBody);
        } catch (JsonSyntaxException e) {
            logger.error("OpenAI Stream (Modell={}): weder SSE noch JSON. Anfang:\n{}",
                    currentModel, preview(responseBody, 2500), e);
            throw new RuntimeException("API-Antwort ist kein gültiges JSON (Modell "
                    + currentModel + "): " + e.getMessage(), e);
        }
        JsonObject json = OpenAIChatCompletionParser.toCompletionEnvelope(root);
        if (json == null) {
            throw new RuntimeException("API-Antwortformat nicht erkannt (Modell " + currentModel + "). Details im Log.");
        }
        if (json.has("error")) {
            JsonObject error = json.getAsJsonObject("error");
            String errorMsg = error.has("message") ? error.get("message").getAsString() : error.toString();
            throw new RuntimeException("API-Fehler (Modell " + currentModel + "): " + errorMsg);
        }
        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("Keine Antwort von der API erhalten (keine choices, Modell "
                    + currentModel + ")");
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");
        String text = message != null ? extractMessageContent(message, choice, responseBody) : "";
        if (text == null) {
            text = "";
        }
        if (!text.isEmpty() && onDelta != null) {
            onDelta.accept(text);
        }
        String finishReason = "";
        if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
            finishReason = choice.get("finish_reason").getAsString();
        }
        boolean truncated = isOutputTruncated(finishReason);
        return new CompletionChunk(text, finishReason, truncated, extractReasoningText(message));
    }

    private HttpResponse<InputStream> sendStreamingWithGatewayRetry(HttpRequest request)
            throws java.io.IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            return response;
        }
        String errorBody = readStreamAsString(response.body());
        if (!GatewayHttpRetry.isRetryableStatus(response.statusCode())) {
            throw httpError(response.statusCode(), errorBody);
        }
        logger.info("OpenAI Agent Stream: HTTP {} – ein Wiederholungsversuch nach {} ms…",
                response.statusCode(), 1500);
        GatewayHttpRetry.sleepBeforeRetry();
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw httpError(response.statusCode(), readStreamAsString(response.body()));
        }
        return response;
    }

    private static String readStreamAsString(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
                if (sb.length() > 4000) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
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
        return httpError(response.statusCode(), response.body());
    }

    private RuntimeException httpError(int statusCode, String body) {
        logger.error("OpenAI API Fehler {} (Modell={}): {}", statusCode, currentModel,
                preview(body, 2500));
        if (statusCode == 413) {
            return new RuntimeException("OpenAI API Fehler 413: Request body zu groß. "
                    + "Der gesendete Text ist zu lang. Bitte Kontext reduzieren oder Projekt aufteilen.");
        }
        if (statusCode == 524) {
            return new RuntimeException("OpenAI API Fehler 524: Zeitüberschreitung am Gateway. "
                    + "Die Anfrage hat zu lange gedauert. Bitte erneut versuchen oder ein schnelleres Modell wählen.");
        }
        return new RuntimeException("OpenAI API Fehler " + statusCode + ": " + preview(body, 400));
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
