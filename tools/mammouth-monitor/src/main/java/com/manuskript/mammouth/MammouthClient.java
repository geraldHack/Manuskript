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
import java.time.LocalDate;
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
            Double explicitRemaining,
            String budgetDuration,
            String budgetResetAt,
            List<String> allowedModels,
            String expires,
            String rawJson
    ) {
        public Double remaining() {
            if (explicitRemaining != null) {
                return explicitRemaining;
            }
            if (maxBudget == null) {
                return null;
            }
            return maxBudget - (spend == null ? 0d : spend);
        }

        boolean hasBalanceNumbers() {
            return spend != null || maxBudget != null || explicitRemaining != null;
        }
    }

    public record ModelInfo(String id, Double inputPerMillion, Double outputPerMillion) {
    }

    public record SpendLog(
            String requestId,
            String model,
            String startedAt,
            Double spend,
            Long totalTokens,
            Long promptTokens,
            Long completionTokens
    ) {
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
        Exception lastError = null;
        KeyInfo fromKey = null;
        KeyInfo fromUser = null;
        try {
            fromKey = firstUsefulInfo(infoUrls("/key/info"), MammouthClient::parseKeyInfo);
        } catch (ApiException | IOException e) {
            lastError = e;
        }
        try {
            fromUser = firstUsefulInfo(infoUrls("/user/info"), MammouthClient::parseUserInfo);
        } catch (ApiException | IOException e) {
            if (lastError == null) {
                lastError = e;
            }
        }
        KeyInfo merged = mergeKeyInfo(fromKey, fromUser);
        if (merged != null) {
            return merged;
        }
        if (lastError instanceof ApiException api) {
            throw api;
        }
        if (lastError instanceof IOException io) {
            throw io;
        }
        throw new ApiException(404, "key/info und user/info ohne Guthaben-Daten");
    }

    public List<SpendLog> getSpendLogs(int days) throws IOException, InterruptedException, ApiException {
        if (!hasApiKey()) {
            throw new ApiException(403, "API-Key fehlt");
        }
        int window = Math.max(1, Math.min(days, 90));
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(window - 1L);
        String startDate = start.toString();
        String endDate = end.toString();
        ApiException lastApi = null;
        IOException lastIo = null;
        List<SpendLog> lastEmpty = null;
        for (String url : spendLogUrls(startDate, endDate)) {
            try {
                HttpResponse<String> response = sendGet(url, true);
                if (response.statusCode() == 200) {
                    List<SpendLog> logs = url.contains("/user/daily/activity")
                            ? parseDailyActivity(response.body())
                            : parseSpendLogs(response.body());
                    if (logs != null && !logs.isEmpty()) {
                        return logs;
                    }
                    lastEmpty = logs != null ? logs : List.of();
                    continue;
                }
                lastApi = new ApiException(response.statusCode(),
                        extractErrorMessage(response.body(), response.statusCode()));
            } catch (ApiException e) {
                lastApi = e;
            } catch (IOException e) {
                lastIo = e;
            }
        }
        if (lastEmpty != null) {
            return lastEmpty;
        }
        if (lastApi != null) {
            throw lastApi;
        }
        if (lastIo != null) {
            throw lastIo;
        }
        throw new ApiException(404, "Spend-Logs nicht erreichbar");
    }

    public List<ModelInfo> getPublicModels() throws IOException, InterruptedException, ApiException {
        HttpResponse<String> response = sendGet(PUBLIC_MODELS_URL, false);
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(),
                    extractErrorMessage(response.body(), response.statusCode()));
        }
        return parseModels(response.body());
    }

    private List<String> infoUrls(String path) {
        String host = hostRoot(baseUrl);
        List<String> urls = new ArrayList<>();
        if (!baseUrl.equals(host)) {
            urls.add(baseUrl + path);
        }
        urls.add(host + path);
        return urls;
    }

    private List<String> spendLogUrls(String startDate, String endDate) {
        String query = "?start_date=" + startDate + "&end_date=" + endDate;
        List<String> urls = new ArrayList<>();
        for (String root : infoUrls("")) {
            urls.add(root + "/user/daily/activity" + query);
            urls.add(root + "/spend/logs" + query + "&summarize=false");
            urls.add(root + "/spend/logs/v2" + query + "&page=1&page_size=100");
        }
        return urls;
    }

    @FunctionalInterface
    private interface InfoParser {
        KeyInfo parse(String body);
    }

    private KeyInfo firstUsefulInfo(List<String> urls, InfoParser parser)
            throws IOException, InterruptedException, ApiException {
        if (!hasApiKey()) {
            throw new ApiException(403, "API-Key fehlt");
        }
        IOException lastIo = null;
        ApiException lastApi = null;
        KeyInfo lastEmpty = null;
        for (String url : urls) {
            try {
                HttpResponse<String> response = sendGet(url, true);
                if (response.statusCode() == 200) {
                    try {
                        KeyInfo info = parser.parse(response.body());
                        if (info != null && info.hasBalanceNumbers()) {
                            return info;
                        }
                        lastEmpty = info;
                    } catch (RuntimeException e) {
                        lastApi = new ApiException(200, "Ungültige Antwort: " + e.getMessage());
                    }
                    continue;
                }
                lastApi = new ApiException(response.statusCode(),
                        extractErrorMessage(response.body(), response.statusCode()));
            } catch (ApiException e) {
                lastApi = e;
            } catch (IOException e) {
                lastIo = e;
            }
        }
        if (lastEmpty != null) {
            return lastEmpty;
        }
        if (lastApi != null) {
            throw lastApi;
        }
        if (lastIo != null) {
            throw lastIo;
        }
        return null;
    }

    static KeyInfo mergeKeyInfo(KeyInfo primary, KeyInfo fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }
        return new KeyInfo(
                firstNonBlank(primary.keyAlias(), fallback.keyAlias()),
                firstNonBlank(primary.keyName(), fallback.keyName()),
                primary.spend() != null ? primary.spend() : fallback.spend(),
                primary.maxBudget() != null ? primary.maxBudget() : fallback.maxBudget(),
                primary.softBudget() != null ? primary.softBudget() : fallback.softBudget(),
                primary.explicitRemaining() != null ? primary.explicitRemaining() : fallback.explicitRemaining(),
                firstNonBlank(primary.budgetDuration(), fallback.budgetDuration()),
                firstNonBlank(primary.budgetResetAt(), fallback.budgetResetAt()),
                (primary.allowedModels() != null && !primary.allowedModels().isEmpty())
                        ? primary.allowedModels() : fallback.allowedModels(),
                firstNonBlank(primary.expires(), fallback.expires()),
                primary.rawJson() != null ? primary.rawJson() : fallback.rawJson()
        );
    }

    private HttpResponse<String> sendGet(String url, boolean authorize)
            throws IOException, InterruptedException, ApiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "Manuskript-MammouthMonitor/1.0.2")
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
        return keyInfoFromObject(info, body);
    }

    static KeyInfo parseUserInfo(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject info = root;
        if (root.has("user_info") && root.get("user_info").isJsonObject()) {
            info = root.getAsJsonObject("user_info");
        } else if (root.has("info") && root.get("info").isJsonObject()) {
            info = root.getAsJsonObject("info");
        }
        return keyInfoFromObject(info, body);
    }

    private static KeyInfo keyInfoFromObject(JsonObject info, String body) {
        JsonObject budgetTable = nestedObject(info, "litellm_budget_table");
        JsonObject metadata = nestedObject(info, "metadata");
        Double spend = firstDouble(info, "spend", "total_spend", "usage", "total_usage");
        Double maxBudget = firstDouble(info, "max_budget", "max_budget_in_usd", "budget",
                "credit_limit", "credits");
        if (maxBudget == null) {
            maxBudget = firstDouble(budgetTable, "max_budget", "max_budget_in_usd", "budget");
        }
        if (maxBudget == null) {
            maxBudget = firstDouble(metadata, "base_budget", "max_budget", "budget");
        }
        Double remaining = firstDouble(info, "remaining", "remaining_budget", "remaining_credits",
                "credit_balance", "balance");
        if (remaining == null) {
            remaining = firstDouble(budgetTable, "remaining", "remaining_budget");
        }
        if (remaining == null) {
            remaining = firstDouble(metadata, "remaining", "credit_balance");
        }
        String alias = firstString(info, "key_alias", "key_name", "alias", "name", "user_email", "user_id");
        String keyName = firstString(info, "key_name", "key_alias");
        List<String> models = stringList(info.get("models"));
        return new KeyInfo(
                alias,
                keyName,
                spend,
                maxBudget,
                firstDouble(info, "soft_budget"),
                remaining,
                firstString(info, "budget_duration"),
                firstString(info, "budget_reset_at", "budget_reset", "expires"),
                models,
                firstString(info, "expires"),
                body
        );
    }

    static List<SpendLog> parseSpendLogs(String body) {
        JsonElement parsed = JsonParser.parseString(body);
        JsonArray data = jsonArrayOf(parsed, "data", "logs");
        List<SpendLog> logs = new ArrayList<>();
        if (data == null) {
            return logs;
        }
        for (JsonElement el : data) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            String requestId = firstString(obj, "request_id", "id", "generation_id");
            String model = firstString(obj, "model", "model_group", "model_id");
            String started = firstString(obj, "startTime", "start_time", "started_at", "created_at",
                    "start_date", "date");
            Double spend = firstDouble(obj, "spend", "total_spend", "cost");
            Long tokens = nullableLong(obj, "total_tokens");
            Long prompt = nullableLong(obj, "prompt_tokens");
            Long completion = nullableLong(obj, "completion_tokens");
            if (requestId.isBlank() && model.isBlank() && started.isBlank() && spend == null) {
                continue;
            }
            logs.add(new SpendLog(requestId, model, started, spend, tokens, prompt, completion));
        }
        return logs;
    }

    static List<SpendLog> parseDailyActivity(String body) {
        JsonElement parsed = JsonParser.parseString(body);
        JsonArray results = jsonArrayOf(parsed, "results", "data");
        List<SpendLog> logs = new ArrayList<>();
        if (results == null) {
            return logs;
        }
        for (JsonElement dayEl : results) {
            if (dayEl == null || !dayEl.isJsonObject()) {
                continue;
            }
            JsonObject day = dayEl.getAsJsonObject();
            String date = firstString(day, "date", "start_date", "startTime");
            JsonObject breakdown = nestedObject(day, "breakdown");
            JsonObject models = nestedObject(breakdown, "model_groups");
            if (models == null) {
                models = nestedObject(breakdown, "models");
            }
            if (models != null) {
                for (String modelId : models.keySet()) {
                    JsonObject metrics = metricsObject(models.get(modelId));
                    if (metrics == null) {
                        continue;
                    }
                    logs.add(new SpendLog(
                            "",
                            shortModelName(modelId),
                            date,
                            firstDouble(metrics, "spend"),
                            nullableLong(metrics, "total_tokens"),
                            nullableLong(metrics, "prompt_tokens"),
                            nullableLong(metrics, "completion_tokens")));
                }
                continue;
            }
            JsonObject metrics = metricsObject(day);
            logs.add(new SpendLog(
                    "",
                    firstString(day, "model"),
                    date,
                    firstDouble(metrics != null ? metrics : day, "spend"),
                    nullableLong(metrics != null ? metrics : day, "total_tokens"),
                    nullableLong(metrics != null ? metrics : day, "prompt_tokens"),
                    nullableLong(metrics != null ? metrics : day, "completion_tokens")));
        }
        return logs;
    }

    private static JsonArray jsonArrayOf(JsonElement parsed, String... objectKeys) {
        if (parsed == null || parsed.isJsonNull()) {
            return null;
        }
        if (parsed.isJsonArray()) {
            return parsed.getAsJsonArray();
        }
        if (!parsed.isJsonObject()) {
            return null;
        }
        JsonObject obj = parsed.getAsJsonObject();
        for (String key : objectKeys) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                return obj.getAsJsonArray(key);
            }
        }
        return null;
    }

    private static JsonObject nestedObject(JsonObject parent, String key) {
        if (parent == null || key == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static JsonObject metricsObject(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        JsonObject obj = el.getAsJsonObject();
        JsonObject nested = nestedObject(obj, "metrics");
        return nested != null ? nested : obj;
    }

    static String shortModelName(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return "";
        }
        int slash = modelId.lastIndexOf('/');
        return slash >= 0 && slash < modelId.length() - 1 ? modelId.substring(slash + 1) : modelId;
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
        models.sort(MammouthClient::compareByCost);
        return models;
    }

    static int compareByCost(ModelInfo a, ModelInfo b) {
        int byInput = compareCost(a.inputPerMillion(), b.inputPerMillion());
        if (byInput != 0) {
            return byInput;
        }
        int byOutput = compareCost(a.outputPerMillion(), b.outputPerMillion());
        if (byOutput != 0) {
            return byOutput;
        }
        return a.id().compareToIgnoreCase(b.id());
    }

    static int compareCost(Double a, Double b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return Double.compare(a, b);
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

    private static Double firstDouble(JsonObject obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Double value = nullableDouble(obj, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Long nullableLong(JsonObject obj, String key) {
        Double value = nullableDouble(obj, key);
        return value == null ? null : value.longValue();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
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
        if (Math.abs(value) >= 1d) {
            return String.format(Locale.GERMANY, "%.2f $", value);
        }
        return String.format(Locale.GERMANY, "%.4f $", value);
    }

    public static String formatPerMillion(Double value) {
        if (value == null) {
            return "—";
        }
        return String.format(Locale.GERMANY, "$%.2f / 1M", value);
    }
}
