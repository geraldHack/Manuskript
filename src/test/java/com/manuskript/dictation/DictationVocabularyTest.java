package com.manuskript.dictation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictationVocabularyTest {

    @Test
    void parseGlossaryFile_readsTermsAndIgnoresComments() {
        String glossary = """
                # Mein Glossar
                vintage
                vintage-Mantel
                Luna Sternfeld
                """;

        assertEquals(List.of("vintage", "vintage-Mantel", "Luna Sternfeld"),
                DictationVocabulary.parseGlossaryFile(glossary));
    }

    @Test
    void extractSectionNames_readsCharacterSheets() {
        String characters = """
                ## Character Sheets

                ## Luna Sternfeld
                **Rolle:** Protagonistin

                ## Jomar Keth
                **Rolle:** Antagonist
                """;

        assertEquals(List.of("Luna Sternfeld", "Jomar Keth"),
                DictationVocabulary.extractSectionNames(characters));
    }

    @Test
    void fromSources_prioritizesUserGlossary() {
        String glossary = "vintage\nvintage-Mantel\n";
        String characters = "## Luna\n";
        String chapter = "Sie trug einen vintage-Mantel.";

        DictationVocabulary vocab = DictationVocabulary.fromSources(
                glossary, characters, "", chapter);

        assertTrue(vocab.userGlossaryTerms().contains("vintage-Mantel"));
        assertTrue(vocab.characterNames().contains("Luna"));
        assertTrue(vocab.whisperInitialPrompt().contains("vintage-Mantel"));
        assertFalse(vocab.isEmpty());
    }

    @Test
    void extractTermsFromManuscript_findsHyphenCompounds() {
        List<String> terms = DictationVocabulary.extractTermsFromManuscript(
                "Ihr vintage-Mantel flatterte.", java.util.Set.of());

        assertTrue(terms.contains("vintage-Mantel"));
    }
}
