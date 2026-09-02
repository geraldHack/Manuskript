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
    void scansEveryMarkdownChapterNotOnlySelection() throws Exception {
        Path book = temp.resolve("roman");
        Files.createDirectories(book.resolve("data"));
        Files.writeString(book.resolve("data").resolve("1. Anfang.md"), "Hallo Welt", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve("2. Spaeter.md"), "Noch mehr Text hier", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve("10. Ende.md"), "Schluss jetzt", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve(".manuskript_selection.json"),
                "[\"1. Anfang.docx\"]", StandardCharsets.UTF_8);
        Files.writeString(book.resolve("data").resolve(".hidden.md"), "nicht", StandardCharsets.UTF_8);
        BookStats stats = StatsEngine.scan(book, temp);
        assertEquals(3, stats.chapters.size());
        assertEquals("1. Anfang", stats.chapters.get(0).name());
        assertEquals("2. Spaeter", stats.chapters.get(1).name());
        assertEquals("10. Ende", stats.chapters.get(2).name());
        assertEquals(2, stats.chapters.get(0).words());
        assertEquals("2. Spaeter", stats.longest.name());
        assertEquals("1. Anfang", stats.shortest.name());
    }

    @Test
    void sortsLastModifiedDescendingInList() throws Exception {
        Path book = temp.resolve("zeit");
        Files.createDirectories(book.resolve("data"));
        Path oldMd = book.resolve("data").resolve("alt.md");
        Path newMd = book.resolve("data").resolve("neu.md");
        Files.writeString(oldMd, "eins", StandardCharsets.UTF_8);
        Thread.sleep(30);
        Files.writeString(newMd, "zwei drei", StandardCharsets.UTF_8);
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
                "„Los“, sagte Jomar leise. Dann fragte Mara nach dem Weg. Später erwiderte sie.",
                props);
        assertEquals(1, counts.verbs().getOrDefault("sagte", 0));
        assertEquals(1, counts.verbs().getOrDefault("fragte", 0));
        assertEquals(1, counts.verbs().getOrDefault("erwiderte", 0));
        assertTrue(counts.phrases().keySet().stream()
                .anyMatch(key -> key.toLowerCase().startsWith("sagte jomar")));
    }

    @Test
    void countsEverySagteNotOnlyOneWordThenPeriod() {
        Properties props = new Properties();
        props.setProperty("sprechwörter", "sagte");
        String text = "sagte Jomar leise. sagte er und ging. Jomar sagte nichts. sagte sie.";
        StatsEngine.SpeechCounts counts = StatsEngine.countSpeech(text, props);
        assertEquals(4, counts.verbs().getOrDefault("sagte", 0));
        int phraseHits = counts.phrases().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(4, phraseHits);
    }

    @Test
    void countsEveryAntworteteVariant() {
        Properties props = new Properties();
        props.setProperty("sprechwörter", "antwortete");
        String text = """
                »Ja«, antwortete Jomar.
                Dann antwortete sie ihm leise.
                antwortete er.
                „Nein!“ antwortete der Alte, ohne aufzusehen.
                Antwortete:
                Später antwortete Mara und ging.
                """;
        StatsEngine.SpeechCounts counts = StatsEngine.countSpeech(text, props);
        assertEquals(6, counts.verbs().getOrDefault("antwortete", 0));
        int phraseHits = counts.phrases().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(6, phraseHits);
    }

    @Test
    void duplicateSpeechVerbInListIsCountedOnce() {
        Properties props = new Properties();
        props.setProperty("sprechwörter", "antwortete,antwortete,berichtete");
        StatsEngine.SpeechCounts counts = StatsEngine.countSpeech("Dann antwortete sie.", props);
        assertEquals(1, counts.verbs().getOrDefault("antwortete", 0));
    }

    @Test
    void speechDoesNotMatchVerbInsideAnotherWord() {
        Properties props = new Properties();
        props.setProperty("sprechwörter", "sang");
        StatsEngine.SpeechCounts none = StatsEngine.countSpeech("Gesang der Vögel.", props);
        assertTrue(none.verbs().isEmpty());
        StatsEngine.SpeechCounts hit = StatsEngine.countSpeech("Dann sang Mara.", props);
        assertEquals(1, hit.verbs().getOrDefault("sang", 0));
    }

    @Test
    void countsWildcardPhrasesEachOccurrence() {
        Properties props = new Properties();
        props.setProperty("phrasen_gefuehle", "* herz schlug *");
        Map<String, Integer> phrases = StatsEngine.countPhrases(
                "Ihr Herz schlug laut. Später schlug irgendetwas anderes. Noch ihr Herz schlug wild.",
                props);
        assertEquals(2, phrases.get("* herz schlug *"));
    }

    @Test
    void wildcardPhraseMatchesWithoutSideWord() {
        Properties props = new Properties();
        props.setProperty("phrasen_gefuehle", "* herz schlug *");
        Map<String, Integer> phrases = StatsEngine.countPhrases("Herz schlug.", props);
        assertEquals(1, phrases.get("* herz schlug *"));
    }

    @Test
    void exactPhraseDoesNotMatchLongerWord() {
        Properties props = new Properties();
        props.setProperty("phrasen_dialog", "sagte er");
        Map<String, Integer> miss = StatsEngine.countPhrases("sagte erst später.", props);
        assertTrue(miss.isEmpty());
        Map<String, Integer> hit = StatsEngine.countPhrases("sagte er leise.", props);
        assertEquals(1, hit.get("sagte er"));
    }

    @Test
    void duplicatePhraseInTwoCategoriesIsCountedOnce() {
        Properties props = new Properties();
        props.setProperty("phrasen_emotionen", "runzelte die Stirn");
        props.setProperty("phrasen_bewegung", "runzelte die Stirn");
        Map<String, Integer> phrases = StatsEngine.countPhrases("Sie runzelte die Stirn.", props);
        assertEquals(1, phrases.get("runzelte die Stirn"));
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
