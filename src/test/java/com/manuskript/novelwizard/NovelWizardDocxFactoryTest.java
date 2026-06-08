package com.manuskript.novelwizard;

import org.junit.jupiter.api.Test;

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
    void chapterDocxFileName_usesKapitelNumberDashTitlePattern() {
        ChapterEntry chapter = new ChapterEntry(1, "Kapitel 1: Der Kristall", "Summary");
        assertEquals("Kapitel 1 - Der Kristall.docx", NovelWizardDocxFactory.chapterDocxFileName(chapter));

        ChapterEntry second = new ChapterEntry(12, "Kapitel 12: Erste Vision", "Summary");
        assertEquals("Kapitel 12 - Erste Vision.docx", NovelWizardDocxFactory.chapterDocxFileName(second));

        ChapterEntry third = new ChapterEntry(3, "Kapitel 3: Brücktücke", "Summary");
        assertEquals("Kapitel 3 - Brücktücke.docx", NovelWizardDocxFactory.chapterDocxFileName(third));
    }
}
