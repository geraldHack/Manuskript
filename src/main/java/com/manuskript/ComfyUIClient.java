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
import com.google.gson.reflect.TypeToken;

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

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8188";
    /** Standard-Stil: deutsch, ohne amerikanischen Akzent, neutrales Hochdeutsch. */
    public static final String DEFAULT_INSTRUCT_DEUTSCH =
            "Deutsch. Neutrale deutsche Stimme, Hochdeutsch, ohne amerikanischen oder englischen Akzent. "
            + "Warm, ruhig, natürlich. Kurze Pausen an Kommas. Keine Überbetonung.";
    private static final int POLL_INTERVAL_MS = 500;
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

    /** Gespeicherte Stimme: Name + Parameter für reproduzierbare Wiedergabe. */
    public static class SavedVoice {
        private String name;
        private long seed;
        private double temperature;
        private String voiceDescription;
        private boolean highQuality;
        private boolean consistentVoice;

        public SavedVoice() {
            this("", DEFAULT_SEED, DEFAULT_TEMPERATURE, "", true, false);
        }

        public SavedVoice(String name, long seed, double temperature, String voiceDescription, boolean highQuality, boolean consistentVoice) {
            this.name = name != null ? name : "";
            this.seed = seed;
            this.temperature = temperature;
            this.voiceDescription = voiceDescription != null ? voiceDescription : "";
            this.highQuality = highQuality;
            this.consistentVoice = consistentVoice;
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
    }

    /** Lädt die Liste gespeicherter Stimmen aus {@value #TTS_VOICES_PATH}. */
    public static java.util.List<SavedVoice> loadSavedVoices() {
        Path path = Paths.get(TTS_VOICES_PATH);
        if (!Files.isRegularFile(path)) {
            return new java.util.ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            java.util.List<SavedVoice> list = new Gson().fromJson(reader, new TypeToken<java.util.List<SavedVoice>>() {}.getType());
            return list != null ? list : new java.util.ArrayList<>();
        } catch (Exception e) {
            logger.warn("Gespeicherte Stimmen konnten nicht geladen werden: {} – leere Liste", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    /** Speichert die Liste gespeicherter Stimmen nach {@value #TTS_VOICES_PATH}. */
    public static void saveSavedVoices(java.util.List<SavedVoice> voices) throws IOException {
        Path path = Paths.get(TTS_VOICES_PATH);
        Files.createDirectories(path.getParent());
        String json = JsonUtil.toJsonPretty(voices != null ? voices : java.util.List.of());
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
        this(DEFAULT_BASE_URL);
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
     */
    public String queuePrompt(Map<String, Object> workflow) throws IOException, InterruptedException {
        String body = com.manuskript.JsonUtil.toJson(Map.of("prompt", workflow));
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
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> history = getHistory(promptId);
            if (!history.isEmpty()) {
                return history;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
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
                                                                     Long seed, Double temperature, String voiceDescription) {
        long seedVal = seed != null ? seed : DEFAULT_SEED;
        double tempVal = temperature != null ? Math.max(0.0, Math.min(2.0, temperature)) : DEFAULT_TEMPERATURE;
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
            inputs.put("top_p", 1.0);
            inputs.put("top_k", 46);
            inputs.put("temperature", tempVal);
            inputs.put("repetition_penalty", 1.1);
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
            inputs.put("speaker", "Ryan");
            inputs.put("model_choice", highQuality ? "1.7B" : "0.6B");
            inputs.put("device", "auto");
            inputs.put("precision", "fp32");
            inputs.put("language", "German");
            inputs.put("seed", seedVal);
            inputs.put("max_new_tokens", 2048);
            inputs.put("top_p", 1.0);
            inputs.put("top_k", 46);
            inputs.put("temperature", tempVal);
            inputs.put("repetition_penalty", 1.1);
            inputs.put("attention", "auto");
            inputs.put("unload_model_after_generate", true);
            inputs.put("instruct", instructVal);
            inputs.put("custom_model_path", "");
            inputs.put("custom_speaker_name", "");
            ttsNode.put("inputs", inputs);
        }

        // Save Audio (MP3): MP3 wird von JavaFX Media erkannt; WAV von ComfyUI oft „unrecognized file signature“
        Map<String, Object> saveNode = new java.util.HashMap<>();
        saveNode.put("class_type", "SaveAudioMP3");
        Map<String, Object> saveInputs = new java.util.HashMap<>();
        saveInputs.put("audio", List.of("3", 0));
        saveInputs.put("filename_prefix", "tts");
        saveInputs.put("quality", "320k");  // erforderlich: z. B. "V0", "128k", "320k"
        saveNode.put("inputs", saveInputs);

        Map<String, Object> workflow = new java.util.HashMap<>();
        workflow.put("3", ttsNode);
        workflow.put("4", saveNode);
        return workflow;
    }

    /** Wie {@link #buildQwen3CustomVoiceWorkflow(String, String, boolean, boolean, Long, Double, String)} mit Standard-Seed/Temperatur, ohne Stimmbeschreibung. */
    public static Map<String, Object> buildQwen3CustomVoiceWorkflow(String text, String instruct, boolean highQuality, boolean consistentVoice) {
        return buildQwen3CustomVoiceWorkflow(text, instruct, highQuality, consistentVoice, null, null, null);
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
                                                Long seed, Double temperature, String voiceDescription) throws IOException, InterruptedException {
        Map<String, String> lexicon = pronunciationLexicon != null ? pronunciationLexicon : getDefaultPronunciationLexicon();
        String textForTTS = removeStageDirections(text);
        textForTTS = lexicon.isEmpty() ? textForTTS : applyPronunciationLexicon(textForTTS, lexicon);
        if (!textForTTS.equals(text)) {
            logger.info("Text für TTS (nach Regie-Strip + Lexikon): {}", textForTTS);
        }
        Map<String, Object> workflow = buildQwen3CustomVoiceWorkflow(textForTTS, instruct, highQuality, consistentVoice, seed, temperature, voiceDescription);
        Map<String, Object> fullPrompt = Map.of("prompt", workflow);
        String pretty = JsonUtil.toJsonPretty(fullPrompt);
        logger.info("ComfyUI Prompt (Workflow, menschenlesbar):\n{}", pretty);
        if (promptLogger != null) {
            promptLogger.accept(pretty);
        }
        String promptId = queuePrompt(workflow);
        logger.info("ComfyUI prompt queued: {}", promptId);
        return waitForCompletion(promptId);
    }

    /** Wie {@link #generateQwen3TTS(..., Long, Double, String)} mit Standard-Seed/Temperatur, ohne Stimmbeschreibung. */
    public Map<String, Object> generateQwen3TTS(String text, String instruct, boolean highQuality, boolean consistentVoice,
                                                Map<String, String> pronunciationLexicon, Consumer<String> promptLogger) throws IOException, InterruptedException {
        return generateQwen3TTS(text, instruct, highQuality, consistentVoice, pronunciationLexicon, promptLogger, null, null, null);
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
     * Extrahiert aus der ComfyUI-History die erste gefundene Audio-Output-Info (filename oder base64).
     * Returns: Map mit "filename", "subfolder", "type" ODER "base64" (Data-URL oder Rohdaten).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractFirstAudioFromHistory(Map<String, Object> history) {
        Object outputsObj = history.get("outputs");
        if (!(outputsObj instanceof Map)) {
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
