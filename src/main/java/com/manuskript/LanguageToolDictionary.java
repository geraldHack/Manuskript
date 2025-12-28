package com.manuskript;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verwaltet das benutzerdefinierte Wörterbuch für LanguageTool
 * Speichert Eigennamen und andere Wörter, die nicht als Fehler markiert werden sollen
 */
public class LanguageToolDictionary {
    private static final Logger logger = LoggerFactory.getLogger(LanguageToolDictionary.class);
    
    private static final String DICTIONARY_FILE = "config/languagetool-dictionary.txt";
    private Set<String> words;
    private Path dictionaryPath;
    
    /**
     * Konstruktor
     */
    public LanguageToolDictionary() {
        this.words = new HashSet<>();
        this.dictionaryPath = Paths.get(DICTIONARY_FILE);
        loadDictionary();
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
                    if (!word.isEmpty() && !word.startsWith("#")) { // Kommentare ignorieren
                        words.add(word);
                    }
                }
                logger.info("LanguageTool Wörterbuch geladen: " + words.size() + " Wörter");
            } else {
                // Erstelle leere Datei
                Files.createDirectories(dictionaryPath.getParent());
                Files.createFile(dictionaryPath);
                logger.info("Neues LanguageTool Wörterbuch erstellt");
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
            
            // Sortiere Wörter alphabetisch
            List<String> sortedWords = words.stream()
                .sorted()
                .collect(Collectors.toList());
            
            for (String word : sortedWords) {
                lines.add(word);
            }
            
            Files.write(dictionaryPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("LanguageTool Wörterbuch gespeichert: " + words.size() + " Wörter");
        } catch (IOException e) {
            logger.error("Fehler beim Speichern des LanguageTool Wörterbuchs", e);
        }
    }
    
    /**
     * Fügt ein Wort zum Wörterbuch hinzu
     */
    public void addWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            words.add(word.trim().toLowerCase());
            saveDictionary();
        }
    }
    
    /**
     * Entfernt ein Wort aus dem Wörterbuch
     */
    public void removeWord(String word) {
        if (word != null) {
            words.remove(word.trim().toLowerCase());
            saveDictionary();
        }
    }
    
    /**
     * Prüft ob ein Wort im Wörterbuch ist
     */
    public boolean containsWord(String word) {
        if (word == null) return false;
        return words.contains(word.trim().toLowerCase());
    }
    
    /**
     * Prüft ob ein Wort (oder Teil davon) im Wörterbuch ist
     * Prüft auch Varianten (mit/ohne Großbuchstaben)
     */
    public boolean containsWordOrVariant(String word) {
        if (word == null || word.trim().isEmpty()) return false;
        
        String normalized = word.trim().toLowerCase();
        
        // Exakte Übereinstimmung
        if (words.contains(normalized)) {
            logger.debug("Wort gefunden (exakt): '" + word + "' -> '" + normalized + "'");
            return true;
        }
        
        // Prüfe auch Varianten (z.B. "Max" wenn "max" im Wörterbuch ist)
        // Entferne Satzzeichen am Anfang/Ende
        String cleaned = normalized.replaceAll("^[^a-zäöüß]+|[^a-zäöüß]+$", "");
        if (!cleaned.isEmpty() && words.contains(cleaned)) {
            logger.debug("Wort gefunden (bereinigt): '" + word + "' -> '" + cleaned + "'");
            return true;
        }
        
        logger.debug("Wort NICHT gefunden: '" + word + "' (normalized: '" + normalized + "', cleaned: '" + cleaned + "')");
        return false;
    }
    
    /**
     * Gibt alle Wörter zurück
     */
    public Set<String> getAllWords() {
        return new HashSet<>(words);
    }
    
    /**
     * Gibt die Anzahl der Wörter zurück
     */
    public int getWordCount() {
        return words.size();
    }
    
    /**
     * Filtert Matches, die Wörter aus dem Wörterbuch enthalten
     */
    public List<LanguageToolService.Match> filterMatches(List<LanguageToolService.Match> matches, String text) {
        if (matches == null || text == null) {
            return matches != null ? matches : new ArrayList<>();
        }
        
        List<LanguageToolService.Match> filtered = new ArrayList<>();
        
        for (LanguageToolService.Match match : matches) {
            int start = match.getOffset();
            int end = start + match.getLength();
            
            if (start >= 0 && end <= text.length()) {
                // WICHTIG: Hole den Text OHNE trim() zuerst, um die exakte Position zu prüfen
                String matchedTextRaw = text.substring(start, end);
                String matchedText = matchedTextRaw.trim();
                
                // Prüfe ob das gematchte Wort im Wörterbuch ist
                // Prüfe sowohl den rohen Text als auch den getrimmten Text
                // WICHTIG: Prüfe auch verschiedene Varianten (mit/ohne Großbuchstaben)
                boolean isInDictionary = containsWordOrVariant(matchedText) || 
                                         containsWordOrVariant(matchedTextRaw) ||
                                         containsWordOrVariant(matchedTextRaw.toLowerCase()) ||
                                         containsWordOrVariant(matchedText.toLowerCase());
                
                if (!isInDictionary) {
                    filtered.add(match);
                } else {
                    logger.info("Match gefiltert (im Wörterbuch): '" + matchedTextRaw + "' -> '" + matchedText + "'");
                }
            } else {
                // Ungültige Position - trotzdem hinzufügen
                filtered.add(match);
            }
        }
        
        logger.info("filterMatches: " + matches.size() + " Matches -> " + filtered.size() + " gefiltert");
        return filtered;
    }
}

