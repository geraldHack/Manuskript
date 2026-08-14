package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuityCheckServiceTest {

    @Test
    void parseCharacters_readsH2AndFacts() {
        String sheet = """
                ## Luna Stern
                **Rolle:** Heldin
                **Alter / Aussehen:** 17, blaue Augen, blondes Haar

                ## Jomar
                **Persönlichkeit:** stur
                """;
        List<ContinuityCheckService.CharacterSheet> sheets = ContinuityCheckService.parseCharacters(sheet);
        assertEquals(2, sheets.size());
        assertEquals("Luna Stern", sheets.get(0).name());
        assertTrue(sheets.get(0).facts().get("Alter / Aussehen").contains("blau"));
    }

    @Test
    void check_flagsConflictingEyeColorNearName() {
        String sheet = """
                ## Luna
                **Alter / Aussehen:** blaue Augen
                """;
        Map<String, String> chapters = new LinkedHashMap<>();
        chapters.put("kap1.md", "Luna hob den Blick. Ihre grünen Augen blitzten.");
        ContinuityCheckService.Report report = ContinuityCheckService.check(sheet, chapters);
        assertFalse(report.findings().isEmpty());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().toLowerCase().contains("grün")));
    }

    @Test
    void check_notesUnmentionedCharacter() {
        String sheet = "## Kalem\n**Rolle:** Mentor\n";
        Map<String, String> chapters = Map.of("kap1.md", "Es war still im Wald.");
        ContinuityCheckService.Report report = ContinuityCheckService.check(sheet, chapters);
        assertTrue(report.findings().stream()
                .anyMatch(f -> "Kalem".equals(f.character()) && f.message().contains("nicht vor")));
    }
}
