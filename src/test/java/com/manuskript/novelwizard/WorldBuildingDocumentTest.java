package com.manuskript.novelwizard;

import com.manuskript.WorldbuildingTermIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBuildingDocumentTest {

    @Test
    void detectsRequiredSections() {
        String doc = """
                ## Setting
                Magie ist verboten.

                ## Orte
                ### Hauptstadt
                Zentrum des Reiches.

                ## Lore
                ### Der grosse Bruch
                Vor hundert Jahren …
                """;
        assertTrue(WorldBuildingDocument.hasRequiredSections(doc));
    }

    @Test
    void rejectsMissingSection() {
        assertFalse(WorldBuildingDocument.hasRequiredSections("""
                ## Setting
                Nur Setting.
                """));
    }

    @Test
    void normalize_convertsBoldBulletsUnderOrte() {
        String raw = """
                ## Setting
                Magie prägt den Alltag.

                ## Orte
                Geografie und Orte:**

                *   **Windbruch:** Ein kleines, ländliches Dorf, in dem die Geschichte beginnt.
                *   **Alduria:** Die Hauptstadt, eine große und überfüllte Stadt.
                *   **Die Akademie der Geistbinder:** Eine prestigeträchtige Einrichtung in Alduria.

                ## Lore
                (noch offen)
                """;

        String normalized = WorldBuildingDocument.normalize(raw);

        assertTrue(normalized.contains("### Windbruch"));
        assertTrue(normalized.contains("### Alduria"));
        assertTrue(normalized.contains("### Die Akademie der Geistbinder"));
        assertFalse(normalized.contains("*   **Windbruch:**"));
        assertTrue(normalized.contains("ländliches Dorf"));

        WorldbuildingTermIndex index = WorldbuildingTermIndex.load("", normalized);
        assertEquals(3, index.entries().stream()
                .filter(e -> e.category() == WorldbuildingTermIndex.Category.PLACE)
                .count());
    }

    @Test
    void normalize_movesGeographyBlockFromSettingIntoOrte() {
        String raw = """
                ## Setting
                Die Welt ist repressiv.

                Geografie und Orte:**

                *   **Windbruch:** Ein kleines Dorf.
                *   **Alduria:** Die Hauptstadt.

                ## Orte

                ## Lore
                Alte Mythen.
                """;

        String normalized = WorldBuildingDocument.normalize(raw);

        assertTrue(normalized.contains("### Windbruch"));
        assertTrue(normalized.contains("### Alduria"));
        int orteIdx = normalized.indexOf("## Orte");
        int windbruchIdx = normalized.indexOf("### Windbruch");
        assertTrue(windbruchIdx > orteIdx);
        assertFalse(normalized.contains("*   **Windbruch:**"));
    }

    @Test
    void normalize_demotesH2PlacesToH3UnderOrte() {
        String raw = """
                ## Setting
                Ok.

                ## Orte

                ## Windbruch
                Ein kleines Dorf.

                ## Alduria
                Die Hauptstadt.

                ## Lore
                Mythen.
                """;

        String normalized = WorldBuildingDocument.normalize(raw);
        assertTrue(normalized.contains("### Windbruch"));
        assertTrue(normalized.contains("### Alduria"));
        assertFalse(normalized.contains("\n## Windbruch\n"));
        assertTrue(normalized.contains("## Orte"));
        assertTrue(normalized.contains("## Lore"));
    }

    @Test
    void normalize_keepsExistingH3Places() {
        String raw = """
                ## Setting
                Ok.

                ## Orte
                ### Nerathis
                Kuppelstadt.

                ## Lore
                ### Bruch
                Damals.
                """;
        String normalized = WorldBuildingDocument.normalize(raw);
        assertTrue(normalized.contains("### Nerathis"));
        assertTrue(normalized.contains("Kuppelstadt."));
    }
}
