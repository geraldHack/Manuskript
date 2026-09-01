package com.manuskript.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsEngineTest {

    @TempDir
    Path temp;

    @Test
    void countsWordsLikeMainWindow() {
        assertEquals(0, StatsEngine.countWords("   "));
        assertEquals(3, StatsEngine.countWords("eins zwei drei"));
    }

    @Test
    void usesSelectionJsonAndMarkdown() throws Exception {
        Path book = temp.resolve("roman");
        Files.createDirectories(book.resolve("data"));
        Files.writeString(book.resolve("1. Anfang.docx"), "stub", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("2. Spaeter.docx"), "stub", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve("1. Anfang.md"), "Hallo Welt", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve("2. Spaeter.md"), "Noch mehr Text hier", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve(".manuskript_selection.json"),
                "[\"1. Anfang.docx\",\"2. Spaeter.docx\"]", StandardCharsets.UTF_8);
        BookStats stats = StatsEngine.scan(book, temp);
        assertEquals(2, stats.chapters.size());
        assertEquals(2, stats.chapters.get(0).words());
        assertEquals("2. Spaeter", stats.longest.name());
        assertEquals("1. Anfang", stats.shortest.name());
        assertTrue(stats.chapters.get(0).modified().isBefore(stats.chapters.get(1).modified())
                || !stats.chapters.get(0).modified().isAfter(stats.chapters.get(1).modified()));
    }

    @Test
    void sortsLastModifiedDescendingInList() throws Exception {
        Path book = temp.resolve("zeit");
        Files.createDirectories(book.resolve("data"));
        Files.writeString(book.resolve("alt.docx"), "x", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("neu.docx"), "x", StandardCharsets.UTF_8);
        Path oldMd = book.resolve("data").resolve("alt.md");
        Path newMd = book.resolve("data").resolve("neu.md");
        Files.writeString(oldMd, "eins", StandardCharsets.UTF_8);
        Thread.sleep(30);
        Files.writeString(newMd, "zwei drei", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve(".manuskript_selection.json"),
                "[\"alt.docx\",\"neu.docx\"]", StandardCharsets.UTF_8);
        BookStats stats = StatsEngine.scan(book, temp);
        List<BookStats.Chapter> byTime = stats.chapters.stream()
                .sorted((a, b) -> b.modified().compareTo(a.modified()))
                .toList();
        assertEquals("neu", byTime.get(0).name());
    }

    @Test
    void groupsSpeechAttributions() {
        Properties props = new Properties();
        props.setProperty("sprechwörter", "sagte,fragte,erwiderte");
        StatsEngine.SpeechCounts counts = StatsEngine.countSpeech(
                "„Los“, sagte Jomar. Dann fragte Mara: Er schwieg. Später erwiderte sie.",
                props);
        assertTrue(counts.phrases().containsKey("sagte Jomar.") || counts.phrases().keySet().stream()
                .anyMatch(key -> key.toLowerCase().startsWith("sagte jomar")));
        assertTrue(counts.verbs().getOrDefault("sagte", 0) >= 1);
        assertTrue(counts.verbs().getOrDefault("fragte", 0) >= 1);
    }

    @Test
    void countsWildcardPhrases() {
        Properties props = new Properties();
        props.setProperty("phrasen_gefuehle", "* herz schlug *");
        Map<String, Integer> phrases = StatsEngine.countPhrases("Ihr Herz schlug laut.", props);
        assertEquals(1, phrases.get("* herz schlug *"));
    }

    @Test
    void extractsMarkdownImages() throws Exception {
        Path book = temp.resolve("bilder");
        Path data = book.resolve("data");
        Files.createDirectories(data);
        Path png = book.resolve("familie.png");
        Files.write(png, new byte[] {1, 2, 3});
        List<BookStats.ImageHit> hits = StatsEngine.findImages(
                "1. Start",
                "![](familie.png){ width=80% }\n\n><c>Familie</c>",
                data,
                book);
        assertEquals(1, hits.size());
        assertEquals("Familie", hits.get(0).caption());
        assertEquals(png.toRealPath(), hits.get(0).resolved().toRealPath());
    }

    @Test
    void sprechantwortFallbackWhenRegexMangled() {
        Pattern pattern = StatsEngine.compileSprechantwortenPattern(
                "(sagte|fragte)s+w+.", "sagte,fragte");
        Matcher matcher = pattern.matcher("sagte er.");
        assertTrue(matcher.find());
        assertFalse(StatsEngine.isUsableSprechantwortenRegex("(sagte)s+w+."));
    }

    @Test
    void pluginDoesNotRequestBackground() {
        assertFalse(new SchreibStatistikPlugin().wantsBackgroundStart());
    }
}
