package com.manuskript.dictation;

import com.manuskript.MicrophoneRecorder;
import com.manuskript.OllamaService;
import com.manuskript.ParameterRegistry;
import com.manuskript.ResourceManager;
import com.manuskript.agent.AIBackend;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.agent.OpenAIBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Orchestriert Diktat: Aufnahme → STT → LLM-Interpreter.
 */
public class DictationService {

    private static final Logger logger = LoggerFactory.getLogger(DictationService.class);
    private static final int LLM_MAX_TOKENS = 2000;

    private final MicrophoneRecorder recorder = new MicrophoneRecorder();
    /** Anzahl laufender STT/LLM-Jobs (Aufnahme und Verarbeitung sind entkoppelt). */
    private final AtomicInteger pendingJobs = new AtomicInteger(0);

    public MicrophoneRecorder getRecorder() {
        return recorder;
    }

    public boolean isRecording() {
        return recorder.isRecording();
    }

    public boolean isProcessing() {
        return pendingJobs.get() > 0;
    }

    public int getPendingJobCount() {
        return pendingJobs.get();
    }

    public boolean startRecording() {
        if (recorder.isRecording()) {
            return false;
        }
        if (!MicrophoneRecorder.isMicrophoneAvailable()) {
            return false;
        }
        recorder.discardBuffer();
        return recorder.startRecording();
    }

    public CompletableFuture<DictationResult> stopAndProcess(String editorContext) {
        return stopAndProcess(editorContext, DictationVocabulary.empty(), null, 0);
    }

    /**
     * Beendet Aufnahme und verarbeitet Audio zu fertigem Manuskripttext.
     *
     * @param editorContext Text vor dem Cursor zur Disambiguierung
     * @param vocabulary    Figurennamen und Begriffe aus dem Projekt
     * @param onTranscriptAnalyzed optional, nach STT (z. B. UI: „Anweisung erkannt“)
     * @param quoteStyleIndex Anführungszeichen-Stil aus dem Editor ({@code host.getQuoteStyleIndex()})
     */
    public CompletableFuture<DictationResult> stopAndProcess(
            String editorContext,
            DictationVocabulary vocabulary,
            Consumer<DictationPromptBuilder.TranscriptAnalysis> onTranscriptAnalyzed,
            int quoteStyleIndex) {
        Path wav = recorder.stopRecording();
        if (wav == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Keine Aufnahmedaten."));
        }

        final SpeechToTextBackend stt;
        try {
            stt = createSttBackend();
        } catch (RuntimeException e) {
            deleteQuietly(wav);
            return CompletableFuture.failedFuture(e);
        }

        pendingJobs.incrementAndGet();
        String language = ResourceManager.getParameter("dictation.language", "de");
        DictationVocabulary vocab = vocabulary != null ? vocabulary : DictationVocabulary.empty();
        String whisperPrompt = vocab.whisperInitialPrompt();

        return stt.transcribe(wav, language, whisperPrompt)
                .thenCompose(raw -> {
                    DictationPromptBuilder.TranscriptAnalysis analysis =
                            DictationPromptBuilder.analyzeTranscript(raw);
                    if (onTranscriptAnalyzed != null) {
                        onTranscriptAnalyzed.accept(analysis);
                    }
                    return interpretWithLlm(raw, editorContext, analysis, vocab, quoteStyleIndex);
                })
                .whenComplete((result, error) -> {
                    pendingJobs.decrementAndGet();
                    deleteQuietly(wav);
                });
    }

    public void cancelRecording() {
        if (recorder.isRecording()) {
            Path wav = recorder.stopRecording();
            deleteQuietly(wav);
        } else {
            recorder.discardBuffer();
        }
    }

    public CompletableFuture<DictationResult> stopAndProcess(
            String editorContext,
            DictationVocabulary vocabulary,
            Consumer<DictationPromptBuilder.TranscriptAnalysis> onTranscriptAnalyzed) {
        return stopAndProcess(editorContext, vocabulary, onTranscriptAnalyzed, 0);
    }

    private CompletableFuture<DictationResult> interpretWithLlm(
            String rawTranscript,
            String editorContext,
            DictationPromptBuilder.TranscriptAnalysis analysis,
            DictationVocabulary vocabulary,
            int quoteStyleIndex) {
        if (rawTranscript == null || rawTranscript.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Leeres Transkript."));
        }
        boolean instruction = analysis.mode() == DictationMode.INSTRUCTION;
        DictationVocabulary vocab = vocabulary != null ? vocabulary : DictationVocabulary.empty();
        AIBackend backend = createLlmBackend();
        backend.setTemperature(instruction ? 0.65 : 0.3);

        String transcriptForLlm = analysis.rawTranscript();
        // Gesprochene Quote-Befehle vor dem LLM auflösen (sonst streicht das Modell oft nur die Wörter).
        if (!instruction) {
            transcriptForLlm = DictationSpokenMarkup.finish(
                    transcriptForLlm, editorContext, quoteStyleIndex);
        }

        String systemPrompt = instruction
                ? DictationPromptBuilder.buildInstructionSystemPrompt(quoteStyleIndex)
                : DictationPromptBuilder.buildSystemPrompt(quoteStyleIndex);
        String userMessage = instruction
                ? DictationPromptBuilder.buildInstructionUserMessage(
                        analysis.instructionText(), editorContext, vocab)
                : DictationPromptBuilder.buildUserMessage(transcriptForLlm, editorContext, vocab);

        final String transcriptForDedup = transcriptForLlm;
        return backend.chat(systemPrompt, userMessage, LLM_MAX_TOKENS)
                .thenApply(response -> {
                    String processed = DictationPromptBuilder.cleanLlmOutput(response);
                    if (!instruction) {
                        processed = DictationPromptBuilder.deduplicateAgainstContext(
                                processed, editorContext, transcriptForDedup);
                    }
                    processed = DictationSpokenMarkup.finish(processed, editorContext, quoteStyleIndex);
                    if (processed.isBlank()) {
                        throw new IllegalStateException("LLM lieferte leeren Text.");
                    }
                    logger.debug("Diktat verarbeitet ({}): {} → {} Zeichen",
                            analysis.mode(), analysis.rawTranscript().length(), processed.length());
                    return new DictationResult(analysis.rawTranscript(), processed, analysis.mode());
                });
    }

    static SpeechToTextBackend createSttBackend() {
        String backend = resolveSttBackendName();
        if ("OpenAI".equalsIgnoreCase(backend)) {
            return new OpenAIWhisperBackend();
        }
        if ("Local".equalsIgnoreCase(backend)) {
            return new LocalWhisperBackend();
        }
        throw new IllegalStateException("Unbekanntes STT-Backend: " + backend
                + " (unterstützt: Local, OpenAI)");
    }

    /**
     * Prüft Mikrofon und STT-Einrichtung vor der Aufnahme.
     *
     * @return Fehlertext oder {@code null} wenn bereit
     */
    public static String checkReadiness() {
        if (!MicrophoneRecorder.isMicrophoneAvailable()) {
            return "Kein Mikrofon verfügbar. Bitte Mikrofon anschließen und Systemeinstellungen prüfen.";
        }
        String backend = resolveSttBackendName();
        if ("Local".equalsIgnoreCase(backend)) {
            return checkLocalWhisperReadiness();
        }
        if ("OpenAI".equalsIgnoreCase(backend)) {
            if (!OpenAIWhisperBackend.hasConfiguredApiKey()) {
                return "Kein API-Key für OpenAI-Whisper.\n\n"
                        + "Entweder dictation.whisper_api_key setzen oder dictation.stt_backend auf Local stellen.\n\n"
                        + WhisperRuntime.buildSetupHint();
            }
        }
        return null;
    }

    private static String resolveSttBackendName() {
        String backend = ResourceManager.getParameter("dictation.stt_backend", "Local").trim();
        if ("OpenAI".equalsIgnoreCase(backend) && !OpenAIWhisperBackend.hasConfiguredApiKey()) {
            logger.info("dictation.stt_backend=OpenAI, aber kein API-Key – nutze Local (whisper.cpp)");
            ResourceManager.saveParameter("dictation.stt_backend", "Local");
            return "Local";
        }
        return backend;
    }

    private static String checkLocalWhisperReadiness() {
        if (WhisperRuntime.resolveExecutable() == null) {
            return "whisper-cli nicht gefunden.\n\n" + WhisperRuntime.buildSetupHint();
        }
        Path model = WhisperRuntime.resolveModelPath();
        if (!Files.isRegularFile(model)) {
            return "Whisper-Modell fehlt:\n" + model + "\n\n" + WhisperRuntime.buildSetupHint();
        }
        return null;
    }

    static AIBackend createLlmBackend() {
        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");
        AIBackend backend = "OpenAI".equals(backendType)
                ? new OpenAIBackend()
                : new OllamaBackend(new OllamaService());
        String model = "OpenAI".equals(backendType)
                ? ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini")
                : ResourceManager.getParameter("agent.ollama.model", ParameterRegistry.DEFAULT_OLLAMA_MODEL);
        backend.setCurrentModel(model);
        backend.setTemperature(0.3);
        return backend;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            logger.trace("Temp-Audio löschen: {}", e.getMessage());
        }
    }
}
