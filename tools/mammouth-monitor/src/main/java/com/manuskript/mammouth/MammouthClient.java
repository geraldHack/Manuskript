package com.manuskript.mammouth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * HTTP-Client für Mammouth {@code /key/info} und {@code /public/models}.
 */
public class MammouthClient {

    public static final String DEFAULT_BASE = "https://api.mammouth.ai/v1";
    public static final String PUBLIC_MODELS_URL = "https://api.mammouth.ai/public/models";
    public static final String DASHBOARD_URL = "https://mammouth.ai/app/account/settings/api";

    private final String baseUrl;
    private String apiKey;
    private final HttpClient httpClient;

    public MammouthClient(String baseUrl, String apiKey) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public static String normalizeBaseUrl(String url) {
        String normalized = url == null || url.isBlank() ? DEFAULT_BASE : url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /** LiteLLM hängt {@code /key/info} typischerweise an die Host-Wurzel, nicht unter {@code /v1}. */
    public static String hostRoot(String baseUrl) {
        String base = normalizeBaseUrl(baseUrl);
        if (base.toLowerCase(Locale.ROOT).endsWith("/v1")) {
            return base.substring(0, base.length() - 3);
        }
        return base;
    }

    public record KeyInfo(
            String keyAlias,
            String keyName,
            Double spend,
            Double maxBudget,
            Double softBudget,
            String budgetDuration,
            String budgetResetAt,
            List<String> allowedModels,
            String expires,
            String rawJson
    ) {
        public Double remaining() {
            if (maxBudget == null || spend == null) {
                return null;
            }
            return maxBudget - spend;
        }
    }

    public record ModelInfo(String id, Double inputPerMillion, Double outputPerMillion) {
    }

    public static class ApiException extends Exception {
        private final int statusCode;

        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isAuthError() {
            return statusCode == 401 || statusCode == 403;
        }
    }

    public KeyInfo getKeyInfo() throws IOException, InterruptedException, ApiException {
        if (!hasApiKey()) {
            throw new ApiException(403, "API-Key fehlt");
        }
        IOException lastIo = null;
        ApiException lastApi = null;
        for (String url : keyInfoUrls()) {
            try {
                HttpResponse<String> response = sendGet(url, true);
                if (response.statusCode() == 200) {
                    return parseKeyInfo(response.body());
                }
                lastApi = new ApiException(response.statusCode(),
                        extractErrorMessage(response.body(), response.statusCode()));
                if (response.statusCode() != 404) {
                    throw lastApi;
                }
            } catch (ApiException e) {
                lastApi = e;
                if (e.getStatusCode() != 404) {
                    throw e;
                }
            } catch (IOException e) {
                lastIo = e;
            }
        }
        if (lastApi != null) {
            throw lastApi;
        }
        if (lastIo != null) {
            throw lastIo;
        }
        throw new ApiException(404, "key/info nicht erreichbar");
    }

    public List<ModelInfo> getPublicModels() throws IOException, InterruptedException, ApiException {
        HttpResponse<String> response = sendGet(PUBLIC_MODELS_URL, false);
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(),
                    extractErrorMessage(response.body(), response.statusCode()));
        }
        return parseModels(response.body());
    }

    private List<String> keyInfoUrls() {
        String host = hostRoot(baseUrl);
        List<String> urls = new ArrayList<>();
        urls.add(host + "/key/info");
        if (!baseUrl.equals(host)) {
            urls.add(baseUrl + "/key/info");
        }
        return urls;
    }

    private HttpResponse<String> sendGet(String url, boolean authorize)
            throws IOException, InterruptedException, ApiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30));
        if (authorize) {
            if (!hasApiKey()) {
                throw new ApiException(403, "API-Key fehlt");
            }
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static KeyInfo parseKeyInfo(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject info = root;
        if (root.has("info") && root.get("info").isJsonObject()) {
            info = root.getAsJsonObject("info");
        }
        String alias = firstString(info, "key_alias", "key_name", "alias", "name");
        String keyName = firstString(info, "key_name", "key_alias");
        List<String> models = stringList(info.get("models"));
        return new KeyInfo(
                alias,
                keyName,
                nullableDouble(info, "spend"),
                nullableDouble(info, "max_budget"),
                nullableDouble(info, "soft_budget"),
                firstString(info, "budget_duration"),
                firstString(info, "budget_reset_at", "budget_reset", "expires"),
                models,
                firstString(info, "expires"),
                body
        );
    }

    static List<ModelInfo> parseModels(String body) {
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
        List<ModelInfo> models = new ArrayList<>();
        for (JsonElement el : data) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            String id = firstString(obj, "id", "model_name", "model");
            if (id.isBlank()) {
                continue;
            }
            Double input = costPerMillion(obj, true);
            Double output = costPerMillion(obj, false);
            models.add(new ModelInfo(id, input, output));
        }
        models.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return models;
    }

    private static Double costPerMillion(JsonObject model, boolean input) {
        String tokenKey = input ? "input_cost_per_token" : "output_cost_per_token";
        String millionKey = input ? "input_cost_per_million" : "output_cost_per_million";
        if (model.has("model_info") && model.get("model_info").isJsonObject()) {
            JsonObject info = model.getAsJsonObject("model_info");
            Double perToken = nullableDouble(info, tokenKey);
            if (perToken != null) {
                return perToken * 1_000_000d;
            }
            Double perMillion = nullableDouble(info, millionKey);
            if (perMillion != null) {
                return perMillion;
            }
        }
        if (model.has("pricing") && model.get("pricing").isJsonObject()) {
            JsonObject pricing = model.getAsJsonObject("pricing");
            String priceKey = input ? "prompt" : "completion";
            Double perToken = nullableDouble(pricing, priceKey);
            if (perToken != null) {
                return perToken * 1_000_000d;
            }
        }
        return nullableDouble(model, tokenKey);
    }

    private static List<String> stringList(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (item != null && item.isJsonPrimitive()) {
                String value = item.getAsString();
                if (value != null && !value.isBlank()) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private static String firstString(JsonObject obj, String... keys) {
        if (obj == null) {
            return "";
        }
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                try {
                    String value = obj.get(key).getAsString();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }

    private static Double nullableDouble(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        try {
            return el.getAsDouble();
        } catch (Exception e) {
            try {
                return Double.parseDouble(el.getAsString());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static String extractErrorMessage(String body, int status) {
        if (body == null || body.isBlank()) {
            return "HTTP " + status;
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                JsonElement err = root.get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    return err.getAsJsonObject().get("message").getAsString();
                }
                if (err.isJsonPrimitive()) {
                    return err.getAsString();
                }
            }
            if (root.has("detail") && root.get("detail").isJsonPrimitive()) {
                return root.get("detail").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + status + ": " + body.substring(0, Math.min(200, body.length()));
    }

    public static String formatUsd(Double value) {
        if (value == null) {
            return "—";
        }
        return String.format(Locale.GERMANY, "$%.4f", value);
    }

    public static String formatPerMillion(Double value) {
        if (value == null) {
            return "—";
        }
        return String.format(Locale.GERMANY, "$%.2f / 1M", value);
    }
}
