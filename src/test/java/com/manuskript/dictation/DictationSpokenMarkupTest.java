package com.manuskript.dictation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DictationSpokenMarkupTest {

    private static final int GERMAN = 0;
    private static final int FRENCH = 1;
    private static final int ENGLISH = 2;

    private static final String OPEN_DE = "\u201E";
    private static final String CLOSE_DE = "\u201C";
    private static final String OPEN_DE_SINGLE = "\u201A";
    private static final String CLOSE_DE_SINGLE = "\u2019";
    private static final String OPEN_FR = "\u00BB";
    private static final String CLOSE_FR = "\u00AB";

    @Test
    void finish_smartQuoteAlternatesGerman() {
        assertEquals("Sie sagte " + OPEN_DE + "Hallo" + CLOSE_DE,
                DictationSpokenMarkup.finish("Sie sagte Anführungszeichen Hallo Anführungszeichen", "", GERMAN));
    }

    @Test
    void finish_wrapWordInQuotesWithBitteSetzen() {
        assertEquals("Die " + OPEN_DE + "Glühlampen" + CLOSE_DE + " glommen schwach.",
                DictationSpokenMarkup.finish(
                        "Die Glühlampen bitte in Anführungszeichen setzen glommen schwach.",
                        "", GERMAN));
    }

    @Test
    void finish_wrapWordInSimpleQuotes() {
        assertEquals("Die " + OPEN_DE_SINGLE + "Glühlampen" + CLOSE_DE_SINGLE + " glommen schwach.",
                DictationSpokenMarkup.finish(
                        "Die Glühlampen bitte in einfache Anführungszeichen setzen glommen schwach.",
                        "", GERMAN));
    }

    @Test
    void finish_smartQuoteContinuesFromEditorContext() {
        String before = "Er flüsterte " + OPEN_DE + "Komm";
        assertEquals("mit" + CLOSE_DE,
                DictationSpokenMarkup.finish("mit Anführungszeichen", before, GERMAN));
    }

    @Test
    void finish_explicitOpenStillWorks() {
        assertEquals("Sie sagte " + OPEN_DE + "Hallo",
                DictationSpokenMarkup.finish("Sie sagte Anführungszeichen unten Hallo", "", GERMAN));
    }

    @Test
    void finish_stripsLiteralCommandInQuotes() {
        assertEquals(OPEN_DE + "Hallo",
                DictationSpokenMarkup.finish(OPEN_DE + "Anführungszeichen" + CLOSE_DE + " Hallo", "", GERMAN));
    }

    @Test
    void finish_usesFrenchQuoteStyle() {
        assertEquals("Il dit " + OPEN_FR + "bonjour" + CLOSE_FR,
                DictationSpokenMarkup.finish("Il dit Anführungszeichen bonjour Anführungszeichen", "", FRENCH));
    }

    @Test
    void finish_usesEnglishQuoteStyle() {
        assertEquals("She said \"hello\"",
                DictationSpokenMarkup.finish("She said Anführungszeichen hello Anführungszeichen", "", ENGLISH));
    }

    @Test
    void finish_convertsLlmGermanQuotesToFrenchStyle() {
        assertEquals("Il dit »bonjour«",
                DictationSpokenMarkup.finish("Il dit „bonjour“", "", FRENCH));
    }

    @Test
    void finish_convertsLlmGermanQuotesToEnglishStyle() {
        assertEquals("Sie sagte \"hello\"",
                DictationSpokenMarkup.finish("Sie sagte „hello“", "", ENGLISH));
    }

    @Test
    void finish_recognizesAnfuehrungszeichenWithoutUmlaut() {
        assertEquals("Sie sagte " + OPEN_DE + "Hallo" + CLOSE_DE,
                DictationSpokenMarkup.finish("Sie sagte Anfuehrungszeichen Hallo Anfuehrungszeichen", "", GERMAN));
    }

    @Test
    void finish_replacesSpokenGedankenstrich() {
        assertEquals("Wort " + "\u2013" + " Pause",
                DictationSpokenMarkup.finish("Wort Gedankenstrich Pause", "", GERMAN));
    }

    @Test
    void finish_replacesDoubleHyphenButNotTriple() {
        assertEquals("a " + "\u2013" + " b --- c",
                DictationSpokenMarkup.finish("a -- b --- c", "", GERMAN));
    }
}
