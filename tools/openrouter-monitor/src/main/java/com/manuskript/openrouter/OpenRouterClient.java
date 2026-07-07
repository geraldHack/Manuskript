package com.manuskript.openrouter;

import com.google.gson.Gson;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * HTTP-Client für OpenRouter Management- und Key-Endpunkte.
 */
public class OpenRouterClient {

    private static final Gson GSON = new Gson();

    private final String baseUrl;
    private final String inferenceApiKey;
    private String managementApiKey;
    private final HttpClient httpClient;

    public OpenRouterClient(String baseUrl, String inferenceApiKey) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.inferenceApiKey = inferenceApiKey == null ? "" : inferenceApiKey.trim();
        this.managementApiKey = "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void setManagementApiKey(String managementApiKey) {
        this.managementApiKey = managementApiKey == null ? "" : managementApiKey.trim();
    }

    public boolean hasManagementApiKey() {
        return managementApiKey != null && !managementApiKey.isBlank();
    }

    private static String normalizeBaseUrl(String url) {
        String normalized = url == null || url.isBlank() ? "https://openrouter.ai/api/v1" : url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static class KeyInfo {
        private final String label;
        private final Double limit;
        private final Double limitRemaining;
        private final double usage;
        private final double usageDaily;
        private final double usageWeekly;
        private final double usageMonthly;
        private final boolean freeTier;

        public KeyInfo(String label, Double limit, Double limitRemaining, double usage,
                       double usageDaily, double usageWeekly, double usageMonthly, boolean freeTier) {
            this.label = label;
            this.limit = limit;
            this.limitRemaining = limitRemaining;
            this.usage = usage;
            this.usageDaily = usageDaily;
            this.usageWeekly = usageWeekly;
            this.usageMonthly = usageMonthly;
            this.freeTier = freeTier;
        }

        public String getLabel() { return label; }
        public Double getLimit() { return limit; }
        public Double getLimitRemaining() { return limitRemaining; }
        public double getUsage() { return usage; }
        public double getUsageDaily() { return usageDaily; }
        public double getUsageWeekly() { return usageWeekly; }
        public double getUsageMonthly() { return usageMonthly; }
        public boolean isFreeTier() { return freeTier; }
    }

    public static class AccountCredits {
        private final double totalCredits;
        private final double totalUsage;

        public AccountCredits(double totalCredits, double totalUsage) {
            this.totalCredits = totalCredits;
            this.totalUsage = totalUsage;
        }

        public double getTotalCredits() { return totalCredits; }
        public double getTotalUsage() { return totalUsage; }
        public double getRemaining() { return totalCredits - totalUsage; }
    }

    public static class LogEntry {
        private final String generationId;
        private final String model;
        private final String createdAt;
        private final double totalUsage;
        private final long requestCount;
        private final long tokensTotal;

        public LogEntry(String generationId, String model, String createdAt,
                        double totalUsage, long requestCount, long tokensTotal) {
            this.generationId = generationId;
            this.model = model;
            this.createdAt = createdAt;
            this.totalUsage = totalUsage;
            this.requestCount = requestCount;
            this.tokensTotal = tokensTotal;
        }

        public String getGenerationId() { return generationId; }
        public String getModel() { return model; }
        public String getCreatedAt() { return createdAt; }
        public double getTotalUsage() { return totalUsage; }
        public long getRequestCount() { return requestCount; }
        public long getTokensTotal() { return tokensTotal; }

        public LogEntry withDetails(String model, String createdAt) {
            String mergedModel = (model != null && !model.isBlank()) ? model : this.model;
            String mergedCreatedAt = (createdAt != null && !createdAt.isBlank()) ? createdAt : this.createdAt;
            return new LogEntry(generationId, mergedModel, mergedCreatedAt, totalUsage, requestCount, tokensTotal);
        }
    }

    public static class GenerationDetail {
        private final String id;
        private final String model;
        private final Double totalCost;
        private final Long tokensPrompt;
        private final Long tokensCompletion;
        private final String createdAt;
        private final String rawJson;

        public GenerationDetail(String id, String model, Double totalCost, Long tokensPrompt,
                                Long tokensCompletion, String createdAt, String rawJson) {
            this.id = id;
            this.model = model;
            this.totalCost = totalCost;
            this.tokensPrompt = tokensPrompt;
            this.tokensCompletion = tokensCompletion;
            this.createdAt = createdAt;
            this.rawJson = rawJson;
        }

        public String getId() { return id; }
        public String getModel() { return model; }
        public Double getTotalCost() { return totalCost; }
        public Long getTokensPrompt() { return tokensPrompt; }
        public Long getTokensCompletion() { return tokensCompletion; }
        public String getCreatedAt() { return createdAt; }
        public String getRawJson() { return rawJson; }
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
        JsonObject data = getJsonObject("/key", false).getAsJsonObject("data");
        String label = stringOrEmpty(data, "label");
        Double limit = nullableDouble(data, "limit");
        Double limitRemaining = nullableDouble(data, "limit_remaining");
        double usage = doubleValue(data, "usage");
        double usageDaily = doubleValue(data, "usage_daily");
        double usageWeekly = doubleValue(data, "usage_weekly");
        double usageMonthly = doubleValue(data, "usage_monthly");
        boolean freeTier = data.has("is_free_tier") && data.get("is_free_tier").getAsBoolean();
        return new KeyInfo(label, limit, limitRemaining, usage, usageDaily, usageWeekly, usageMonthly, freeTier);
    }

    public AccountCredits getAccountCredits() throws IOException, InterruptedException, ApiException {
        JsonObject data = getJsonObject("/credits", true).getAsJsonObject("data");
        double totalCredits = doubleValue(data, "total_credits");
        double totalUsage = doubleValue(data, "total_usage");
        return new AccountCredits(totalCredits, totalUsage);
    }

    public static class LogsQueryResult {
        private final List<LogEntry> entries;
        private final int rowCount;
        private final boolean truncated;

        public LogsQueryResult(List<LogEntry> entries, int rowCount, boolean truncated) {
            this.entries = entries;
            this.rowCount = rowCount;
            this.truncated = truncated;
        }

        public List<LogEntry> getEntries() { return entries; }
        public int getRowCount() { return rowCount; }
        public boolean isTruncated() { return truncated; }
    }

    public LogsQueryResult queryLogsWithMeta(int days, int limit) throws IOException, InterruptedException, ApiException {
        if (!hasManagementApiKey()) {
            throw new ApiException(403, "Management API Key fehlt");
        }
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofDays(Math.max(1, days)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("metrics", List.of("total_usage", "request_count", "tokens_total"));
        body.put("dimensions", List.of("generation_id", "model"));
        body.put("time_range", Map.of(
                "start", toApiDateTime(start),
                "end", toApiDateTime(end)
        ));
        body.put("order_by", Map.of("field", "total_usage", "direction", "desc"));
        body.put("limit", Math.max(1, Math.min(limit, 1000)));

        String jsonBody = GSON.toJson(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/analytics/query"))
                .header("Authorization", "Bearer " + requireManagementKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), extractErrorMessage(response.body(), response.statusCode()));
        }
        LogsQueryResult result = parseAnalyticsLogResponseWithMeta(response.body());
        List<LogEntry> enriched = enrichLogEntries(result.getEntries());
        List<LogEntry> sorted = sortByNewestFirst(enriched);
        return new LogsQueryResult(sorted, result.getRowCount(), result.isTruncated());
    }

    private static List<LogEntry> sortByNewestFirst(List<LogEntry> entries) {
        return entries.stream()
                .sorted((a, b) -> {
                    Instant timeA = parseInstantOrNull(a.getCreatedAt());
                    Instant timeB = parseInstantOrNull(b.getCreatedAt());
                    if (timeA == null && timeB == null) {
                        return 0;
                    }
                    if (timeA == null) {
                        return 1;
                    }
                    if (timeB == null) {
                        return -1;
                    }
                    return timeB.compareTo(timeA);
                })
                .toList();
    }

    private static Instant parseInstantOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(raw.trim()));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public List<LogEntry> enrichLogEntries(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return entries;
        }
        LogEntry[] enriched = new LogEntry[entries.size()];
        ExecutorService pool = Executors.newFixedThreadPool(12, r -> {
            Thread t = new Thread(r, "openrouter-enrich");
            t.setDaemon(true);
            return t;
        });
        try {
            CompletableFuture<?>[] tasks = new CompletableFuture[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                final int index = i;
                final LogEntry entry = entries.get(i);
                tasks[i] = CompletableFuture.runAsync(() -> enriched[index] = enrichSingleEntry(entry), pool);
            }
            CompletableFuture.allOf(tasks).get(120, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            for (int i = 0; i < entries.size(); i++) {
                if (enriched[i] == null) {
                    enriched[i] = entries.get(i);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return List.of(enriched);
    }

    private LogEntry enrichSingleEntry(LogEntry entry) {
        try {
            GenerationDetail detail = getGeneration(entry.getGenerationId());
            return entry.withDetails(detail.getModel(), detail.getCreatedAt());
        } catch (Exception e) {
            return entry;
        }
    }

    public List<LogEntry> queryLogs(int days, int limit) throws IOException, InterruptedException, ApiException {
        return queryLogsWithMeta(days, limit).getEntries();
    }

    private LogsQueryResult parseAnalyticsLogResponseWithMeta(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray rows = extractAnalyticsRows(root);
        AnalyticsQueryInfo meta = parseAnalyticsMeta(root);

        List<LogEntry> entries = new ArrayList<>();
        for (JsonElement rowEl : rows) {
            if (!rowEl.isJsonObject()) continue;
            JsonObject row = rowEl.getAsJsonObject();
            String generationId = dimensionValue(row, "generation_id");
            if (generationId.isBlank()) continue;
            String model = dimensionValue(row, "model");
            String createdAt = dimensionValue(row, "created_at");
            double usage = metricValue(row, "total_usage");
            long requestCount = (long) metricValue(row, "request_count");
            long tokensTotal = (long) metricValue(row, "tokens_total");
            entries.add(new LogEntry(generationId, model, createdAt, usage, requestCount, tokensTotal));
        }
        int rowCount = meta.getRowCount() > 0 ? meta.getRowCount() : entries.size();
        return new LogsQueryResult(entries, rowCount, meta.isTruncated());
    }

    private List<LogEntry> parseAnalyticsLogResponse(String body) {
        return parseAnalyticsLogResponseWithMeta(body).getEntries();
    }

    private static JsonArray extractAnalyticsRows(JsonObject root) {
        if (!root.has("data") || root.get("data").isJsonNull()) {
            return new JsonArray();
        }
        JsonElement dataEl = root.get("data");
        if (dataEl.isJsonArray()) {
            return dataEl.getAsJsonArray();
        }
        if (dataEl.isJsonObject()) {
            JsonObject dataObj = dataEl.getAsJsonObject();
            if (dataObj.has("data") && dataObj.get("data").isJsonArray()) {
                return dataObj.getAsJsonArray("data");
            }
        }
        return new JsonArray();
    }

    private static AnalyticsQueryInfo parseAnalyticsMeta(JsonObject root) {
        try {
            if (!root.has("data") || !root.get("data").isJsonObject()) {
                return AnalyticsQueryInfo.empty();
            }
            JsonObject dataObj = root.getAsJsonObject("data");
            if (!dataObj.has("metadata") || !dataObj.get("metadata").isJsonObject()) {
                return AnalyticsQueryInfo.empty();
            }
            JsonObject meta = dataObj.getAsJsonObject("metadata");
            int rowCount = meta.has("row_count") ? meta.get("row_count").getAsInt() : 0;
            boolean truncated = meta.has("truncated") && meta.get("truncated").getAsBoolean();
            return new AnalyticsQueryInfo(rowCount, truncated);
        } catch (Exception e) {
            return AnalyticsQueryInfo.empty();
        }
    }

    private static class AnalyticsQueryInfo {
        private final int rowCount;
        private final boolean truncated;

        public AnalyticsQueryInfo(int rowCount, boolean truncated) {
            this.rowCount = rowCount;
            this.truncated = truncated;
        }

        public static AnalyticsQueryInfo empty() {
            return new AnalyticsQueryInfo(0, false);
        }

        public int getRowCount() { return rowCount; }
        public boolean isTruncated() { return truncated; }
    }

    public GenerationDetail getGeneration(String generationId) throws IOException, InterruptedException, ApiException {
        if (generationId == null || generationId.isBlank()) {
            throw new IllegalArgumentException("generation_id fehlt");
        }
        String url = baseUrl + "/generation?id=" + java.net.URLEncoder.encode(generationId, StandardCharsets.UTF_8);
        HttpRequest request = authorizedGet(url, Duration.ofSeconds(30), false);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), extractErrorMessage(response.body(), response.statusCode()));
        }
        String body = response.body();
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject data = root.has("data") && root.get("data").isJsonObject()
                ? root.getAsJsonObject("data") : root;
        String id = stringOrEmpty(data, "id");
        if (id.isEmpty()) id = generationId;
        String model = stringOrEmpty(data, "model");
        Double totalCost = nullableDouble(data, "total_cost");
        if (totalCost == null) totalCost = nullableDouble(data, "usage");
        Long tokensPrompt = nullableLong(data, "tokens_prompt");
        Long tokensCompletion = nullableLong(data, "tokens_completion");
        String createdAt = stringOrEmpty(data, "created_at");
        if (createdAt.isEmpty() && data.has("created") && !data.get("created").isJsonNull()) {
            try {
                JsonElement createdEl = data.get("created");
                long epochSeconds = createdEl.getAsLong();
                createdAt = Instant.ofEpochSecond(epochSeconds).toString();
            } catch (Exception ignored) {
            }
        }
        return new GenerationDetail(id, model, totalCost, tokensPrompt, tokensCompletion, createdAt, body);
    }

    private JsonObject getJsonObject(String path, boolean management) throws IOException, InterruptedException, ApiException {
        HttpRequest request = authorizedGet(baseUrl + path, Duration.ofSeconds(30), management);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), extractErrorMessage(response.body(), response.statusCode()));
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private HttpRequest authorizedGet(String url, Duration timeout, boolean management) throws ApiException {
        String key = management ? requireManagementKey() : inferenceApiKey;
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + key)
                .header("Accept", "application/json")
                .GET()
                .timeout(timeout)
                .build();
    }

    private String requireManagementKey() throws ApiException {
        if (!hasManagementApiKey()) {
            throw new ApiException(403, "Management API Key fehlt");
        }
        return managementApiKey;
    }

    private static String dimensionValue(JsonObject row, String name) {
        if (row.has(name) && !row.get(name).isJsonNull()) {
            return row.get(name).getAsString();
        }
        if (row.has("dimensions") && row.get("dimensions").isJsonObject()) {
            JsonObject dims = row.getAsJsonObject("dimensions");
            if (dims.has(name) && !dims.get(name).isJsonNull()) {
                return dims.get(name).getAsString();
            }
        }
        return "";
    }

    private static double metricValue(JsonObject row, String name) {
        JsonElement el = row.get(name);
        if (el == null || el.isJsonNull()) {
            if (row.has("metrics") && row.get("metrics").isJsonObject()) {
                el = row.getAsJsonObject("metrics").get(name);
            }
        }
        if (el == null || el.isJsonNull()) return 0;
        if (el.isJsonPrimitive()) {
            try {
                return el.getAsDouble();
            } catch (NumberFormatException e) {
                try {
                    return Double.parseDouble(el.getAsString());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String stringOrEmpty(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private static double doubleValue(JsonObject obj, String key) {
        Double v = nullableDouble(obj, key);
        return v == null ? 0 : v;
    }

    private static Double nullableDouble(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement el = obj.get(key);
        try {
            return el.getAsDouble();
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(el.getAsString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static Long nullableLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement el = obj.get(key);
        try {
            return el.getAsLong();
        } catch (NumberFormatException e) {
            try {
                return Long.parseLong(el.getAsString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static String toApiDateTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
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
        } catch (Exception ignored) {
        }
        return "HTTP " + status + ": " + body.substring(0, Math.min(200, body.length()));
    }

    public static String formatUsd(double value) {
        return String.format(Locale.GERMANY, "$%.4f", value);
    }
}
