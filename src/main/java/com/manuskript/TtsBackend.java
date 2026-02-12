package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fassade für TTS-Generierung: einheitlich „Text + Stimme + Lexikon → Audiodatei“.
 * Verzweigt intern auf ComfyUI oder ElevenLabs je nach voice.getProvider().
 */
public final class TtsBackend {

    private static final Logger logger = LoggerFactory.getLogger(TtsBackend.class);

    private TtsBackend() {}

    /**
     * Erzeugt TTS und schreibt die Audiodatei nach outputPath.
     * Lexikon wird auf den Text angewendet (für beide Backends).
     *
     * @param text Rohtext
     * @param voice Gewählte Stimme (ComfyUI oder ElevenLabs)
     * @param lexicon Aussprache-Lexikon (kann null/leer sein)
     * @param outputPath Ziel-Pfad (z. B. .mp3)
     * @param useConsistencyTemperature nur für ComfyUI: Temperatur für Hörbuch-Konsistenz begrenzen
     * @param promptLogger nur für ComfyUI: optional Logger für den Prompt
     * @return outputPath bei Erfolg
     */
    public static Path generateTtsToFile(String text, ComfyUIClient.SavedVoice voice,
                                         Map<String, String> lexicon, Path outputPath,
                                         boolean useConsistencyTemperature,
                                         Consumer<String> promptLogger) throws IOException, InterruptedException {
        if (voice == null) {
            throw new IOException("Keine Stimme angegeben.");
        }
        Map<String, String> lex = lexicon != null ? lexicon : ComfyUIClient.getDefaultPronunciationLexicon();
        String preparedText = (lex.isEmpty() || text == null) ? (text != null ? text : "")
                : ComfyUIClient.applyPronunciationLexicon(text, lex);

        if ("elevenlabs".equalsIgnoreCase(voice.getProvider()) && voice.getElevenLabsVoiceId() != null && !voice.getElevenLabsVoiceId().isBlank()) {
            ElevenLabsClient client = new ElevenLabsClient();
            String apiKey = ResourceManager.getParameter("tts.elevenlabs_api_key", "");
            client.setApiKey(apiKey);
            String modelId = voice.getElevenLabsModelId();
            if (modelId != null && modelId.isBlank()) modelId = null;
            ElevenLabsClient.VoiceSettings vs = new ElevenLabsClient.VoiceSettings(
                    voice.getElevenLabsStability(),
                    voice.getElevenLabsSimilarityBoost(),
                    voice.getElevenLabsSpeed(),
                    voice.isElevenLabsUseSpeakerBoost(),
                    voice.getElevenLabsStyle());
            client.generateToFile(preparedText, voice.getElevenLabsVoiceId(), modelId, outputPath, vs);
            return outputPath;
        }

        ComfyUIClient client = new ComfyUIClient();
        var history = client.generateTTSWithSavedVoice(text, voice, lex, useConsistencyTemperature, promptLogger);
        client.downloadAudioToFile(history, outputPath);
        return outputPath;
    }
}
