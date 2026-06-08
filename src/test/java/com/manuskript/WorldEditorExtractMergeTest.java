package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEditorExtractMergeTest {

    @Test
    void mergeAppend_keepsExistingCharactersAndAddsNew() {
        String existing = """
                # Charaktere

                ## Anna Mueller
                **Rolle:** Protagonistin
                **Ziele:** Freiheit

                ## Thomas Berg
                **Rolle:** Antagonist
                """;

        String extracted = """
                ## Lisa Wagner
                **Rolle:** Freundin
                **Beziehungen:** Anna
                """;

        String merged = WorldEditorExtractMerge.mergeAppendExtract("characters.txt", existing, extracted);

        assertTrue(merged.contains("## Anna Mueller"));
        assertTrue(merged.contains("## Thomas Berg"));
        assertTrue(merged.contains("## Lisa Wagner"));
        assertTrue(merged.contains("Protagonistin"));
    }

    @Test
    void mergeAppend_updatesExistingSectionWithoutRemovingOthers() {
        String existing = """
                ## Anna Mueller
                **Rolle:** Protagonistin

                ## Thomas Berg
                **Rolle:** Antagonist
                """;

        String extracted = """
                ## Anna Mueller
                **Rolle:** Protagonistin
                **Character Arc:** Lernt Vertrauen
                """;

        String merged = WorldEditorExtractMerge.mergeAppendExtract("characters.txt", existing, extracted);

        assertTrue(merged.contains("## Thomas Berg"));
        assertTrue(merged.contains("Antagonist"));
        assertTrue(merged.contains("Lernt Vertrauen"));
        assertFalse(merged.contains("**Rolle:** alt"));
    }

    @Test
    void mergeAppend_matchesUmlautVariants() {
        String existing = """
                ## Anna Mueller
                **Rolle:** alt
                """;

        String extracted = """
                ## Anna Müller
                **Rolle:** neu
                """;

        String merged = WorldEditorExtractMerge.mergeAppendExtract("characters.txt", existing, extracted);

        assertTrue(merged.contains("**Rolle:** neu"));
        assertFalse(merged.contains("## Anna Mueller\n**Rolle:** alt"));
        assertFalse(merged.split("## Anna").length > 2);
    }
}
