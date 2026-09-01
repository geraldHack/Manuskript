package com.manuskript.stats;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

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

    private static final Gson GSON = new Gson();
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
        List<Path> docxFiles = selectedDocx(bookRoot);
        List<BookStats.Chapter> chapters = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        List<BookStats.ImageHit> images = new ArrayList<>();
        Path dataDir = bookRoot.resolve("data");
        for (Path docx : docxFiles) {
            String base = stripDocx(docx.getFileName().toString());
            Path md = dataDir.resolve(base + ".md");
            if (!Files.isRegularFile(md)) {
                continue;
            }
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

    static List<Path> selectedDocx(Path bookRoot) throws IOException {
        Path selection = bookRoot.resolve("data").resolve(".manuskript_selection.json");
        List<String> names = new ArrayList<>();
        if (Files.isRegularFile(selection)) {
            try (Reader reader = Files.newBufferedReader(selection, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonArray()) {
                    JsonArray array = root.getAsJsonArray();
                    for (JsonElement element : array) {
                        if (element.isJsonPrimitive()) {
                            names.add(element.getAsString());
                        }
                    }
                } else {
                    List<String> parsed = GSON.fromJson(root, new TypeToken<List<String>>() {
                    }.getType());
                    if (parsed != null) {
                        names.addAll(parsed);
                    }
                }
            } catch (Exception ignored) {
                names.clear();
            }
        }
        List<Path> files = new ArrayList<>();
        if (!names.isEmpty()) {
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                Path file = bookRoot.resolve(name);
                if (Files.isRegularFile(file) && name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                    files.add(file);
                }
            }
            if (!files.isEmpty()) {
                return files;
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(bookRoot, "*.docx")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && !path.getFileName().toString().startsWith(".")) {
                    files.add(path);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return files;
    }

    static SpeechCounts countSpeech(String text, Properties props) {
        Map<String, Integer> phrases = new LinkedHashMap<>();
        Map<String, Integer> verbs = new LinkedHashMap<>();
        String regex = props.getProperty("sprechantworten_regex", "");
        String sprechwoerter = firstNonBlank(
                props.getProperty("sprechwörter", ""),
                props.getProperty("sprechwoerter", ""));
        Pattern pattern = compileSprechantwortenPattern(regex, sprechwoerter);
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String phrase = matcher.group().trim();
            phrases.merge(phrase, 1, Integer::sum);
            String verb = firstWord(phrase);
            if (!verb.isEmpty()) {
                verbs.merge(verb.toLowerCase(Locale.GERMAN), 1, Integer::sum);
            }
        }
        return new SpeechCounts(sortByCount(phrases), sortByCount(verbs));
    }

    static Map<String, Integer> countPhrases(String text, Properties props) {
        Map<String, Integer> phraseCount = new LinkedHashMap<>();
        String corpus = text == null ? "" : text;
        for (String category : PHRASE_KEYS) {
            for (String phrase : props.getProperty(category, "").split(",")) {
                String trimmed = phrase.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Pattern pattern;
                try {
                    pattern = Pattern.compile(trimmed.replace("*", ".*"), Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    continue;
                }
                Matcher matcher = pattern.matcher(corpus);
                int count = 0;
                while (matcher.find()) {
                    count++;
                }
                if (count > 0) {
                    phraseCount.merge(trimmed, count, Integer::sum);
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
        if (sprechwoerter != null) {
            for (String raw : sprechwoerter.split(",")) {
                String word = raw.trim();
                if (!word.isEmpty()) {
                    quoted.add(Pattern.quote(word));
                }
            }
        }
        if (quoted.isEmpty()) {
            quoted.add(Pattern.quote("sagte"));
            quoted.add(Pattern.quote("fragte"));
            quoted.add(Pattern.quote("erwiderte"));
        }
        quoted.sort(Comparator.comparingInt(String::length).reversed());
        return "(?:" + String.join("|", quoted) + ")\\s+\\p{L}+[.!?:,]";
    }

    static Properties loadTextanalysis(Path configDir) {
        Properties props = new Properties();
        Path root = configDir == null ? Path.of(".") : configDir;
        Path file = root.resolve("config").resolve("textanalysis.properties");
        if (!Files.isRegularFile(file)) {
            return props;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException ignored) {
            return props;
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

    private static String firstWord(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return "";
        }
        String[] parts = phrase.trim().split("\\s+");
        return parts.length == 0 ? "" : parts[0];
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null ? second : "";
    }

    private static String stripDocx(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return name.substring(0, name.length() - 5);
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
