package com.manuskript.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformRulesTest {

    @Test
    void keywordLimits() {
        assertTrue(PlatformRules.keywordOk("kurz"));
        assertTrue(PlatformRules.keywordOk("a".repeat(50)));
        assertFalse(PlatformRules.keywordOk("a".repeat(51)));
        assertTrue(PlatformRules.keywordOk(""));
        assertTrue(PlatformRules.keywordOk(null));
    }

    @Test
    void descriptionLimit() {
        assertTrue(PlatformRules.descriptionOk("Hallo"));
        assertTrue(PlatformRules.descriptionOk("x".repeat(4000)));
        assertFalse(PlatformRules.descriptionOk("x".repeat(4001)));
    }

    @Test
    void kdpRatio() {
        assertTrue(PlatformRules.ratioNearKdp(1600, 2560));
        assertFalse(PlatformRules.ratioNearKdp(1600, 1600));
    }
}

class ChecklistEngineTest {

    @Test
    void emptyModelFailsBasics() {
        PublishPackageModel model = new PublishPackageModel();
        CoverAnalyzer.CoverInfo cover = CoverAnalyzer.analyze((String) null);
        ChapterTocChecker.TocResult toc = new ChapterTocChecker.TocResult(0, 0, 0, List.of());
        ChecklistEngine.Report report = ChecklistEngine.evaluate(model, cover, toc);
        assertTrue(report.failCount() >= 3);
        assertTrue(report.items().stream().anyMatch(i -> "title".equals(i.id())
                && i.status() == ChecklistItem.Status.FAIL));
    }

    @Test
    void filledKeywordsPass() {
        PublishPackageModel model = new PublishPackageModel();
        model.title = "Titel";
        model.author = "Autor";
        model.blurb = "x".repeat(200);
        for (int i = 0; i < 7; i++) {
            model.setKeyword(i, "keyword " + i);
        }
        model.rightsConfirmed = true;
        CoverAnalyzer.CoverInfo cover = new CoverAnalyzer.CoverInfo(
                Path.of("cover.jpg"), true, 1600, 2560, 100_000, "jpg", null);
        ChapterTocChecker.TocResult toc = new ChapterTocChecker.TocResult(3, 0, 0, List.of());
        ChecklistEngine.Report report = ChecklistEngine.evaluate(model, cover, toc);
        assertEquals(0, report.failCount());
        assertTrue(report.passCount() >= 5);
    }

    @Test
    void coverTooSmallFails() {
        CoverAnalyzer.CoverInfo cover = new CoverAnalyzer.CoverInfo(
                Path.of("c.png"), true, 400, 600, 1000, "png", null);
        List<ChecklistItem> items = ChecklistEngine.evaluateCoverOnly(cover);
        assertTrue(items.stream().anyMatch(i ->
                i.id().startsWith("cover-kdp") && i.status() == ChecklistItem.Status.FAIL));
    }

    @Test
    void keywordOverLimitFails() {
        PublishPackageModel model = new PublishPackageModel();
        model.title = "T";
        model.author = "A";
        model.blurb = "x".repeat(150);
        model.setKeyword(0, "a".repeat(51));
        CoverAnalyzer.CoverInfo cover = new CoverAnalyzer.CoverInfo(
                Path.of("c.jpg"), true, 1600, 2560, 1000, "jpg", null);
        ChapterTocChecker.TocResult toc = new ChapterTocChecker.TocResult(1, 0, 0, List.of());
        ChecklistEngine.Report report = ChecklistEngine.evaluate(model, cover, toc);
        assertTrue(report.items().stream().anyMatch(i ->
                "keywords".equals(i.id()) && i.status() == ChecklistItem.Status.FAIL));
    }
}

class ChapterTocCheckerTest {

    @TempDir
    Path temp;

    @Test
    void detectsMissingAndHeadings() throws Exception {
        Path data = temp.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("eins.md"), "# Kapitel Eins\nText", StandardCharsets.UTF_8);
        Files.writeString(data.resolve("zwei.md"), "Ohne Überschrift", StandardCharsets.UTF_8);
        ChapterTocChecker.TocResult result = ChapterTocChecker.check(
                temp, List.of("eins.docx", "zwei.docx", "fehlt.docx"));
        assertEquals(3, result.selectedCount());
        assertEquals(1, result.missingMd());
        assertEquals(1, result.withoutHeading());
    }
}

class PublishPackageStoreTest {

    @TempDir
    Path temp;

    @Test
    void roundTripAndPandocSeed() throws Exception {
        Path data = temp.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"),
                "{\"title\":\"Pandoc Titel\",\"author\":\"Max\",\"abstract\":\"Blurb\"}",
                StandardCharsets.UTF_8);
        PublishPackageModel seeded = PublishPackageStore.loadOrSeed(temp);
        assertEquals("Pandoc Titel", seeded.title);
        assertEquals("Max", seeded.author);
        assertEquals("Blurb", seeded.blurb);

        seeded.subtitle = "Unter";
        seeded.setKeyword(0, "fantasy roman");
        PublishPackageStore.save(temp, seeded);

        PublishPackageModel loaded = PublishPackageStore.load(PublishPackageStore.packageFile(temp));
        assertEquals("Unter", loaded.subtitle);
        assertEquals("fantasy roman", loaded.keywordAt(0));
    }

    @Test
    void syncBlurbWritesAbstractToPandocMetadata() throws Exception {
        Path data = temp.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"),
                "{\"title\":\"Alt\",\"author\":\"Max\"}",
                StandardCharsets.UTF_8);
        PublishPackageModel model = new PublishPackageModel();
        model.blurb = "Neuer Klappentext für den Export.";
        model.blurbToExport = true;
        PublishPackageStore.syncBlurbToExport(temp, model);
        Map<String, String> pandoc = PublishPackageStore.loadPandocMap(temp);
        assertEquals("Neuer Klappentext für den Export.", pandoc.get("abstract"));
        assertEquals("Alt", pandoc.get("title"));
        assertEquals("Max", pandoc.get("author"));
    }

    @Test
    void syncBlurbSkippedWhenDisabled() throws Exception {
        Path data = temp.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"),
                "{\"abstract\":\"Bleibt\"}",
                StandardCharsets.UTF_8);
        PublishPackageModel model = new PublishPackageModel();
        model.blurb = "Soll nicht landen";
        model.blurbToExport = false;
        PublishPackageStore.syncBlurbToExport(temp, model);
        String json = Files.readString(data.resolve("pandoc_metadata.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("Bleibt"));
        assertFalse(json.contains("Soll nicht landen"));
    }

    @Test
    void exportBlurbDefaultOnWhenFieldMissing() {
        PublishPackageModel model = new PublishPackageModel();
        model.blurbToExport = null;
        assertTrue(model.exportBlurb());
    }
}

class KeywordAiSupportTest {

    @Test
    void parsesSevenPhrasesAndDropsSingleWords() {
        String raw = """
                space opera kolonieschiff
                thriller
                militärische science fiction
                1. slow burn feinde zu verbündeten
                "politische intrigue im all"
                LitRPG raumschiff crew
                dunkle space opera macht
                vergessene kolonie überleben
                noch eine achte phrase die ignoriert wird
                """;
        List<String> phrases = KeywordAiSupport.parseKeywords(raw);
        assertEquals(7, phrases.size());
        assertFalse(phrases.stream().anyMatch(p -> p.equalsIgnoreCase("thriller")));
        assertTrue(phrases.get(0).contains("space opera"));
        assertFalse(phrases.get(2).contains("\""));
    }

    @Test
    void rejectsBannedClaims() {
        List<String> phrases = KeywordAiSupport.parseKeywords("bestseller science fiction\nspace opera flotte");
        assertEquals(1, phrases.size());
        assertEquals("space opera flotte", phrases.get(0));
    }

    @TempDir
    Path temp;

    @Test
    void loadsWorldFilesIntoPrompt() throws Exception {
        Files.writeString(temp.resolve("synopsis.txt"), "Die Crew fliegt zur Sonne.", StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("characters.txt"), "Lyra ist Navigatorin.", StandardCharsets.UTF_8);
        PublishPackageModel model = new PublishPackageModel();
        model.title = "Die Sonnenfresser";
        model.blurb = "Ein Schiff jagt Sterne.";
        String user = KeywordAiSupport.userPrompt(model, temp, "LitRPG Elemente, Space Opera");
        assertTrue(user.contains("LitRPG"));
        assertTrue(user.contains("synopsis.txt"));
        assertTrue(user.contains("Lyra"));
        assertTrue(user.contains("Sonnenfresser"));
    }
}

class BlurbAiSupportTest {

    @Test
    void parsesBlurbAndStripsMarkdown() {
        String raw = """
                ```text
                Klappentext: Ein Schiff jagt die Sonne.

                Die Crew steht vor der Wahl.
                ```
                """;
        String blurb = BlurbAiSupport.parseBlurb(raw);
        assertTrue(blurb.contains("Ein Schiff jagt die Sonne"));
        assertFalse(blurb.toLowerCase(Locale.ROOT).startsWith("klappentext"));
        assertFalse(blurb.contains("```"));
    }

    @Test
    void trimsToKdpLimit() {
        String longText = "Wort ".repeat(900);
        String parsed = BlurbAiSupport.parseBlurb(longText);
        assertTrue(parsed.length() <= PlatformRules.KDP_DESCRIPTION_MAX_CHARS);
    }

    @TempDir
    Path temp;

    @Test
    void promptIncludesWorldAndChapters() throws Exception {
        Files.writeString(temp.resolve("synopsis.txt"), "Die Sonne frisst Welten.", StandardCharsets.UTF_8);
        Path data = temp.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve(".manuskript_selection.json"), "[\"k1.docx\"]", StandardCharsets.UTF_8);
        Files.writeString(data.resolve("k1.md"), "# Kapitel 1\nEs beginnt.", StandardCharsets.UTF_8);
        PublishPackageModel model = new PublishPackageModel();
        model.title = "Sonnenfresser";
        model.authorHints = "Space Opera";
        model.blurbHints = "düster, langsam";
        String user = BlurbAiSupport.userPrompt(model, temp, model.blurbHints);
        assertTrue(user.contains("Space Opera"));
        assertTrue(user.contains("düster"));
        assertTrue(user.contains("synopsis.txt"));
        assertTrue(user.contains("Kapitel 1"));
    }
}
