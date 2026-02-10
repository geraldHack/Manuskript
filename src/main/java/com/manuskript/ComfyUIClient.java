package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Client für die ComfyUI-API (Qwen3-TTS und andere Workflows).
 * Zur späteren Verwendung im TTS-/Hörbuch-Modul.
 *
 * <p><b>Aussprache:</b> Qwen3-TTS unterstützt <i>kein</i> IPA, SSML oder Phonem-API.
 * Anweisungen wie „Sprich Cache englisch aus“ oder /kɛʃ/ werden ignoriert.
 * Einzige zuverlässige Methode: <b>Text-Ersetzung</b> vor dem Senden (Lexikon: Wort → Aussprache-Schreibweise).
 *
 * <p><b>Stimmkonsistenz:</b> VoiceDesign erzeugt aus der Beschreibung eine Stimme – kann pro Lauf variieren.
 * Für <i>dieselbe</i> Stimme: „Konsistente Stimme“ nutzen (CustomVoice mit festem Sprecher) oder Voice Clone.
 */
public class ComfyUIClient {

    private static final Logger logger = LoggerFactory.getLogger(ComfyUIClient.class);

    /** Standard-URL des ComfyUI-Servers (wird auch als Default in der Parameter-Verwaltung verwendet). */
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:8188";

    /**
     * Liest die ComfyUI-Basis-URL aus der Konfiguration (Parameter-Verwaltung / parameters.properties).
     */
    public static String getBaseUrlFromConfig() {
        String url = ResourceManager.getParameter("comfyui.base_url", DEFAULT_BASE_URL);
        if (url == null || url.isBlank()) return DEFAULT_BASE_URL;
        return url.trim();
    }
    /** Standard-Stil: deutsch, ohne amerikanischen Akzent, neutrales Hochdeutsch. */
    public static final String DEFAULT_INSTRUCT_DEUTSCH =
            "Deutsch. Neutrale deutsche Stimme, Hochdeutsch, ohne amerikanischen oder englischen Akzent. "
            + "Warm, ruhig, natürlich. Kurze Pausen an Kommas. Keine Überbetonung.";
    /** Abstand zwischen zwei History-Abfragen (weniger = mehr CPU, mehr = schonender – reduziert Systemlast beim Warten). */
    private static final int POLL_INTERVAL_MS = 2500;
    /** Wartezeit auf ComfyUI-Abschluss (z. B. TTS 1.7B kann bei langem Text dauern). */
    private static final int DEFAULT_TIMEOUT_SECONDS = 600;

    /** Konfigurationsdatei für das Aussprache-Lexikon (im Projekt unter config/). */
    public static final String PRONUNCIATION_LEXICON_PATH = "config/tts-pronunciation.json";

    /** Konfigurationsdatei für gespeicherte TTS-Stimmen (Name, Seed, Temperatur, Stimmbeschreibung). */
    public static final String TTS_VOICES_PATH = "config/tts-voices.json";

    /** Konfigurationsdatei für zuletzt verwendete Stimmbeschreibungen (Dropdown). */
    public static final String TTS_RECENT_DESCRIPTIONS_PATH = "config/tts-recent-descriptions.json";

    /** Datei für den zuletzt eingegebenen „zu sprechenden Text“ (wird beim Schließen des TTS-Fensters gespeichert). */
    public static final String TTS_LAST_TEXT_PATH = "config/tts-last-text.txt";

    /** Datei für den Suchtext in der Stimmsuche (wird beim Schließen des TTS-Fensters gespeichert). */
    public static final String TTS_VOICE_SEARCH_SAMPLE_PATH = "config/tts-voice-search-sample.txt";

    /** Lädt den zuletzt gespeicherten Suchtext der Stimmsuche. Liefert null oder leeren String, wenn keine Datei existiert. */
    public static String loadVoiceSearchSampleText() {
        Path path = Paths.get(TTS_VOICE_SEARCH_SAMPLE_PATH);
        if (!Files.isRegularFile(path)) return null;
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Stimmsuche-Suchtext konnte nicht geladen werden: {}", e.getMessage());
            return null;
        }
    }

    /** Speichert den Suchtext der Stimmsuche (wird beim Schließen des TTS-Fensters aufgerufen). */
    public static void saveVoiceSearchSampleText(String text) {
        if (text == null) text = "";
        try {
            Path path = Paths.get(TTS_VOICE_SEARCH_SAMPLE_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Stimmsuche-Suchtext konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    /** Lädt den zuletzt gespeicherten Text für TTS. Liefert null oder leeren String, wenn keine Datei existiert. */
    public static String loadLastSpokenText() {
        Path path = Paths.get(TTS_LAST_TEXT_PATH);
        if (!Files.isRegularFile(path)) return null;
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("TTS-Last-Text konnte nicht geladen werden: {}", e.getMessage());
            return null;
        }
    }

    /** Speichert den zu sprechenden Text (wird beim Schließen des TTS-Fensters aufgerufen). */
    public static void saveLastSpokenText(String text) {
        if (text == null) text = "";
        try {
            Path path = Paths.get(TTS_LAST_TEXT_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("TTS-Last-Text konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    /** Vorschlag für männlichen Hörbuch-Vorleser (Stimmbeschreibung). */
    public static final String DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK =
            "Männlich, tief, ruhig, professioneller Vorleser, Hörbuch, angenehm, klar artikuliert.";

    private static final int MAX_RECENT_DESCRIPTIONS = 15;

    /** Lädt zuletzt verwendete Stimmbeschreibungen für die Dropdown-Liste. */
    public static java.util.List<String> loadRecentVoiceDescriptions() {
        Path path = Paths.get(TTS_RECENT_DESCRIPTIONS_PATH);
        if (!Files.isRegularFile(path)) {
            java.util.List<String> def = new java.util.ArrayList<>();
            def.add(DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
            return def;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            java.util.List<String> list = new Gson().fromJson(reader, new TypeToken<java.util.List<String>>() {}.getType());
            if (list != null && !list.isEmpty()) return list;
        } catch (Exception e) {
            logger.warn("Recent Stimmbeschreibungen konnten nicht geladen werden: {}", e.getMessage());
        }
        java.util.List<String> def = new java.util.ArrayList<>();
        def.add(DEFAULT_VOICE_DESCRIPTION_MALE_AUDIOBOOK);
        return def;
    }

    /** Hängt eine verwendete Stimmbeschreibung vorne an die Recent-Liste an und speichert. */
    public static void addRecentVoiceDescription(String description) {
        if (description == null || description.isBlank()) return;
        String trimmed = description.trim();
        java.util.List<String> list = new java.util.ArrayList<>(loadRecentVoiceDescriptions());
        list.remove(trimmed);
        list.add(0, trimmed);
        while (list.size() > MAX_RECENT_DESCRIPTIONS) list.remove(list.size() - 1);
        try {
            Path path = Paths.get(TTS_RECENT_DESCRIPTIONS_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, new Gson().toJson(list), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Recent Stimmbeschreibungen speichern fehlgeschlagen: {}", e.getMessage());
        }
    }

    /** Standard top_p / top_k falls nicht gesetzt (ComfyUI-Qwen3-TTS). */
    public static final double DEFAULT_TOP_P = 1.0;
    public static final int DEFAULT_TOP_K = 46;
    /** Standard Repetition-Penalty (ComfyUI-Qwen3-TTS). */
    public static final double DEFAULT_REPETITION_PENALTY = 1.1;

    /** Default-CustomVoice-Sprecher (falls kein anderer gesetzt ist). */
    public static final String DEFAULT_CUSTOM_SPEAKER = "Ryan";
    /** Verfügbare CustomVoice-Presets (laut Qwen3-Doku). */
    private static final java.util.List<String> CUSTOM_VOICE_SPEAKERS = java.util.List.of(
            "Ryan",       // EN
            "Aiden",      // EN
            "Vivian",     // ZH
            "Serena",     // ZH
            "Uncle_Fu",   // ZH
            "Dylan",      // ZH (Beijing)
            "Eric",       // ZH (Sichuan)
            "Ono_Anna",   // JA
            "Sohee"       // KO
    );
    /** ThreadLocal, um für einen Aufruf einen CustomVoice-Sprecher zu setzen (z. B. aus Stimmsuche/Gespeicherter Stimme). */
    private static final ThreadLocal<String> CURRENT_CUSTOM_SPEAKER = new ThreadLocal<>();

    public static java.util.List<String> getCustomVoiceSpeakers() {
        return CUSTOM_VOICE_SPEAKERS;
    }

    public static void setCurrentCustomSpeaker(String speakerId) {
        if (speakerId == null || speakerId.isBlank()) {
            CURRENT_CUSTOM_SPEAKER.remove();
        } else {
            CURRENT_CUSTOM_SPEAKER.set(speakerId.trim());
        }
    }

    public static void clearCurrentCustomSpeaker() {
        CURRENT_CUSTOM_SPEAKER.remove();
    }

    /**
     * Max. Temperatur beim Vorlesen mit gespeicherter Stimme, damit die Sprecheridentität über verschiedene Texte hinweg stabil bleibt.
     * Qwen3-TTS VoiceDesign kann bei gleichem Seed bei höherer Temperatur je Text unterschiedliche Stimmen liefern (bekanntes Problem).
     * Nur für „Text vorlesen“ verwendet; „Probe abspielen“ nutzt die gespeicherte Temperatur unverändert.
     */
    public static final double MAX_TEMPERATURE_FOR_VOICE_CONSISTENCY = 0.35;

    /** Gespeicherte Stimme: Name + Parameter für reproduzierbare Wiedergabe. */
    public static class SavedVoice {
        private String name;
        private long seed;
        private double temperature;
        private String voiceDescription;
        private boolean highQuality;
        private boolean consistentVoice;
        private double topP = DEFAULT_TOP_P;
        private int topK = DEFAULT_TOP_K;
        private double repetitionPenalty = DEFAULT_REPETITION_PENALTY;
        /** CustomVoice-Sprecher-ID (z. B. Ryan, Sohee, Vivian). */
        private String speakerId = DEFAULT_CUSTOM_SPEAKER;

        public SavedVoice() {
            this("", DEFAULT_SEED, DEFAULT_TEMPERATURE, "", true, false, DEFAULT_TOP_P, DEFAULT_TOP_K, DEFAULT_REPETITION_PENALTY, DEFAULT_CUSTOM_SPEAKER);
        }

        public SavedVoice(String name, long seed, double temperature, String voiceDescription, boolean highQuality, boolean consistentVoice) {
            this(name, seed, temperature, voiceDescription, highQuality, consistentVoice, DEFAULT_TOP_P, DEFAULT_TOP_K, DEFAULT_REPETITION_PENALTY, DEFAULT_CUSTOM_SPEAKER);
        }

        public SavedVoice(String name, long seed, double temperature, String voiceDescription, boolean highQuality, boolean consistentVoice, double topP, int topK) {
            this(name, seed, temperature, voiceDescription, highQuality, consistentVoice, topP, topK, DEFAULT_REPETITION_PENALTY, DEFAULT_CUSTOM_SPEAKER);
        }

        public SavedVoice(String name, long seed, double temperature, String voiceDescription, boolean highQuality, boolean consistentVoice, double topP, int topK, double repetitionPenalty) {
            this(name, seed, temperature, voiceDescription, highQuality, consistentVoice, topP, topK, repetitionPenalty, DEFAULT_CUSTOM_SPEAKER);
        }

        public SavedVoice(String name, long seed, double temperature, String voiceDescription, boolean highQuality, boolean consistentVoice, double topP, int topK, double repetitionPenalty, String speakerId) {
            this.name = name != null ? name : "";
            this.seed = seed;
            this.temperature = temperature;
            this.voiceDescription = voiceDescription != null ? voiceDescription : "";
            this.highQuality = highQuality;
            this.consistentVoice = consistentVoice;
            this.topP = topP > 0 ? topP : DEFAULT_TOP_P;
            this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
            this.repetitionPenalty = repetitionPenalty > 0 ? Math.min(2.0, Math.max(1.0, repetitionPenalty)) : DEFAULT_REPETITION_PENALTY;
            this.speakerId = (speakerId != null && !speakerId.isBlank()) ? speakerId.trim() : DEFAULT_CUSTOM_SPEAKER;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name != null ? name : ""; }
        public long getSeed() { return seed; }
        public void setSeed(long seed) { this.seed = seed; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public String getVoiceDescription() { return voiceDescription; }
        public void setVoiceDescription(String voiceDescription) { this.voiceDescription = voiceDescription != null ? voiceDescription : ""; }
        public boolean isHighQuality() { return highQuality; }
        public void setHighQuality(boolean highQuality) { this.highQuality = highQuality; }
        public boolean isConsistentVoice() { return consistentVoice; }
        public void setConsistentVoice(boolean consistentVoice) { this.consistentVoice = consistentVoice; }
        public double getTopP() { return topP > 0 ? topP : DEFAULT_TOP_P; }
        public void setTopP(double topP) { this.topP = topP > 0 ? topP : DEFAULT_TOP_P; }
        public int getTopK() { return topK > 0 ? topK : DEFAULT_TOP_K; }
        public void setTopK(int topK) { this.topK = topK > 0 ? topK : DEFAULT_TOP_K; }
        public double getRepetitionPenalty() { return repetitionPenalty > 0 ? repetitionPenalty : DEFAULT_REPETITION_PENALTY; }
        public void setRepetitionPenalty(double repetitionPenalty) { this.repetitionPenalty = repetitionPenalty > 0 ? Math.min(2.0, Math.max(1.0, repetitionPenalty)) : DEFAULT_REPETITION_PENALTY; }
        public String getSpeakerId() { return (speakerId != null && !speakerId.isBlank()) ? speakerId : DEFAULT_CUSTOM_SPEAKER; }
        public void setSpeakerId(String speakerId) { this.speakerId = (speakerId != null && !speakerId.isBlank()) ? speakerId.trim() : DEFAULT_CUSTOM_SPEAKER; }
    }

    /** Gson mit Seed als String (Long-Genauigkeit in JSON erhalten). */
    private static final Gson GSON_VOICES = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(SavedVoice.class, new SavedVoiceTypeAdapter())
            .create();

    private static final com.google.gson.reflect.TypeToken<java.util.List<SavedVoice>> VOICE_LIST_TYPE = new TypeToken<java.util.List<SavedVoice>>() {};

    private static class SavedVoiceTypeAdapter extends TypeAdapter<SavedVoice> {
        @Override
        public void write(JsonWriter out, SavedVoice src) throws IOException {
            out.beginObject();
            out.name("name").value(src.getName());
            out.name("seed").value(String.valueOf(src.getSeed()));
            out.name("temperature").value(src.getTemperature());
            out.name("voiceDescription").value(src.getVoiceDescription());
            out.name("highQuality").value(src.isHighQuality());
            out.name("consistentVoice").value(src.isConsistentVoice());
            out.name("topP").value(src.getTopP());
            out.name("topK").value(src.getTopK());
            out.name("repetitionPenalty").value(src.getRepetitionPenalty());
            out.name("speakerId").value(src.getSpeakerId());
            out.endObject();
        }

        @Override
        public SavedVoice read(JsonReader in) throws IOException {
            String name = "", voiceDescription = "";
            long seed = DEFAULT_SEED;
            double temperature = DEFAULT_TEMPERATURE;
            boolean highQuality = true, consistentVoice = false;
            double topP = DEFAULT_TOP_P, repetitionPenalty = DEFAULT_REPETITION_PENALTY;
            int topK = DEFAULT_TOP_K;
            String speakerId = DEFAULT_CUSTOM_SPEAKER;
            in.beginObject();
            while (in.hasNext()) {
                String field = in.nextName();
                switch (field) {
                    case "name": name = in.nextString(); break;
                    case "seed":
                        if (in.peek() == JsonToken.STRING) seed = Long.parseLong(in.nextString());
                        else seed = (long) in.nextDouble();
                        break;
                    case "temperature": temperature = in.nextDouble(); break;
                    case "voiceDescription": voiceDescription = in.nextString(); break;
                    case "highQuality": highQuality = in.nextBoolean(); break;
                    case "consistentVoice": consistentVoice = in.nextBoolean(); break;
                    case "topP": topP = in.nextDouble(); break;
                    case "topK": topK = (int) in.nextDouble(); break;
                    case "repetitionPenalty": repetitionPenalty = in.nextDouble(); break;
                    case "speakerId": speakerId = in.nextString(); break;
                    default: in.skipValue(); break;
                }
            }
            in.endObject();
            return new SavedVoice(name, seed, temperature, voiceDescription, highQuality, consistentVoice, topP, topK, repetitionPenalty, speakerId);
        }
    }

    /** Lädt die Liste gespeicherter Stimmen aus {@value #TTS_VOICES_PATH}. Seed wird als String gelesen (volle Long-Genauigkeit). */
    public static java.util.List<SavedVoice> loadSavedVoices() {
        Path path = Paths.get(TTS_VOICES_PATH);
        if (!Files.isRegularFile(path)) {
            return new java.util.ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            java.util.List<SavedVoice> list = GSON_VOICES.fromJson(reader, VOICE_LIST_TYPE.getType());
            return list != null ? list : new java.util.ArrayList<>();
        } catch (Exception e) {
            logger.warn("Gespeicherte Stimmen konnten nicht geladen werden: {} – leere Liste", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    /** Speichert die Liste gespeicherter Stimmen nach {@value #TTS_VOICES_PATH}. Seed wird als String geschrieben (volle Long-Genauigkeit). */
    public static void saveSavedVoices(java.util.List<SavedVoice> voices) throws IOException {
        Path path = Paths.get(TTS_VOICES_PATH);
        Files.createDirectories(path.getParent());
        String json = GSON_VOICES.toJson(voices != null ? voices : java.util.List.of());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /** Fallback, wenn keine Datei existiert (Wort → Aussprache-Schreibweise). Apostroph ' für Betonung wird vor dem Senden entfernt. */
    private static final Map<String, String> DEFAULT_PRONUNCIATION_LEXICON = new LinkedHashMap<>(Map.of(
            "Cache", "Käsch",
            "Ayen", "Ajen",
            "Djohmaar", "Djo-mahr"
    ));

    private final String baseUrl;
    private final HttpClient httpClient;

    public ComfyUIClient() {
        this(getBaseUrlFromConfig());
    }

    public ComfyUIClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Entfernt gängige Regieanweisungen in eckigen Klammern (z. B. [flüsternd], [lachend]),
     * damit sie nicht vorgelesen werden. Qwen3-TTS unterstützt keine Inline-Stimmangaben.
     */
    public static String removeStageDirections(String text) {
        if (text == null || text.isEmpty()) return text;
        String t = text;
        // [flüsternd], [lachend], [seufzend] etc. entfernen (inkl. Leerzeichen danach)
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\s*\\[(flüsternd|lachend|seufzend|leise|laut|zornig|sarkastisch|traurig|freudig|nachdenklich|ironisch|schnell|langsam|wispernd|rufend|skeptisch|sanft|böse|freundlich)\\s*]\\s*",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        t = p.matcher(t).replaceAll(" ");
        return t.replaceAll("\\s+", " ").trim();
    }

    /**
     * Wendet ein Aussprache-Lexikon auf den Text an: ganze Wörter (groß/klein) werden durch die
     * Lexikon-Ersetzung ersetzt. Im Ersetzungstext wird ein Apostroph ' (Betonung) vor dem Senden entfernt.
     * Qwen3-TTS unterstützt kein IPA/SSML – nur diese Text-Ersetzung ist zuverlässig.
     */
    public static String applyPronunciationLexicon(String text, Map<String, String> lexicon) {
        if (text == null || text.isEmpty() || lexicon == null || lexicon.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> e : lexicon.entrySet()) {
            String word = Pattern.quote(e.getKey().trim());
            if (word.isEmpty()) continue;
            String replacement = e.getValue() == null ? "" : e.getValue().replace("'", "").trim();
            // Ganze Wörter ersetzen (case-insensitive)
            result = result.replaceAll("(?iu)\\b" + word + "\\b", Matcher.quoteReplacement(replacement));
        }
        return result;
    }

    /**
     * Lädt das Aussprache-Lexikon aus der Datei {@value #PRONUNCIATION_LEXICON_PATH}.
     * Format: JSON-Objekt mit Schlüsseln = Wort, Werten = Ersetzung (Aussprache-Schreibweise).
     * Wenn die Datei fehlt oder ungültig ist, wird das eingebaute Fallback-Lexikon zurückgegeben.
     */
    public static Map<String, String> getDefaultPronunciationLexicon() {
        Path path = Paths.get(PRONUNCIATION_LEXICON_PATH);
        if (!Files.isRegularFile(path)) {
            logger.debug("Kein Aussprache-Lexikon unter {}, nutze Fallback", path.toAbsolutePath());
            return new LinkedHashMap<>(DEFAULT_PRONUNCIATION_LEXICON);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, String> loaded = new Gson().fromJson(reader, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
            if (loaded != null && !loaded.isEmpty()) {
                return new LinkedHashMap<>(loaded);
            }
        } catch (Exception e) {
            logger.warn("Aussprache-Lexikon konnte nicht geladen werden: {} – nutze Fallback", e.getMessage());
        }
        return new LinkedHashMap<>(DEFAULT_PRONUNCIATION_LEXICON);
    }

    /**
     * Sendet einen Prompt (Workflow) an ComfyUI und gibt die prompt_id zurück.
     * client_id pro Aufruf eindeutig, damit ComfyUI die Anfrage nicht als Duplikat ablehnt.
     */
    public String queuePrompt(Map<String, Object> workflow) throws IOException, InterruptedException {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("prompt", workflow);
        payload.put("client_id", java.util.UUID.randomUUID().toString());
        String body = com.manuskript.JsonUtil.toJson(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("ComfyUI /prompt failed: " + response.statusCode() + " " + response.body());
        }
        Map<String, Object> json = com.manuskript.JsonUtil.fromJson(response.body());
        if (json.containsKey("error")) {
            throw new IOException("ComfyUI error: " + json.get("error"));
        }
        String promptId = (String) json.get("prompt_id");
        if (promptId == null) {
            throw new IOException("ComfyUI response missing prompt_id: " + response.body());
        }
        return promptId;
    }

    /**
     * Holt die History für eine prompt_id (nach Abschluss der Ausführung).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHistory(String promptId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/history/" + promptId))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("ComfyUI /history failed: " + response.statusCode());
        }
        Map<String, Object> json = JsonUtil.fromJson(response.body());
        return (Map<String, Object>) json.getOrDefault(promptId, Map.of());
    }

    /**
     * Lädt eine Datei aus dem ComfyUI-Output (subfolder + filename).
     */
    public byte[] getFile(String filename, String subfolder, String type) throws IOException, InterruptedException {
        String path = "/view?"
                + "filename=" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8);
        if (subfolder != null && !subfolder.isEmpty()) {
            path += "&subfolder=" + java.net.URLEncoder.encode(subfolder, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (type != null && !type.isEmpty()) {
            path += "&type=" + java.net.URLEncoder.encode(type, java.nio.charset.StandardCharsets.UTF_8);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("ComfyUI /view failed: " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Wartet bis der Prompt abgeschlossen ist und liefert die History.
     */
    public Map<String, Object> waitForCompletion(String promptId) throws IOException, InterruptedException {
        int timeout = ResourceManager.getIntParameter("comfyui.timeout_seconds", DEFAULT_TIMEOUT_SECONDS);
        return waitForCompletion(promptId, timeout);
    }

    public Map<String, Object> waitForCompletion(String promptId, int timeoutSeconds) throws IOException, InterruptedException {
        logger.info("Warte auf ComfyUI-Abschluss für prompt_id {} (Timeout: {} s)", promptId, timeoutSeconds);
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        int pollCount = 0;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> history = getHistory(promptId);
            if (!history.isEmpty()) {
                logger.info("ComfyUI prompt abgeschlossen: {}", promptId);
                return history;
            }
            pollCount++;
            if (pollCount % 20 == 0) {
                long remaining = (deadline - System.currentTimeMillis()) / 1000;
                logger.info("Noch keine History für {} (noch {} s)", promptId, remaining);
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        logger.warn("ComfyUI Timeout: prompt {} nicht innerhalb von {} s abgeschlossen", promptId, timeoutSeconds);
        throw new IOException("ComfyUI prompt did not complete within " + timeoutSeconds + " seconds");
    }

    /** Default-Seed für reproduzierbare Stimmen (VoiceDesign/CustomVoice). */
    public static final long DEFAULT_SEED = 754534819103377L;
    /** Default-Temperatur für TTS (mehr Variation bei höheren Werten). */
    public static final double DEFAULT_TEMPERATURE = 0.6;

    /**
     * Baut den Qwen3-TTS-Workflow mit optionalem Seed, Temperatur und Stimmbeschreibung.
     * @param highQuality true = 1.7B, false = 0.6B
     * @param consistentVoice true = immer gleicher Sprecher (CustomVoice); false bei highQuality = VoiceDesign (kann variieren)
     * @param seed null = {@value #DEFAULT_SEED}; bei VoiceDesign steuert die Stimmenvariante
     * @param temperature null = {@value #DEFAULT_TEMPERATURE}; höher = mehr Variation
     * @param voiceDescription null/leer = nur instruct; sonst an instruct angehängt (z. B. "Männlich, tief, ruhig") für VoiceDesign
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildQwen3CustomVoiceWorkflow(String text, String instruct, boolean highQuality, boolean consistentVoice,
                                                                     Long seed, Double temperature, String voiceDescription, Double topP, Integer topK, Double repetitionPenalty,
                                                                     String filenamePrefix) {
        long seedVal = seed != null ? seed : DEFAULT_SEED;
        double tempVal = temperature != null ? Math.max(0.0, Math.min(2.0, temperature)) : DEFAULT_TEMPERATURE;
        double topPVal = topP != null && topP > 0 ? topP : DEFAULT_TOP_P;
        int topKVal = topK != null && topK > 0 ? Math.min(100, topK) : DEFAULT_TOP_K;
        double repPenVal = repetitionPenalty != null && repetitionPenalty > 0 ? Math.min(2.0, Math.max(1.0, repetitionPenalty)) : DEFAULT_REPETITION_PENALTY;
        String instructVal = instruct != null ? instruct : DEFAULT_INSTRUCT_DEUTSCH;
        if (voiceDescription != null && !voiceDescription.isBlank()) {
            instructVal = instructVal + " Stimme: " + voiceDescription.trim();
        }
        Map<String, Object> ttsNode;
        boolean useVoiceDesign = highQuality && !consistentVoice;
        if (useVoiceDesign) {
            // VoiceDesign 1.7B – Stimme per Beschreibung/Seed, kann pro Lauf leicht variieren
            ttsNode = new java.util.HashMap<>();
            ttsNode.put("class_type", "FB_Qwen3TTSVoiceDesign");
            Map<String, Object> inputs = new java.util.HashMap<>();
            inputs.put("text", text);
            inputs.put("model_choice", "1.7B");
            inputs.put("device", "auto");
            inputs.put("precision", "fp32");
            inputs.put("language", "German");
            inputs.put("seed", seedVal);
            inputs.put("max_new_tokens", 2048);
            inputs.put("top_p", topPVal);
            inputs.put("top_k", topKVal);
            inputs.put("temperature", tempVal);
            inputs.put("repetition_penalty", repPenVal);
            inputs.put("attention", "auto");
            inputs.put("unload_model_after_generate", true);
            inputs.put("instruct", instructVal);
            ttsNode.put("inputs", inputs);
        } else {
            // CustomVoice – fester Sprecher, konsistente Stimme (0.6B oder 1.7B)
            ttsNode = new java.util.HashMap<>();
            ttsNode.put("class_type", "FB_Qwen3TTSCustomVoice");
            Map<String, Object> inputs = new java.util.HashMap<>();
            inputs.put("text", text);
            String speaker = DEFAULT_CUSTOM_SPEAKER;
            String override = CURRENT_CUSTOM_SPEAKER.get();
            if (override != null && !override.isBlank()) {
                speaker = override.trim();
            }
            inputs.put("speaker", speaker);
            inputs.put("model_choice", highQuality ? "1.7B" : "0.6B");
            inputs.put("device", "auto");
            inputs.put("precision", "fp32");
            inputs.put("language", "German");
            inputs.put("seed", seedVal);
            inputs.put("max_new_tokens", 2048);
            inputs.put("top_p", topPVal);
            inputs.put("top_k", topKVal);
            inputs.put("temperature", tempVal);
            inputs.put("repetition_penalty", repPenVal);
            inputs.put("attention", "auto");
            inputs.put("unload_model_after_generate", true);
            inputs.put("instruct", instructVal);
            inputs.put("custom_model_path", "");
            inputs.put("custom_speaker_name", "");
            ttsNode.put("inputs", inputs);
        }

        // Save Audio (MP3): MP3 wird von JavaFX Media erkannt; WAV von ComfyUI oft „unrecognized file signature“
        // Eindeutiger filename_prefix pro Aufruf, damit ComfyUI wiederholte/ähnliche Anfragen nicht als Duplikat ablehnt
        String prefix = (filenamePrefix != null && !filenamePrefix.isEmpty()) ? filenamePrefix : "tts";
        Map<String, Object> saveNode = new java.util.HashMap<>();
        saveNode.put("class_type", "SaveAudioMP3");
        Map<String, Object> saveInputs = new java.util.HashMap<>();
        saveInputs.put("audio", List.of("3", 0));
        saveInputs.put("filename_prefix", prefix);
        saveInputs.put("quality", "320k");  // erforderlich: z. B. "V0", "128k", "320k"
        saveNode.put("inputs", saveInputs);

        Map<String, Object> workflow = new java.util.HashMap<>();
        workflow.put("3", ttsNode);
        workflow.put("4", saveNode);
        return workflow;
    }

    /** Wie {@link #buildQwen3CustomVoiceWorkflow(String, String, boolean, boolean, Long, Double, String, Double, Integer, Double, String)} mit Standard-Seed/Temperatur/top_p/top_k/repetitionPenalty, ohne Stimmbeschreibung, Prefix „tts“. */
    public static Map<String, Object> buildQwen3CustomVoiceWorkflow(String text, String instruct, boolean highQuality, boolean consistentVoice) {
        return buildQwen3CustomVoiceWorkflow(text, instruct, highQuality, consistentVoice, null, null, null, null, null, null, null);
    }

    /** Wie {@link #buildQwen3CustomVoiceWorkflow(String, String, boolean, boolean)} mit consistentVoice = false. */
    public static Map<String, Object> buildQwen3CustomVoiceWorkflow(String text, String instruct, boolean highQuality) {
        return buildQwen3CustomVoiceWorkflow(text, instruct, highQuality, false);
    }

    /** Wie {@link #buildQwen3CustomVoiceWorkflow(String, String, boolean, boolean)} mit highQuality = true, consistentVoice = false. */
    public static Map<String, Object> buildQwen3CustomVoiceWorkflow(String text, String instruct) {
        return buildQwen3CustomVoiceWorkflow(text, instruct, true, false);
    }

    /**
     * Generiert Audio per Qwen3-TTS. Optional: Aussprache-Lexikon, Seed, Temperatur, Stimmbeschreibung.
     * @param pronunciationLexicon null = Standard-Lexikon, leer = keine Ersetzung
     * @param seed null = Standard-Seed; bei VoiceDesign steuert die Stimmenvariante
     * @param temperature null = Standard-Temperatur
     * @param voiceDescription null/leer = nur instruct; sonst z. B. "Männlich, tief" für VoiceDesign
     */
    public Map<String, Object> generateQwen3TTS(String text, String instruct, boolean highQuality, boolean consistentVoice,
                                                Map<String, String> pronunciationLexicon, Consumer<String> promptLogger,
                                                Long seed, Double temperature, String voiceDescription, Double topP, Integer topK, Double repetitionPenalty) throws IOException, InterruptedException {
        Map<String, String> lexicon = pronunciationLexicon != null ? pronunciationLexicon : getDefaultPronunciationLexicon();
        // Regieanweisungen in eckigen Klammern NICHT mehr filtern – vollständigen Text sprechen.
        String textForTTS = text;
        textForTTS = lexicon.isEmpty() ? textForTTS : applyPronunciationLexicon(textForTTS, lexicon);
        if (!textForTTS.equals(text)) {
            logger.info("Text für TTS (nach Lexikon): {}", textForTTS);
        }
        // Eindeutigen, unsichtbaren Suffix anhängen, damit ComfyUI bei gleichem Text + geänderten Parametern nicht ablehnt (Duplikat-Erkennung nur am Text).
        textForTTS = textForTTS + "\u200B".repeat((int) (System.nanoTime() % 5) + 1);
        if (seed != null || voiceDescription != null) {
            logger.info("TTS gespeicherte Stimme: seed={}, temperature={}, voiceDescription=\"{}\", topP={}, topK={}, repetitionPenalty={}, highQuality={}, consistentVoice={}",
                    seed, temperature, voiceDescription, topP, topK, repetitionPenalty, highQuality, consistentVoice);
        }
        String runPrefix = "tts_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Map<String, Object> workflow = buildQwen3CustomVoiceWorkflow(textForTTS, instruct, highQuality, consistentVoice, seed, temperature, voiceDescription, topP, topK, repetitionPenalty, runPrefix);
        Map<String, Object> fullPrompt = Map.of("prompt", workflow);
        String pretty = JsonUtil.toJsonPretty(fullPrompt);
        logger.debug("ComfyUI Prompt (Workflow, menschenlesbar):\n{}", pretty);
        if (promptLogger != null) {
            promptLogger.accept(pretty);
        }
        String promptId = queuePrompt(workflow);
        logger.info("ComfyUI prompt queued: {}", promptId);
        return waitForCompletion(promptId);
    }

    /** Wie {@link #generateQwen3TTS(..., Long, Double, String, Double, Integer, Double)} mit Standard-Seed/Temperatur/top_p/top_k/repetitionPenalty, ohne Stimmbeschreibung. */
    public Map<String, Object> generateQwen3TTS(String text, String instruct, boolean highQuality, boolean consistentVoice,
                                                Map<String, String> pronunciationLexicon, Consumer<String> promptLogger) throws IOException, InterruptedException {
        return generateQwen3TTS(text, instruct, highQuality, consistentVoice, pronunciationLexicon, promptLogger, null, null, null, null, null, null);
    }

    /** Wie {@link #generateQwen3TTS(String, String, boolean, boolean, Map, Consumer)} mit consistentVoice = false, Standard-Lexikon, promptLogger = null. */
    public Map<String, Object> generateQwen3TTS(String text, String instruct, boolean highQuality, Consumer<String> promptLogger) throws IOException, InterruptedException {
        return generateQwen3TTS(text, instruct, highQuality, false, null, promptLogger);
    }

    /** Wie {@link #generateQwen3TTS(String, String, boolean, boolean, Map, Consumer)} mit promptLogger = null. */
    public Map<String, Object> generateQwen3TTS(String text, String instruct, boolean highQuality, boolean consistentVoice,
                                                Map<String, String> pronunciationLexicon) throws IOException, InterruptedException {
        return generateQwen3TTS(text, instruct, highQuality, consistentVoice, pronunciationLexicon, null);
    }

    /** Wie oben mit highQuality = true, consistentVoice = false, Standard-Lexikon, promptLogger = null. */
    public Map<String, Object> generateQwen3TTS(String text, String instruct) throws IOException, InterruptedException {
        return generateQwen3TTS(text, instruct, true, false, null, null);
    }

    /**
     * TTS mit gespeicherter Stimme (für Kapitel-TTS-Editor und andere Aufrufer).
     * @param useConsistencyTemperature true = Temperatur für Hörbuch-Konsistenz begrenzen
     */
    public Map<String, Object> generateTTSWithSavedVoice(String text, SavedVoice voice, Map<String, String> pronunciationLexicon, boolean useConsistencyTemperature, java.util.function.Consumer<String> promptLogger) throws IOException, InterruptedException {
        String desc = (voice.getVoiceDescription() == null || voice.getVoiceDescription().isEmpty()) ? null : voice.getVoiceDescription();
        double temp = useConsistencyTemperature
                ? Math.min(voice.getTemperature(), MAX_TEMPERATURE_FOR_VOICE_CONSISTENCY)
                : voice.getTemperature();
        try {
            setCurrentCustomSpeaker(voice.getSpeakerId());
            return generateQwen3TTS(text, DEFAULT_INSTRUCT_DEUTSCH, voice.isHighQuality(), true,
                    pronunciationLexicon != null ? pronunciationLexicon : getDefaultPronunciationLexicon(), promptLogger,
                    voice.getSeed(), temp, desc, voice.getTopP(), voice.getTopK(), voice.getRepetitionPenalty());
        } finally {
            clearCurrentCustomSpeaker();
        }
    }

    /**
     * Extrahiert aus der ComfyUI-History die erste gefundene Audio-Output-Info (filename oder base64).
     * Returns: Map mit "filename", "subfolder", "type" ODER "base64" (Data-URL oder Rohdaten).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractFirstAudioFromHistory(Map<String, Object> history) {
        Object outputsObj = history.get("outputs");
        if (!(outputsObj instanceof Map)) {
            logger.warn("History hat kein 'outputs'-Map: keys={}", history.keySet());
            return Map.of();
        }
        Map<String, Object> outputs = (Map<String, Object>) outputsObj;
        for (Object nodeVal : outputs.values()) {
            if (!(nodeVal instanceof Map)) continue;
            Map<String, Object> node = (Map<String, Object>) nodeVal;
            Object audioObj = node.get("audio");
            if (audioObj instanceof Object[] && ((Object[]) audioObj).length > 0) {
                Object first = ((Object[]) audioObj)[0];
                if (first instanceof Map) {
                    return (Map<String, Object>) first;
                }
            }
            if (audioObj instanceof java.util.List && !((java.util.List<?>) audioObj).isEmpty()) {
                Object first = ((java.util.List<?>) audioObj).get(0);
                if (first instanceof Map) {
                    return (Map<String, Object>) first;
                }
            }
        }
        logger.warn("History enthält kein Audio-Output; outputs-Knoten: {}. History-Keys: {}, status: {}, messages: {}",
                outputs.keySet(), history.keySet(), history.get("status"), history.get("messages"));
        return Map.of();
    }

    /**
     * Lädt die generierte Audiodatei von ComfyUI herunter und speichert sie lokal.
     * @param history Ergebnis von waitForCompletion
     * @param targetPath lokale Datei (z. B. .wav)
     * @return targetPath wenn erfolgreich
     */
    public Path downloadAudioToFile(Map<String, Object> history, Path targetPath) throws IOException, InterruptedException {
        Map<String, Object> audioInfo = extractFirstAudioFromHistory(history);
        if (audioInfo.isEmpty()) {
            throw new IOException("No audio output in ComfyUI history");
        }
        String filename = (String) audioInfo.get("filename");
        String subfolder = (String) audioInfo.get("subfolder");
        String type = (String) audioInfo.get("type");
        if (filename == null) {
            throw new IOException("Audio output has no filename");
        }
        byte[] data = getFile(filename, subfolder != null ? subfolder : "", type != null ? type : "output");
        Files.write(targetPath, data);
        logger.info("Saved ComfyUI audio to {}", targetPath);
        return targetPath;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
