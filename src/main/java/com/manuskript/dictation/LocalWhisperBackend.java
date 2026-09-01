package com.manuskript.dictation;

import com.manuskript.MicrophoneRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.manuskript.ResourceManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

        String prompt = WhisperTranscriptGuard.promptForClip(initialPrompt, audioBytes);
        List<String> cmd = WhisperRuntime.buildCommand(executable, model, audioFile, language, outputPrefix,
                prompt);
        logger.debug("Lokales Whisper: {}", String.join(" ", cmd));

        int timeoutSec = resolveTimeoutSec();
        Process process = null;
        try {
            process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            final Process running = process;
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return WhisperRuntime.readProcessOutput(running);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new IllegalStateException(
                        "Lokales Whisper: Timeout nach " + timeoutSec
                                + " s – Aufnahme kürzen oder dictation.local_whisper_timeout_sec erhöhen.");
            }

            String consoleOutput;
            try {
                consoleOutput = outputFuture.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                consoleOutput = "";
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new IllegalStateException("Lokales Whisper: Ausgabe lesen fehlgeschlagen: "
                        + cause.getMessage(), cause);
            }
            int exitCode = process.exitValue();
            if (looksLikeCliFailure(consoleOutput, exitCode)) {
                throw new IllegalStateException(
                        "whisper-cli Fehler (Exit " + exitCode + "): " + abbreviate(consoleOutput, 400));
            }

            if (Files.isRegularFile(txtOutput)) {
                String text = Files.readString(txtOutput, StandardCharsets.UTF_8).trim();
                if (!text.isBlank()) {
                    logger.debug("Lokales Whisper: {} Zeichen (Datei)", text.length());
                    return WhisperTranscriptGuard.requireRealSpeech(text);
                }
            }

            if (!consoleOutput.isBlank()) {
                String cleaned = stripWhisperConsoleNoise(consoleOutput);
                if (!cleaned.isBlank()) {
                    logger.debug("Lokales Whisper: {} Zeichen (Stdout)", cleaned.length());
                    return WhisperTranscriptGuard.requireRealSpeech(cleaned);
                }
            }

            throw new IllegalStateException("whisper-cli lieferte leere Transkription.");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            WhisperRuntime.deleteQuietly(outputPrefix);
        }
    }

    static boolean looksLikeCliFailure(String consoleOutput, int exitCode) {
        if (exitCode != 0) {
            return true;
        }
        if (consoleOutput == null || consoleOutput.isBlank()) {
            return false;
        }
        String lower = consoleOutput.toLowerCase();
        return lower.contains("unknown argument")
                || lower.contains("usage: whisper-cli")
                || lower.contains("error: unknown");
    }

    private static int resolveTimeoutSec() {
        int t = ResourceManager.getIntParameter("dictation.local_whisper_timeout_sec", 180);
        return Math.max(30, Math.min(900, t));
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
