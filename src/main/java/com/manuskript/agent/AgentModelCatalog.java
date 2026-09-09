package com.manuskript.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.manuskript.OllamaService;
import com.manuskript.ParameterRegistry;
import com.manuskript.ResourceManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modellliste aus den globalen Agent-Parametern (gleicher Provider wie in der Parameter-Verwaltung).
 */
public final class AgentModelCatalog {

    public static final String MAMMOUTH_PUBLIC_MODELS = "https://api.mammouth.ai/public/models";

    private AgentModelCatalog() {
    }

    public static List<String> loadFromParameters() throws Exception {
        String backend = ResourceManager.getParameter("agent.backend", "Ollama");
        if ("OpenAI".equals(backend)) {
            return fetchOpenAiCompatible(
                    ResourceManager.getParameter("agent.openai.api_key", ""),
                    ResourceManager.getParameter("agent.openai.api_url", "https://api.openai.com/v1"));
        }
        return loadOllamaModels();
    }

    public static List<String> loadOllamaModels() throws Exception {
        String[] models = new OllamaService().getAvailableModels().get();
        if (models == null || models.length == 0) {
            return List.of(ParameterRegistry.DEFAULT_OLLAMA_MODEL);
        }
        List<String> out = new ArrayList<>();
        for (String model : models) {
            if (model != null && !model.isBlank()) {
                out.add(model.trim());
            }
        }
        return out;
    }

    public static List<String> fetchOpenAiCompatible(String apiKey, String baseUrl)
            throws IOException, InterruptedException {
        String url = modelsUrl(baseUrl);
        String key = OpenAIBackend.resolveApiKey(apiKey, baseUrl);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAIBackend.requireHttpUri(url))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("User-Agent", "Manuskript-AgentModelCatalog/1.0")
                .GET();
        if (key != null && !key.isBlank()) {
            builder.header("Authorization", "Bearer " + key);
        }
        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " von " + url);
        }
        List<String> ids = parseModelIds(response.body());
        if (ids.isEmpty()) {
            throw new IOException("Keine Modelle in der Antwort von " + url);
        }
        return ids;
    }

    public static String modelsUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.toLowerCase(Locale.ROOT).contains("mammouth.ai")) {
            return MAMMOUTH_PUBLIC_MODELS;
        }
        if (base.isBlank()) {
            base = "https://api.openai.com/v1";
        }
        return base + "/models";
    }

    public static List<String> parseModelIds(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonElement parsed = JsonParser.parseString(body);
        JsonArray data;
        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("data")
                && parsed.getAsJsonObject().get("data").isJsonArray()) {
            data = parsed.getAsJsonObject().getAsJsonArray("data");
        } else if (parsed.isJsonArray()) {
            data = parsed.getAsJsonArray();
        } else {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement el : data) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            String id = firstString(obj, "id", "model_name", "model");
            if (!id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private static String firstString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                try {
                    String value = obj.get(key).getAsString();
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }
}
