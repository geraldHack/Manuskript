package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterSheetDocumentTest {

    @Test
    void parseAndSerialize_roundTrip() {
        String markdown = """
                # Charaktere

                ## Character Sheets
                Automatisch erzeugt.

                ## Acen Alvaro
                ![](acen.png){ width=80% }

                **Kurzname:** Acen
                **Rolle:** Protagonist
                **Alter / Aussehen:** Grau-blaue Augen
                """;

        CharacterSheetDocument.Document document = CharacterSheetDocument.parse(markdown);
        assertEquals("Acen", document.characters().get(0).field("Kurzname"));
        assertTrue(document.characters().get(0).imageMarkdown().contains("acen.png"));
        assertEquals(2, document.blocks().size());

        String serialized = CharacterSheetDocument.serialize(document);
        CharacterSheetDocument.Document again = CharacterSheetDocument.parse(serialized);
        assertEquals(document.characters().get(0).name(), again.characters().get(0).name());
        assertEquals(document.characters().get(0).field("Rolle"), again.characters().get(0).field("Rolle"));
    }

    @Test
    void mergeGenerated_updatesEmptyFieldsAndName() {
        CharacterSheetDocument.CharacterEntry existing = new CharacterSheetDocument.CharacterEntry("Neue Figur");
        CharacterSheetDocument.CharacterEntry generated = CharacterSheetDocument.parseCharacterSection("""
                ## Luna Vega
                **Rolle:** Antagonistin
                **Hintergrund:** Wuchs im Orbit-Viertel auf.
                """);
        CharacterSheetDocument.CharacterEntry merged =
                CharacterSheetDocument.mergeGenerated(existing, generated);
        assertEquals("Luna Vega", merged.name());
        assertEquals("Antagonistin", merged.field("Rolle"));
        assertTrue(merged.field("Hintergrund").contains("Orbit-Viertel"));
    }

    @Test
    void sanitizeFieldText_stripsStandaloneHorizontalRules() {
        assertEquals("Zeile eins\nZeile zwei",
                CharacterSheetDocument.sanitizeFieldText("Zeile eins\n---\nZeile zwei"));
        assertEquals("Notiz", CharacterSheetDocument.sanitizeFieldText("***\nNotiz\n___"));
    }

    @Test
    void parseCharacterSection_stripsRulesFromNotes() {
        CharacterSheetDocument.CharacterEntry entry = CharacterSheetDocument.parseCharacterSection("""
                ## Test
                ---
                Freitext in Notizen
                ---
                """);
        assertEquals("Freitext in Notizen", entry.field("Notizen"));
    }

    @Test
    void mergeGenerated_respectsFieldSelectionAndOnlyEmpty() {
        CharacterSheetDocument.CharacterEntry existing = new CharacterSheetDocument.CharacterEntry("Luna")
                .withField("Rolle", "Protagonistin")
                .withField("Persönlichkeit", "Bestehend");
        CharacterSheetDocument.CharacterEntry generated = new CharacterSheetDocument.CharacterEntry("Luna")
                .withField("Rolle", "Antagonistin")
                .withField("Persönlichkeit", "Neu")
                .withField("Hintergrund", "Neu");
        CharacterCardAiOptions options = new CharacterCardAiOptions(
                Set.of("Persönlichkeit", "Hintergrund"),
                false,
                false,
                true,
                "");

        CharacterSheetDocument.CharacterEntry merged =
                CharacterSheetDocument.mergeGenerated(existing, generated, options);

        assertEquals("Protagonistin", merged.field("Rolle"));
        assertEquals("Bestehend", merged.field("Persönlichkeit"));
        assertEquals("Neu", merged.field("Hintergrund"));
    }
}
