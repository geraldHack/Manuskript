package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verarbeitet RTF-Dateien für das Kapitel-Split Feature
 */
public class RtfSplitProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RtfSplitProcessor.class);
    
    /**
     * Repräsentiert ein gefundenes Kapitel
     */
    public static class Chapter {
        private final int number;
        private final String title;
        private final int startLine;
        private final int endLine;
        private final List<String> lines;
        
        public Chapter(int number, String title, int startLine, int endLine, List<String> lines) {
            this.number = number;
            this.title = title;
            this.startLine = startLine;
            this.endLine = endLine;
            this.lines = new ArrayList<>(lines);
        }
        
        public int getNumber() { return number; }
        public String getTitle() { return title; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public List<String> getLines() { return lines; }
        
        @Override
        public String toString() {
            return String.format("Kapitel %d: %s", number, title);
        }
    }
    
    /**
     * Analysiert eine RTF-Datei und findet alle Kapitel
     */
    public List<Chapter> analyzeDocument(File rtfFile) throws IOException {
        logger.info("Analysiere RTF-Datei: {}", rtfFile.getAbsolutePath());
        
        List<Chapter> chapters = new ArrayList<>();
        List<String> allLines = new ArrayList<>();
        
        // RTF-Datei lesen
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(rtfFile), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
        }
        
        logger.info("Gefundene Zeilen: {}", allLines.size());
        
        // Kapitel finden
        for (int i = 0; i < allLines.size(); i++) {
            String line = allLines.get(i);
            String cleanLine = cleanRtfLine(line);
            
            // Debug: Zeige alle nicht-leeren Zeilen
            if (!cleanLine.trim().isEmpty() && cleanLine.length() < 100) {
                logger.debug("Zeile {}: '{}'", i, cleanLine);
            }
            
            if (!cleanLine.trim().isEmpty()) {
                Chapter chapter = detectChapter(cleanLine, i, allLines);
                if (chapter != null) {
                    chapters.add(chapter);
                    logger.info("Kapitel gefunden: {}", chapter);
                }
            }
        }
        
        logger.info("Insgesamt {} Kapitel gefunden", chapters.size());
        return chapters;
    }
    
    /**
     * Vereinfachte RTF-Bereinigung - nur das Nötigste
     */
    private String cleanRtfLine(String rtfLine) {
        if (rtfLine == null || rtfLine.trim().isEmpty()) {
            return "";
        }
        
        // Sehr einfache Bereinigung - nur RTF-Header entfernen
        String clean = rtfLine
            .replaceAll("\\\\[a-z]+\\d*", "") // RTF-Codes
            .replaceAll("\\\\[{}]", "") // Geschweifte Klammern
            .replaceAll("\\{[^}]*\\}", "") // Geschweifte Klammern mit Inhalt
            .replaceAll("\\\\par", "\n") // Paragraphen
            .replaceAll("\\\\line", "\n") // Zeilen
            .trim();
        
        return clean;
    }
    
    /**
     * Erkennt ob eine Zeile ein Kapitel-Start ist
     */
    private Chapter detectChapter(String text, int lineIndex, List<String> allLines) {
        // Kapitel-Kandidaten basierend auf verschiedenen Kriterien
        if (isLikelyChapter(text)) {
            // Versuche Kapitelnummer zu extrahieren
            int chapterNumber = extractChapterNumber(text, lineIndex);
            String chapterTitle = extractChapterTitle(text);
            
            // Kapitel-Inhalt bestimmen (bis zum nächsten Kapitel oder Ende)
            int endLine = findChapterEnd(lineIndex, allLines);
            List<String> chapterLines = allLines.subList(lineIndex, endLine);
            
            return new Chapter(chapterNumber, chapterTitle, lineIndex, endLine, chapterLines);
        }
        return null;
    }
    
    /**
     * Prüft ob eine Zeile wahrscheinlich ein Kapitel ist
     */
    private boolean isLikelyChapter(String text) {
        if (text.trim().isEmpty()) {
            return false;
        }
        
        String lowerText = text.toLowerCase().trim();
        
        // Nur sehr spezifische Kapitel-Muster akzeptieren
        // 1. "Kapitel X" oder "Chapter X" am Anfang
        if (lowerText.matches("^kapitel\\s+\\d+.*") || 
            lowerText.matches("^chapter\\s+\\d+.*")) {
            logger.debug("Gefunden durch Schlüsselwort: {}", text);
            return true;
        }
        
        // 2. Einfache Nummerierung: "1. Titel" oder "1 - Titel"
        if (lowerText.matches("^\\d{1,2}\\s*[.:\\-]\\s+[A-Z].*")) {
            logger.debug("Gefunden durch Nummerierung: {}", text);
            return true;
        }
        
        // 3. Römische Zahlen: "I. Titel" oder "I - Titel"
        if (lowerText.matches("^[IVX]+\\s*[.:\\-]\\s+[A-Z].*")) {
            logger.debug("Gefunden durch römische Zahlen: {}", text);
            return true;
        }
        
        logger.debug("NICHT als Kapitel erkannt: '{}'", text);
        return false;
    }
    
    /**
     * Extrahiert die Kapitelnummer aus dem Text
     */
    private int extractChapterNumber(String text, int lineIndex) {
        // Versuche verschiedene Formate zu erkennen
        String[] patterns = {
            "(\\d+)", // Einfache Zahl
            "kapitel\\s+(\\d+)", // Kapitel X
            "chapter\\s+(\\d+)", // Chapter X
            "teil\\s+(\\d+)", // Teil X
            "part\\s+(\\d+)" // Part X
        };
        
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    // Ignoriere und versuche nächsten Pattern
                }
            }
        }
        
        // Fallback: Verwende Zeilen-Index als Nummer
        return lineIndex + 1;
    }
    
    /**
     * Extrahiert den Kapiteltitel aus dem Text
     */
    private String extractChapterTitle(String text) {
        // Entferne Kapitel-Schlüsselwörter und Zahlen am Anfang
        String title = text.trim();
        
        // Entferne "Kapitel X:", "Chapter X:", etc.
        title = title.replaceAll("(?i)^(kapitel|chapter|teil|part)\\s+\\d+\\s*[:\\-]?\\s*", "");
        
        // Entferne Zahlen am Anfang mit Punkt/Doppelpunkt
        title = title.replaceAll("^\\d+\\s*[.:\\-]?\\s*", "");
        
        // Entferne führende/trailing Whitespace
        title = title.trim();
        
        if (title.isEmpty()) {
            // Fallback: Verwende den ursprünglichen Text
            title = text.trim();
        }
        
        return title;
    }
    
    /**
     * Findet das Ende eines Kapitels (nächster Kapitel-Start oder Ende der Datei)
     */
    private int findChapterEnd(int startIndex, List<String> allLines) {
        for (int i = startIndex + 1; i < allLines.size(); i++) {
            String line = allLines.get(i);
            String cleanLine = cleanRtfLine(line);
            if (!cleanLine.trim().isEmpty()) {
                // Prüfe ob es ein neues Kapitel ist
                if (isLikelyChapter(cleanLine)) {
                    return i;
                }
            }
        }
        return allLines.size(); // Ende der Datei
    }
    
    /**
     * Speichert ein Kapitel als separate RTF-Datei
     */
    public void saveChapter(Chapter chapter, File outputDir, String baseFileName) throws IOException {
        String fileName = String.format("%s_Kapitel_%02d.rtf", baseFileName, chapter.getNumber());
        File outputFile = new File(outputDir, fileName);
        
        logger.info("Speichere Kapitel {} als: {}", chapter.getNumber(), outputFile.getAbsolutePath());
        
        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            // RTF-Header
            writer.write("{\\rtf1\\ansi\\ansicpg1252\\deff0 {\\fonttbl {\\f0 Times New Roman;}}\n");
            writer.write("\\f0\\fs24\n");
            
            // Kapitel-Titel
            writer.write("\\b\\fs32 ");
            writer.write(chapter.getTitle());
            writer.write("\\b0\\fs24\\par\n");
            writer.write("\\par\n");
            
            // Kapitel-Inhalt
            for (String line : chapter.getLines()) {
                String cleanLine = cleanRtfLine(line);
                if (!cleanLine.trim().isEmpty()) {
                    writer.write(cleanLine);
                    writer.write("\\par\n");
                }
            }
            
            // RTF-Footer
            writer.write("}");
        }
        
        logger.info("Kapitel {} erfolgreich gespeichert", chapter.getNumber());
    }
    
    /**
     * Splittet ein RTF-Dokument in Kapitel
     */
    public void splitDocument(File rtfFile, File outputDir) throws IOException {
        logger.info("Starte RTF-Split: {} -> {}", rtfFile.getAbsolutePath(), outputDir.getAbsolutePath());

        // Kapitel analysieren
        List<Chapter> chapters = analyzeDocument(rtfFile);

        if (chapters.isEmpty()) {
            logger.warn("Keine Kapitel in der Datei gefunden!");
            throw new IOException("Keine Kapitel in der Datei gefunden");
        }

        // Ausgabe-Verzeichnis erstellen falls nicht vorhanden
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Basis-Dateiname (ohne .rtf)
        String baseFileName = rtfFile.getName().replaceFirst("\\.rtf$", "");

        // Alle Kapitel speichern
        for (Chapter chapter : chapters) {
            saveChapter(chapter, outputDir, baseFileName);
        }

        logger.info("RTF-Split erfolgreich abgeschlossen: {} Kapitel", chapters.size());
    }
}
