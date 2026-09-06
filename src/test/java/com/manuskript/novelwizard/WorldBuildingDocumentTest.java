package com.manuskript.novelwizard;

import org.junit.jupiter.api.Test;

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
}
