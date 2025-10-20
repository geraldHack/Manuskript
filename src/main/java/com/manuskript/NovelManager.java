package com.manuskript;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verwaltet die TXT-Dateien für jeden Roman
 * - context.txt, characters.txt, synopsis.txt, outline.txt: einmal pro Verzeichnis (Roman)
 * - [Dateiname].chapter.txt: eine pro DOCX-Datei
 */
public class NovelManager {
    
    private static final Logger logger = LoggerFactory.getLogger(NovelManager.class);
    
    // Dateinamen für die TXT-Dateien (einmal pro Verzeichnis)
    public static final String CONTEXT_FILE = "context.txt";
    public static final String CHARACTERS_FILE = "characters.txt";
    public static final String SYNOPSIS_FILE = "synopsis.txt";
    public static final String OUTLINE_FILE = "outline.txt";
    public static final String WORLDBUILDING_FILE = "worldbuilding.txt";
    
    /**
     * Erstellt die TXT-Dateien für einen Roman im Verzeichnis
     * context.txt, characters.txt, synopsis.txt, outline.txt werden einmal pro Verzeichnis erstellt
     */
    public static void initializeNovelFolder(String docxFilePath) {
        try {
            // Verwende den vollständigen Pfad zur DOCX-Datei
            Path docxPath = Paths.get(docxFilePath);
            Path directory = docxPath.getParent();
            String novelName = docxPath.getFileName().toString().replaceAll("\\.docx$", "");
            
            // Erstelle TXT-Dateien im Verzeichnis falls nicht vorhanden (einmal pro Verzeichnis)
            createFileIfNotExists(directory, CONTEXT_FILE, "# Zusätzlicher Kontext für " + novelName + "\n\n");
            createFileIfNotExists(directory, CHARACTERS_FILE, "# Charaktere für " + novelName + "\n\n");
            createFileIfNotExists(directory, SYNOPSIS_FILE, "# Zusammenfassung für " + novelName + "\n\n");
            createFileIfNotExists(directory, OUTLINE_FILE, "# Gliederung für " + novelName + "\n\n");
            createFileIfNotExists(directory, WORLDBUILDING_FILE, "# Worldbuilding für " + novelName + "\n\n");
            
            // Erstelle chapter.txt spezifisch für diese DOCX-Datei
            String chapterFileName = novelName + ".chapter.txt";
            createFileIfNotExists(directory, chapterFileName, "# Kapitelbeschreibung und Szenen für " + novelName + "\n\n");
            
            
        } catch (IOException e) {
            logger.warn("Fehler beim Erstellen der Roman-Dateien", e);
        }
    }
    
    /**
     * Erstellt eine Datei falls sie nicht existiert
     */
    private static void createFileIfNotExists(Path directory, String fileName, String defaultContent) throws IOException {
        Path filePath = directory.resolve(fileName);
        if (!Files.exists(filePath)) {
            Files.write(filePath, defaultContent.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Lädt den Inhalt einer TXT-Datei aus dem Verzeichnis (für context, characters, synopsis, outline)
     */
    public static String loadNovelFile(String docxFilePath, String fileName) {
        try {
            Path docxPath = Paths.get(docxFilePath);
            Path directory = docxPath.getParent();
            Path filePath = directory.resolve(fileName);
            
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                return content;
            } else {
                logger.warn("Datei nicht gefunden: " + filePath);
                return "";
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Laden der Datei: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Speichert den Inhalt in eine TXT-Datei im Verzeichnis (für context, characters, synopsis, outline)
     */
    public static void saveNovelFile(String docxFilePath, String fileName, String content) {
        try {
            Path docxPath = Paths.get(docxFilePath);
            Path directory = docxPath.getParent();
            Path filePath = directory.resolve(fileName);
            
            Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warn("Fehler beim Speichern der Datei: " + e.getMessage());
        }
    }
    
    /**
     * Lädt die Kapitel-Datei für eine spezifische DOCX-Datei
     */
    public static String loadChapterFile(String docxFilePath) {
        try {
            Path docxPath = Paths.get(docxFilePath);
            Path directory = docxPath.getParent();
            String novelName = docxPath.getFileName().toString().replaceAll("\\.docx$", "");
            String chapterFileName = novelName + ".chapter.txt";
            Path filePath = directory.resolve(chapterFileName);
            
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                return content;
            } else {
                logger.warn("Kapitel-Datei nicht gefunden: " + filePath);
                return "";
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Laden der Kapitel-Datei: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Speichert die Kapitel-Datei für eine spezifische DOCX-Datei
     */
    public static void saveChapterFile(String docxFilePath, String content) {
        try {
            Path docxPath = Paths.get(docxFilePath);
            Path directory = docxPath.getParent();
            String novelName = docxPath.getFileName().toString().replaceAll("\\.docx$", "");
            String chapterFileName = novelName + ".chapter.txt";
            Path filePath = directory.resolve(chapterFileName);
            
            Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warn("Fehler beim Speichern der Kapitel-Datei: " + e.getMessage());
        }
    }
    
    /**
     * Lädt den zusätzlichen Kontext für einen Roman
     */
    public static String loadContext(String docxFilePath) {
        return loadNovelFile(docxFilePath, CONTEXT_FILE);
    }
    
    /**
     * Speichert den zusätzlichen Kontext für einen Roman
     */
    public static void saveContext(String docxFilePath, String context) {
        saveNovelFile(docxFilePath, CONTEXT_FILE, context);
    }
    
    /**
     * Lädt die Charaktere für einen Roman
     */
    public static String loadCharacters(String docxFilePath) {
        return loadNovelFile(docxFilePath, CHARACTERS_FILE);
    }
    
    /**
     * Speichert die Charaktere für einen Roman
     */
    public static void saveCharacters(String docxFilePath, String characters) {
        saveNovelFile(docxFilePath, CHARACTERS_FILE, characters);
    }
    
    /**
     * Lädt die Zusammenfassung für einen Roman
     */
    public static String loadSynopsis(String docxFilePath) {
        return loadNovelFile(docxFilePath, SYNOPSIS_FILE);
    }
    
    /**
     * Speichert die Zusammenfassung für einen Roman
     */
    public static void saveSynopsis(String docxFilePath, String synopsis) {
        saveNovelFile(docxFilePath, SYNOPSIS_FILE, synopsis);
    }
    
    /**
     * Lädt die Gliederung für einen Roman
     */
    public static String loadOutline(String docxFilePath) {
        return loadNovelFile(docxFilePath, OUTLINE_FILE);
    }
    
    /**
     * Speichert die Gliederung für einen Roman
     */
    public static void saveOutline(String docxFilePath, String outline) {
        saveNovelFile(docxFilePath, OUTLINE_FILE, outline);
    }

    /**
     * Lädt das Worldbuilding für einen Roman
     */
    public static String loadWorldbuilding(String docxFilePath) {
        return loadNovelFile(docxFilePath, WORLDBUILDING_FILE);
    }

    /**
     * Speichert das Worldbuilding für einen Roman
     */
    public static void saveWorldbuilding(String docxFilePath, String worldbuilding) {
        saveNovelFile(docxFilePath, WORLDBUILDING_FILE, worldbuilding);
    }
    
    /**
     * Lädt die Kapitelbeschreibung für eine spezifische DOCX-Datei
     */
    public static String loadChapter(String docxFilePath) {
        return loadChapterFile(docxFilePath);
    }
    
    /**
     * Speichert die Kapitelbeschreibung für eine spezifische DOCX-Datei
     */
    public static void saveChapter(String docxFilePath, String chapter) {
        saveChapterFile(docxFilePath, chapter);
    }
    
    /**
     * Prüft ob die Roman-Dateien im Verzeichnis existieren
     */
    public static boolean novelFilesExist(String docxFilePath) {
        try {
            Path docxPath = Paths.get(docxFilePath);
            Path directory = docxPath.getParent();
            
            return Files.exists(directory.resolve(CONTEXT_FILE)) &&
                   Files.exists(directory.resolve(CHARACTERS_FILE)) &&
                   Files.exists(directory.resolve(SYNOPSIS_FILE)) &&
                   Files.exists(directory.resolve(OUTLINE_FILE)) &&
                   Files.exists(directory.resolve(WORLDBUILDING_FILE));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Löscht die Roman-Dateien im Verzeichnis
     */
    public static void deleteNovelFiles(String docxFilePath) {
        try {
            Path docxPath = Paths.get(docxFilePath);
            Path directory = docxPath.getParent();
            String novelName = docxPath.getFileName().toString().replaceAll("\\.docx$", "");
            
            // Lösche die Verzeichnis-Dateien
            deleteFileIfExists(directory, CONTEXT_FILE);
            deleteFileIfExists(directory, CHARACTERS_FILE);
            deleteFileIfExists(directory, SYNOPSIS_FILE);
            deleteFileIfExists(directory, OUTLINE_FILE);
            
            // Lösche die Kapitel-Datei
            String chapterFileName = novelName + ".chapter.txt";
            deleteFileIfExists(directory, chapterFileName);
            
        } catch (IOException e) {
            logger.warn("Fehler beim Löschen der Roman-Dateien: " + e.getMessage());
        }
    }
    
    /**
     * Löscht eine Datei falls sie existiert
     */
    private static void deleteFileIfExists(Path directory, String fileName) throws IOException {
        Path filePath = directory.resolve(fileName);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
    }
} 