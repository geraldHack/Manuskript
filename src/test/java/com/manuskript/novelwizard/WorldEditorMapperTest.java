package com.manuskript.novelwizard;

import com.manuskript.NovelManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEditorMapperTest {

    @TempDir
    Path projectDir;

    @Test
    void characterSheetsReplacePreviousWizardBlock() throws Exception {
        Path file = projectDir.resolve(NovelManager.CHARACTERS_FILE);
        Files.writeString(file, """
                Alte manuelle Liste

                ## Character Sheets

                ## Acen Alvaro (Acen Alvaro)
                **Rolle:** Protagonist
                """, StandardCharsets.UTF_8);

        WorldEditorMapper mapper = new WorldEditorMapper(projectDir);
        mapper.persistPhase(NovelWizardPhase.CHARACTERS, """
                ## Nene Arista (Nene Arista)
                **Rolle:** Protagonistin
                """);

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("Alte manuelle Liste"));
        assertFalse(written.contains("Acen Alvaro"));
        assertTrue(written.contains("Nene Arista"));
        assertEquals(1, countOccurrences(written, "## Character Sheets"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
