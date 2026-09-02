package com.manuskript.stats;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Liest Kapitel-Markdown und Textanalyse-Parameter ohne die Haupt-App.
 */
public final class StatsEngine {

    private static final Pattern IMAGE_BLOCK = Pattern.compile(
            "!\\[([^\\]]*)]\\(([^)]+)\\)(?:\\{\\s*width\\s*=\\s*(\\d+)%\\s*})?"
                    + "(?:\\r?\\n(?:\\r?\\n)*\\s*(?:><c>|><center>)(.*?)(?:</c>|</center>))?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final String[] PHRASE_KEYS = {
            "phrasen_begann", "phrasen_emotionen", "phrasen_dialog",
            "phrasen_denken", "phrasen_gefuehle", "phrasen_bewegung"
    };

    private StatsEngine() {
    }

    public static BookStats scan(Path bookRoot, Path configDir) throws IOException {
        if (bookRoot == null || !Files.isDirectory(bookRoot)) {
            throw new IllegalArgumentException("Kein Projektverzeichnis");
        }
        Properties props = loadTextanalysis(configDir);
        Path dataDir = bookRoot.resolve("data");
        List<Path> markdownFiles = listAllChapterMarkdown(dataDir);
        List<BookStats.Chapter> chapters = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        List<BookStats.ImageHit> images = new ArrayList<>();
        for (Path md : markdownFiles) {
            String base = stripMarkdown(md.getFileName().toString());
            String text = Files.readString(md, StandardCharsets.UTF_8);
            int words = countWords(text);
            Instant modified = Files.getLastModifiedTime(md).toInstant();
            chapters.add(new BookStats.Chapter(base, md, words, modified, text));
            all.append(text).append('\n');
            images.addAll(findImages(base, text, dataDir, bookRoot));
        }
        int total = chapters.stream().mapToInt(BookStats.Chapter::words).sum();
        int average = chapters.isEmpty() ? 0 : total / chapters.size();
        BookStats.Chapter shortest = chapters.stream()
                .min(Comparator.comparingInt(BookStats.Chapter::words))
                .orElse(null);
        BookStats.Chapter longest = chapters.stream()
                .max(Comparator.comparingInt(BookStats.Chapter::words))
                .orElse(null);
        String corpus = all.toString();
        SpeechCounts speech = countSpeech(corpus, props);
        Map<String, Integer> phrases = countPhrases(corpus, props);
        return new BookStats(chapters, total, average, shortest, longest,
                speech.phrases, speech.verbs, phrases, images);
    }

    static int countWords(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    /**
     * Alle Kapitel-Arbeitskopien unter {@code data/*.md}, unabhängig von der Auswahl.
     */
    static List<Path> listAllChapterMarkdown(Path dataDir) throws IOException {
        List<Path> files = new ArrayList<>();
        if (dataDir == null || !Files.isDirectory(dataDir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.md")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (Files.isRegularFile(path) && !name.startsWith(".")) {
                    files.add(path);
                }
            }
        }
        files.sort((a, b) -> compareNatural(
                a.getFileName().toString(), b.getFileName().toString()));
        return files;
    }

    static SpeechCounts countSpeech(String text, Properties props) {
        String sprechwoerter = firstNonBlank(
                props.getProperty("sprechwörter", ""),
                props.getProperty("sprechwoerter", ""));
        String corpus = text == null ? "" : text;
        return new SpeechCounts(
                countSpeechPhrases(corpus, sprechwoerter),
                countSpeechVerbs(corpus, sprechwoerter));
    }

    static Map<String, Integer> countSpeechVerbs(String text, String sprechwoerter) {
        Map<String, Integer> verbs = new LinkedHashMap<>();
        String corpus = text == null ? "" : text;
        for (String verb : speechVerbs(sprechwoerter)) {
            int count = countWholeWord(corpus, verb);
            if (count > 0) {
                verbs.put(verb.toLowerCase(Locale.GERMAN), count);
            }
        }
        return sortByCount(verbs);
    }

    /**
     * Jedes Vorkommen des Sprechverbs, plus optionale Folgeworte bis zum Satzzeichen.
     * Zählt auch bloße „antwortete.“ / „antwortete:“.
     */
    static Map<String, Integer> countSpeechPhrases(String text, String sprechwoerter) {
        Map<String, Integer> phrases = new LinkedHashMap<>();
        String corpus = text == null ? "" : text;
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
        Pattern tail = Pattern.compile("(?:\\s+\\p{L}+){0,6}", flags);
        for (String verb : speechVerbs(sprechwoerter)) {
            Matcher matcher = wholeWordPattern(verb).matcher(corpus);
            while (matcher.find()) {
                int end = matcher.end();
                Matcher extra = tail.matcher(corpus);
                extra.region(end, corpus.length());
                if (extra.lookingAt()) {
                    end = extra.end();
                }
                phrases.merge(corpus.substring(matcher.start(), end).trim(), 1, Integer::sum);
            }
        }
        return sortByCount(phrases);
    }

    static Map<String, Integer> countPhrases(String text, Properties props) {
        Map<String, Integer> phraseCount = new LinkedHashMap<>();
        String corpus = text == null ? "" : text;
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
        for (String category : PHRASE_KEYS) {
            for (String phrase : props.getProperty(category, "").split(",")) {
                String trimmed = phrase.trim();
                if (trimmed.isEmpty() || phraseCount.containsKey(trimmed)) {
                    continue;
                }
                Pattern pattern;
                try {
                    pattern = Pattern.compile(toPhraseRegex(trimmed), flags);
                } catch (PatternSyntaxException e) {
                    continue;
                }
                Matcher matcher = pattern.matcher(corpus);
                int count = 0;
                while (matcher.find()) {
                    count++;
                }
                if (count > 0) {
                    phraseCount.put(trimmed, count);
                }
            }
        }
        return sortByCount(phraseCount);
    }

    static List<BookStats.ImageHit> findImages(String chapter, String markdown, Path mdDir, Path bookRoot) {
        List<BookStats.ImageHit> hits = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return hits;
        }
        Matcher matcher = IMAGE_BLOCK.matcher(markdown);
        while (matcher.find()) {
            String path = stripWidthSuffix(matcher.group(2)).trim();
            String caption = matcher.group(4);
            if (caption != null) {
                caption = caption.trim();
            }
            Path resolved = resolveImage(path, mdDir, bookRoot);
            hits.add(new BookStats.ImageHit(chapter, path, caption == null ? "" : caption, resolved));
        }
        return hits;
    }

    static Path resolveImage(String imagePath, Path mdDirectory, Path projectDirectory) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        String normalized = imagePath.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return null;
        }
        Path direct = Path.of(normalized);
        if (direct.isAbsolute()) {
            return Files.isRegularFile(direct) ? direct : null;
        }
        Path[] candidates = {
                mdDirectory == null ? null : mdDirectory.resolve(normalized),
                projectDirectory == null ? null : projectDirectory.resolve(normalized),
                mdDirectory != null && mdDirectory.getParent() != null
                        ? mdDirectory.getParent().resolve(normalized) : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static Pattern compileSprechantwortenPattern(String regex, String sprechwoerter) {
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
        if (isUsableSprechantwortenRegex(regex)) {
            try {
                return Pattern.compile(regex.trim(), flags);
            } catch (PatternSyntaxException ignored) {
                // fallback
            }
        }
        return Pattern.compile(buildSprechantwortenRegex(sprechwoerter), flags);
    }

    static boolean isUsableSprechantwortenRegex(String regex) {
        if (regex == null || regex.isBlank()) {
            return false;
        }
        String value = regex.trim();
        boolean mangled = value.contains("s+w+") && !value.contains("\\s") && !value.contains("\\w");
        return !mangled;
    }

    static String buildSprechantwortenRegex(String sprechwoerter) {
        List<String> quoted = new ArrayList<>();
        for (String verb : speechVerbs(sprechwoerter)) {
            quoted.add(Pattern.quote(verb));
        }
        return "(?<!\\p{L})(" + String.join("|", quoted) + ")(?!\\p{L})(?:\\s+\\p{L}+){0,6}";
    }

    static List<String> speechVerbs(String sprechwoerter) {
        List<String> verbs = new ArrayList<>();
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (sprechwoerter != null) {
            for (String raw : sprechwoerter.split(",")) {
                String word = raw.trim();
                if (!word.isEmpty()) {
                    unique.putIfAbsent(word.toLowerCase(Locale.GERMAN), word);
                }
            }
        }
        verbs.addAll(unique.values());
        if (verbs.isEmpty()) {
            verbs.add("sagte");
            verbs.add("fragte");
            verbs.add("erwiderte");
        }
        verbs.sort(Comparator.comparingInt(String::length).reversed());
        return verbs;
    }

    /**
     * {@code *} = beliebig viele Buchstaben (auch keins), jedes Vorkommen einzeln.
     * Nicht {@code .*} — das würde den ganzen Text in einem Treffer verschlucken.
     */
    static String toPhraseRegex(String template) {
        if (template == null || template.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder("(?<!\\p{L})");
        boolean lastWasStar = false;
        int i = 0;
        while (i < template.length()) {
            char ch = template.charAt(i);
            if (ch == '*') {
                out.append("\\p{L}*");
                lastWasStar = true;
                i++;
            } else if (Character.isWhitespace(ch)) {
                while (i < template.length() && Character.isWhitespace(template.charAt(i))) {
                    i++;
                }
                boolean nextIsStar = i < template.length() && template.charAt(i) == '*';
                out.append(lastWasStar || nextIsStar ? "\\s*" : "\\s+");
                lastWasStar = false;
            } else {
                out.append(Pattern.quote(String.valueOf(ch)));
                lastWasStar = false;
                i++;
            }
        }
        out.append("(?!\\p{L})");
        return out.toString();
    }

    static int countWholeWord(String text, String word) {
        if (text == null || word == null || word.isBlank()) {
            return 0;
        }
        Matcher matcher = wholeWordPattern(word).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    static Pattern wholeWordPattern(String word) {
        return Pattern.compile(
                "(?<!\\p{L})" + Pattern.quote(word) + "(?!\\p{L})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE);
    }

    static int compareNatural(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int ia = i;
                int ib = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(ia, i).replaceFirst("^0+", "");
                String nb = b.substring(ib, j).replaceFirst("^0+", "");
                if (na.isEmpty()) {
                    na = "0";
                }
                if (nb.isEmpty()) {
                    nb = "0";
                }
                int length = Integer.compare(na.length(), nb.length());
                if (length != 0) {
                    return length;
                }
                int value = na.compareTo(nb);
                if (value != 0) {
                    return value;
                }
            } else {
                int cmp = Character.compare(
                        Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) {
                    return cmp;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    static Properties loadTextanalysis(Path configDir) {
        Properties props = new Properties();
        Path root = configDir == null ? Path.of(".") : configDir;
        Path[] candidates = {
                root.resolve("config").resolve("textanalysis.properties"),
                root.resolve("textanalysis.properties")
        };
        for (Path file : candidates) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
                return props;
            } catch (IOException ignored) {
                return props;
            }
        }
        return props;
    }

    private static Map<String, Integer> sortByCount(Map<String, Integer> source) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null ? second : "";
    }

    private static String stripMarkdown(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static String stripWidthSuffix(String path) {
        if (path == null) {
            return "";
        }
        int widthIndex = path.indexOf("{ width=");
        if (widthIndex >= 0) {
            return path.substring(0, widthIndex).trim();
        }
        return path.trim();
    }

    record SpeechCounts(Map<String, Integer> phrases, Map<String, Integer> verbs) {
    }
}
