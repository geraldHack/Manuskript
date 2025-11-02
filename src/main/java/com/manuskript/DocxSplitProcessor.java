package com.manuskript;

import org.apache.poi.xwpf.usermodel.*;
import org.docx4j.Docx4J;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart;
import org.docx4j.wml.P;
import org.docx4j.wml.Styles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.xml.bind.JAXBElement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
        private final int number;
        private final String title;
        private final int startParagraph;
        private final int endParagraph;
        private final List<XWPFParagraph> paragraphs;
        
        public Chapter(int number, String title, int startParagraph, int endParagraph, List<XWPFParagraph> paragraphs) {
            this.number = number;
            this.title = title;
            this.startParagraph = startParagraph;
            this.endParagraph = endParagraph;
            this.paragraphs = new ArrayList<>(paragraphs);
        }
        
        public int getNumber() { return number; }
        public String getTitle() { return title; }
        public int getStartParagraph() { return startParagraph; }
        public int getEndParagraph() { return endParagraph; }
        public List<XWPFParagraph> getParagraphs() { return paragraphs; }
        
        @Override
        public String toString() {
            return title;
        }
    }
    
    private File sourceDocxFile;

    public void setSourceDocxFile(File docxFile) {
        this.sourceDocxFile = docxFile;
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
            
            // Extrahiere den ursprünglichen Titel (ohne Nummer)
            String originalTitle = extractOriginalTitle(text);
            String chapterTitle = originalTitle;
            
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
     * Gibt den ursprünglichen Text zurück (ohne Bearbeitung)
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
    
    /**
     * Speichert ein Kapitel als separate DOCX-Datei (verwendet Apache POI statt Docx4J)
     */
    public void saveChapter(Chapter chapter, File outputDir, String baseFileName) throws IOException {
        int chapterNum = chapter.getNumber();
        String fileName;
        String chapterTitle = chapter.getTitle();
        
        // Entscheide Dateiname basierend auf Titel:
        // - Wenn Titel dem Muster "-Zahl-" entspricht: Verwende nur die Nummer (z.B. "01.docx")
        // - Wenn Titel ein normaler Text ist (nicht nur Zahlen): Verwende den Titel (z.B. "Leben_in_der_Kuppel.docx")
        
        String trimmedTitle = chapterTitle.trim();
        // Prüfe auf numerische Muster: "-1-", "-6-", "-16-", aber auch "1", "-1", "1-" (nur für sehr kurze Texte)
        boolean isNumericTitle = trimmedTitle.matches("^-\\d+-$") || // "-1-", "-6-", "-16-" (primäres Muster)
                                 (trimmedTitle.length() <= 5 && trimmedTitle.matches("^-?\\d+-?$")); // "1", "-1", "1-" (nur für kurze Texte)
        
        if (isNumericTitle) {
            // Numerischer Titel: Verwende nur die Kapitelnummer (ohne baseFileName)
            fileName = String.format("%02d.docx", chapterNum);
            logger.debug("Numerischer Titel '{}' -> Verwende Nummer: '{}'", chapterTitle, fileName);
        } else {
            // Text-Titel: Verwende Titel als Dateinamen (sanitisiert für Dateisystem)
            String sanitizedTitle = chapterTitle
                .replaceAll("[<>:\"/\\|?*]", "_") // Ersetze ungültige Zeichen
                .replaceAll("\\s+", "_") // Ersetze Leerzeichen
                .trim();
            if (sanitizedTitle.isEmpty()) {
                // Fallback falls Titel leer
                sanitizedTitle = "Kapitel_" + chapterNum;
            }
            fileName = sanitizedTitle + ".docx";
            logger.info("Verwende Titel als Dateinamen: '{}' -> '{}'", chapterTitle, fileName);
        }
        
        File outputFile = new File(outputDir, fileName);
        
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
            
            // Neues Dokument mit Docx4J erstellen
            WordprocessingMLPackage targetPackage = WordprocessingMLPackage.createPackage();
            MainDocumentPart targetMainPart = targetPackage.getMainDocumentPart();
            
            // WICHTIG: Kopiere Styles vom Quelldokument
            try {
                StyleDefinitionsPart sourceStylesPart = sourceMainPart.getStyleDefinitionsPart();
                if (sourceStylesPart != null) {
                    StyleDefinitionsPart targetStylesPart = targetMainPart.getStyleDefinitionsPart();
                    if (targetStylesPart == null) {
                        targetStylesPart = new StyleDefinitionsPart();
                        targetMainPart.getRelationshipsPart().addTargetPart(targetStylesPart);
                    }
                    
                    // Kopiere Styles durch Marshalling/Unmarshalling
                    Styles sourceStyles = sourceStylesPart.getJaxbElement();
                    if (sourceStyles != null) {
                        String stylesXml = XmlUtils.marshaltoString(sourceStyles, true, true);
                        Styles targetStyles = (Styles) XmlUtils.unmarshalString(stylesXml);
                        targetStylesPart.setJaxbElement(targetStyles);
                        logger.debug("Styles kopiert");
                    }
                }
            } catch (Exception e) {
                logger.warn("Fehler beim Kopieren der Styles: {}", e.getMessage());
                // Fehler nicht kritisch - Dokument kann auch ohne Styles funktionieren
            }
            
            // Kopiere Paragraphs durch Marshalling/Unmarshalling für vollständige Formatierung
            for (int i = start; i < end; i++) {
                P sourcePara = sourceParagraphs.get(i);
                
                // Kopiere Paragraph durch XML-Serialisierung (behält ALLE Formatierungen!)
                String paraXml = XmlUtils.marshaltoString(sourcePara, true, true);
                P targetPara = (P) XmlUtils.unmarshalString(paraXml);
                
                targetMainPart.addObject(targetPara);
            }
            
            // Dokument speichern
            Docx4J.save(targetPackage, outputFile);
            
            logger.info("Kapitel {} erfolgreich gespeichert: {}", chapter.getNumber(), fileName);
            
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Kapitels {}: {}", chapter.getNumber(), e.getMessage(), e);
            throw new IOException("Fehler beim Speichern des Kapitels: " + e.getMessage(), e);
        }
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

                    // Basis-Dateiname (ohne .docx)
                    String baseFileName = docxFile.getName().replaceFirst("\\.docx$", "");

                    // Alle Kapitel speichern
                    for (Chapter chapter : chapters) {
                        saveChapter(chapter, outputDir, baseFileName);
                    }

                }
}
