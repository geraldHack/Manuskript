package com.manuskript.dictation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictationInsertCapitalizationTest {

    @Test
    void midSentence_lowercasesFirstLetter() {
        assertEquals("öffnete die Tür. ",
                DictationInsertCapitalization.adjustLeadingCapital(
                        "Öffnete die Tür. ", "Er ging zur Tür und "));
    }

    @Test
    void afterPeriod_keepsCapital() {
        assertEquals("Dann ging er.",
                DictationInsertCapitalization.adjustLeadingCapital(
                        "Dann ging er.", "Er blieb stehen. "));
    }

    @Test
    void afterQuestionOrExclamation_keepsCapital() {
        assertEquals("Nein.",
                DictationInsertCapitalization.adjustLeadingCapital("Nein.", "Kommst du? "));
        assertEquals("Sofort.",
                DictationInsertCapitalization.adjustLeadingCapital("Sofort.", "Halt! "));
    }

    @Test
    void afterColonOrOpeningQuote_keepsCapital() {
        assertEquals("Komm her.",
                DictationInsertCapitalization.adjustLeadingCapital("Komm her.", "Er sagte: "));
        assertEquals("Hallo",
                DictationInsertCapitalization.adjustLeadingCapital("Hallo", "Er sagte: „"));
    }

    @Test
    void newParagraphOrDocumentStart_keepsCapital() {
        assertEquals("Am Morgen",
                DictationInsertCapitalization.adjustLeadingCapital("Am Morgen", "Ende des Abschnitts.\n\n"));
        assertEquals("Am Morgen",
                DictationInsertCapitalization.adjustLeadingCapital("Am Morgen", ""));
    }

    @Test
    void emphasisPrefix_lowercasesLetterInside() {
        assertEquals("*wirklich* nicht",
                DictationInsertCapitalization.adjustLeadingCapital(
                        "*Wirklich* nicht", "Sie sagte, "));
    }

    @Test
    void quotedInsert_keepsCapital() {
        assertEquals("„Hallo“, rief er.",
                DictationInsertCapitalization.adjustLeadingCapital(
                        "„Hallo“, rief er.", "Und dann "));
    }

    @Test
    void sentenceAlreadyStarted_detectsContext() {
        assertTrue(DictationInsertCapitalization.sentenceAlreadyStarted("Er ging und"));
        assertFalse(DictationInsertCapitalization.sentenceAlreadyStarted("Er ging."));
        assertFalse(DictationInsertCapitalization.sentenceAlreadyStarted("Er ging.\n"));
    }
}
