package com.manuskript.dictation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.manuskript.GatewayHttpRetry;
import com.manuskript.MicrophoneRecorder;
import com.manuskript.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI Whisper API ({@code /v1/audio/transcriptions}).
 * Nutzt eine eigene API-URL (nicht OpenRouter), da nur OpenAI Whisper transkribiert.
 */
public class OpenAIWhisperBackend implements SpeechToTextBackend {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIWhisperBackend.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    /** Mindestens ~0,15 s PCM bei 22050 Hz, 16-bit, Mono. */
    private static final int MIN_PCM_BYTES = 6600;

    private final HttpClient httpClient;
    private final Gson gson;

    public OpenAIWhisperBackend() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
    }

    @Override
    public String getName() {
        return "OpenAI Whisper";
    }

    @Override
    public CompletableFuture<String> transcribe(Path audioFile, String language, String initialPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return transcribeSync(audioFile, language, initialPrompt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String transcribeSync(Path audioFile, String language, String initialPrompt) throws Exception {
        String apiKey = resolveApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Kein API-Key für Whisper gesetzt (Parameter: dictation.whisper_api_key, "
                            + "agent.openai.api_key oder api.lektorat.api_key).");
        }
        if (audioFile == null || !Files.isRegularFile(audioFile)) {
            throw new IllegalArgumentException("Audiodatei fehlt: " + audioFile);
        }

        byte[] audioBytes = Files.readAllBytes(audioFile);
        validateAudioSize(audioBytes);

        String baseUrl = resolveBaseUrl();
        String model = ResourceManager.getParameter("dictation.whisper_model", "whisper-1").trim();
        if (model.isEmpty()) {
            model = "whisper-1";
        }

        String fileName = audioFile.getFileName() != null ? audioFile.getFileName().toString() : "audio.wav";
        String boundary = "----" + UUID.randomUUID().toString().replace("-", "");

        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        sb.append(model).append("\r\n");

        if (language != null && !language.isBlank()) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            sb.append(language.trim()).append("\r\n");
        }

        String prompt = WhisperTranscriptGuard.promptForClip(initialPrompt, audioBytes);
        if (prompt != null && !prompt.isBlank()) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n");
            sb.append(prompt.trim()).append("\r\n");
        }

        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"temperature\"\r\n\r\n");
        sb.append("0").append("\r\n");

        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
        sb.append("json\r\n");

        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        sb.append("Content-Type: audio/wav\r\n\r\n");

        byte[] headerBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        String url = baseUrl + "/audio/transcriptions";
        logger.debug("Whisper-Request: {} ({} Bytes Audio)", url, audioBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(resolveTimeoutSec()))
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(headerBytes, audioBytes, footer)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status != 200 && GatewayHttpRetry.isRetryableStatus(status)) {
            logger.info("Whisper API: HTTP {} – Wiederholungsversuch…", status);
            GatewayHttpRetry.sleepBeforeRetry();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            status = response.statusCode();
        }
        String body = response.body();
        if (status != 200) {
            logger.warn("Whisper API Fehler: HTTP {} – URL={} – Antwort={}", status, url, body);
            throw new IllegalStateException(formatHttpError(status, body, baseUrl));
        }
        String text = parseTranscript(body);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Whisper API: Leere Transkription.");
        }
        logger.debug("Whisper-Transkription: {} Zeichen", text.length());
        return WhisperTranscriptGuard.requireRealSpeech(text);
    }

    private static void validateAudioSize(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < 44) {
            throw new IllegalStateException("Aufnahme zu kurz – bitte etwas länger sprechen.");
        }
        int pcmBytes = audioBytes.length - 44;
        if (pcmBytes < MIN_PCM_BYTES) {
            double seconds = pcmBytes / (MicrophoneRecorder.SAMPLE_RATE * 2.0);
            throw new IllegalStateException(String.format(
                    "Aufnahme zu kurz (%.1f s) – Diktat-Taste etwas länger gedrückt halten.", seconds));
        }
        int peak = MicrophoneRecorder.peakAmplitudeWav(audioBytes);
        if (peak < MicrophoneRecorder.MIN_SPEECH_PEAK) {
            throw new IllegalStateException(
                    "Aufnahme ist praktisch stumm (kein Sprachsignal).\n\n"
                            + "Bitte Mikrofon einschalten und prüfen:\n"
                            + "• Systemeinstellungen → Ton → Eingabe\n"
                            + "• Datenschutz → Mikrofon (Terminal/Java bzw. Manuskript erlauben)");
        }
    }

    private static String resolveBaseUrl() {
        String baseUrl = ResourceManager.getParameter("dictation.whisper_api_url", DEFAULT_BASE_URL).trim();
        if (baseUrl.isEmpty()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    static boolean hasConfiguredApiKey() {
        return !resolveApiKey().isEmpty();
    }

    private static String resolveApiKey() {
        String apiKey = ResourceManager.getParameter("dictation.whisper_api_key", "");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = ResourceManager.getParameter("agent.openai.api_key", "");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = ResourceManager.getParameter("api.lektorat.api_key", "");
        }
        return apiKey != null ? apiKey.trim() : "";
    }

    private static String formatHttpError(int status, String body, String baseUrl) {
        String trimmedBody = body != null && body.length() > 300 ? body.substring(0, 300) + "…" : body;
        String message = "Whisper API: HTTP " + status + " – " + trimmedBody;
        if (status == 400 || status == 404) {
            String lower = baseUrl.toLowerCase();
            if (lower.contains("openrouter") || lower.contains("mammouth") || lower.contains("deepseek")) {
                message += " Hinweis: Whisper läuft nur über die OpenAI-API. "
                        + "Parameter dictation.whisper_api_url auf https://api.openai.com/v1 setzen "
                        + "und dictation.whisper_api_key mit einem OpenAI-Key befüllen.";
            }
        }
        return message;
    }

    private static int resolveTimeoutSec() {
        try {
            int t = Integer.parseInt(ResourceManager.getParameter("agent.openai.request_timeout_sec", "120"));
            return Math.max(30, Math.min(900, t));
        } catch (NumberFormatException e) {
            return 120;
        }
    }

    private String parseTranscript(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json != null && json.has("text") && !json.get("text").isJsonNull()) {
                return json.get("text").getAsString();
            }
        } catch (JsonSyntaxException e) {
            logger.trace("Whisper-Antwort kein JSON, als Klartext verwenden");
        }
        return body.trim();
    }
}
