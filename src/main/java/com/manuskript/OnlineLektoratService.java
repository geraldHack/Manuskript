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

/**
 * Service für das Online-Lektorat per OpenAI-kompatibler API (chat/completions).
 * Liest API-Key, Basis-URL und Modell aus den Parametern; sendet Kapiteltext mit Lektorat-Prompt
 * und parst die Antwort als JSON-Array (Original, 2–3 Vorschläge, Begründung, Gewichtung).
 */
public class OnlineLektoratService {
    private static final Logger logger = LoggerFactory.getLogger(OnlineLektoratService.class);

    private static final int CONNECT_TIMEOUT_SEC = 30;
    private static final int REQUEST_TIMEOUT_SEC = 120;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
            .build();

    /**
     * Führt das Lektorat für den übergebenen Kapiteltext aus.
     * Ermittelt offset/length für jedes Match per indexOf(original) im Text.
     *
     * @param chapterText vollständiger Kapiteltext
     * @return Liste der Lektorat-Matches (mit offset/length gesetzt) oder bei Fehler leere Liste / Exception
     */
    public CompletableFuture<List<LektoratMatch>> runLektorat(String chapterText) {
        String apiKey = ResourceManager.getParameter("api.lektorat.api_key", "").trim();
        String baseUrl = ResourceManager.getParameter("api.lektorat.base_url", "https://api.openai.com/v1").trim().replaceAll("/$", "");
        String model = ResourceManager.getParameter("api.lektorat.model", "gpt-4o-mini").trim();

        if (apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API-Key für Online-Lektorat ist nicht gesetzt. Bitte unter Parameter → Online-Lektorat eintragen."));
        }
        if (baseUrl.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Basis-URL für Online-Lektorat ist nicht gesetzt."));
        }

        String extraPrompt = ResourceManager.getParameter("api.lektorat.extra_prompt", "").trim();
        String lektoratType = ResourceManager.getParameter("api.lektorat.type", "allgemein").trim();
        if (lektoratType.isEmpty()) lektoratType = "allgemein";

        logger.info("Online-Lektorat: Start (Modell={}, Textlänge={}, Typ={})", model.isEmpty() ? "gpt-4o-mini" : model, chapterText != null ? chapterText.length() : 0, lektoratType);
        String systemPrompt = buildSystemPrompt(extraPrompt, lektoratType);
        String userPrompt = buildUserPrompt(chapterText, extraPrompt);

        JsonObject body = new JsonObject();
        body.addProperty("model", model.isEmpty() ? "gpt-4o-mini" : model);
        body.addProperty("temperature", 0.3);
        body.addProperty("max_tokens", 16384);
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8))
                .build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = response.statusCode();
                if (code != 200) {
                    String errBody = response.body();
                    if (code == 504) {
                        throw new RuntimeException("Gateway Timeout (504): Die Anfrage hat zu lange gedauert. Tipp: Ein schnelleres/kleineres Modell wählen (z. B. mistral-small statt großer Modelle), dann unter Parameter → Online-Lektorat das Modell wechseln.");
                    }
                    throw new RuntimeException("API antwortete mit HTTP " + code + ": " + (errBody != null && errBody.length() > 200 ? errBody.substring(0, 200) + "…" : errBody));
                }
                String responseBody = response.body();
                logger.info("Online-Lektorat: API-Antwort erhalten, Länge={}", responseBody != null ? responseBody.length() : 0);
                return responseBody;
            } catch (Exception e) {
                logger.warn("Online-Lektorat API-Fehler", e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }).thenApply(responseBody -> parseResponseAndResolveOffsets(responseBody, chapterText));
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
