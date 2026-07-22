package com.manuskript.dictation;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Spracherkennung (Speech-to-Text) für die Diktierfunktion.
 */
public interface SpeechToTextBackend {

    String getName();

    /**
     * Transkribiert eine Audiodatei (z. B. WAV) in Rohtext.
     *
     * @param audioFile Pfad zur Audiodatei
     * @param language  ISO-Sprachcode (z. B. {@code de})
     */
    default CompletableFuture<String> transcribe(Path audioFile, String language) {
        return transcribe(audioFile, language, "");
    }

    /**
     * @param initialPrompt optionaler Kontext für Eigennamen/Fachbegriffe (Whisper {@code --prompt})
     */
    CompletableFuture<String> transcribe(Path audioFile, String language, String initialPrompt);
}
