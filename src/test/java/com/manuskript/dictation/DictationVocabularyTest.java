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
    void whisperPrompt_doesNotUseCapitalizedChapterNouns() {
        DictationVocabulary vocab = DictationVocabulary.fromSources(
                "",
                "",
                "",
                "Nacken und Schultern. Ihre nackten Schultern glänzten.");

        String prompt = vocab.whisperInitialPrompt();
        assertFalse(prompt.contains("Nacken"));
        assertFalse(prompt.contains("Schultern"));
        assertFalse(vocab.llmGlossaryBlock().contains("Nacken"));
    }

    @Test
    void mergeTermsIntoGlossaryText_skipsDuplicatesCaseInsensitive() {
        StringBuilder glossary = new StringBuilder("Luna\nvintage\n");
        int added = DictationVocabulary.mergeTermsIntoGlossaryText(
                glossary, List.of("luna", "Paleus", "vintage-Mantel"));

        assertEquals(2, added);
        assertTrue(glossary.toString().contains("Paleus"));
        assertTrue(glossary.toString().contains("vintage-Mantel"));
        assertEquals(1, glossary.toString().toLowerCase().split("luna", -1).length - 1);
    }

    @Test
    void collectWorldEditorTerms_readsHeadings() {
        List<String> terms = DictationVocabulary.collectWorldEditorTerms(
                "## Luna\n\nText\n",
                "## Paleus\n\nOrt\n");
        assertEquals(List.of("Luna", "Paleus"), terms);
    }
}
