package com.manuskript.dictation;

import com.manuskript.MicrophoneRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lokale Spracherkennung via whisper.cpp ({@code whisper-cli}), ohne Cloud-API.
 */
public class LocalWhisperBackend implements SpeechToTextBackend {

    private static final Logger logger = LoggerFactory.getLogger(LocalWhisperBackend.class);
    private static final int MIN_PCM_BYTES = 6600;

    @Override
    public String getName() {
        return "Lokal (whisper.cpp)";
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
        if (audioFile == null || !Files.isRegularFile(audioFile)) {
            throw new IllegalArgumentException("Audiodatei fehlt: " + audioFile);
        }
        byte[] audioBytes = Files.readAllBytes(audioFile);
        validateAudioSize(audioBytes);

        String executable = WhisperRuntime.resolveExecutable();
        if (executable == null || executable.isBlank()) {
            throw new IllegalStateException("whisper-cli nicht gefunden.\n\n" + WhisperRuntime.buildSetupHint());
        }

        Path model = WhisperRuntime.resolveModelPath();
        if (!Files.isRegularFile(model)) {
            throw new IllegalStateException(
                    "Whisper-Modell fehlt: " + model + "\n\n" + WhisperRuntime.buildSetupHint());
        }

        Path outputPrefix = Files.createTempFile("manuskript-whisper-out-", "");
        Files.deleteIfExists(outputPrefix);
        Path txtOutput = Path.of(outputPrefix.toString() + ".txt");

        List<String> cmd = WhisperRuntime.buildCommand(executable, model, audioFile, language, outputPrefix,
                initialPrompt);
        logger.debug("Lokales Whisper: {}", String.join(" ", cmd));

        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            String consoleOutput = WhisperRuntime.readProcessOutput(process);
            int exitCode = process.waitFor();

            if (Files.isRegularFile(txtOutput)) {
                String text = Files.readString(txtOutput, StandardCharsets.UTF_8).trim();
                if (!text.isBlank()) {
                    logger.debug("Lokales Whisper: {} Zeichen (Datei)", text.length());
                    return text;
                }
            }

            if (!consoleOutput.isBlank()) {
                String cleaned = stripWhisperConsoleNoise(consoleOutput);
                if (!cleaned.isBlank()) {
                    logger.debug("Lokales Whisper: {} Zeichen (Stdout)", cleaned.length());
                    return cleaned;
                }
            }

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "whisper-cli Fehler (Exit " + exitCode + "): " + abbreviate(consoleOutput, 400));
            }
            throw new IllegalStateException("whisper-cli lieferte leere Transkription.");
        } finally {
            WhisperRuntime.deleteQuietly(outputPrefix);
        }
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

    private static String stripWhisperConsoleNoise(String output) {
        StringBuilder sb = new StringBuilder();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("whisper_")
                    || trimmed.startsWith("main:")
                    || trimmed.startsWith("system_info")
                    || trimmed.startsWith("ggml_")
                    || trimmed.contains("load time")
                    || trimmed.contains("fallbacks")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(trimmed);
        }
        return sb.toString().trim();
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
