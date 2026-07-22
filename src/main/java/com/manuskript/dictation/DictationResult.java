package com.manuskript.dictation;

/**
 * Ergebnis einer Diktier-Pipeline (STT + LLM).
 */
public record DictationResult(String rawTranscript, String processedText, DictationMode mode) {

    public DictationResult(String rawTranscript, String processedText) {
        this(rawTranscript, processedText, DictationMode.TRANSCRIPTION);
    }
}
