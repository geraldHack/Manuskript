package com.manuskript;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Berechnet Textanalyse-Treffer ohne RichTextFX (für den Canvas-Editor).
 */
public class TextAnalysisEngine {

    public record AnalysisSpan(int start, int end, String styleId) {
    }

    public record AnalysisResult(List<AnalysisSpan> spans, List<AnalysisSpan> navigationHits, String summary) {
        public static AnalysisResult empty(String message) {
            return new AnalysisResult(List.of(), List.of(), message);
        }
    }

    private record WordRepeat(String word, int pos1, int pos2, int distance) {
    }

    public List<AnalysisSpan> analyzeFuellwoerter(String text) throws IOException {
        return analyzeFuellwoerterDetailed(text).spans();
    }

    public AnalysisResult analyzeFuellwoerterDetailed(String text) throws IOException {
        Properties props = loadProperties();
        String list = props.getProperty("fuellwoerter", "");
        String[] words = list.split(",");
        if (words.length == 0 || words[0].trim().isEmpty()) {
            return AnalysisResult.empty("Keine Füllwörter in der Konfiguration gefunden.");
        }

        Map<String, Integer> wordCount = new LinkedHashMap<>();
        List<AnalysisSpan> spans = new ArrayList<>();
        List<AnalysisSpan> nav = new ArrayList<>();

        for (String word : words) {
            String trimmed = word.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(trimmed) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            int count = 0;
            while (matcher.find()) {
                count++;
                AnalysisSpan span = new AnalysisSpan(matcher.start(), matcher.end(), "highlight-yellow");
                spans.add(span);
                nav.add(span);
            }
            if (count > 0) {
                wordCount.put(trimmed, count);
            }
        }

        String summary = buildWordCountSummary("FÜLLWÖRTER-ANALYSE", wordCount, nav.size());
        return new AnalysisResult(spans, nav, summary);
    }

    public AnalysisResult analyzeSprechwoerter(String text) throws IOException {
        Properties props = loadProperties();
        String list = props.getProperty("sprechwörter", props.getProperty("sprechwoerter", ""));
        String[] words = list.split(",");
        if (words.length == 0 || words[0].trim().isEmpty()) {
            return AnalysisResult.empty("Keine Sprechwörter in der Konfiguration gefunden.");
        }

        Map<String, Integer> wordCount = new HashMap<>();
        List<AnalysisSpan> spans = new ArrayList<>();
        List<AnalysisSpan> nav = new ArrayList<>();

        for (String word : words) {
            String trimmed = word.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(trimmed) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            int count = 0;
            while (matcher.find()) {
                count++;
                AnalysisSpan span = new AnalysisSpan(matcher.start(), matcher.end(), "search-match-first");
                spans.add(span);
                nav.add(span);
            }
            if (count > 0) {
                wordCount.put(trimmed, count);
            }
        }

        StringBuilder summary = new StringBuilder("Sprechwörter-Analyse\n");
        summary.append("Gefundene Treffer: ").append(nav.size()).append("\n\n");
        if (!wordCount.isEmpty()) {
            summary.append("Gefundene Sprechwörter:\n");
            wordCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> summary.append(String.format("  %-20s %d Mal\n", e.getKey(), e.getValue())));
            summary.append("\nVerwende „Nächster Treffer“ / „Vorheriger Treffer“ zur Navigation.\n");
        } else {
            summary.append("Keine Sprechwörter im Text gefunden.\n");
        }
        return new AnalysisResult(spans, nav, summary.toString());
    }

    public AnalysisResult analyzeSprechantworten(String text) throws IOException {
        Properties props = loadProperties();
        String regex = props.getProperty("sprechantworten_regex", "");
        if (regex.isBlank()) {
            return AnalysisResult.empty("Kein Sprechantworten-Pattern in der Konfiguration gefunden.");
        }
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        List<AnalysisSpan> spans = new ArrayList<>();
        while (matcher.find()) {
            AnalysisSpan span = new AnalysisSpan(matcher.start(), matcher.end(), "search-match-first");
            spans.add(span);
        }
        String summary = "Sprechantworten-Analyse\nGefundene Treffer: " + spans.size() + "\n\n";
        if (!spans.isEmpty()) {
            summary += "Verwende „Nächster Treffer“ / „Vorheriger Treffer“ zur Navigation.\n";
        }
        return new AnalysisResult(spans, spans, summary);
    }

    public AnalysisResult analyzeWortwiederholungen(String text, int abstand) throws IOException {
        Properties props = loadProperties();
        int minLaenge = Integer.parseInt(props.getProperty("wortwiederholungen_min_laenge", "4"));
        Set<String> ignoreWords = parseIgnoreWords(props.getProperty("wortwiederholungen_ignoriere_woerter", ""));

        Pattern wordPattern = Pattern.compile("\\b\\w+\\b", Pattern.CASE_INSENSITIVE);
        Matcher wordMatcher = wordPattern.matcher(text);
        List<String> relevantWords = new ArrayList<>();
        List<Integer> wordPositions = new ArrayList<>();

        while (wordMatcher.find()) {
            String word = wordMatcher.group().toLowerCase();
            if (word.length() >= minLaenge && !ignoreWords.contains(word)) {
                relevantWords.add(word);
                wordPositions.add(wordMatcher.start());
            }
        }

        List<WordRepeat> repeats = findWordRepeats(relevantWords, wordPositions, abstand, false);
        return buildWordRepeatResult(text, repeats, ignoreWords,
                "Wortwiederholungen-Analyse\nKonfiguration: Abstand ≤ " + abstand
                        + " Wörter, Mindestlänge ≥ " + minLaenge + " Zeichen\n");
    }

    public AnalysisResult analyzeWortwiederholungNah(String text) throws IOException {
        Properties props = loadProperties();
        int minLaenge = Integer.parseInt(props.getProperty("wortwiederholungen_min_laenge", "4"));

        Pattern wordPattern = Pattern.compile("\\b[a-zA-ZäöüßÄÖÜ]+\\b");
        Matcher wordMatcher = wordPattern.matcher(text);
        List<String> words = new ArrayList<>();
        List<Integer> wordPositions = new ArrayList<>();

        while (wordMatcher.find()) {
            String word = wordMatcher.group().toLowerCase();
            if (word.length() >= minLaenge) {
                words.add(word);
                wordPositions.add(wordMatcher.start());
            }
        }

        List<WordRepeat> repeats = findWordRepeats(words, wordPositions, 10, true);
        return buildWordRepeatResult(text, repeats, Set.of(),
                "=== WORTWIEDERHOLUNGEN NAH ===\n\n");
    }

    public AnalysisResult analyzePhrasenDetailed(String text) throws IOException {
        Properties props = loadProperties();
        String[] categories = {
                "phrasen_begann", "phrasen_emotionen", "phrasen_dialog",
                "phrasen_denken", "phrasen_gefuehle", "phrasen_bewegung"
        };
        Map<String, Integer> phraseCount = new LinkedHashMap<>();
        Map<Integer, Integer> phraseRanges = new TreeMap<>();

        for (String category : categories) {
            for (String phrase : props.getProperty(category, "").split(",")) {
                String trimmed = phrase.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Pattern pattern = Pattern.compile(trimmed.replace("*", ".*"), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(text);
                int count = 0;
                while (matcher.find()) {
                    count++;
                    int start = matcher.start();
                    int end = matcher.end();
                    if (!phraseRanges.containsKey(start) || end - start > phraseRanges.get(start) - start) {
                        phraseRanges.put(start, end);
                    }
                }
                if (count > 0) {
                    phraseCount.put(trimmed, count);
                }
            }
        }

        List<AnalysisSpan> spans = new ArrayList<>();
        List<AnalysisSpan> nav = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : phraseRanges.entrySet()) {
            AnalysisSpan span = new AnalysisSpan(entry.getKey(), entry.getValue(), "highlight-orange");
            spans.add(span);
            nav.add(span);
        }

        StringBuilder summary = new StringBuilder("=== PHRASEN-ANALYSE ===\n\n");
        if (phraseCount.isEmpty()) {
            summary.append("Keine Phrasen im Text gefunden.\n");
        } else {
            phraseCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> summary.append(String.format("  %-35s %3dx\n", e.getKey(), e.getValue())));
            summary.append("\nGesamt: ").append(nav.size()).append(" markierte Phrasen\n");
            summary.append("\nVerwende „Nächster Treffer“ / „Vorheriger Treffer“ zur Navigation.\n");
        }
        return new AnalysisResult(spans, nav, summary.toString());
    }

    public AnalysisResult analyzeSentenceLengthsDetailed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return AnalysisResult.empty("Kein Text zum Analysieren vorhanden.");
        }

        String[] paragraphs = text.split("\\n\\s*\\n", -1);
        List<ParagraphInfo> paragraphInfos = new ArrayList<>();
        List<AnalysisSpan> spans = new ArrayList<>();
        int currentPos = 0;

        Pattern sentencePattern = Pattern.compile("([^.!?]+[.!?])(?=\\s+|$)", Pattern.MULTILINE);

        for (String paragraphText : paragraphs) {
            if (paragraphText.trim().isEmpty()) {
                currentPos += paragraphText.length() + 2;
                continue;
            }

            int paragraphStart = text.indexOf(paragraphText, currentPos);
            if (paragraphStart < 0) {
                paragraphStart = currentPos;
            }
            int paragraphEnd = paragraphStart + paragraphText.length();

            List<Integer> sentenceWordCounts = new ArrayList<>();
            int totalWords = 0;
            int maxSentenceLength = 0;
            int longSentences = 0;
            int veryLongSentences = 0;

            Matcher sentenceMatcher = sentencePattern.matcher(paragraphText);
            while (sentenceMatcher.find()) {
                String sentenceText = sentenceMatcher.group(1).trim();
                if (sentenceText.isEmpty()) {
                    continue;
                }
                int wordCount = countWords(sentenceText);
                if (wordCount > 0) {
                    sentenceWordCounts.add(wordCount);
                    totalWords += wordCount;
                    maxSentenceLength = Math.max(maxSentenceLength, wordCount);
                    if (wordCount > 25) {
                        longSentences++;
                    }
                    if (wordCount > 30) {
                        veryLongSentences++;
                    }
                }
            }

            if (sentenceWordCounts.isEmpty() && !paragraphText.trim().isEmpty()) {
                int wordCount = countWords(paragraphText.trim());
                if (wordCount > 0) {
                    sentenceWordCounts.add(wordCount);
                    totalWords = wordCount;
                    maxSentenceLength = wordCount;
                    if (wordCount > 25) {
                        longSentences = 1;
                    }
                    if (wordCount > 30) {
                        veryLongSentences = 1;
                    }
                }
            }

            String category = null;
            if (!sentenceWordCounts.isEmpty()) {
                double avgLength = totalWords / (double) sentenceWordCounts.size();
                if (maxSentenceLength > 30) {
                    category = "sentence-long";
                } else if (maxSentenceLength > 25) {
                    category = "sentence-long";
                } else if (avgLength > 20 || maxSentenceLength > 20) {
                    category = "sentence-medium";
                } else {
                    category = "sentence-short";
                }
            }

            if (category != null) {
                int avgRounded = sentenceWordCounts.isEmpty()
                        ? 0
                        : (int) Math.round(totalWords / (double) sentenceWordCounts.size());
                paragraphInfos.add(new ParagraphInfo(paragraphStart, paragraphEnd,
                        sentenceWordCounts.size(), maxSentenceLength, avgRounded,
                        longSentences, veryLongSentences, category));
                spans.add(new AnalysisSpan(paragraphStart, paragraphEnd, category));
            }

            currentPos = paragraphEnd;
        }

        String summary = buildSentenceLengthSummary(text, paragraphInfos);
        return new AnalysisResult(spans, spans, summary);
    }

    private static int countWords(String text) {
        int count = 0;
        for (String word : text.split("\\s+")) {
            if (!word.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static String buildSentenceLengthSummary(String text, List<ParagraphInfo> paragraphInfos) {
        StringBuilder result = new StringBuilder("=== SATZLÄNGEN-ANALYSE (NACH ABSÄTZEN) ===\n\n");
        if (paragraphInfos.isEmpty()) {
            result.append("Keine Absätze zum Analysieren gefunden.\n");
            return result.toString();
        }

        int totalParagraphs = paragraphInfos.size();
        int totalSentences = 0;
        int shortParagraphs = 0;
        int mediumParagraphs = 0;
        int longParagraphs = 0;
        int totalVeryLongSentences = 0;
        int totalLongSentences = 0;

        for (ParagraphInfo para : paragraphInfos) {
            totalSentences += para.sentenceCount;
            totalVeryLongSentences += para.veryLongSentences;
            totalLongSentences += para.longSentences;
            switch (para.category) {
                case "sentence-short" -> shortParagraphs++;
                case "sentence-medium" -> mediumParagraphs++;
                default -> longParagraphs++;
            }
        }

        result.append(String.format("Gesamtanzahl Absätze: %d%n", totalParagraphs));
        result.append(String.format("Gesamtanzahl Sätze: %d%n", totalSentences));
        result.append(String.format("Absätze mit kurzen Sätzen: %d (%.1f%%)%n", shortParagraphs,
                totalParagraphs > 0 ? shortParagraphs * 100.0 / totalParagraphs : 0));
        result.append(String.format("Absätze mit mittleren Sätzen: %d (%.1f%%)%n", mediumParagraphs,
                totalParagraphs > 0 ? mediumParagraphs * 100.0 / totalParagraphs : 0));
        result.append(String.format("Absätze mit langen Sätzen: %d (%.1f%%)%n", longParagraphs,
                totalParagraphs > 0 ? longParagraphs * 100.0 / totalParagraphs : 0));
        result.append(String.format("Sehr lange Sätze (>30 Wörter): %d%n", totalVeryLongSentences));
        result.append(String.format("Lange Sätze (>25 Wörter): %d%n%n", totalLongSentences));

        if (totalVeryLongSentences > 0) {
            result.append(String.format("⚠ %d überlange Sätze gefunden (>30 Wörter).%n", totalVeryLongSentences));
        }
        if (totalLongSentences > 0 && totalSentences > 0) {
            double longPercentage = totalLongSentences * 100.0 / totalSentences;
            if (longPercentage > 20) {
                result.append(String.format(
                        "⚠ %.1f%% der Sätze sind lang (>25 Wörter) - erwäge Kürzungen oder Aufteilungen.%n",
                        longPercentage));
            }
        }

        if (totalParagraphs > 1) {
            int goodTransitions = 0;
            int badTransitions = 0;
            for (int i = 0; i < paragraphInfos.size() - 1; i++) {
                ParagraphInfo current = paragraphInfos.get(i);
                ParagraphInfo next = paragraphInfos.get(i + 1);
                boolean currentIsLong = "sentence-long".equals(current.category);
                boolean nextIsLong = "sentence-long".equals(next.category);
                boolean currentIsShort = "sentence-short".equals(current.category);
                boolean nextIsShort = "sentence-short".equals(next.category);
                if ((currentIsLong && nextIsShort) || (currentIsShort && nextIsLong)) {
                    goodTransitions++;
                } else if (currentIsLong && nextIsLong) {
                    badTransitions++;
                }
            }
            double transitionRatio = goodTransitions * 100.0 / (totalParagraphs - 1);
            if (badTransitions > 0) {
                result.append(String.format(
                        "⚠ %d mal folgen Absätze mit langen Sätzen direkt aufeinander.%n", badTransitions));
            }
            if (transitionRatio < 30 && totalParagraphs > 3) {
                result.append("⚠ Geringer Wechsel zwischen kurzen und langen Absätzen.\n");
            } else if (transitionRatio >= 30) {
                result.append("✓ Guter Wechsel zwischen Absätzen mit kurzen und langen Sätzen.\n");
            }
        }

        result.append("""
                
                💡 Markierungen im Text:
                  Grün = Absätze mit kurzen Sätzen
                  Gelb = Absätze mit mittleren Sätzen
                  Rot = Absätze mit langen Sätzen (>25 Wörter)
                """);
        return result.toString();
    }

    public List<AnalysisSpan> analyzePhrasen(String text) throws IOException {
        return analyzePhrasenDetailed(text).spans();
    }

    private record ParagraphInfo(int start, int end, int sentenceCount, int maxSentenceLength,
                                 int avgSentenceLength, int longSentences, int veryLongSentences,
                                 String category) {
    }

    public List<AnalysisSpan> analyzeSentenceLengths(String text) {
        return analyzeSentenceLengthsDetailed(text).spans();
    }

    private static List<WordRepeat> findWordRepeats(List<String> words, List<Integer> wordPositions,
                                                   int maxAbstand, boolean nahOnly) {
        List<WordRepeat> repeats = new ArrayList<>();
        Map<String, List<Integer>> occurrences = new HashMap<>();
        for (int i = 0; i < words.size(); i++) {
            occurrences.computeIfAbsent(words.get(i), k -> new ArrayList<>()).add(i);
        }

        if (nahOnly) {
            int minAbstand = 5;
            for (int i = 0; i < words.size() - 1; i++) {
                String current = words.get(i);
                for (int j = i + 1; j <= Math.min(i + maxAbstand + 1, words.size() - 1); j++) {
                    if (!current.equals(words.get(j))) {
                        continue;
                    }
                    int distance = j - i - 1;
                    if (distance >= minAbstand && distance <= maxAbstand) {
                        repeats.add(new WordRepeat(current, wordPositions.get(i), wordPositions.get(j), distance));
                    }
                }
            }
        } else {
            for (Map.Entry<String, List<Integer>> entry : occurrences.entrySet()) {
                List<Integer> positions = entry.getValue();
                if (positions.size() <= 1) {
                    continue;
                }
                for (int i = 0; i < positions.size() - 1; i++) {
                    for (int j = i + 1; j < positions.size(); j++) {
                        int pos1 = positions.get(i);
                        int pos2 = positions.get(j);
                        int distance = pos2 - pos1;
                        if (distance <= maxAbstand) {
                            repeats.add(new WordRepeat(entry.getKey(), wordPositions.get(pos1),
                                    wordPositions.get(pos2), distance));
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        repeats.sort(Comparator.comparingInt((WordRepeat w) -> w.distance).thenComparing(w -> w.word));
        return repeats;
    }

    private AnalysisResult buildWordRepeatResult(String text, List<WordRepeat> repeats,
                                                  Set<String> ignoreWords, String header) {
        if (repeats.isEmpty()) {
            return AnalysisResult.empty(header + "Keine Wortwiederholungen gefunden.\n");
        }

        Set<Integer> positionsToMark = new HashSet<>();
        for (WordRepeat w : repeats) {
            if (!ignoreWords.contains(w.word)) {
                positionsToMark.add(w.pos1);
                positionsToMark.add(w.pos2);
            }
        }

        List<Integer> sortedPositions = new ArrayList<>(positionsToMark);
        Collections.sort(sortedPositions);

        List<AnalysisSpan> spans = new ArrayList<>();
        List<AnalysisSpan> nav = new ArrayList<>();
        Pattern wordPattern = Pattern.compile("\\b\\w+\\b", Pattern.CASE_INSENSITIVE);

        for (int i = 0; i < sortedPositions.size(); i++) {
            int pos = sortedPositions.get(i);
            Matcher matcher = wordPattern.matcher(text);
            if (matcher.find(pos) && matcher.start() == pos) {
                String style = (i % 2 == 0) ? "search-match-first" : "search-match-second";
                AnalysisSpan span = new AnalysisSpan(matcher.start(), matcher.end(), style);
                spans.add(span);
                nav.add(span);
            }
        }

        StringBuilder summary = new StringBuilder(header);
        long relevant = repeats.stream().filter(w -> !ignoreWords.contains(w.word)).count();
        summary.append("Gefundene Wiederholungen: ").append(repeats.size())
                .append(" (davon ").append(relevant).append(" relevante)\n\n");

        Map<String, Integer> pairCounts = new LinkedHashMap<>();
        for (WordRepeat w : repeats) {
            if (!ignoreWords.contains(w.word)) {
                String key = w.word + " (Abstand " + w.distance + ")";
                pairCounts.put(key, pairCounts.getOrDefault(key, 0) + 1);
            }
        }
        if (!pairCounts.isEmpty()) {
            summary.append(String.format("%-35s %s%n", "Wortpaar", "Anzahl"));
            summary.append("-".repeat(40)).append("\n");
            pairCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> summary.append(String.format("  %-33s %dx%n", e.getKey(), e.getValue())));
            summary.append("\nVerwende „Nächster Treffer“ / „Vorheriger Treffer“ zur Navigation.\n");
        }
        return new AnalysisResult(spans, nav, summary.toString());
    }

    private static Set<String> parseIgnoreWords(String raw) {
        Set<String> ignore = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return ignore;
        }
        for (String word : raw.split(",")) {
            String trimmed = word.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                ignore.add(trimmed);
            }
        }
        return ignore;
    }

    private static String buildWordCountSummary(String title, Map<String, Integer> wordCount, int totalHits) {
        StringBuilder result = new StringBuilder("=== ").append(title).append(" ===\n\n");
        if (wordCount.isEmpty()) {
            result.append("Keine Treffer gefunden.\n");
            return result.toString();
        }
        wordCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> result.append(String.format("  %-20s %3dx%n", e.getKey(), e.getValue())));
        result.append("\n").append("=".repeat(30)).append("\n");
        result.append(String.format("GESAMT: %d Treffer%n", totalHits));
        result.append("=".repeat(30)).append("\n\n");
        result.append("Verwende „Nächster Treffer“ / „Vorheriger Treffer“ zur Navigation.\n");
        return result.toString();
    }

    private Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream input = ResourceManager.getPropertiesResource("textanalysis.properties")) {
            if (input == null) {
                throw new IOException("textanalysis.properties nicht gefunden");
            }
            props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return props;
    }
}
