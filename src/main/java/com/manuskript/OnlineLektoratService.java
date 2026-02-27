package com.manuskript;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service für das Online-Lektorat per OpenAI-kompatibler API (chat/completions).
 * Liest API-Key, Basis-URL und Modell aus den Parametern; sendet Kapiteltext mit Lektorat-Prompt
 * und parst die Antwort als JSON-Array (Original, 2–3 Vorschläge, Begründung, Gewichtung).
 * Bei Abbruch (z. B. Timeout) werden bereits verarbeitete Chunks als partielle Ergebnisliste zurückgegeben.
 */
public class OnlineLektoratService {
    private static final Logger logger = LoggerFactory.getLogger(OnlineLektoratService.class);

    /**
     * Ergebnis des Lektorat-Laufs. Bei Abbruch (z. B. Timeout) ist partial=true und
     * getMatches() enthält die Vorschläge aus den bereits verarbeiteten Abschnitten.
     * chunksDone/chunksTotal sind nur bei partial relevant (z. B. 2/4 = 2 von 4 Abschnitte gelesen).
     */
    public static final class LektoratResult {
        private final List<LektoratMatch> matches;
        private final boolean partial;
        private final String errorMessage;
        private final int chunksDone;
        private final int chunksTotal;

        public LektoratResult(List<LektoratMatch> matches, boolean partial, String errorMessage, int chunksDone, int chunksTotal) {
            this.matches = matches != null ? new ArrayList<>(matches) : new ArrayList<>();
            this.partial = partial;
            this.errorMessage = errorMessage;
            this.chunksDone = chunksDone;
            this.chunksTotal = chunksTotal;
        }

        public static LektoratResult full(List<LektoratMatch> matches) {
            return new LektoratResult(matches, false, null, -1, -1);
        }

        public static LektoratResult partial(List<LektoratMatch> matches, String errorMessage, int chunksDone, int chunksTotal) {
            return new LektoratResult(matches, true, errorMessage, chunksDone, chunksTotal);
        }

        public List<LektoratMatch> getMatches() { return matches; }
        public boolean isPartial() { return partial; }
        public String getErrorMessage() { return errorMessage; }
        /** Bei partial: Anzahl bereits gelesener Abschnitte (z. B. 2). Sonst -1. */
        public int getChunksDone() { return chunksDone; }
        /** Bei partial: Gesamtanzahl Abschnitte (z. B. 4). Sonst -1. */
        public int getChunksTotal() { return chunksTotal; }
    }

    private static final int CONNECT_TIMEOUT_SEC = 30;
    private static final int REQUEST_TIMEOUT_SEC_DEFAULT = 300;
    private static final int REQUEST_TIMEOUT_SEC_MIN = 60;
    private static final int REQUEST_TIMEOUT_SEC_MAX = 900;
    private static final int CHUNK_SIZE_MIN = 1000;
    private static final int CHUNK_SIZE_MAX = 100000;
    private static final int CHUNK_SIZE_DEFAULT = 12000;
    private static final int DELAY_BETWEEN_CHUNKS_MS_DEFAULT = 1500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
            .build();

    /**
     * Führt das Lektorat aus (ohne Fortschritts-Callback).
     */
    public CompletableFuture<LektoratResult> runLektorat(String chapterText) {
        return runLektorat(chapterText, null);
    }

    /**
     * Führt das Lektorat für den übergebenen Kapiteltext aus.
     * Bei langen Kapiteln (> MAX_CHARS_PER_REQUEST) werden mehrere Durchläufe gemacht.
     * onChunkProgress wird nach jedem erfolgreich bearbeiteten Abschnitt mit (done, total) aufgerufen.
     * Bei Abbruch (z. B. Timeout) enthält das Ergebnis die bereits verarbeiteten Chunks (partial=true).
     *
     * @param chapterText vollständiger Kapiteltext
     * @param onChunkProgress optionaler Callback (done, total) nach jedem erfolgreich bearbeiteten Abschnitt; wird aus Hintergrund-Thread aufgerufen
     * @return LektoratResult mit allen oder partiellen Matches; bei vollständigem Fehler (z. B. kein API-Key) failed Future
     */
    public CompletableFuture<LektoratResult> runLektorat(String chapterText, BiConsumer<Integer, Integer> onChunkProgress) {
        String apiKey = ResourceManager.getParameter("api.lektorat.api_key", "").trim();
        String baseUrl = ResourceManager.getParameter("api.lektorat.base_url", "https://api.openai.com/v1").trim().replaceAll("/$", "");
        String model = ResourceManager.getParameter("api.lektorat.model", "gpt-4o-mini").trim();

        if (apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API-Key für Online-Lektorat ist nicht gesetzt. Bitte unter Parameter → Online-Lektorat eintragen."));
        }
        if (baseUrl.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Basis-URL für Online-Lektorat ist nicht gesetzt."));
        }
        if (chapterText == null || chapterText.isEmpty()) {
            return CompletableFuture.completedFuture(LektoratResult.full(new ArrayList<>()));
        }

        String extraPrompt = ResourceManager.getParameter("api.lektorat.extra_prompt", "").trim();
        String lektoratType = ResourceManager.getParameter("api.lektorat.type", "allgemein").trim();
        if (lektoratType.isEmpty()) lektoratType = "allgemein";

        int maxCharsPerRequest = parseChunkSize(ResourceManager.getParameter("api.lektorat.chunk_size", String.valueOf(CHUNK_SIZE_DEFAULT)));

        String systemPrompt = buildSystemPrompt(extraPrompt, lektoratType);

        if (chapterText.length() <= maxCharsPerRequest) {
            logger.info("Online-Lektorat: ein Durchlauf (Textlänge={} <= {}), Modell={}, Typ={}", chapterText.length(), maxCharsPerRequest, model.isEmpty() ? "gpt-4o-mini" : model, lektoratType);
            return runOneRequestWithRetry(chapterText, systemPrompt, extraPrompt, apiKey, baseUrl, model, 1, 1)
                    .thenApply(body -> parseResponseAndResolveOffsets(body, chapterText))
                    .thenApply(LektoratResult::full);
        }

        List<String> chunks = splitIntoChunks(chapterText, maxCharsPerRequest);
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(LektoratResult.full(new ArrayList<>()));
        }
        int[] startOffsets = new int[chunks.size()];
        int pos = 0;
        for (int i = 0; i < chunks.size(); i++) {
            startOffsets[i] = pos;
            pos += chunks.get(i).length();
        }
        int firstChunkLen = chunks.get(0).length();
        logger.info("Online-Lektorat: Kapitel in {} Abschnitte geteilt (je max. {} Zeichen), Gesamtlänge={}, 1. Chunk={} Zeichen, Modell={}, Typ={}", chunks.size(), maxCharsPerRequest, chapterText.length(), firstChunkLen, model.isEmpty() ? "gpt-4o-mini" : model, lektoratType);

        return runChunksSequentially(chunks, startOffsets, 0, new ArrayList<>(), systemPrompt, extraPrompt, apiKey, baseUrl, model, onChunkProgress);
    }

    /**
     * Liest die Pause in ms zwischen zwei Chunk-Anfragen (vermeidet Timeouts bei vielen Gateways).
     */
    private static int getDelayBetweenChunksMs() {
        String v = ResourceManager.getParameter("api.lektorat.delay_between_chunks_ms", String.valueOf(DELAY_BETWEEN_CHUNKS_MS_DEFAULT));
        if (v == null || v.isBlank()) return DELAY_BETWEEN_CHUNKS_MS_DEFAULT;
        try {
            int ms = Integer.parseInt(v.trim());
            return Math.max(0, Math.min(30000, ms)); // 0–30 s
        } catch (NumberFormatException e) {
            return DELAY_BETWEEN_CHUNKS_MS_DEFAULT;
        }
    }

    private static int getRequestTimeoutSec() {
        String v = ResourceManager.getParameter("api.lektorat.request_timeout_sec", String.valueOf(REQUEST_TIMEOUT_SEC_DEFAULT));
        if (v == null || v.isBlank()) return REQUEST_TIMEOUT_SEC_DEFAULT;
        try {
            int sec = Integer.parseInt(v.trim());
            return Math.max(REQUEST_TIMEOUT_SEC_MIN, Math.min(REQUEST_TIMEOUT_SEC_MAX, sec));
        } catch (NumberFormatException e) {
            return REQUEST_TIMEOUT_SEC_DEFAULT;
        }
    }

    /**
     * Verarbeitet Chunks strikt nacheinander. Zwischen zwei Anfragen wird optional eine Pause
     * eingefügt (Parameter api.lektorat.delay_between_chunks_ms), um Gateway-/Rate-Limit-Timeouts zu vermeiden.
     * Bei Abbruch wird ein partielles Ergebnis zurückgegeben.
     */
    private CompletableFuture<LektoratResult> runChunksSequentially(
            List<String> chunks, int[] startOffsets, int index,
            List<LektoratMatch> accumulated,
            String systemPrompt, String extraPrompt, String apiKey, String baseUrl, String model,
            BiConsumer<Integer, Integer> onChunkProgress) {
        if (index >= chunks.size()) {
            return CompletableFuture.completedFuture(LektoratResult.full(accumulated));
        }
        String chunk = chunks.get(index);
        int chunkStart = startOffsets[index];
        int chunkNum = index + 1;
        List<LektoratMatch> accumulatedCopy = new ArrayList<>(accumulated);
        int delayMs = getDelayBetweenChunksMs();
        return runOneRequestWithRetry(chunk, systemPrompt, extraPrompt, apiKey, baseUrl, model, chunkNum, chunks.size())
                .thenApply(body -> parseResponseAndResolveOffsets(body, chunk))
                .thenApply(matches -> {
                    for (LektoratMatch m : matches) {
                        m.setOffset(m.getOffset() + chunkStart);
                    }
                    logger.info("Online-Lektorat: Chunk {}/{} abgeschlossen ({} Zeichen), {} Vorschläge", chunkNum, chunks.size(), chunk.length(), matches.size());
                    if (onChunkProgress != null) {
                        onChunkProgress.accept(chunkNum, chunks.size());
                    }
                    List<LektoratMatch> combined = new ArrayList<>(accumulated);
                    combined.addAll(matches);
                    return combined;
                })
                .thenCompose(combined -> {
                    if (delayMs <= 0 || index + 1 >= chunks.size()) {
                        return runChunksSequentially(chunks, startOffsets, index + 1, combined, systemPrompt, extraPrompt, apiKey, baseUrl, model, onChunkProgress);
                    }
                    // Pause vor dem nächsten Request, um Gateway-/Rate-Limit-Timeouts zu vermeiden
                    if (logger.isDebugEnabled()) {
                        logger.debug("Online-Lektorat: Pause {} ms vor Chunk {}/{}", delayMs, index + 2, chunks.size());
                    }
                    return CompletableFuture.completedFuture(combined)
                            .thenApplyAsync(c -> c, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS))
                            .thenCompose(c -> runChunksSequentially(chunks, startOffsets, index + 1, c, systemPrompt, extraPrompt, apiKey, baseUrl, model, onChunkProgress));
                })
                .exceptionally(ex -> {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = t != null ? t.getMessage() : "Unbekannter Fehler";
                    int done = index;
                    logger.warn("Online-Lektorat: Abbruch nach Chunk {}/{} – partielle Ergebnisse werden zurückgegeben. Fehler: {}", chunkNum, chunks.size(), msg);
                    return LektoratResult.partial(accumulatedCopy, msg, done, chunks.size());
                });
    }

    private static int parseChunkSize(String value) {
        if (value == null || value.isBlank()) return CHUNK_SIZE_DEFAULT;
        try {
            int v = Integer.parseInt(value.trim());
            return Math.max(CHUNK_SIZE_MIN, Math.min(CHUNK_SIZE_MAX, v));
        } catch (NumberFormatException e) {
            return CHUNK_SIZE_DEFAULT;
        }
    }

    /**
     * Teilt Text in Abschnitte (vorzugsweise an Zeilenumbrüchen oder Leerzeichen).
     */
    private static List<String> splitIntoChunks(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        int from = 0;
        while (from < text.length()) {
            int to = Math.min(from + maxChars, text.length());
            if (to < text.length()) {
                int minBreak = Math.max(from, to - 400);
                int lastN = text.lastIndexOf('\n', to - 1);
                int lastSpace = text.lastIndexOf(' ', to - 1);
                if (lastN >= minBreak) to = lastN + 1;
                else if (lastSpace >= minBreak) to = lastSpace + 1;
            }
            chunks.add(text.substring(from, to));
            from = to;
        }
        return chunks;
    }

    /**
     * Ein API-Durchlauf mit einmaliger Wiederholung bei 504/Timeout (Gateway bricht oft nach ~60 s ab).
     */
    private CompletableFuture<String> runOneRequestWithRetry(String chunkText, String systemPrompt, String extraPrompt,
                                                              String apiKey, String baseUrl, String model,
                                                              int chunkNum, int totalChunks) {
        return runOneRequest(chunkText, systemPrompt, extraPrompt, apiKey, baseUrl, model)
                .thenApply(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    if (is504OrTimeout(t)) {
                        logger.info("Online-Lektorat: Chunk {}/{} – 504/Timeout, ein Versuch wird wiederholt…", chunkNum, totalChunks);
                        return runOneRequest(chunkText, systemPrompt, extraPrompt, apiKey, baseUrl, model);
                    }
                    return CompletableFuture.<String>failedFuture(t);
                })
                .thenCompose(cf -> cf);
    }

    private static boolean is504OrTimeout(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null && (msg.contains("504") || msg.contains("Timeout") || msg.contains("timeout"))) return true;
        return is504OrTimeout(t.getCause());
    }

    /**
     * Ein API-Durchlauf für einen Textabschnitt (Chunk oder ganzes Kapitel).
     * Verwendet Streaming (stream: true), damit Gateways nicht wegen langer Laufzeit ohne Datenfluss mit 504 abbrechen.
     * Gestreamte Delta-Inhalte werden gesammelt und als eine Antwort für den bestehenden Parser zurückgegeben.
     */
    private CompletableFuture<String> runOneRequest(String chunkText, String systemPrompt, String extraPrompt,
                                                     String apiKey, String baseUrl, String model) {
        logger.info("Online-Lektorat: runOneRequest sendet genau {} Zeichen (Chunk-Länge), Streaming=an", chunkText.length());
        String userPrompt = buildUserPrompt(chunkText, extraPrompt);
        JsonObject body = new JsonObject();
        body.addProperty("model", model.isEmpty() ? "gpt-4o-mini" : model);
        body.addProperty("temperature", 0.3);
        body.addProperty("max_tokens", 16384);
        body.addProperty("stream", true);
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);
        messages.add(sys);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);
        messages.add(user);
        body.add("messages", messages);

        String url = baseUrl + "/chat/completions";
        String bodyStr = new Gson().toJson(body);
        int timeoutSec = getRequestTimeoutSec();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSec))
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8))
                .build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
                int code = response.statusCode();
                List<String> lines;
                try (Stream<String> stream = response.body()) {
                    lines = stream.collect(Collectors.toList());
                }
                if (code != 200) {
                    String errBody = String.join("\n", lines);
                    if (code == 504) {
                        throw new RuntimeException("Gateway Timeout (504): Die Anfrage hat zu lange gedauert. Tipp: Ein schnelleres/kleineres Modell wählen, oder das Kapitel ist zu lang – es wird automatisch in Abschnitte geteilt.");
                    }
                    throw new RuntimeException("API antwortete mit HTTP " + code + ": " + (errBody != null && errBody.length() > 200 ? errBody.substring(0, 200) + "…" : errBody));
                }
                StringBuilder content = new StringBuilder();
                for (String line : lines) {
                    if (line == null || !line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonObject obj = new Gson().fromJson(data, JsonObject.class);
                        if (obj == null || !obj.has("choices") || !obj.getAsJsonArray("choices").isJsonArray()) continue;
                        JsonArray choices = obj.getAsJsonArray("choices");
                        if (choices.size() == 0) continue;
                        JsonObject choice = choices.get(0).getAsJsonObject();
                        if (!choice.has("delta")) continue;
                        JsonObject delta = choice.getAsJsonObject("delta");
                        if (delta.has("content") && !delta.get("content").isJsonNull()) {
                            content.append(delta.get("content").getAsString());
                        }
                    } catch (Exception e) {
                        logger.trace("Online-Lektorat: Stream-Zeile ignoriert: {}", e.getMessage());
                    }
                }
                String fullContent = content.toString();
                logger.info("Online-Lektorat: Streaming abgeschlossen, Content-Länge={}", fullContent.length());
                if (fullContent.isEmpty()) {
                    return "{\"choices\":[{\"message\":{\"content\":\"\"}}]}";
                }
                String escapedContent = new Gson().toJson(fullContent);
                return "{\"choices\":[{\"message\":{\"content\":" + escapedContent + "}}]}";
            } catch (Exception e) {
                logger.warn("Online-Lektorat API-Fehler", e);
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private static String buildSystemPrompt(String extraPrompt, String lektoratType) {
        String focus = "";
        switch (lektoratType.toLowerCase()) {
            case "stil": focus = " Fokus: Stil, Ton, Wortwahl, Rhythmus. "; break;
            case "grammatik": focus = " Fokus: Grammatik, Rechtschreibung, Zeichensetzung. "; break;
            case "plot": focus = " Fokus: Plot, Dramaturgie, Spannungsbogen, Figurenzeichnung. "; break;
            default: focus = " ";
        }
        String base = "Du agierst als sehr erfahrener, kritischer deutscher Lektor. "
                + "Du analysierst den gegebenen Text ohne Schonung und nutzt alle Register eines professionellen Lektorats "
                + "(orthografische Präzision, stilistische Wirkung, Logikprüfung, Kohärenz, Tonalität, Figurenzeichnung, Tempo, Szenendramaturgie)."
                + focus
                + "Antworte AUSSCHLIESSLICH mit einem JSON-Array. Kein anderer Text außer dem JSON. "
                + "Jedes Element des Arrays hat genau diese Felder: "
                + "\"original\" (der unveränderte Zitat-Text aus dem Kapitel), "
                + "\"suggestions\" (Array mit 2 bis 3 verbesserten Versionen dieses Abschnitts), "
                + "\"reason\" (kurze Begründung für die Änderung), "
                + "\"weight\" (Zahl 1–5: wie wichtig die Änderung ist, 5 = sehr wichtig). "
                + "Jedes \"original\" muss ein exakter Abschnitt aus dem übergebenen Kapiteltext sein (wörtlich übernommen, inkl. Zeilenumbrüche).";
        if (extraPrompt != null && !extraPrompt.isEmpty()) {
            base = base + "\n\nZusätzliche Anweisungen:\n" + extraPrompt;
        }
        return base;
    }

    private static String buildUserPrompt(String chapterText, String extraPrompt) {
        String s = "Analysiere den folgenden Kapiteltext und gib Verbesserungsvorschläge als JSON-Array zurück. "
                + "Jeder Eintrag: original (exakter Textausschnitt inkl. Zeilenumbrüche), suggestions (2–3 Alternativen), reason, weight (1–5).\n\n"
                + "=== KAPITELTEXT ===\n" + chapterText;
        if (extraPrompt != null && !extraPrompt.isEmpty()) {
            s = s + "\n\n(Zusatzanweisungen siehe System-Prompt.)";
        }
        return s;
    }

    /**
     * Parst den API-Inhalt (LLM-Antwort) als JSON-Array von Lektorat-Einträgen.
     * Akzeptiert: reines Array; ein Objekt mit "original"/"suggestions" (ein Eintrag);
     * oder ein Objekt, das ein Array unter einem gängigen Key enthält (items, feedback, suggestions, results, data).
     */
    private JsonArray parseContentAsArray(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            JsonElement parsed = new Gson().fromJson(content, JsonElement.class);
            if (parsed == null) return null;
            if (parsed.isJsonArray()) return parsed.getAsJsonArray();
            if (!parsed.isJsonObject()) return null;
            JsonObject obj = parsed.getAsJsonObject();
            // Einzelnes Vorschlag-Objekt (original, suggestions, reason, weight)?
            if (obj.has("original")) {
                JsonArray single = new JsonArray();
                single.add(obj);
                return single;
            }
            // Objekt mit Array unter bekanntem Key
            for (String key : new String[]{"items", "feedback", "suggestions", "results", "data", "corrections", "lektorat", "edits", "matches", "changes"}) {
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    logger.debug("Online-Lektorat: Array unter Key \"{}\" verwendet", key);
                    return obj.getAsJsonArray(key);
                }
            }
            // Fallback: erstes Array unter beliebigem Key
            for (String key : obj.keySet()) {
                if (obj.get(key).isJsonArray()) {
                    logger.info("Online-Lektorat: Array unter Key \"{}\" verwendet (nicht in Standardliste)", key);
                    return obj.getAsJsonArray(key);
                }
            }
            logger.warn("Online-Lektorat: Unbekanntes JSON-Format (Objekt ohne original/Array-Key), Keys: {}", obj.keySet());
            return null;
        } catch (Exception e) {
            logger.warn("Online-Lektorat: parseContentAsArray fehlgeschlagen: {}", e.getMessage(), e);
            // Bei abgeschnittenem JSON (Array): nutze nur bis zum letzten vollständigen Objekt "},"
            String trimmed = content.trim();
            if (trimmed.startsWith("[")) {
                int lastComplete = trimmed.lastIndexOf("},");
                if (lastComplete > 0) {
                    String repaired = trimmed.substring(0, lastComplete + 1) + "]";
                    try {
                        JsonElement p = new Gson().fromJson(repaired, JsonElement.class);
                        if (p != null && p.isJsonArray()) {
                            logger.info("Online-Lektorat: abgeschnittenes JSON repariert (bis letztes vollständiges Element), {} Einträge", p.getAsJsonArray().size());
                            return p.getAsJsonArray();
                        }
                    } catch (Exception e2) {
                        logger.debug("Online-Lektorat: Reparatur fehlgeschlagen", e2);
                    }
                }
                if (!trimmed.endsWith("]")) {
                    try {
                        JsonElement p = new Gson().fromJson(trimmed + "]", JsonElement.class);
                        if (p != null && p.isJsonArray()) {
                            logger.info("Online-Lektorat: abgeschnittenes JSON durch ']' repariert");
                            return p.getAsJsonArray();
                        }
                    } catch (Exception e2) {
                        logger.debug("Online-Lektorat: Reparatur mit ']' fehlgeschlagen", e2);
                    }
                }
            }
            return null;
        }
    }

    private List<LektoratMatch> parseResponseAndResolveOffsets(String responseBody, String chapterText) {
        List<LektoratMatch> out = new ArrayList<>();
        try {
            JsonObject root = new Gson().fromJson(responseBody, JsonObject.class);
            if (root == null || !root.has("choices")) {
                logger.warn("Online-Lektorat: Antwort ohne choices");
                return out;
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() == 0) {
                return out;
            }
            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.has("message") ? firstChoice.getAsJsonObject("message") : null;
            if (message == null) {
                logger.warn("Online-Lektorat: choices[0] hat kein message");
                return out;
            }
            JsonElement contentEl = message.get("content");
            if (contentEl == null) contentEl = message.get("text");
            String content = (contentEl != null && contentEl.isJsonPrimitive()) ? contentEl.getAsString() : null;
            if (content == null || content.isBlank()) {
                logger.warn("Online-Lektorat: message ohne content/text");
                return out;
            }
            content = content.trim();
            // Markdown-Codeblock oder Fließtext mit eingebettetem JSON: erstes [ oder { bis passendes ] oder }
            if (content.startsWith("```")) {
                int start = content.indexOf('{');
                int startArr = content.indexOf('[');
                if (startArr >= 0 && (start < 0 || startArr < start)) start = startArr;
                if (start < 0) start = content.indexOf('{');
                int end = content.lastIndexOf('}');
                int endArr = content.lastIndexOf(']');
                if (endArr > end) end = endArr;
                if (start >= 0 && end > start) content = content.substring(start, end + 1);
            } else if (!content.startsWith("[") && !content.startsWith("{")) {
                int start = content.indexOf('[');
                int startObj = content.indexOf('{');
                if (start < 0 || (startObj >= 0 && startObj < start)) start = startObj;
                int end = start >= 0 ? (content.charAt(start) == '[' ? content.lastIndexOf(']') : content.lastIndexOf('}')) : -1;
                if (start >= 0 && end > start) content = content.substring(start, end + 1);
            }
            String contentPreview = content.length() > 400 ? content.substring(0, 400) + "…" : content;
            logger.info("Online-Lektorat: content (Anfang, {} Zeichen) = {}", content.length(), contentPreview);
            JsonArray arr = parseContentAsArray(content);
            if (arr == null) {
                logger.warn("Online-Lektorat: parseContentAsArray lieferte null – keine Einträge aus API-Antwort");
                return out;
            }
            logger.info("Online-Lektorat: {} Einträge aus JSON, prüfe Offset im Text…", arr.size());

            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String original = o.has("original") ? o.get("original").getAsString() : "";
                if (original.isBlank()) continue;

                List<String> suggestions = new ArrayList<>();
                if (o.has("suggestions") && o.get("suggestions").isJsonArray()) {
                    for (JsonElement s : o.getAsJsonArray("suggestions")) {
                        if (s.isJsonPrimitive()) suggestions.add(s.getAsString());
                    }
                }
                String reason = o.has("reason") ? o.get("reason").getAsString() : "";
                int weight = o.has("weight") ? o.get("weight").getAsInt() : 3;
                weight = Math.max(1, Math.min(5, weight));

                int offset = chapterText.indexOf(original);
                if (offset < 0) {
                    logger.info("Online-Lektorat: Original nicht im Text gefunden, überspringe (Länge {}): \"{}\"", original.length(), original.length() > 60 ? original.substring(0, 60).replace("\n", " ") + "…" : original.replace("\n", " "));
                    continue;
                }
                int length = original.length();
                out.add(new LektoratMatch(offset, length, original, suggestions, reason, weight));
            }
            logger.info("Online-Lektorat: {} Vorschläge nach Offset-Prüfung übernommen", out.size());
        } catch (Exception e) {
            logger.warn("Online-Lektorat: Parse-Fehler", e);
            throw new RuntimeException("Antwort der API konnte nicht gelesen werden: " + e.getMessage(), e);
        }
        return out;
    }
}
