package com.manuskript;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verwaltet das benutzerdefinierte Wörterbuch für LanguageTool.
 * Speichert Eigennamen und andere Wörter, die nicht als Fehler markiert werden sollen.
 * Persistenz: schreibbares Nutzer-Config ({@link ApplicationPaths#resolveConfigPath}),
 * nicht im Installationsordner (sonst Verlust bei Version-Updates).
 */
public class LanguageToolDictionary {
    private static final Logger logger = LoggerFactory.getLogger(LanguageToolDictionary.class);

    private static final String RELATIVE_DICTIONARY = "config/languagetool-dictionary.txt";

    private Set<String> words;
    private Path dictionaryPath;

    public LanguageToolDictionary() {
        this.words = new HashSet<>();
        this.dictionaryPath = ApplicationPaths.resolveConfigPath(RELATIVE_DICTIONARY).toPath();
        migrateLegacyDictionaryIfNeeded();
        loadDictionary();
    }

    /** Sichtbar für Tests. */
    LanguageToolDictionary(Path dictionaryPath) {
        this.words = new HashSet<>();
        this.dictionaryPath = dictionaryPath;
        loadDictionary();
    }

    /**
     * Übernimmt Wörter aus älteren Speicherorten (CWD-relativ / App-Bundle),
     * wenn die stabile Nutzerdatei noch fehlt oder nur den Header enthält.
     */
    private void migrateLegacyDictionaryIfNeeded() {
        try {
            if (hasUserWords(dictionaryPath)) {
                return;
            }
            List<Path> candidates = new ArrayList<>();
            candidates.add(Path.of(RELATIVE_DICTIONARY));
            File appHome = ApplicationPaths.getApplicationHomeDirectory();
            if (appHome != null) {
                candidates.add(new File(appHome, RELATIVE_DICTIONARY).toPath());
            }
            File bundled = ApplicationPaths.resolveBundledPath(RELATIVE_DICTIONARY);
            if (bundled != null) {
                candidates.add(bundled.toPath());
            }
            for (Path legacy : candidates) {
                if (legacy == null || !Files.isRegularFile(legacy)) {
                    continue;
                }
                try {
                    if (legacy.toAbsolutePath().normalize().equals(dictionaryPath.toAbsolutePath().normalize())) {
                        continue;
                    }
                } catch (Exception ignored) {
                    // continue compare via content
                }
                if (!hasUserWords(legacy)) {
                    continue;
                }
                Path parent = dictionaryPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(legacy, dictionaryPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("LanguageTool Wörterbuch aus {} nach {} migriert", legacy, dictionaryPath);
                return;
            }
        } catch (IOException e) {
            logger.warn("LanguageTool Wörterbuch-Migration fehlgeschlagen: {}", e.toString());
        }
    }

    private static boolean hasUserWords(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                String word = line.trim();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * Lädt das Wörterbuch aus der Datei
     */
    public void loadDictionary() {
        words.clear();

        try {
            File dictFile = dictionaryPath.toFile();
            if (dictFile.exists() && dictFile.isFile()) {
                List<String> lines = Files.readAllLines(dictionaryPath);
                for (String line : lines) {
                    String word = line.trim().toLowerCase();
                    if (!word.isEmpty() && !word.startsWith("#")) {
                        words.add(word);
                    }
                }
                logger.info("LanguageTool Wörterbuch geladen: " + words.size() + " Wörter ({})", dictionaryPath);
            } else {
                Files.createDirectories(dictionaryPath.getParent());
                Files.createFile(dictionaryPath);
                logger.info("Neues LanguageTool Wörterbuch erstellt: {}", dictionaryPath);
            }
        } catch (IOException e) {
            logger.error("Fehler beim Laden des LanguageTool Wörterbuchs", e);
        }
    }

    /**
     * Speichert das Wörterbuch in die Datei
     */
    public void saveDictionary() {
        try {
            Files.createDirectories(dictionaryPath.getParent());

            List<String> lines = new ArrayList<>();
            lines.add("# LanguageTool Benutzer-Wörterbuch");
            lines.add("# Eigennamen und andere Wörter, die nicht als Fehler markiert werden sollen");
            lines.add("# Ein Wort pro Zeile");
            lines.add("");

            List<String> sortedWords = words.stream()
                .sorted()
                .collect(Collectors.toList());

            for (String word : sortedWords) {
                lines.add(word);
            }

            Files.write(dictionaryPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("LanguageTool Wörterbuch gespeichert: " + words.size() + " Wörter ({})", dictionaryPath);
        } catch (IOException e) {
            logger.error("Fehler beim Speichern des LanguageTool Wörterbuchs", e);
        }
    }

    public void addWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            words.add(word.trim().toLowerCase());
            saveDictionary();
        }
    }

    public void removeWord(String word) {
        if (word != null) {
            words.remove(word.trim().toLowerCase());
            saveDictionary();
        }
    }

    public boolean containsWord(String word) {
        if (word == null) return false;
        return words.contains(word.trim().toLowerCase());
    }

    public boolean containsWordOrVariant(String word) {
        if (word == null || word.trim().isEmpty()) return false;

        String normalized = word.trim().toLowerCase();

        if (words.contains(normalized)) {
            return true;
        }

        String cleaned = normalized.replaceAll("^[^a-zäöüß]+|[^a-zäöüß]+$", "");
        if (!cleaned.isEmpty() && words.contains(cleaned)) {
            return true;
        }

        return false;
    }

    public Set<String> getAllWords() {
        return new HashSet<>(words);
    }

    public int getWordCount() {
        return words.size();
    }

    Path getDictionaryPath() {
        return dictionaryPath;
    }

    public List<LanguageToolService.Match> filterMatches(List<LanguageToolService.Match> matches, String text) {
        if (matches == null || text == null) {
            return matches != null ? matches : new ArrayList<>();
        }

        List<LanguageToolService.Match> filtered = new ArrayList<>();

        for (LanguageToolService.Match match : matches) {
            int start = match.getOffset();
            int end = start + match.getLength();

            if (start >= 0 && end <= text.length()) {
                String matchedTextRaw = text.substring(start, end);
                String matchedText = matchedTextRaw.trim();

                boolean isInDictionary = containsWordOrVariant(matchedText) ||
                                         containsWordOrVariant(matchedTextRaw) ||
                                         containsWordOrVariant(matchedTextRaw.toLowerCase()) ||
                                         containsWordOrVariant(matchedText.toLowerCase());

                if (!isInDictionary) {
                    filtered.add(match);
                }
            } else {
                filtered.add(match);
            }
        }

        return filtered;
    }
}
