package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Client für die ElevenLabs Text-to-Speech API.
 * Stimmen abrufen (GET /v1/voices), Synthese (POST /v1/text-to-speech/{voice_id}) → Audio in Datei.
 */
public class ElevenLabsClient {

    private static final Logger logger = LoggerFactory.getLogger(ElevenLabsClient.class);

    public static final String DEFAULT_BASE_URL = "https://api.elevenlabs.io";
    public static final String DEFAULT_MODEL_ID = "eleven_multilingual_v2";

    /** Bekannte Model-IDs für das Modell-Dropdown (Reihenfolge = Anzeige). */
    public static final String[] KNOWN_MODEL_IDS = {
            "eleven_v3",
            "eleven_multilingual_v2",
            "eleven_turbo_v2_5",
            "eleven_flash_v2_5",
            "eleven_turbo_v2",
            "eleven_flash_v2"
    };

    private final String baseUrl;
    private String apiKey = "";

    public ElevenLabsClient() {
        this(DEFAULT_BASE_URL);
    }

    public ElevenLabsClient(String baseUrl) {
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim().replaceAll("/+$", "") : DEFAULT_BASE_URL;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public String getApiKey() {
        return apiKey;
    }

    /** Einfaches DTO für eine ElevenLabs-Stimme. */
    public static class ElevenLabsVoice {
        private final String id;
        private final String name;

        public ElevenLabsVoice(String id, String name) {
            this.id = id;
            this.name = name != null ? name : "";
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    /** DTO für Abo-Info (Guthaben: Zeichen verbraucht / Limit). */
    public static class SubscriptionInfo {
        private final int characterCount;
        private final int characterLimit;
        private final Long nextCharacterCountResetUnix;
        private final String tier;

        public SubscriptionInfo(int characterCount, int characterLimit, Long nextCharacterCountResetUnix, String tier) {
            this.characterCount = characterCount;
            this.characterLimit = characterLimit;
            this.nextCharacterCountResetUnix = nextCharacterCountResetUnix;
            this.tier = tier != null ? tier : "";
        }

        public int getCharacterCount() { return characterCount; }
        public int getCharacterLimit() { return characterLimit; }
        /** Verbleibende Zeichen im aktuellen Abrechnungszeitraum. */
        public int getCharactersRemaining() { return Math.max(0, characterLimit - characterCount); }
        public Long getNextCharacterCountResetUnix() { return nextCharacterCountResetUnix; }
        public String getTier() { return tier; }
    }

    /**
     * Ruft Abo-Informationen inkl. Zeichen-Guthaben ab (GET /v1/user/subscription).
     * @return SubscriptionInfo mit character_count, character_limit, optional next_reset; null bei Fehler
     */
    public SubscriptionInfo getSubscription() throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("ElevenLabs API-Key ist nicht gesetzt (Parameter-Verwaltung: tts.elevenlabs_api_key).");
        }
        String url = baseUrl + "/v1/user/subscription";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        String body = response.body();
        if (status != 200) {
            throw new IOException("HTTP " + status);
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int characterCount = root.has("character_count") && !root.get("character_count").isJsonNull() ? root.get("character_count").getAsInt() : 0;
            int characterLimit = root.has("character_limit") && !root.get("character_limit").isJsonNull() ? root.get("character_limit").getAsInt() : 0;
            Long nextReset = null;
            if (root.has("next_character_count_reset_unix") && !root.get("next_character_count_reset_unix").isJsonNull()) {
                nextReset = root.get("next_character_count_reset_unix").getAsLong();
            }
            String tier = root.has("tier") && !root.get("tier").isJsonNull() ? root.get("tier").getAsString() : "";
            return new SubscriptionInfo(characterCount, characterLimit, nextReset, tier);
        } catch (Exception e) {
            logger.warn("ElevenLabs subscription response parse error: {}", e.getMessage());
            throw new IOException("ElevenLabs Subscription-Response konnte nicht gelesen werden: " + e.getMessage(), e);
        }
    }

    /**
     * Ruft alle verfügbaren Stimmen ab (GET /v1/voices).
     * @return Liste der Stimmen (id, name)
     */
    public List<ElevenLabsVoice> getVoices() throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("ElevenLabs API-Key ist nicht gesetzt (Parameter-Verwaltung: tts.elevenlabs_api_key).");
        }
        String url = baseUrl + "/v1/voices";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        String body = response.body();
        if (status != 200) {
            throw new IOException("ElevenLabs voices: HTTP " + status + " – " + body);
        }
        List<ElevenLabsVoice> list = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray voices = root.getAsJsonArray("voices");
            if (voices != null) {
                for (JsonElement el : voices) {
                    JsonObject v = el.getAsJsonObject();
                    String id = v.has("voice_id") ? v.get("voice_id").getAsString() : (v.has("id") ? v.get("id").getAsString() : null);
                    String name = v.has("name") && !v.get("name").isJsonNull() ? v.get("name").getAsString() : (id != null ? id : "Unbekannt");
                    if (id != null && !id.isBlank()) {
                        list.add(new ElevenLabsVoice(id, name));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("ElevenLabs voices response parse error: {}", e.getMessage());
            throw new IOException("ElevenLabs Stimmen-Response konnte nicht gelesen werden: " + e.getMessage(), e);
        }
        return list;
    }

    /** Optionale Voice-Einstellungen für TTS (Stabilität, Similarity, Geschwindigkeit, …). */
    public static class VoiceSettings {
        public final double stability;
        public final double similarityBoost;
        public final double speed;
        public final boolean useSpeakerBoost;
        public final double style;

        public VoiceSettings(double stability, double similarityBoost, double speed, boolean useSpeakerBoost, double style) {
            this.stability = Math.max(0, Math.min(1, stability));
            this.similarityBoost = Math.max(0, Math.min(1, similarityBoost));
            this.speed = Math.max(0.7, Math.min(1.2, speed));
            this.useSpeakerBoost = useSpeakerBoost;
            this.style = Math.max(0, Math.min(1, style));
        }
    }

    /**
     * Erzeugt Sprachausgabe und schreibt sie in die angegebene Datei.
     * @param text Text (nach Lexikon-Anwendung)
     * @param voiceId ElevenLabs voice_id
     * @param modelId model_id (z. B. eleven_multilingual_v2), null = DEFAULT_MODEL_ID
     * @param outputPath Ziel-Pfad (z. B. .mp3)
     * @param voiceSettings optionale Stimm-Parameter (Stabilität, Geschwindigkeit, …); null = API-Defaults
     */
    public void generateToFile(String text, String voiceId, String modelId, Path outputPath, VoiceSettings voiceSettings) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("ElevenLabs API-Key ist nicht gesetzt (Parameter-Verwaltung: tts.elevenlabs_api_key).");
        }
        if (voiceId == null || voiceId.isBlank()) {
            throw new IOException("ElevenLabs voice_id fehlt.");
        }
        String effectiveModel = (modelId != null && !modelId.isBlank()) ? modelId.trim() : DEFAULT_MODEL_ID;
        String url = baseUrl + "/v1/text-to-speech/" + voiceId + "?output_format=mp3_44100_128";
        JsonObject body = new JsonObject();
        body.addProperty("text", text != null ? text : "");
        body.addProperty("model_id", effectiveModel);
        if (voiceSettings != null) {
            JsonObject vs = new JsonObject();
            vs.addProperty("stability", voiceSettings.stability);
            vs.addProperty("similarity_boost", voiceSettings.similarityBoost);
            vs.addProperty("speed", voiceSettings.speed);
            vs.addProperty("use_speaker_boost", voiceSettings.useSpeakerBoost);
            vs.addProperty("style", voiceSettings.style);
            body.add("voice_settings", vs);
        }
        String bodyStr = body.toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        byte[] bytes = response.body();
        if (status != 200) {
            String msg = bytes != null && bytes.length > 0 ? new String(bytes, StandardCharsets.UTF_8) : ("HTTP " + status);
            if (status == 401) {
                throw new IOException("ElevenLabs: Ungültiger API-Key oder nicht autorisiert. " + msg);
            }
            if (status == 429) {
                throw new IOException("ElevenLabs: Quota überschritten oder Rate-Limit. " + msg);
            }
            throw new IOException("ElevenLabs TTS: HTTP " + status + " – " + msg);
        }
        if (bytes == null || bytes.length == 0) {
            throw new IOException("ElevenLabs TTS: Leere Antwort.");
        }
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, bytes);
        logger.debug("ElevenLabs TTS geschrieben: {} ({} bytes)", outputPath, bytes.length);
    }
}
