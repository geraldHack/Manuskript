package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fassade fuer TTS-Generierung: einheitlich "Text + Stimme + Lexikon -> Audiodatei".
 * Verzweigt intern auf ComfyUI oder ElevenLabs je nach voice.getProvider().
 * <p>
 * Bei ElevenLabs wird das Lexikon zusaetzlich als serverseitiges Pronunciation Dictionary
 * (Alias-Eintraege) hochgeladen und per {@code pronunciation_dictionary_locators} referenziert,
 * damit die Aussprache von Eigennamen konsistenter wird.
 */
public final class TtsBackend {

    private static final Logger logger = LoggerFactory.getLogger(TtsBackend.class);

    private TtsBackend() {}

    // -------- Gecachtes ElevenLabs Pronunciation Dictionary --------
    /** Gecachter Locator des zuletzt hochgeladenen Dictionaries. */
    private static volatile ElevenLabsClient.PronunciationDictionaryLocator cachedDictLocator;
    /** Hash des Lexikons, das dem gecachten Dictionary entspricht. */
    private static volatile int cachedLexiconHash;
    /** ID des gecachten Dictionaries (zum Loeschen beim naechsten Upload). */
    private static volatile String cachedDictId;

    /**
     * Sorgt dafuer, dass ein zum Lexikon passendes Pronunciation Dictionary bei ElevenLabs existiert.
     * Bei gleichbleibendem Lexikon wird der Cache wiederverwendet.
     * Bei leerem Lexikon wird kein Dictionary erzeugt (null zurueckgegeben).
     */
    private static ElevenLabsClient.PronunciationDictionaryLocator ensurePronunciationDictionary(
            ElevenLabsClient client, Map<String, String> lexicon) {
        if (lexicon == null || lexicon.isEmpty()) return null;
        int hash = lexicon.hashCode();
        if (cachedDictLocator != null && hash == cachedLexiconHash) {
            return cachedDictLocator;
        }
        try {
            // Altes Dictionary loeschen (falls vorhanden)
            if (cachedDictId != null) {
                client.deletePronunciationDictionary(cachedDictId);
                cachedDictId = null;
                cachedDictLocator = null;
            }
            String plsXml = ElevenLabsClient.buildPlsXml(lexicon);
            ElevenLabsClient.PronunciationDictionaryLocator loc =
                    client.uploadPronunciationDictionary(plsXml, "Manuskript-Lexikon");
            cachedDictLocator = loc;
            cachedLexiconHash = hash;
            cachedDictId = loc.dictionaryId;
            logger.info("ElevenLabs Pronunciation Dictionary aktualisiert: {} ({} Eintraege)", loc.dictionaryId, lexicon.size());
            return loc;
        } catch (Exception e) {
            logger.warn("ElevenLabs Pronunciation Dictionary Upload fehlgeschlagen - clientseitige Ersetzung wird verwendet: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Erzeugt TTS und schreibt die Audiodatei nach outputPath (ohne Seed).
     */
    public static Path generateTtsToFile(String text, ComfyUIClient.SavedVoice voice,
                                         Map<String, String> lexicon, Path outputPath,
                                         boolean useConsistencyTemperature,
                                         Consumer<String> promptLogger) throws IOException, InterruptedException {
        return generateTtsToFile(text, voice, lexicon, outputPath, useConsistencyTemperature, promptLogger, 0L);
    }

    /**
     * Erzeugt TTS und schreibt die Audiodatei nach outputPath.
     * Lexikon wird auf den Text angewendet (fuer beide Backends).
     * Bei ElevenLabs wird das Lexikon zusaetzlich als serverseitiges Pronunciation Dictionary genutzt.
     *
     * @param text Rohtext
     * @param voice Gewaehlte Stimme (ComfyUI oder ElevenLabs)
     * @param lexicon Aussprache-Lexikon (kann null/leer sein)
     * @param outputPath Ziel-Pfad (z. B. .mp3)
     * @param useConsistencyTemperature nur fuer ComfyUI: Temperatur fuer Hoerbuch-Konsistenz begrenzen
     * @param promptLogger nur fuer ComfyUI: optional Logger fuer den Prompt
     * @param seed Seed fuer reproduzierbare Ergebnisse (0 = zufaellig; nur ElevenLabs)
     * @return outputPath bei Erfolg
     */
    public static Path generateTtsToFile(String text, ComfyUIClient.SavedVoice voice,
                                         Map<String, String> lexicon, Path outputPath,
                                         boolean useConsistencyTemperature,
                                         Consumer<String> promptLogger,
                                         long seed) throws IOException, InterruptedException {
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
            Long elSeed = (seed > 0) ? seed : null;
            // Serverseitiges Pronunciation Dictionary hochladen/cachen (Alias-Eintraege)
            ElevenLabsClient.PronunciationDictionaryLocator dictLocator = ensurePronunciationDictionary(client, lex);
            client.generateToFile(preparedText, voice.getElevenLabsVoiceId(), modelId, outputPath, vs, elSeed, dictLocator);
            logger.info("ElevenLabs TTS generiert: {} (seed={}, dict={})", outputPath.getFileName(),
                    seed > 0 ? seed : "zufaellig", dictLocator != null ? dictLocator.dictionaryId : "keins");
            return outputPath;
        }

        ComfyUIClient client = new ComfyUIClient();
        var history = client.generateTTSWithSavedVoice(text, voice, lex, useConsistencyTemperature, promptLogger);
        client.downloadAudioToFile(history, outputPath);
        return outputPath;
    }
}
