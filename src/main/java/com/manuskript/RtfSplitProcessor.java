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
     * Konvertiert RTF zu temporärer Markdown-Datei für saubere Verarbeitung
     */
    public List<Chapter> analyzeDocument(File rtfFile) throws IOException {
        
        // RTF zu temporärer Markdown-Datei konvertieren
        File tempMdFile = convertRtfToMarkdown(rtfFile);
        
        List<Chapter> chapters = new ArrayList<>();
        List<String> allLines = new ArrayList<>();
        
        // Markdown-Datei lesen
        try (BufferedReader reader = new BufferedReader(
                new FileReader(tempMdFile, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
        }
        
        
        // Kapitel finden
        for (int i = 0; i < allLines.size(); i++) {
            String line = allLines.get(i);
           
            if (!line.trim().isEmpty()) {
                Chapter chapter = detectChapter(line, i, allLines);
                if (chapter != null) {
                    chapters.add(chapter);
                }
            }
        }
        
        // Temporäre Datei NICHT löschen - für Debugging behalten
        // tempMdFile.delete();
        
        return chapters;
    }
    
    /**
     * Konvertiert RTF zu temporärer Markdown-Datei mit Formatierung
     */
    private File convertRtfToMarkdown(File rtfFile) throws IOException {
        
        // Temporäre Datei nach G:\ ablegen (sichtbar und NICHT löschen)
        File tempMdFile = new File("G:\\", "rtf_konvertiert.md");
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(rtfFile), StandardCharsets.UTF_8));
             FileWriter writer = new FileWriter(tempMdFile, StandardCharsets.UTF_8)) {
            
            
            String line;
            StringBuilder content = new StringBuilder();
            boolean inBold = false;
            boolean inItalic = false;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // RTF-Formatierung zu Markdown konvertieren
                String processedLine = convertRtfToMarkdownLine(line);
                content.append(processedLine).append("\n");
                
            }
            
            // Inhalt schreiben
            writer.write(content.toString());
            
            // DEBUG: Zeige ersten Teil des Inhalts
            String preview = content.length() > 500 ? content.substring(0, 500) + "..." : content.toString();
            
        }
        
        return tempMdFile;
    }
    
    /**
     * Konvertiert eine RTF-Zeile zu Markdown mit Formatierung
     */
    private String convertRtfToMarkdownLine(String rtfLine) {
        if (rtfLine == null || rtfLine.trim().isEmpty()) {
            return "";
        }
        
        String result = rtfLine;
        
        // RTF-Header entfernen
        result = result.replaceAll("\\{[^}]*\\\\rtf1[^}]*\\}", "");
        result = result.replaceAll("\\{[^}]*\\\\fonttbl[^}]*\\}", "");
        result = result.replaceAll("\\{[^}]*\\\\colortbl[^}]*\\}", "");
        
        // Formatierungs-Codes zu Markdown konvertieren
        result = result.replaceAll("\\\\b\\s*", "**"); // Bold
        result = result.replaceAll("\\\\i\\s*", "*");  // Italic
        result = result.replaceAll("\\\\u\\d+\\s*", ""); // Unicode escapes
        
        // Paragraphen
        result = result.replaceAll("\\\\par", "\n\n");
        result = result.replaceAll("\\\\line", "\n");
        
        // Geschweifte Klammern entfernen
        result = result.replaceAll("\\{[^}]*\\}", "");
        result = result.replaceAll("\\\\[{}]", "");
        
        // Unicode-Escape-Sequenzen dekodieren
        result = decodeUnicodeEscapes(result);
        
        // Mehrfache Leerzeichen bereinigen
        result = result.replaceAll("\\s+", " ");
        result = result.trim();
        
        return result;
    }
    
    /**
     * Verbesserte RTF-Bereinigung für saubere Markdown-Konvertierung
     */
    private String cleanRtfLine(String rtfLine) {
        if (rtfLine == null || rtfLine.trim().isEmpty()) {
            return "";
        }
        
        String clean = rtfLine;
        
        // 1. RTF-Header und Gruppierungen entfernen
        clean = clean.replaceAll("\\{[^}]*\\\\rtf1[^}]*\\}", ""); // RTF-Header
        clean = clean.replaceAll("\\{[^}]*\\\\fonttbl[^}]*\\}", ""); // Font-Tabelle
        clean = clean.replaceAll("\\{[^}]*\\\\colortbl[^}]*\\}", ""); // Farb-Tabelle
        
        // 2. Formatierungs-Codes entfernen
        clean = clean.replaceAll("\\\\[a-z]+\\d*", ""); // Alle RTF-Codes wie \b, \fs24, etc.
        clean = clean.replaceAll("\\\\[{}]", ""); // Geschweifte Klammern
        clean = clean.replaceAll("\\{[^}]*\\}", ""); // Geschweifte Klammern mit Inhalt
        
        // 3. Paragraphen und Zeilen-Trenner
        clean = clean.replaceAll("\\\\par", "\n"); // Paragraphen
        clean = clean.replaceAll("\\\\line", "\n"); // Zeilen
        clean = clean.replaceAll("\\\\tab", "\t"); // Tabs
        
        // 4. Unicode-Escape-Sequenzen dekodieren
        clean = decodeUnicodeEscapes(clean);
        
        // 5. Mehrfache Leerzeichen und Zeilenumbrüche bereinigen
        clean = clean.replaceAll("\\s+", " "); // Mehrfache Leerzeichen zu einem
        clean = clean.replaceAll("\\n\\s*\\n", "\n\n"); // Mehrfache Zeilenumbrüche zu doppelten
        clean = clean.trim();
        
        return clean;
    }
    
    /**
     * Dekodiert Unicode-Escape-Sequenzen in RTF-Text
     */
    private String decodeUnicodeEscapes(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        int len = input.length();
        
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 3 < len && input.charAt(i + 1) == '\'') {
                // Unicode-Escape: \'XX
                String hex = input.substring(i + 2, i + 4);
                if (isHexSequence(hex)) {
                    try {
                        result.append((char) Integer.parseInt(hex, 16));
                        i += 3; // Skip \'XX
                        continue;
                    } catch (NumberFormatException e) {
                        // Fallback: Original beibehalten
                    }
                }
            }
            result.append(c);
        }
        
        return result.toString();
    }
    
    /**
     * Prüft ob ein String eine gültige Hex-Sequenz ist
     */
    private boolean isHexSequence(String hex) {
        if (hex == null || hex.length() != 2) {
            return false;
        }
        for (int i = 0; i < hex.length(); i++) {
            char ch = hex.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                return false;
            }
        }
        return true;
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
        
        String trimmedText = text.trim();
        
        // Kapitel-Erkennung für zentrierte Texte ohne Punkte
        // 1. "Kapitel X" oder "Chapter X" am Anfang
        if (trimmedText.matches("^[Kk]apitel\\s+\\d+.*") || 
            trimmedText.matches("^[Cc]hapter\\s+\\d+.*")) {
            return true;
        }
        
        // 2. Einfache Nummerierung: "1. Titel" oder "1 - Titel"
        if (trimmedText.matches("^\\d{1,2}\\s*[.:\\-]\\s+[A-Z].*")) {
            return true;
        }
        
        // 3. Römische Zahlen: "I. Titel" oder "I - Titel"
        if (trimmedText.matches("^[IVX]+\\s*[.:\\-]\\s+[A-Z].*")) {
            return true;
        }
        
        // 4. NEU: Zentrierte Texte ohne Punkte (für Markdown nach RTF-Konvertierung)
        // Prüfe ob es ein sinnvoller Titel ist (keine Leerzeile, nicht zu kurz, nicht zu lang)
        if (trimmedText.length() >= 3 && trimmedText.length() <= 100 && 
            !trimmedText.endsWith(".") && 
            !trimmedText.matches("^\\s*$") &&
            !trimmedText.matches("^\\d+$") && // Nicht nur Zahlen
            !trimmedText.matches("^[\\s\\-_]+$")) { // Nicht nur Sonderzeichen
            
            // Zusätzliche Prüfung: Ist es wahrscheinlich ein Titel?
            // (enthält Buchstaben, nicht nur Sonderzeichen)
            if (trimmedText.matches(".*[a-zA-ZäöüÄÖÜß].*")) {
                return true;
            }
        }
        
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
        
    }
    
    /**
     * Splittet ein RTF-Dokument in Kapitel
     */
    public void splitDocument(File rtfFile, File outputDir) throws IOException {

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

    }
}
