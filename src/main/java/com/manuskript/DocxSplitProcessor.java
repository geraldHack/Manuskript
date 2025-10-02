package com.manuskript;

import org.apache.poi.xwpf.usermodel.*;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart;
import org.docx4j.wml.Body;
import org.docx4j.jaxb.Context;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.P;
import org.docx4j.wml.SectPr;
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
    private WordprocessingMLPackage cachedSourcePackage;

    public void setSourceDocxFile(File docxFile) {
        this.sourceDocxFile = docxFile;
        this.cachedSourcePackage = null;
    }

    /**
     * Analysiert eine DOCX-Datei und findet alle Kapitel
     */
    public List<Chapter> analyzeDocument(File docxFile) throws IOException {
        logger.info("Analysiere DOCX-Datei: {}", docxFile.getAbsolutePath());
        
        this.sourceDocxFile = docxFile;

        List<Chapter> chapters = new ArrayList<>();
        List<XWPFParagraph> allParagraphs = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(docxFile);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            this.sourceDocxFile = docxFile;
            this.cachedSourcePackage = null;

            // Alle Absätze sammeln
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                allParagraphs.add(paragraph);
            }
            
            logger.info("Gefundene Absätze: {}", allParagraphs.size());
            
            // Kapitel finden
            for (int i = 0; i < allParagraphs.size(); i++) {
                XWPFParagraph paragraph = allParagraphs.get(i);
                String text = paragraph.getText().trim();
                
                if (!text.isEmpty()) {
                    Chapter chapter = detectChapter(text, i, allParagraphs, chapters.size() + 1);
                    if (chapter != null) {
                        chapters.add(chapter);
                        logger.info("Kapitel gefunden: {}", chapter);
                    }
                }
            }
        }
        
        logger.info("Insgesamt {} Kapitel gefunden", chapters.size());
        return chapters;
    }
    
    /**
     * Erkennt ob ein Absatz ein Kapitel-Start ist (flexible Erkennung)
     */
    private Chapter detectChapter(String text, int paragraphIndex, List<XWPFParagraph> allParagraphs, int chapterNumber) {
        XWPFParagraph paragraph = allParagraphs.get(paragraphIndex);
        
        // Kapitel-Kandidaten basierend auf verschiedenen Kriterien
        if (isLikelyChapter(paragraph, text)) {
            // Extrahiere den ursprünglichen Titel (ohne Nummer)
            String originalTitle = extractOriginalTitle(text);
            String chapterTitle = originalTitle;
            
            // Kapitel-Inhalt bestimmen (bis zum nächsten Kapitel oder Ende)
            int endParagraph = findChapterEnd(paragraphIndex, allParagraphs);
            List<XWPFParagraph> chapterParagraphs = allParagraphs.subList(paragraphIndex, endParagraph);
            
            return new Chapter(chapterNumber, chapterTitle, paragraphIndex, endParagraph, chapterParagraphs);
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
            logger.info("STYLE DEBUG: Text='{}' | Style='{}' | ERROR='{}' | NumId='{}'", 
                       text.substring(0, Math.min(50, text.length())), styleName, e.getMessage(), numId);
        }
        
        // 1. PRIMÄR: Echte DOCX-Absatzformat-Stile prüfen
        if (styleName != null) {
            String lowerStyle = styleName.toLowerCase();
            if (lowerStyle.contains("heading") || lowerStyle.contains("überschrift") || 
                lowerStyle.contains("title") || lowerStyle.contains("titel") ||
                lowerStyle.contains("kapitel") || lowerStyle.contains("chapter") ||
                lowerStyle.contains("teil") || lowerStyle.contains("part") ||
                lowerStyle.startsWith("heading") || lowerStyle.startsWith("h")) {
                logger.info("GEFUNDEN durch Style: {} -> {}", styleName, text);
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
                        logger.info("GEFUNDEN durch PStyleVal: {} -> {}", pStyleVal, text);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignoriere Fehler
        }
        

        
        // 3. PRIMÄR: Nummerierte Listen
        if (paragraph.getNumID() != null) {
            logger.debug("Gefunden durch NumId: {}", numId);
            return true;
        }
        
        // 4. PRIMÄR: Formatierung prüfen (fett + groß + kurz)
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
            logger.debug("Gefunden durch Formatierung: fett={}, groß={}, länge={}", isBold, isLargeFont, text.length());
            return true;
        }
        
        // 5. FALLBACK: Nur sehr spezifische Text-Analyse
        String lowerText = text.toLowerCase();
        
        // Nur wenn explizit "Kapitel" oder "Chapter" enthalten UND kurz
        if ((lowerText.contains("kapitel") || lowerText.contains("chapter")) && text.length() < 50) {
            logger.debug("Gefunden durch Schlüsselwort: {}", text);
            return true;
        }
        
        // Nur wenn Zahlen am Anfang UND sehr kurz (weniger als 20 Zeichen) UND keine Satzzeichen am Ende
        if (text.matches("^\\d+.*") && text.length() < 20 && !text.matches(".*[.!?:;]\\s*$")) {
            logger.debug("Gefunden durch Zahlen: {}", text);
            return true;
        }
        
        logger.debug("NICHT als Kapitel erkannt: {}", text);
        return false;
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
     * Speichert ein Kapitel als separate DOCX-Datei
     */
    public void saveChapter(Chapter chapter, File outputDir, String baseFileName) throws IOException {
        String fileName = String.format("%s_%02d.docx", baseFileName, chapter.getNumber());
        File outputFile = new File(outputDir, fileName);
        
        logger.info("Speichere Kapitel {} als: {}", chapter.getNumber(), outputFile.getAbsolutePath());
        
        try {
            if (cachedSourcePackage == null) {
                cachedSourcePackage = WordprocessingMLPackage.load(sourceDocxFile);
            }
            WordprocessingMLPackage sourcePackage = cachedSourcePackage;
            WordprocessingMLPackage targetPackage = WordprocessingMLPackage.createPackage();

            // Styles und Numberings übertragen
            StyleDefinitionsPart styles = sourcePackage.getMainDocumentPart().getStyleDefinitionsPart(false);
            if (styles != null) {
                StyleDefinitionsPart stylePart = new StyleDefinitionsPart();
                stylePart.setPackage(targetPackage);
                Styles stylesClone = (Styles) XmlUtils.deepCopy(styles.getJaxbElement());
                stylePart.setJaxbElement(stylesClone);
                targetPackage.getMainDocumentPart().addTargetPart(stylePart);
            }

            NumberingDefinitionsPart numbering = sourcePackage.getMainDocumentPart().getNumberingDefinitionsPart();
            if (numbering != null) {
                NumberingDefinitionsPart numberingPart = new NumberingDefinitionsPart();
                numberingPart.setPackage(targetPackage);
                Numbering numberingClone = (Numbering) XmlUtils.deepCopy(numbering.getJaxbElement());
                numberingPart.setJaxbElement(numberingClone);
                targetPackage.getMainDocumentPart().addTargetPart(numberingPart);
            }

            MainDocumentPart sourceMain = sourcePackage.getMainDocumentPart();
            MainDocumentPart targetMain = targetPackage.getMainDocumentPart();

            // Neues Body erzeugen
            Body newBody = Context.getWmlObjectFactory().createBody();

            int start = chapter.getStartParagraph();
            int end = chapter.getEndParagraph();

            List<Object> sourceContent = sourceMain.getContents().getBody().getContent();
            for (int i = start; i < end; i++) {
                Object paragraphObject = sourceContent.get(i);
                Object unwrapped = XmlUtils.unwrap(paragraphObject);
                P paragraph;
                if (unwrapped instanceof P) {
                    paragraph = (P) XmlUtils.deepCopy(unwrapped);
                } else if (unwrapped instanceof JAXBElement) {
                    Object value = ((JAXBElement<?>) unwrapped).getValue();
                    paragraph = (P) XmlUtils.deepCopy(value);
                } else {
                    throw new IllegalArgumentException("Unexpected element type: " + unwrapped.getClass());
                }
                newBody.getContent().add(paragraph);
            }

            targetMain.getContents().setBody(newBody);

            // Abschnitts-Eigenschaften übernehmen
            SectPr sectPr = sourceMain.getContents().getBody().getSectPr();
            if (sectPr != null) {
                targetMain.getContents().getBody().setSectPr((SectPr) XmlUtils.deepCopy(sectPr));
            }

            targetPackage.save(outputFile);
        } catch (Docx4JException e) {
            throw new IOException("Fehler beim Speichern des Kapitels", e);
        }
        
        logger.info("Kapitel {} erfolgreich gespeichert", chapter.getNumber());
    }
    
                    /**
                 * Fügt nicht-ausgewählte Kapitel dem vorherigen ausgewählten Kapitel hinzu
                 */
                public List<Chapter> mergeChaptersWithUnselectedContent(List<Chapter> allChapters, List<Boolean> selectionStatus) {
                    logger.info("Starte Kapitel-Zusammenführung: {} Kapitel, {} Auswahl-Status", allChapters.size(), selectionStatus.size());
                    
                    List<Chapter> mergedChapters = new ArrayList<>();
                    Chapter currentMergedChapter = null;
                    
                    for (int i = 0; i < allChapters.size(); i++) {
                        Chapter chapter = allChapters.get(i);
                        boolean isSelected = selectionStatus.get(i);
                        
                        if (isSelected) {
                            // Neues ausgewähltes Kapitel - speichere das vorherige und starte ein neues
                            if (currentMergedChapter != null) {
                                mergedChapters.add(currentMergedChapter);
                                logger.info("Zusammengefügtes Kapitel hinzugefügt: {}", currentMergedChapter.getTitle());
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
                                logger.info("Kapitel '{}' zu '{}' hinzugefügt", chapter.getTitle(), currentMergedChapter.getTitle());
                            } else {
                                // Erstes Kapitel ist nicht ausgewählt - als eigenes Kapitel behandeln
                                currentMergedChapter = new Chapter(
                                    chapter.getNumber(), // Ursprüngliche Nummer beibehalten
                                    "Unbenanntes Kapitel: " + chapter.getTitle(),
                                    chapter.getStartParagraph(),
                                    chapter.getEndParagraph(),
                                    new ArrayList<>(chapter.getParagraphs())
                                );
                                logger.info("Nicht-ausgewähltes Kapitel als 'Unbenanntes Kapitel' behandelt: {}", chapter.getTitle());
                            }
                        }
                    }
                    
                    // Letztes zusammengefügtes Kapitel hinzufügen
                    if (currentMergedChapter != null) {
                        mergedChapters.add(currentMergedChapter);
                        logger.info("Letztes zusammengefügtes Kapitel hinzugefügt: {}", currentMergedChapter.getTitle());
                    }
                    
                    logger.info("Kapitel-Zusammenführung abgeschlossen: {} finale Kapitel", mergedChapters.size());
                    return mergedChapters;
                }

                /**
                 * Speichert alle Kapitel als separate DOCX-Dateien
                 */
                public void splitDocument(File docxFile, File outputDir) throws IOException {
                    logger.info("Starte DOCX-Split: {} -> {}", docxFile.getAbsolutePath(), outputDir.getAbsolutePath());

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

                    logger.info("DOCX-Split abgeschlossen: {} Kapitel gespeichert", chapters.size());
                }
}
