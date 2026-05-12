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

    public OpenAIBackend() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
        this.currentModel = ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini");
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
                body.addProperty("temperature", 0.3);

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

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.error("OpenAI API Fehler {}: {}", response.statusCode(), response.body());
                    throw new RuntimeException("OpenAI API Fehler " + response.statusCode() + ": " + response.body());
                }

                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices != null && choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message != null && message.has("content") && !message.get("content").isJsonNull()) {
                        return message.get("content").getAsString();
                    }
                }

                throw new RuntimeException("Keine Antwort von OpenAI erhalten");

            } catch (Exception e) {
                logger.error("OpenAI chat Fehler", e);
                throw new RuntimeException("OpenAI Fehler: " + e.getMessage(), e);
            }
        });
    }
}
