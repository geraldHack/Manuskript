package com.manuskript;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
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
        MARKDOWN("Markdown"),
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
        
        String detectedChapter = detectChapterHeader(file);
        
        if (detectedChapter != null) {
            content.append(formatChapterHeader(detectedChapter, format)).append("\n\n");
        } else {
            // Verwende den Dateinamen (ohne .docx) als Kapitelnamen
            String fileName = file.getName();
            String chapterName = fileName;
            
            // Entferne .docx Erweiterung
            if (fileName.toLowerCase().endsWith(".docx")) {
                chapterName = fileName.substring(0, fileName.length() - 5);
            }
            
            // Entferne weitere häufige Erweiterungen
            if (chapterName.toLowerCase().endsWith(".doc")) {
                chapterName = chapterName.substring(0, chapterName.length() - 4);
            }
            
            content.append(formatChapterHeader(chapterName, format)).append("\n");
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
        
        String detectedChapter = detectChapterHeader(file);
        
        if (detectedChapter != null) {
            content.append(formatChapterHeader(detectedChapter, format)).append("\n\n");
        } else {
            // Verwende den Dateinamen (ohne .docx) als Kapitelnamen
            String fileName = file.getName();
            String chapterName = fileName;
            
            // Entferne .docx Erweiterung
            if (fileName.toLowerCase().endsWith(".docx")) {
                chapterName = fileName.substring(0, fileName.length() - 5);
            }
            
            // Entferne weitere häufige Erweiterungen
            if (chapterName.toLowerCase().endsWith(".doc")) {
                chapterName = chapterName.substring(0, chapterName.length() - 4);
            }
            
            content.append(formatChapterHeader(chapterName, format)).append("\n");
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
} 