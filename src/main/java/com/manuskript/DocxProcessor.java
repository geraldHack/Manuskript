package com.manuskript;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocxProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(DocxProcessor.class);
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "^(Kapitel|Chapter|KAPITEL|CHAPTER)\\s*(\\d+|[IVX]+|[ivx]+)", 
        Pattern.CASE_INSENSITIVE
    );
    
    public enum OutputFormat {
        PLAIN_TEXT("Einfacher Text"),
        MARKDOWN("Markdown mit Export"),
        HTML("HTML");
        
        private final String displayName;
        
        OutputFormat(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    public String processDocxFile(File file, int chapterNumber, OutputFormat format) throws IOException {
        logger.info("Verarbeite Datei: {} im Format: {}", file.getName(), format);
        
        StringBuilder content = new StringBuilder();
        
        // HTML-Header für HTML-Format
        if (format == OutputFormat.HTML) {
            content.append("<!DOCTYPE html>\n");
            content.append("<html lang=\"de\">\n");
            content.append("<head>\n");
            content.append("    <meta charset=\"UTF-8\">\n");
            content.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            content.append("    <title>").append(file.getName().replaceAll("\\.docx?$", "")).append("</title>\n");
            content.append("    <style>\n");
            content.append("        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 40px; }\n");
            content.append("        h1 { color: #333; border-bottom: 2px solid #333; padding-bottom: 10px; margin-bottom: 30px; }\n");
            content.append("        p { margin: 0 0 15px 0; text-align: justify; }\n");
            content.append("        hr { border: none; border-top: 1px solid #ccc; margin: 20px 0; }\n");
            content.append("        b, strong { font-weight: bold; }\n");
            content.append("        i, em { font-style: italic; }\n");
            content.append("        u { text-decoration: underline; }\n");
            content.append("    </style>\n");
            content.append("</head>\n");
            content.append("<body>\n");
        }
        
        // Immer Dateiname als Titel für Markdown
        if (format == OutputFormat.MARKDOWN) {
            String fileName = file.getName();
            String chapterName = fileName;
            if (fileName.toLowerCase().endsWith(".docx")) {
                chapterName = fileName.substring(0, fileName.length() - 5);
            } else if (fileName.toLowerCase().endsWith(".doc")) {
                chapterName = fileName.substring(0, fileName.length() - 4);
            }
            content.append("# ").append(chapterName).append("\n\n");
        }
        
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            for (XWPFParagraph paragraph : paragraphs) {
                String text = extractFormattedText(paragraph, format);
                if (!text.trim().isEmpty()) {
                    if (format == OutputFormat.HTML) {
                        content.append("<p>").append(text).append("</p>\n");
                    } else {
                        content.append(text).append("\n\n");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Lesen der DOCX-Datei: {}", file.getName(), e);
            content.append("FEHLER: Konnte Datei nicht lesen - ").append(e.getMessage());
        }
        
        // HTML-Footer für HTML-Format
        if (format == OutputFormat.HTML) {
            content.append("</body>\n");
            content.append("</html>");
        }
        
        return content.toString();
    }
    
    // Rückwärtskompatibilität
    public String processDocxFile(File file, int chapterNumber) throws IOException {
        return processDocxFile(file, chapterNumber, OutputFormat.HTML);
    }
    
    // Neue Methode für Verarbeitung ohne Header/Footer (für mehrere Dateien)
    public String processDocxFileContent(File file, int chapterNumber, OutputFormat format) throws IOException {
        logger.info("Verarbeite Datei-Inhalt: {} im Format: {}", file.getName(), format);
        
        StringBuilder content = new StringBuilder();

        // Kapitelname bestimmen
        String fileName = file.getName();
        String chapterName = fileName;
        if (fileName.toLowerCase().endsWith(".docx")) {
            chapterName = fileName.substring(0, fileName.length() - 5);
        } else if (fileName.toLowerCase().endsWith(".doc")) {
            chapterName = fileName.substring(0, fileName.length() - 4);
        }
        
        // Zuerst den Inhalt lesen
        StringBuilder documentContent = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            for (XWPFParagraph paragraph : paragraphs) {
                String text = extractFormattedText(paragraph, format);
                if (!text.trim().isEmpty()) {
                    if (format == OutputFormat.HTML) {
                        documentContent.append("<p>").append(text).append("</p>\n");
                    } else {
                        documentContent.append(text).append("\n\n");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Lesen der DOCX-Datei: {}", file.getName(), e);
            content.append("FEHLER: Konnte Datei nicht lesen - ").append(e.getMessage());
            return content.toString();
        }
        
        String documentText = documentContent.toString();
        
        // Debug-Ausgabe für alle Kapitel
        logger.info("Kapitel-Nummer: {}, Format: {}", chapterNumber, format);
        
        // Für alle Kapitel: Prüfen, ob bereits ein Titel vorhanden ist
        boolean hasTitle = false;
        
        if (format == OutputFormat.MARKDOWN) {
            // Prüfe, ob der Text bereits mit # beginnt
            String trimmedText = documentText.trim();
            hasTitle = trimmedText.startsWith("# ");
            logger.info("Markdown-Titel-Prüfung: '{}' beginnt mit '# ' = {}", trimmedText.substring(0, Math.min(20, trimmedText.length())), hasTitle);
        } else if (format == OutputFormat.HTML) {
            // Prüfe, ob der Text bereits mit <h1> beginnt
            hasTitle = documentText.trim().startsWith("<h1>");
        } else {
            // Für Plain Text: Prüfe, ob der erste Absatz wie ein Titel aussieht
            String[] lines = documentText.split("\n");
            if (lines.length > 0) {
                String firstLine = lines[0].trim();
                // Einfache Heuristik: Kurze Zeile ohne Punkt am Ende könnte ein Titel sein
                hasTitle = firstLine.length() < 100 && !firstLine.endsWith(".") && !firstLine.endsWith("!") && !firstLine.endsWith("?");
            }
        }
        
        // Nur Titel hinzufügen, wenn noch keiner vorhanden ist
        if (!hasTitle) {
            logger.info("Füge Titel hinzu für Kapitel: {}", chapterName);
            switch (format) {
                case MARKDOWN:
                    content.append("# ").append(chapterName).append("\n\n");
                    break;
                case HTML:
                    content.append("<h1>").append(chapterName).append("</h1>\n");
                    break;
                case PLAIN_TEXT:
                default:
                    content.append(chapterName).append("\n\n");
                    break;
            }
        } else {
            logger.info("Titel bereits vorhanden, füge keinen hinzu");
        }
        
        // Den Dokumentinhalt hinzufügen
        content.append(documentText);
        
        return content.toString();
    }
    
    private String formatChapterHeader(String chapterName, OutputFormat format) {
        switch (format) {
            case MARKDOWN:
                return "# " + chapterName;
            case PLAIN_TEXT:
                return chapterName;
            case HTML:
            default:
                return "<h1>" + chapterName + "</h1>";
        }
    }
    
    private String extractFormattedText(XWPFParagraph paragraph, OutputFormat format) {
        StringBuilder result = new StringBuilder();
        
        // Durchlaufe alle Runs im Absatz
        for (var run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text == null) continue;
            
            boolean isBold = run.isBold();
            boolean isItalic = run.isItalic();
            boolean isUnderline = run.getUnderline() != null && run.getUnderline() != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE;
            
                            switch (format) {
                    case MARKDOWN:
                        // Markdown-Formatierung mit visueller Hervorhebung
                        if (isBold && isItalic) {
                            result.append("***").append(text).append("***");
                        } else if (isBold) {
                            result.append("**").append(text).append("**");
                        } else if (isItalic) {
                            result.append("*").append(text).append("*");
                        } else if (isUnderline) {
                            result.append("__").append(text).append("__"); // Markdown underline
                        } else {
                            result.append(text);
                        }
                        break;
                    
                case PLAIN_TEXT:
                    // Nur reiner Text, keine Formatierung
                    result.append(text);
                    break;
                    
                case HTML:
                default:
                    // HTML-ähnliche Formatierung
                    if (isBold && isItalic) {
                        result.append("<b><i>").append(text).append("</i></b>");
                    } else if (isBold) {
                        result.append("<b>").append(text).append("</b>");
                    } else if (isItalic) {
                        result.append("<i>").append(text).append("</i>");
                    } else if (isUnderline) {
                        result.append("<u>").append(text).append("</u>");
                    } else {
                        result.append(text);
                    }
                    break;
            }
        }
        
        String paragraphText = result.toString().trim();
        
        // Prüfe auf Trennzeichen-Patterns
        if (isSeparatorPattern(paragraphText)) {
            switch (format) {
                case MARKDOWN:
                    return "***";
                case PLAIN_TEXT:
                    return "---";
                case HTML:
                default:
                    return "<hr>";
            }
        }
        
        return paragraphText;
    }
    
    private boolean isSeparatorPattern(String text) {
        // Prüfe auf verschiedene Trennzeichen-Patterns
        String trimmed = text.trim();
        
        // Nur sehr spezifische Trennzeichen-Patterns
        // Einzelne Zeichen als Trennzeichen (nur wenn sie alleine auf der Zeile stehen)
        if (trimmed.equals("*") || trimmed.equals("-") || trimmed.equals("_")) {
            return true;
        }
        
        // Mehrfache Zeichen als Trennzeichen (mindestens 3, nur diese Zeichen)
        if (trimmed.matches("^[*\\-_]{3,}$")) {
            return true;
        }
        
        // Keine anderen Texte als Trennzeichen behandeln
        return false;
    }
    
    private String formatParagraph(String text, OutputFormat format) {
        switch (format) {
            case MARKDOWN:
                // Nur normale Textformatierung, keine automatischen Überschriften
                return text;
            case PLAIN_TEXT:
                return text;
            case HTML:
            default:
                return text;
        }
    }
    
    private boolean isLikelyHeading(String text) {
        // Prüfe auf echte Überschriften-Muster
        String trimmedText = text.trim();
        
        // Sehr kurze Texte (1-3 Wörter) mit Großbuchstaben am Anfang
        if (trimmedText.length() < 50 && trimmedText.split("\\s+").length <= 3 && 
            Character.isUpperCase(trimmedText.charAt(0))) {
            return true;
        }
        
        // Prüfe auf typische Überschriften-Muster
        if (trimmedText.matches("^[A-Z][^.!?]*$")) { // Keine Satzzeichen am Ende
            // Aber nur wenn es sehr kurz ist
            if (trimmedText.length() < 40 && trimmedText.split("\\s+").length <= 4) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isLikelyEmphasis(String text) {
        // Prüfe auf echte Hervorhebungen
        String trimmedText = text.trim();
        
        // Sehr kurze, prägnante Aussagen (1-2 Wörter)
        if (trimmedText.length() < 30 && trimmedText.split("\\s+").length <= 2) {
            return true;
        }
        
        // Prüfe auf typische Hervorhebungs-Muster
        if (trimmedText.matches("^[A-Z][^.!?]*[.!?]$")) { // Satz mit Satzzeichen am Ende
            // Aber nur sehr kurze, prägnante Sätze
            if (trimmedText.length() < 25 && trimmedText.split("\\s+").length <= 3) {
                return true;
            }
        }
        
        return false;
    }
    
    private String detectChapterHeader(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            // Suche nach Kapitel-Header in den ersten 10 Absätzen
            for (int i = 0; i < Math.min(10, paragraphs.size()); i++) {
                XWPFParagraph paragraph = paragraphs.get(i);
                String text = paragraph.getText().trim();
                
                if (!text.isEmpty()) {
                    Matcher matcher = CHAPTER_PATTERN.matcher(text);
                    if (matcher.find()) {
                        return text;
                    }
                    
                    // Prüfe auch auf andere Header-Formate
                    if (isLikelyChapterHeader(text)) {
                        return text;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("Konnte Kapitel-Header nicht erkennen für: {}", file.getName(), e);
        }
        
        return null;
    }
    
    private boolean isLikelyChapterHeader(String text) {
        // Prüfe auf verschiedene Header-Formate
        String lowerText = text.toLowerCase();
        
        // Nummerierte Überschriften
        if (Pattern.matches("^\\d+\\..*", text)) {
            return true;
        }
        
        // Römische Zahlen
        if (Pattern.matches("^[IVX]+\\..*", text)) {
            return true;
        }
        
        // Kurze Texte (wahrscheinlich Überschriften)
        if (text.length() < 100 && text.split("\\s+").length < 10) {
            // Prüfe auf Großbuchstaben am Anfang
            if (Character.isUpperCase(text.charAt(0))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Konvertiert Markdown-Text zu einer DOCX-Datei
     */
    public void exportMarkdownToDocx(String markdownText, File outputFile) throws IOException {
        logger.info("Exportiere Markdown zu DOCX: {}", outputFile.getName());
        
        // WICHTIG: Prüfe ob der Markdown-Text leer ist!
        if (markdownText == null || markdownText.trim().isEmpty()) {
            logger.error("Versuch, leeren Markdown-Text zu DOCX zu exportieren!");
            throw new IllegalArgumentException("Markdown-Text ist leer - kann nicht exportiert werden!");
        }

        // Schreibe zuerst in eine temporäre Datei im selben Verzeichnis und ersetze danach atomar
        File parentDir = outputFile.getAbsoluteFile().getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                logger.warn("Konnte Ausgabeverzeichnis nicht erstellen: {}", parentDir.getAbsolutePath());
            }
        }
        File tempFile = File.createTempFile("manuskript-", ".docx", parentDir);
        
        // Dokument erzeugen und in tempFile schreiben
        try (XWPFDocument document = new XWPFDocument()) {
            String[] lines = markdownText.split("\n");
            boolean hasContent = false;
            
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    document.createParagraph();
                    continue;
                }
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(trimmed);
                hasContent = true;
            }
            
            if (!hasContent) {
                logger.error("DOCX-Dokument hat keinen Inhalt - wird nicht gespeichert!");
                throw new IllegalArgumentException("DOCX-Dokument ist leer - kann nicht gespeichert werden!");
            }

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                document.write(fos);
                fos.flush();
            }
        }

        // Validierung: Versuche die temp-Datei als DOCX zu öffnen, bevor wir ersetzen
        try (java.io.FileInputStream validateFis = new java.io.FileInputStream(tempFile);
             XWPFDocument ignored = new XWPFDocument(validateFis)) {
            // ok
        } catch (Exception e) {
            logger.error("Validierung fehlgeschlagen – erzeugte DOCX ist unlesbar: {}", e.getMessage());
            // Temp-Datei aufräumen und Fehler werfen
            try { tempFile.delete(); } catch (Exception ignored) {}
            throw new IOException("Erzeugte DOCX konnte nicht validiert werden: " + e.getMessage(), e);
        }

        // Ersetzen: atomar wenn möglich, sonst normal
        try {
            java.nio.file.Files.move(
                    tempFile.toPath(),
                    outputFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception atomicEx) {
            logger.warn("ATOMIC_MOVE nicht möglich – verwende normales Ersetzen: {}", atomicEx.getMessage());
            java.nio.file.Files.move(
                    tempFile.toPath(),
                    outputFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        }

        logger.info("DOCX-Export abgeschlossen: {}", outputFile.getAbsolutePath());
    }
    
    // Keine komplexe Markdown-Formatierung mehr - nur einfacher Text!
} 