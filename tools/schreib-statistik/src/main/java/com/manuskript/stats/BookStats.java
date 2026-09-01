package com.manuskript.stats;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ergebnis eines Projekt-Scans.
 */
public final class BookStats {

    public record Chapter(
            String name,
            Path mdFile,
            int words,
            Instant modified,
            String text) {
    }

    public record ImageHit(
            String chapter,
            String path,
            String caption,
            Path resolved) {
    }

    public final List<Chapter> chapters;
    public final int totalWords;
    public final int averageWords;
    public final Chapter shortest;
    public final Chapter longest;
    public final Map<String, Integer> speechPhrases;
    public final Map<String, Integer> speechVerbs;
    public final Map<String, Integer> phrases;
    public final List<ImageHit> images;

    public BookStats(
            List<Chapter> chapters,
            int totalWords,
            int averageWords,
            Chapter shortest,
            Chapter longest,
            Map<String, Integer> speechPhrases,
            Map<String, Integer> speechVerbs,
            Map<String, Integer> phrases,
            List<ImageHit> images) {
        this.chapters = chapters;
        this.totalWords = totalWords;
        this.averageWords = averageWords;
        this.shortest = shortest;
        this.longest = longest;
        this.speechPhrases = speechPhrases;
        this.speechVerbs = speechVerbs;
        this.phrases = phrases;
        this.images = images;
    }
}
