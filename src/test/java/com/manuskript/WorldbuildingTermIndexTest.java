package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldbuildingTermIndexTest {

    @Test
    void load_extractsCharactersPlacesAndLore() {
        String characters = """
                ## Character Sheets

                ## Acen
                **Rolle:** Protagonist

                ## Nene
                Tochter Paleus'
                """;

        String worldbuilding = """
                ## Setting
                Allgemeine Regeln.

                ## Orte
                ### Nerathis
                Kuppelstadt in Sektor Epsilon-9.

                ## Lore
                ### Aufstieg Paleus
                Er erfand die Hyperspace-Bewegung.
                """;

        WorldbuildingTermIndex index = WorldbuildingTermIndex.load(characters, worldbuilding);
        List<WorldbuildingTermIndex.Entry> entries = index.entries();

        assertTrue(entries.stream().anyMatch(e ->
                e.term().equals("Acen") && e.category() == WorldbuildingTermIndex.Category.CHARACTER));
        assertTrue(entries.stream().anyMatch(e ->
                e.term().equals("Nerathis") && e.category() == WorldbuildingTermIndex.Category.PLACE));
        assertTrue(entries.stream().anyMatch(e ->
                e.term().equals("Aufstieg Paleus") && e.category() == WorldbuildingTermIndex.Category.LORE));
    }

    @Test
    void findMatches_respectsWordBoundaries() {
        WorldbuildingTermIndex index = WorldbuildingTermIndex.load(
                "## Luna\n",
                "## Orte\n### Luna\nStadt\n");

        List<WorldbuildingTermIndex.TextMatch> matches = index.findMatches("Luna ging nach Lunapark.");

        assertEquals(1, matches.size());
        assertEquals(0, matches.get(0).start());
        assertEquals(4, matches.get(0).end());
    }

    @Test
    void findMatches_prefersLongerTerms() {
        WorldbuildingTermIndex index = WorldbuildingTermIndex.load(
                "",
                """
                ## Orte
                ### Haus Kryos
                O2-Versorgung
                """);

        List<WorldbuildingTermIndex.TextMatch> matches = index.findMatches("Haus Kryos liefert O2.");

        assertEquals(1, matches.size());
        assertEquals("Haus Kryos", matches.get(0).entry().term());
    }

    @Test
    void load_readsKurznameAliases() {
        String characters = """
                ## Acen Alvaro
                **Kurzname:** Acen
                **Rolle:** Protagonist
                """;

        WorldbuildingTermIndex index = WorldbuildingTermIndex.load(characters, "");
        List<WorldbuildingTermIndex.TextMatch> matches = index.findMatches("Acen ging los.");

        assertEquals(1, matches.size());
        assertEquals("Acen", matches.get(0).entry().term());
        assertEquals("Acen Alvaro", matches.get(0).entry().sectionHeading());
    }

    @Test
    void findMatches_recognizesGenitiveAndApostropheForms() {
        WorldbuildingTermIndex index = WorldbuildingTermIndex.load(
                """
                ## Nene Arista
                **Kurzname:** Nene
                """,
                """
                ## Orte
                ### Kael'ir
                Volk
                """);

        List<WorldbuildingTermIndex.TextMatch> neneMatches = index.findMatches("In Nenes Schlafzimmer lag etwas.");
        assertEquals(1, neneMatches.size());
        assertEquals(3, neneMatches.get(0).start());
        assertEquals(8, neneMatches.get(0).end());

        List<WorldbuildingTermIndex.TextMatch> apostropheMatches = index.findMatches("Die Kael\u2019ir warten.");
        assertEquals(1, apostropheMatches.size());
        assertEquals("Kael'ir", apostropheMatches.get(0).entry().term());

        List<WorldbuildingTermIndex.TextMatch> paleusMatches = WorldbuildingTermIndex.load(
                """
                ## Paleus Arista
                **Kurzname:** Paleus
                """, "").findMatches("Paleus' Zorn war grenzenlos.");
        assertEquals(1, paleusMatches.size());
        assertEquals(7, paleusMatches.get(0).end());
    }

    @Test
    void normalizeCharacterHeading_stripsDuplicateParentheses() {
        assertEquals("Nene Arista", WorldbuildingTermIndex.normalizeCharacterHeading("Nene Arista (Nene Arista)"));
    }

    @Test
    void excerpt_stripsMarkdownForDisplay() {
        String characters = """
                ## Acen
                **Kurzname:** Acen
                **Rolle:** *Protagonist* mit [Wiki](http://example.test)
                """;

        WorldbuildingTermIndex index = WorldbuildingTermIndex.load(characters, "");
        WorldbuildingTermIndex.Entry acen = index.entries().stream()
                .filter(e -> e.term().equals("Acen"))
                .findFirst()
                .orElseThrow();

        assertTrue(acen.excerpt().contains("Rolle:"));
        assertTrue(acen.excerpt().contains("Protagonist"));
        assertFalse(acen.excerpt().contains("**"));
        assertFalse(acen.excerpt().contains("http://"));
    }

    @Test
    void excerpt_omitsImageMarkdown() {
        String characters = """
                ## Acen
                ![](portrait.png){ width=80% }

                **Rolle:** Protagonist
                """;

        WorldbuildingTermIndex index = WorldbuildingTermIndex.load(characters, "");
        WorldbuildingTermIndex.Entry acen = index.entries().stream()
                .filter(e -> e.sectionHeading().equals("Acen"))
                .findFirst()
                .orElseThrow();

        assertTrue(acen.excerpt().contains("Rolle:"));
        assertFalse(acen.excerpt().contains("![]"));
        assertFalse(acen.excerpt().contains("width="));
    }
}
