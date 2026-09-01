package com.manuskript.dictation;

/**
 * Ergebnis einer Diktier-Pipeline (STT + optional LLM).
 *
 * @param llmFormatted {@code false}, wenn nur das Whisper-Rohtranskript eingefügt wurde
 *                     (KI-Timeout oder Fehler)
 */
public record DictationResult(String rawTranscript, String processedText, DictationMode mode,
                              boolean llmFormatted) {

    public DictationResult(String rawTranscript, String processedText, DictationMode mode) {
        this(rawTranscript, processedText, mode, true);
    }

    public DictationResult(String rawTranscript, String processedText) {
        this(rawTranscript, processedText, DictationMode.TRANSCRIPTION, true);
    }
}
