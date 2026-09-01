package com.manuskript.novelwizard;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovelWizardDocxFactoryTest {

    @Test
    void extractChapters_parsesHierarchyWithMarkdown() {
        String markdown = """
                ## Roman-Assistent: Kapitel
                # TRAUWELT
                ## Kapitelübersicht und Struktur
                Die **Traumwelt** verbindet Realität und Traum.

                ---
                ## AKT 1: Der Riss
                Im ersten Akt **eskalieren** die Ereignisse.

                ### Kapitel 1–3: Erwachen
                Drei Kapitel über den **Einstieg** in die Welt.

                #### Kapitel 1: Der Kristall
                Lena findet den *Kristall* im Keller.

                #### Kapitel 2: Erste Vision
                Sie sieht **Traumbilder** zum ersten Mal.
                """;

        List<ChapterEntry> chapters = NovelWizardDocxFactory.extractChapters(markdown);

        assertEquals(2, chapters.size());

        ChapterEntry first = chapters.get(0);
        assertEquals(1, first.number());
        assertEquals("Kapitel 1: Der Kristall", first.title());
        assertTrue(first.overviewMarkdown().contains("**Traumwelt**"));
        assertEquals("AKT 1: Der Riss", first.actHeading());
        assertTrue(first.actMarkdown().contains("**eskalieren**"));
        assertEquals("Kapitel 1–3: Erwachen", first.groupHeading());
        assertTrue(first.groupMarkdown().contains("**Einstieg**"));
        assertTrue(first.summary().contains("*Kristall*"));

        ChapterEntry second = chapters.get(1);
        assertEquals(2, second.number());
        assertTrue(second.summary().contains("**Traumbilder**"));
        assertEquals(first.actHeading(), second.actHeading());
        assertEquals(first.groupHeading(), second.groupHeading());
    }

    @Test
    void extractChapters_acceptsLevel2KapitelHeadings() {
        String markdown = """
                ## Kapitel 1: Der Kristall
                Lena findet den Kristall.

                ## Kapitel 2: Erste Vision
                Sie sieht Traumbilder.
                """;

        List<ChapterEntry> chapters = NovelWizardDocxFactory.extractChapters(markdown);

        assertEquals(2, chapters.size());
        assertEquals("Kapitel 1: Der Kristall", chapters.get(0).title());
        assertEquals("Kapitel 2: Erste Vision", chapters.get(1).title());
    }

    @Test
    void extractChapters_parsesSonnenfresserStyleHeadings() {
        String markdown = """
                ## Roman-Assistent: Kapitel

                ## Simulation
                Acen trainiert in einer Simulation.

                ## Feldversuch
                Maximilian wirft Acen einen Anzug zu.
                """;

        List<ChapterEntry> chapters = NovelWizardDocxFactory.extractChapters(markdown);

        assertEquals(2, chapters.size());
        assertEquals("Kapitel 1: Simulation", chapters.get(0).title());
        assertEquals("Kapitel 2: Feldversuch", chapters.get(1).title());
    }

    @Test
    void normalizeChapterMarkdown_preservesExplicitNumbersAndDropsDuplicateBlocks() {
        String markdown = """
                ## Simulation
                Erster Entwurf.

                ## Kapitel 1: Simulation
                Kanonische Fassung.

                ## Kapitel 2: Feldversuch
                Zweites Kapitel.

                ## Simulation
                Duplikat ignorieren.
                """;

        String normalized = NovelWizardDocxFactory.normalizeChapterMarkdown(markdown);

        assertTrue(normalized.contains("## Kapitel 1: Simulation"));
        assertTrue(normalized.contains("## Kapitel 2: Feldversuch"));
        assertTrue(normalized.contains("Kanonische Fassung"));
        assertTrue(!normalized.contains("Duplikat ignorieren"));

        List<ChapterEntry> chapters = NovelWizardDocxFactory.extractChapters(normalized);
        assertEquals(2, chapters.size());
        assertEquals(1, chapters.get(0).number());
        assertEquals("Kapitel 1: Simulation", chapters.get(0).title());
        assertTrue(chapters.get(0).summary().contains("Kanonische Fassung"));
    }

    @Test
    void extractChapters_ignoresRepeatedSonnenfresserBlocks() {
        String markdown = """
                ## Simulation
                Alt.

                ## Kapitel 1: Simulation
                Neu.

                ## Kapitel 2: Feldversuch
                Zwei.

                ## Simulation
                Wieder alt.

                ## Feldversuch
                Wieder zwei.
                """;

        List<ChapterEntry> chapters = NovelWizardDocxFactory.extractChapters(markdown);

        assertEquals(2, chapters.size());
        assertEquals("Kapitel 1: Simulation", chapters.get(0).title());
        assertEquals("Kapitel 2: Feldversuch", chapters.get(1).title());
    }

    @Test
    void chapterDocxFileName_usesNumberEvenForUnnumberedStyleTitle() {
        ChapterEntry chapter = new ChapterEntry(1, "Simulation", "Summary");
        assertEquals("Kapitel 1 - Simulation.docx", NovelWizardDocxFactory.chapterDocxFileName(chapter));
    }

    @Test
    void resolveChapterDocxPath_matchesExistingProjectFileName() throws Exception {
        Path dir = Files.createTempDirectory("novel-wizard-docx");
        Path existing = dir.resolve("Die \"Ende der Reise\".docx");
        Files.writeString(existing, "placeholder", StandardCharsets.UTF_8);
        ChapterEntry chapter = new ChapterEntry(5, "Die „Ende der Reise\"", "Summary");
        Path resolved = NovelWizardDocxFactory.resolveChapterDocxPath(dir, chapter);
        assertEquals(existing, resolved);
    }

    @Test
    void chapterDocxFileName_usesKapitelNumberDashTitlePattern() {
        ChapterEntry chapter = new ChapterEntry(1, "Kapitel 1: Der Kristall", "Summary");
        assertEquals("Kapitel 1 - Der Kristall.docx", NovelWizardDocxFactory.chapterDocxFileName(chapter));

        ChapterEntry second = new ChapterEntry(12, "Kapitel 12: Erste Vision", "Summary");
        assertEquals("Kapitel 12 - Erste Vision.docx", NovelWizardDocxFactory.chapterDocxFileName(second));

        ChapterEntry third = new ChapterEntry(3, "Kapitel 3: Brücktücke", "Summary");
        assertEquals("Kapitel 3 - Brücktücke.docx", NovelWizardDocxFactory.chapterDocxFileName(third));
    }
}
