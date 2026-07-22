package com.manuskript.dictation;

/**
 * Verarbeitungsmodus nach der Spracherkennung.
 */
public enum DictationMode {
    /** Rohtranskript bereinigen und Korrekturen anwenden. */
    TRANSCRIPTION,
    /** Gesprochene Anweisung in neuen Manuskripttext umsetzen. */
    INSTRUCTION
}
