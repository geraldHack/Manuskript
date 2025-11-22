package com.manuskript;

import org.apache.poi.xwpf.usermodel.*;
import org.docx4j.Docx4J;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Body;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.xml.bind.JAXBElement;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verarbeitet DOCX-Dateien für das Kapitel-Split Feature
 */
public class DocxSplitProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DocxSplitProcessor.class);
    
    // Kapitel-Erkennung basierend auf Formatierung und Inhalt
    // Keine starren Regex-Patterns mehr - flexiblere Erkennung

    
    /**
     * Repräsentiert ein gefundenes Kapitel
     */
    public static class Chapter {
        private static final int PREVIEW_MAX_LENGTH = 300;
        private final int number;
        private final String title;
        private final int startParagraph;
        private final int endParagraph;
        private final List<XWPFParagraph> paragraphs;
        private final String fullText;
        private final int wordCount;
        private final int characterCount;
        private final String previewText;
        
        public Chapter(int number, String title, int startParagraph, int endParagraph, List<XWPFParagraph> paragraphs) {
            this.number = number;
            this.title = title;
            this.startParagraph = startParagraph;
            this.endParagraph = endParagraph;
            this.paragraphs = new ArrayList<>(paragraphs);
            this.fullText = buildFullText(this.paragraphs);
            this.characterCount = fullText.length();
            this.wordCount = calculateWordCount(fullText);
            this.previewText = buildPreview(fullText);
        }
        
        public int getNumber() { return number; }
        public String getTitle() { return title; }
        public int getStartParagraph() { return startParagraph; }
        public int getEndParagraph() { return endParagraph; }
        public List<XWPFParagraph> getParagraphs() { return paragraphs; }
        public String getFullText() { return fullText; }
        public int getWordCount() { return wordCount; }
        public int getCharacterCount() { return characterCount; }
        public String getPreviewText() { return previewText; }
        
        @Override
        public String toString() {
            return title;
        }

        private static String buildFullText(List<XWPFParagraph> paragraphs) {
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph != null ? paragraph.getText() : null;
                if (text != null) {
                    builder.append(text);
                }
                builder.append("\n");
            }
            return builder.toString().trim();
        }

        private static int calculateWordCount(String text) {
            if (text == null || text.trim().isEmpty()) {
                return 0;
            }
            String[] words = text.trim().split("\\s+");
            int count = 0;
            for (String word : words) {
                if (!word.isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        private static String buildPreview(String text) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            String normalized = text.replaceAll("\n+", " ").trim();
            if (normalized.length() <= PREVIEW_MAX_LENGTH) {
                return normalized;
            }
            return normalized.substring(0, PREVIEW_MAX_LENGTH).trim() + "…";
        }
    }
    
    private File sourceDocxFile;

    public void setSourceDocxFile(File docxFile) {
        this.sourceDocxFile = docxFile;
    }

    public String generateDefaultFileName(Chapter chapter) {
        return ensureDocxExtension(buildDefaultNameBody(chapter));
    }

    public String normalizeFileName(String desiredFileName, Chapter chapter) {
        String sanitized = sanitizeFileName(desiredFileName);
        if (sanitized == null || sanitized.isEmpty()) {
            return generateDefaultFileName(chapter);
        }
        return ensureDocxExtension(sanitized);
    }

    /**
     * Analysiert eine DOCX-Datei und findet alle Kapitel
     */
    public List<Chapter> analyzeDocument(File docxFile) throws IOException {
        
        this.sourceDocxFile = docxFile;

        List<Chapter> chapters = new ArrayList<>();
        List<XWPFParagraph> allParagraphs = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(docxFile);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            this.sourceDocxFile = docxFile;

            // Alle Absätze sammeln
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                allParagraphs.add(paragraph);
            }
            
            
            // Kapitel finden
            for (int i = 0; i < allParagraphs.size(); i++) {
                XWPFParagraph paragraph = allParagraphs.get(i);
                String text = paragraph.getText().trim();
                
                if (!text.isEmpty()) {
                    Chapter chapter = detectChapter(text, i, allParagraphs, chapters.size() + 1);
                    if (chapter != null) {
                        chapters.add(chapter);
                    }
                }
            }
        }
        
        return chapters;
    }
    
    /**
     * Erkennt ob ein Absatz ein Kapitel-Start ist (flexible Erkennung)
     */
    private Chapter detectChapter(String text, int paragraphIndex, List<XWPFParagraph> allParagraphs, int chapterNumber) {
        XWPFParagraph paragraph = allParagraphs.get(paragraphIndex);
        
        // Kapitel-Kandidaten basierend auf verschiedenen Kriterien
        if (isLikelyChapter(paragraph, text)) {
            // Versuche Kapitelnummer aus dem Text zu extrahieren
            int extractedNumber = extractChapterNumber(text, paragraph);
            logger.info("Kapitel erkannt: Text='{}', extrahierte Nummer={}, Fallback-Nummer={}", 
                        text, extractedNumber, chapterNumber);
            
            // Wenn eine Nummer extrahiert wurde (auch wenn 0!), verwende diese, sonst Fallback auf sequenzielle Nummer
            // WICHTIG: Wenn extractChapterNumber 0 zurückgibt, bedeutet das "nicht gefunden", nicht "Kapitel 0"
            int finalChapterNumber;
            if (extractedNumber > 0) {
                finalChapterNumber = extractedNumber;
                logger.info("✓ Kapitelnummer aus Text extrahiert: '{}' -> Nummer {} (wird für Dateinamen verwendet)", 
                           text, extractedNumber);
            } else {
                finalChapterNumber = chapterNumber;
                logger.warn("⚠ Keine Kapitelnummer in '{}' gefunden, verwende Fallback-Nummer {} (sequenziell)", 
                           text, chapterNumber);
            }
            
            // Extrahiere den vollständigen Titel (mit Nummer, z.B. "1. Kapitel")
            String chapterTitle = extractOriginalTitle(text);
            
            // Kapitel-Inhalt bestimmen (bis zum nächsten Kapitel oder Ende)
            int endParagraph = findChapterEnd(paragraphIndex, allParagraphs);
            List<XWPFParagraph> chapterParagraphs = allParagraphs.subList(paragraphIndex, endParagraph);
            
            return new Chapter(finalChapterNumber, chapterTitle, paragraphIndex, endParagraph, chapterParagraphs);
        }
        return null;
    }
    
    /**
     * Prüft ob ein Absatz wahrscheinlich ein Kapitel ist (mit Debug-Logging)
     */
    private boolean isLikelyChapter(XWPFParagraph paragraph, String text) {
        if (text.trim().isEmpty()) {
            return false;
        }
        
        // DEBUG: Alle verfügbaren Informationen loggen
        String styleName = paragraph.getStyle();
        String numId = paragraph.getNumID() != null ? paragraph.getNumID().toString() : "null";
        
        // Debug: Auch CTStyle-Informationen
        try {
            if (paragraph.getCTP() != null && paragraph.getCTP().getPPr() != null && 
                paragraph.getCTP().getPPr().getPStyle() != null) {
                
            }
        } catch (Exception e) {
        }
        
        // 1. PRIMÄR: Echte DOCX-Absatzformat-Stile prüfen
        if (styleName != null) {
            String lowerStyle = styleName.toLowerCase();
            if (lowerStyle.contains("heading") || lowerStyle.contains("überschrift") || 
                lowerStyle.contains("title") || lowerStyle.contains("titel") ||
                lowerStyle.contains("kapitel") || lowerStyle.contains("chapter") ||
                lowerStyle.contains("teil") || lowerStyle.contains("part") ||
                lowerStyle.startsWith("heading") || lowerStyle.startsWith("h")) {
                return true;
            }
        }
        
        // 1b. PRIMÄR: Auch CTStyle-Val prüfen (für benutzerdefinierte Stile)
        try {
            if (paragraph.getCTP() != null && paragraph.getCTP().getPPr() != null && 
                paragraph.getCTP().getPPr().getPStyle() != null) {
                String pStyleVal = paragraph.getCTP().getPPr().getPStyle().getVal();
                if (pStyleVal != null) {
                    String lowerPStyle = pStyleVal.toLowerCase();
                    if (lowerPStyle.contains("heading") || lowerPStyle.contains("überschrift") || 
                        lowerPStyle.contains("title") || lowerPStyle.contains("titel") ||
                        lowerPStyle.contains("kapitel") || lowerPStyle.contains("chapter") ||
                        lowerPStyle.contains("teil") || lowerPStyle.contains("part")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignoriere Fehler
        }
        

        
        // 3. PRIMÄR: Nummerierte Listen
        if (paragraph.getNumID() != null) {
            return true;
        }
        
        // 4. PRIMÄR: Kapitelmarkierungen mit Muster "-Zahl-" (z.B. "-2-", "-6-", "-16-")
        // Prüfe zuerst ob der Text dem Muster entspricht, unabhängig von Ausrichtung
        String trimmedText = text.trim();
        if (trimmedText.matches("^-[0-9]+-$")) {
            logger.debug("Erkannt als Kapitelmarkierung '-Zahl-': '{}'", trimmedText);
            return true;
        }
        
        // Auch Varianten ohne Bindestriche am Ende/Anfang (aber nur wenn sehr kurz)
        if (trimmedText.length() <= 10 && trimmedText.matches("^-?[0-9]+-?$")) {
            logger.debug("Erkannt als Kapitelmarkierung (Variante): '{}'", trimmedText);
            return true;
        }
        
        // 4b. Zentrierte Kapitelmarkierungen (zusätzliche Erkennung für zentrierte Absätze)
        ParagraphAlignment alignment = paragraph.getAlignment();
        if (alignment == ParagraphAlignment.CENTER) {
            logger.debug("Prüfe zentrierten Absatz: '{}'", trimmedText);
            
            // Prüfe ob der Text dem Muster "-Zahl-" entspricht (z.B. "-06-", "-2-", "-16-")
            if (trimmedText.matches("^-[0-9]+-$")) {
                logger.debug("Erkannt als zentrierte Kapitelmarkierung: '{}'", trimmedText);
                return true;
            }
            // Auch wenn es sehr kurz ist und nur Zahlen/Bindestriche enthält
            if (trimmedText.length() <= 10 && trimmedText.matches("^[-0-9]+$")) {
                logger.debug("Erkannt als zentrierte Kapitelmarkierung (kurz): '{}'", trimmedText);
                return true;
            }
        }
        
        // 5. PRIMÄR: Formatierung prüfen (fett + groß + kurz)
        boolean isBold = false;
        boolean isLargeFont = false;
        int maxFontSize = 0;
        
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.isBold()) {
                isBold = true;
            }
            if (run.getFontSize() != -1) {
                maxFontSize = Math.max(maxFontSize, run.getFontSize());
            }
        }
        
        isLargeFont = maxFontSize > 14;
        
        // Fett UND große Schrift UND kurzer Text = wahrscheinlich Überschrift
        if (isBold && isLargeFont && text.length() < 100) {
            return true;
        }
        
        // 6. FALLBACK: Nur sehr spezifische Text-Analyse
        String lowerText = text.toLowerCase();
        
        // Nur wenn explizit "Kapitel" oder "Chapter" enthalten UND kurz
        if ((lowerText.contains("kapitel") || lowerText.contains("chapter")) && text.length() < 50) {
            return true;
        }
        
        // Nur wenn Zahlen am Anfang UND sehr kurz (weniger als 20 Zeichen) UND keine Satzzeichen am Ende
        if (text.matches("^\\d+.*") && text.length() < 20 && !text.matches(".*[.!?:;]\\s*$")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Extrahiert die Kapitelnummer aus dem Text
     */
    private int extractChapterNumber(String text, XWPFParagraph paragraph) {
        String trimmedText = text.trim();
        
        logger.debug("extractChapterNumber: Input Text='{}', Länge={}", trimmedText, trimmedText.length());
        
        // 0. PRIMÄR: Muster "-Zahl-" erkennen (unabhängig von Ausrichtung)
        // Dies ist das häufigste Format für zentrierte Kapitelmarkierungen
        boolean matchesPattern = trimmedText.matches("^-[0-9]+-$");
        logger.debug("Pattern '^-[0-9]+-$' matched für '{}': {}", trimmedText, matchesPattern);
        
        if (matchesPattern) {
            // Entferne beide Bindestriche
            String numberOnly = trimmedText.replaceAll("^-", "").replaceAll("-$", "");
            logger.debug("Muster '-Zahl-' gefunden: Original='{}', Zahl='{}'", trimmedText, numberOnly);
            try {
                int num = Integer.parseInt(numberOnly);
                logger.info("✓ Nummer aus '-Zahl-' Muster extrahiert: '{}' -> {}", trimmedText, num);
                return num;
            } catch (NumberFormatException e) {
                logger.warn("Konnte '{}' nicht als Zahl parsen", numberOnly);
            }
        } else {
            logger.debug("Pattern '^-[0-9]+-$' matched NICHT für Text '{}' (Länge: {})", trimmedText, trimmedText.length());
            // Zeige alle Zeichen für Debugging
            for (int i = 0; i < trimmedText.length(); i++) {
                char c = trimmedText.charAt(i);
                logger.debug("  Zeichen [{}]: '{}' (Code: {})", i, c, (int)c);
            }
        }
        
        // 1. Zentrierte Markierungen wie "-1-", "-2-", "-16-", "2-", "-2", "2"
        ParagraphAlignment alignment = paragraph.getAlignment();
        if (alignment == ParagraphAlignment.CENTER) {
            logger.debug("Zentriert erkannt: Text='{}'", trimmedText);
            
            // Entferne alle Bindestriche und versuche Zahl zu extrahieren
            String numberOnly = trimmedText.replaceAll("^[-]+", "").replaceAll("[-]+$", "");
            logger.debug("Zentriert: Original='{}', nach Entfernen der Bindestriche='{}'", trimmedText, numberOnly);
            if (numberOnly.matches("^\\d+$")) {
                try {
                    int num = Integer.parseInt(numberOnly);
                    logger.info("✓ Nummer aus zentrierter Markierung extrahiert: '{}' -> {}", trimmedText, num);
                    return num;
                } catch (NumberFormatException e) {
                    logger.debug("Konnte '{}' nicht als Zahl parsen", numberOnly);
                }
            }
        }
        
        // 2. Standard-Formate: "Kapitel 2", "Chapter 3", "Teil 1", "Part 4"
        Pattern pattern = Pattern.compile("(?i)(kapitel|chapter|teil|part)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(trimmedText);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                // Fallback
            }
        }
        
        // 3. Zahlen am Anfang: "2. Titel", "2 - Titel", "2: Titel"
        pattern = Pattern.compile("^(\\d+)\\s*[.:\\-]");
        matcher = pattern.matcher(trimmedText);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Fallback
            }
        }
        
        // 4. Einfache Zahl (wenn der Text nur aus einer Zahl besteht oder mit einer Zahl beginnt)
        pattern = Pattern.compile("^(\\d+)");
        matcher = pattern.matcher(trimmedText);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Fallback
            }
        }
        
        // Fallback: Keine Nummer gefunden
        return 0;
    }
    
    /**
     * Gibt den vollständigen Titel zurück (mit Nummer, z.B. "1. Kapitel")
     */
    private String extractOriginalTitle(String text) {
        return text.trim();
    }
    
    /**
     * Findet das Ende eines Kapitels (nächster Kapitel-Start oder Ende der Datei)
     */
    private int findChapterEnd(int startIndex, List<XWPFParagraph> allParagraphs) {
        for (int i = startIndex + 1; i < allParagraphs.size(); i++) {
            XWPFParagraph paragraph = allParagraphs.get(i);
            String text = paragraph.getText().trim();
            if (!text.isEmpty()) {
                // Prüfe ob es ein neues Kapitel ist
                if (isLikelyChapter(paragraph, text)) {
                    return i;
                }
            }
        }
        return allParagraphs.size(); // Ende der Datei
    }
    
    private String buildDefaultNameBody(Chapter chapter) {
        String chapterTitle = chapter.getTitle() != null ? chapter.getTitle().trim() : "";
        int chapterNum = Math.max(1, chapter.getNumber());
        boolean isNumericTitle = chapterTitle.matches("^-\\d+-$") ||
                                 (chapterTitle.length() <= 5 && chapterTitle.matches("^-?\\d+-?$"));

        if (isNumericTitle) {
            return String.format("%02d", chapterNum);
        }

        String sanitizedTitle = chapterTitle
            .replaceAll("[\\\\/:*?\"<>|:]", "_")
            .replaceAll("\\s+", "_")
            .trim();

        if (sanitizedTitle.isEmpty()) {
            sanitizedTitle = "Kapitel_" + chapterNum;
        }

        return sanitizedTitle;
    }

    private String sanitizeFileName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return "";
        }
        name = name.replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|:]", "_");
        name = name.replaceAll("\\s+", " ").trim();
        return name;
    }

    private String ensureDocxExtension(String name) {
        if (name == null || name.isEmpty()) {
            return "Kapitel.docx";
        }
        if (!name.toLowerCase().endsWith(".docx")) {
            return name + ".docx";
        }
        return name;
    }

    /**
     * Speichert ein Kapitel als separate DOCX-Datei (verwendet Docx4J)
     */
    public void saveChapter(Chapter chapter, File outputDir, String targetFileName) throws IOException {
        String fileName = normalizeFileName(targetFileName, chapter);
        int chapterNum = chapter.getNumber();

        File outputFile = new File(outputDir, fileName);
        // Überschreib-Schutz: niemals die Originaldatei überschreiben, und bestehende Dateien unik machen
        try {
            String srcPath = sourceDocxFile != null ? sourceDocxFile.getCanonicalPath() : "";
            String outPath = outputFile.getCanonicalPath();
            if (srcPath.equalsIgnoreCase(outPath) || outputFile.exists()) {
                outputFile = generateUniqueFile(outputDir, fileName);
            }
        } catch (IOException ignore) {
            if (outputFile.exists()) {
                outputFile = generateUniqueFile(outputDir, fileName);
            }
        }
        
        logger.info("═══════════════════════════════════════════════════════════");
        logger.info("Speichere Kapitel:");
        logger.info("  Titel: '{}'", chapter.getTitle());
        logger.info("  Kapitelnummer (chapter.getNumber()): {}", chapterNum);
        logger.info("  Dateiname: {}", fileName);
        logger.info("  Absatz-Bereich: {}-{}", chapter.getStartParagraph(), chapter.getEndParagraph());
        logger.info("═══════════════════════════════════════════════════════════");
        
        // WICHTIG: Verwende Docx4J für vollständige Kompatibilität beim Lesen
        try {
            // Quelldokument mit Docx4J öffnen
            WordprocessingMLPackage sourcePackage = WordprocessingMLPackage.load(sourceDocxFile);
            MainDocumentPart sourceMainPart = sourcePackage.getMainDocumentPart();
            
            // Alle Paragraphs aus dem Quelldokument holen
            List<Object> sourceContent = sourceMainPart.getContent();
            List<P> sourceParagraphs = new ArrayList<>();
            for (Object obj : sourceContent) {
                if (obj instanceof P) {
                    sourceParagraphs.add((P) obj);
                } else if (obj instanceof JAXBElement) {
                    Object value = ((JAXBElement<?>) obj).getValue();
                    if (value instanceof P) {
                        sourceParagraphs.add((P) value);
                    }
                }
            }

            int start = chapter.getStartParagraph();
            int end = chapter.getEndParagraph();

            // Validierung der Indizes
            if (start < 0 || start >= sourceParagraphs.size()) {
                throw new IOException("Ungültiger Start-Index für Kapitel: " + start + " (Max: " + sourceParagraphs.size() + ")");
            }
            if (end < start || end > sourceParagraphs.size()) {
                throw new IOException("Ungültiger End-Index für Kapitel: " + end + " (Start: " + start + ", Max: " + sourceParagraphs.size() + ")");
            }
            
            logger.debug("Kopiere Absätze von Index {} bis {} (insgesamt {} Absätze)", start, end, end - start);
            
            // Vollständige Kopie der Quelle speichern und anschließend die Kopie laden
            Docx4J.save(sourcePackage, outputFile);
            WordprocessingMLPackage targetPackage = WordprocessingMLPackage.load(outputFile);
            MainDocumentPart targetMainPart = targetPackage.getMainDocumentPart();
            
            // Erzeuge Ziel-Dokument direkt aus Source-Document und ersetze Body-Inhalt
            org.docx4j.wml.Document sourceDoc = sourceMainPart.getJaxbElement();
            org.docx4j.wml.Document targetDoc = (org.docx4j.wml.Document) XmlUtils.deepCopy(sourceDoc);
            Body targetBody = targetDoc.getBody();
            SectPr sectPr = targetBody != null ? targetBody.getSectPr() : null;
            if (targetBody != null) {
                List<Object> newContent = new ArrayList<>();
                int copyStart = Math.min(end, Math.max(start + 1, 0));
                boolean removedFirstStarsLine = false;
                for (int i = copyStart; i < end; i++) {
                    P sourcePara = sourceParagraphs.get(i);
                    String plain = extractPlainTextFromParagraph(sourcePara).trim();
                    // Horizontale Linien beibehalten (nicht entfernen)
                    // if (isHorizontalRule(sourcePara)) continue;  // DEAKTIVIERT: HR-Tags sollen erhalten bleiben
                    if (!removedFirstStarsLine && !plain.isEmpty() && plain.matches("^\\s*\\*{2,}\\s*$")) {
                        removedFirstStarsLine = true;
                        continue;
                    }
                    newContent.add(XmlUtils.deepCopy(sourcePara));
                }
                targetBody.getContent().clear();
                targetBody.getContent().addAll(newContent);
                if (sectPr != null) {
                    targetBody.setSectPr((SectPr) XmlUtils.deepCopy(sectPr));
                }
            }
            targetMainPart.setJaxbElement(targetDoc);

            // Dokument speichern
            Docx4J.save(targetPackage, outputFile);
            
            logger.info("Kapitel {} erfolgreich gespeichert: {}", chapter.getNumber(), fileName);
            
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Kapitels {}: {}", chapter.getNumber(), e.getMessage(), e);
            throw new IOException("Fehler beim Speichern des Kapitels: " + e.getMessage(), e);
        }
    }

    private File generateUniqueFile(File dir, String desiredName) {
        String name = desiredName;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : ".docx";
        int idx = 1;
        File candidate = new File(dir, name);
        while (candidate.exists()) {
            candidate = new File(dir, base + " (" + idx + ")" + ext);
            idx++;
        }
        return candidate;
    }

    private String extractPlainTextFromParagraph(P paragraph) {
        if (paragraph == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : paragraph.getContent()) {
            Object unwrapped = XmlUtils.unwrap(o);
            if (unwrapped instanceof R) {
                R run = (R) unwrapped;
                for (Object rc : run.getContent()) {
                    Object uw = XmlUtils.unwrap(rc);
                    if (uw instanceof Text) {
                        sb.append(((Text) uw).getValue());
                    }
                }
            }
        }
        return sb.toString();
    }

    private boolean isHorizontalRule(P paragraph) {
        try {
            if (paragraph == null || paragraph.getPPr() == null) return false;
            org.docx4j.wml.PPr ppr = paragraph.getPPr();
            // Absatzrahmen oben/unten/zwischen deuten wir als horizontale Linie, wenn kein Text im Absatz ist
            if (ppr.getPBdr() != null) {
                String text = extractPlainTextFromParagraph(paragraph).trim();
                return text.isEmpty();
            }
        } catch (Exception ignore) {
        }
        return false;
    }
    
                    /**
                 * Fügt nicht-ausgewählte Kapitel dem vorherigen ausgewählten Kapitel hinzu
                 */
                public List<Chapter> mergeChaptersWithUnselectedContent(List<Chapter> allChapters, List<Boolean> selectionStatus) {
                    
                    List<Chapter> mergedChapters = new ArrayList<>();
                    Chapter currentMergedChapter = null;
                    
                    for (int i = 0; i < allChapters.size(); i++) {
                        Chapter chapter = allChapters.get(i);
                        boolean isSelected = selectionStatus.get(i);
                        
                        if (isSelected) {
                            // Neues ausgewähltes Kapitel - speichere das vorherige und starte ein neues
                            if (currentMergedChapter != null) {
                                mergedChapters.add(currentMergedChapter);
                            }
                            
                            // Neues Kapitel starten (mit ursprünglicher Nummer)
                            currentMergedChapter = new Chapter(
                                chapter.getNumber(), // Ursprüngliche Nummer beibehalten
                                chapter.getTitle(),
                                chapter.getStartParagraph(),
                                chapter.getEndParagraph(),
                                new ArrayList<>(chapter.getParagraphs())
                            );
                            
                        } else {
                            // Nicht ausgewähltes Kapitel - zum aktuellen hinzufügen
                            if (currentMergedChapter != null) {
                                // Paragraphs des nicht-ausgewählten Kapitels hinzufügen
                                currentMergedChapter.getParagraphs().addAll(chapter.getParagraphs());
                                // End-Paragraph aktualisieren (Nummer bleibt gleich)
                                currentMergedChapter = new Chapter(
                                    currentMergedChapter.getNumber(), // Nummer des ausgewählten Kapitels beibehalten
                                    currentMergedChapter.getTitle() + " + " + chapter.getTitle(),
                                    currentMergedChapter.getStartParagraph(),
                                    chapter.getEndParagraph(),
                                    currentMergedChapter.getParagraphs()
                                );
                            } else {
                                // Erstes Kapitel ist nicht ausgewählt - als eigenes Kapitel behandeln
                                currentMergedChapter = new Chapter(
                                    chapter.getNumber(), // Ursprüngliche Nummer beibehalten
                                    "Unbenanntes Kapitel: " + chapter.getTitle(),
                                    chapter.getStartParagraph(),
                                    chapter.getEndParagraph(),
                                    new ArrayList<>(chapter.getParagraphs())
                                );
                            }
                        }
                    }
                    
                    // Letztes zusammengefügtes Kapitel hinzufügen
                    if (currentMergedChapter != null) {
                        mergedChapters.add(currentMergedChapter);
                    }
                    
                    return mergedChapters;
                }

                /**
                 * Speichert alle Kapitel als separate DOCX-Dateien
                 */
                public void splitDocument(File docxFile, File outputDir) throws IOException {

                    // Kapitel analysieren
                    List<Chapter> chapters = analyzeDocument(docxFile);

                    if (chapters.isEmpty()) {
                        logger.warn("Keine Kapitel in der Datei gefunden!");
                        throw new IOException("Keine Kapitel in der Datei gefunden");
                    }

                    // Ausgabe-Verzeichnis erstellen falls nicht vorhanden
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }

                    // Alle Kapitel speichern
                    for (Chapter chapter : chapters) {
                        saveChapter(chapter, outputDir, null);
                    }

                }
}
