package com.manuskript.dictation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DictationPromptBuilderTest {

    @Test
    void analyzeTranscript_detectsAnweisungWithColon() {
        DictationPromptBuilder.TranscriptAnalysis analysis = DictationPromptBuilder.analyzeTranscript(
                "Anweisung: Schreibe, dass Luna wacklige Knie hat nach der Ankunft");

        assertEquals(DictationMode.INSTRUCTION, analysis.mode());
        assertEquals("Schreibe, dass Luna wacklige Knie hat nach der Ankunft", analysis.instructionText());
    }

    @Test
    void analyzeTranscript_detectsAnweisungWithoutColon() {
        DictationPromptBuilder.TranscriptAnalysis analysis = DictationPromptBuilder.analyzeTranscript(
                "Anweisung Schreibe dass Luna wacklige Knie hat");

        assertEquals(DictationMode.INSTRUCTION, analysis.mode());
        assertEquals("Schreibe dass Luna wacklige Knie hat", analysis.instructionText());
    }

    @Test
    void analyzeTranscript_detectsBefehlAndKommando() {
        assertEquals(DictationMode.INSTRUCTION,
                DictationPromptBuilder.analyzeTranscript("Befehl: Streiche den letzten Satz").mode());
        assertEquals(DictationMode.INSTRUCTION,
                DictationPromptBuilder.analyzeTranscript("Kommando: Füge einen Absatz ein").mode());
    }

    @Test
    void analyzeTranscript_normalDictationStaysTranscription() {
        DictationPromptBuilder.TranscriptAnalysis analysis = DictationPromptBuilder.analyzeTranscript(
                "Luna betrat den Raum mit wackligen Knien.");

        assertEquals(DictationMode.TRANSCRIPTION, analysis.mode());
        assertNull(analysis.instructionText());
    }

    @Test
    void analyzeTranscript_befehlInProseIsNotInstruction() {
        assertEquals(DictationMode.TRANSCRIPTION,
                DictationPromptBuilder.analyzeTranscript(
                        "Er gab den Befehl, die Tore zu schließen.").mode());
        assertEquals(DictationMode.TRANSCRIPTION,
                DictationPromptBuilder.analyzeTranscript(
                        "Auf Kommando drehte sie sich um.").mode());
    }

    @Test
    void deduplicateAgainstContext_removesRepeatedLastSentence() {
        String context = "Es war kalt. Luna zog den Mantel enger.";
        String duplicate = "Luna zog den Mantel enger.";
        String raw = "Dann ging sie nach Hause.";

        assertEquals(raw, DictationPromptBuilder.deduplicateAgainstContext(duplicate, context, raw));
    }

    @Test
    void deduplicateAgainstContext_stripsLeadingDuplicateKeepsNewText() {
        String context = "Luna zog den Mantel enger.";
        String output = "Luna zog den Mantel enger. Dann ging sie nach Hause.";
        String raw = "Dann ging sie nach Hause.";

        assertEquals("Dann ging sie nach Hause.",
                DictationPromptBuilder.deduplicateAgainstContext(output, context, raw));
    }

    @Test
    void cleanLlmOutput_unwrapsSimpleOuterQuotes() {
        assertEquals("Nur ein Satz.",
                DictationPromptBuilder.cleanLlmOutput("\"Nur ein Satz.\""));
    }

    @Test
    void cleanLlmOutput_keepsDialogueWhenOuterUnwrapWouldOrphanCloseQuote() {
        // Typischer LLM-Fehler: Dialog beginnt mit ", Modell hängt am Ende noch " an.
        String llm = "\"Kalem, nach allem hören\", sagte Jomar.\"";
        assertEquals(llm, DictationPromptBuilder.cleanLlmOutput(llm));
    }
}
