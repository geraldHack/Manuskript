package com.manuskript;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.DocumentSettingsPart;
import org.docx4j.wml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class DocxProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(DocxProcessor.class);
    
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
        
        // DOCX-Lesen mit Docx4J
        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(file);
            List<Object> document = pkg.getMainDocumentPart().getContent();
            
            for (Object obj : document) {
                if (obj instanceof P) {
                    P paragraph = (P) obj;
                    String paragraphText = extractTextFromParagraph(paragraph);
                    
                    if (!paragraphText.trim().isEmpty()) {
                    if (format == OutputFormat.HTML) {
                            content.append("<p>").append(paragraphText).append("</p>\n");
                        } else if (format == OutputFormat.MARKDOWN) {
                            content.append(paragraphText).append("\n\n");
                    } else {
                            content.append(paragraphText).append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Lesen der DOCX-Datei: {}", e.getMessage());
            content.append("Fehler beim Lesen der DOCX-Datei: ").append(e.getMessage());
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
        
        // DOCX-Lesen mit Docx4J
        try {
            logger.info("Lade DOCX-Datei: {}", file.getAbsolutePath());
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(file);
            List<Object> document = pkg.getMainDocumentPart().getContent();
            logger.info("DOCX-Datei geladen, {} Objekte gefunden", document.size());
            
            int paragraphCount = 0;
            for (Object obj : document) {
                if (obj instanceof P) {
                    paragraphCount++;
                    P paragraph = (P) obj;
                    String paragraphText = extractTextFromParagraph(paragraph);
                    logger.info("Absatz {}: '{}'", paragraphCount, paragraphText.substring(0, Math.min(50, paragraphText.length())));
                    
                                                            // Leere Absätze als Leerzeilen erhalten (für "DOCX übernehmen")
                    if (format == OutputFormat.HTML) {
                        documentContent.append("<p>").append(paragraphText).append("</p>\n");
                    } else if (format == OutputFormat.MARKDOWN) {
                        documentContent.append(paragraphText).append("\n\n");
                    } else {
                        documentContent.append(paragraphText).append("\n");
                    }
                } else {
                    logger.debug("Nicht-Paragraph Objekt: {}", obj.getClass().getSimpleName());
                }
            }
            logger.info("Insgesamt {} Absätze verarbeitet", paragraphCount);
        } catch (Exception e) {
            logger.error("Fehler beim Lesen der DOCX-Datei: {}", e.getMessage(), e);
            documentContent.append("Fehler beim Lesen der DOCX-Datei: ").append(e.getMessage());
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
    
    public void exportMarkdownToDocx(String markdownText, File outputFile) throws IOException {
        // Standard-Export ohne Optionen
        exportMarkdownToDocxWithOptions(markdownText, outputFile, null);
    }
    
    public void exportMarkdownToDocxWithOptions(String markdownText, File outputFile, DocxOptions options) throws IOException {
        logger.info("Exportiere Markdown zu DOCX mit Docx4J: {}", outputFile.getName());
        
        // WICHTIG: Prüfe ob der Markdown-Text leer ist!
        if (markdownText == null || markdownText.trim().isEmpty()) {
            logger.error("Versuch, leeren Markdown-Text zu DOCX zu exportieren!");
            throw new IllegalArgumentException("Markdown-Text ist leer - kann nicht exportiert werden!");
        }

        try {
            // Schreibe zuerst in eine temporäre Datei im selben Verzeichnis und ersetze danach atomar
            File parentDir = outputFile.getAbsoluteFile().getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.warn("Konnte Ausgabeverzeichnis nicht erstellen: {}", parentDir.getAbsolutePath());
                }
            }
            File tempFile = File.createTempFile("manuskript-", ".docx", parentDir);
            
            // Docx4J Setup
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            ObjectFactory f = new ObjectFactory();
            
            // Optionen anwenden
            if (options != null) {
                logger.info("Wende DOCX-Optionen an: Schriftart={}, Blocksatz={}, Zentrierte H1={}", 
                    options.defaultFont, options.justifyText, options.centerH1);
                
                // Silbentrennung aktivieren, falls gewünscht
                if (options.enableHyphenation) {
                    ensureHyphenationEnabled(pkg);
                }
                
                // Inhaltsverzeichnis hinzufügen, falls gewünscht
                if (options.includeTableOfContents) {
                    addTableOfContents(pkg, f);
                }
            }
            
            // Einfache String-Verarbeitung statt Flexmark
            String[] lines = markdownText.split("\r?\n");
            
            // Zähler für automatische Nummerierung
            int h1Counter = 0;
            int h2Counter = 0;
            int h3Counter = 0;
            boolean firstH1Seen = false;
            boolean lastWasHeading = false;
            
            // Zeilen verarbeiten
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                
                if (line.isEmpty()) {
                    continue; // Leere Zeilen überspringen
                }
                
                // Überschriften erkennen
                if (line.startsWith("# ")) {
                    // H1
                    String headingText = line.substring(2);
                    h1Counter++;
                    h2Counter = 0;
                    h3Counter = 0;
                    
                    if (options != null && options.autoNumberHeadings) {
                        headingText = h1Counter + ". " + headingText;
                    }
                    
                    addHeading(pkg, f, headingText, 1, firstH1Seen, options);
                    if (!firstH1Seen) firstH1Seen = true;
                    lastWasHeading = true;
                    
                } else if (line.startsWith("## ")) {
                    // H2
                    String headingText = line.substring(3);
                    h2Counter++;
                    h3Counter = 0;
                    
                    if (options != null && options.autoNumberHeadings) {
                        headingText = h1Counter + "." + h2Counter + " " + headingText;
                    }
                    
                    addHeading(pkg, f, headingText, 2, firstH1Seen, options);
                    lastWasHeading = true;
                    
                } else if (line.startsWith("### ")) {
                    // H3
                    String headingText = line.substring(4);
                    h3Counter++;
                    
                    if (options != null && options.autoNumberHeadings) {
                        headingText = h1Counter + "." + h2Counter + "." + h3Counter + " " + headingText;
                    }
                    
                    addHeading(pkg, f, headingText, 3, firstH1Seen, options);
                    lastWasHeading = true;
                    
                } else if (line.startsWith("```")) {
                    // Code-Block
                    StringBuilder codeBlock = new StringBuilder();
                    i++; // Nächste Zeile
                    while (i < lines.length && !lines[i].trim().startsWith("```")) {
                        codeBlock.append(lines[i]).append("\n");
                        i++;
                    }
                    addCodeBlock(pkg, f, codeBlock.toString().trim(), options);
                    lastWasHeading = false;
                    
                } else if (line.equals("---") || line.equals("***")) {
                    // Horizontale Linie
                    addHorizontalRule(pkg, f);
                    lastWasHeading = false;
                    
                } else {
                    // Normaler Text/Absatz
                    boolean shouldIndent = (options != null && options.firstLineIndent && !lastWasHeading);
                    
                    if (shouldIndent) {
                        logger.info("Einrückung für Absatz: '{}'", line.substring(0, Math.min(30, line.length())));
                        addParagraph(pkg, f, line, options);
                    } else {
                        logger.info("Keine Einrückung für Absatz: '{}'", line.substring(0, Math.min(30, line.length())));
                        addParagraphWithoutIndent(pkg, f, line, options);
                    }
                    lastWasHeading = false;
                }
            }
            
            // Dokument speichern
            Docx4J.save(pkg, tempFile);
            
            // Validierung: Versuche die temp-Datei als DOCX zu öffnen, bevor wir ersetzen
            try {
                WordprocessingMLPackage validatePkg = WordprocessingMLPackage.load(tempFile);
                // ok
            } catch (Exception e) {
                logger.error("Validierung fehlgeschlagen – erzeugte DOCX ist unlesbar: {}", e.getMessage());
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
            
            logger.info("DOCX-Export mit Docx4J abgeschlossen: {}", outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            logger.error("Fehler beim DOCX-Export mit Docx4J: {}", e.getMessage(), e);
            throw new IOException("DOCX-Export fehlgeschlagen: " + e.getMessage(), e);
        }
    }
    
    private static void ensureHyphenationEnabled(WordprocessingMLPackage pkg) throws Exception {
        DocumentSettingsPart dsp = pkg.getMainDocumentPart().getDocumentSettingsPart();
        if (dsp == null) {
            dsp = new DocumentSettingsPart();
            pkg.getMainDocumentPart().addTargetPart(dsp);
            dsp.setJaxbElement(new CTSettings());
        }
        CTSettings settings = dsp.getJaxbElement();
        settings.setAutoHyphenation(new BooleanDefaultTrue());
        // HyphenationZone als CTTwipsMeasure
        CTTwipsMeasure zone = new CTTwipsMeasure();
        zone.setVal(BigInteger.valueOf(360));
        settings.setHyphenationZone(zone);
        // ConsecutiveHyphenLimit setzen
        CTSettings.ConsecutiveHyphenLimit limit = new CTSettings.ConsecutiveHyphenLimit();
        limit.setVal(BigInteger.valueOf(2));
        settings.setConsecutiveHyphenLimit(limit);
    }
    
    private static void addTableOfContents(WordprocessingMLPackage pkg, ObjectFactory f) {
        try {
            // TOC-Titel
            P tocTitle = f.createP();
            PPr tocTitlePr = f.createPPr();
            PPrBase.PStyle tocTitleStyle = f.createPPrBasePStyle();
            tocTitleStyle.setVal("Heading1");
            tocTitlePr.setPStyle(tocTitleStyle);
            tocTitle.setPPr(tocTitlePr);
            
            R tocTitleRun = f.createR();
            org.docx4j.wml.Text tocTitleText = f.createText();
            tocTitleText.setValue("Inhaltsverzeichnis");
            tocTitleRun.getContent().add(tocTitleText);
            tocTitle.getContent().add(tocTitleRun);
            
            pkg.getMainDocumentPart().addObject(tocTitle);
            
            // Echtes TOC-Feld mit Docx4J
            P tocField = f.createP();
            
            // TOC-Feld BEGIN
            org.docx4j.wml.FldChar fldChar1 = f.createFldChar();
            fldChar1.setFldCharType(STFldCharType.BEGIN);
            R fldCharRun1 = f.createR();
            fldCharRun1.getContent().add(fldChar1);
            tocField.getContent().add(fldCharRun1);
            
            // TOC-InstrText (muss in separatem Run sein)
            org.docx4j.wml.Text instrText = f.createText();
            instrText.setValue("TOC \\o \"1-3\" \\h \\z \\u");
            R instrRun = f.createR();
            instrRun.getContent().add(instrText);
            tocField.getContent().add(instrRun);
            
            // TOC-Feld SEPARATOR
            org.docx4j.wml.FldChar fldCharSep = f.createFldChar();
            fldCharSep.setFldCharType(STFldCharType.SEPARATE);
            R fldCharRunSep = f.createR();
            fldCharRunSep.getContent().add(fldCharSep);
            tocField.getContent().add(fldCharRunSep);
            
            // TOC-Feld END
            org.docx4j.wml.FldChar fldChar2 = f.createFldChar();
            fldChar2.setFldCharType(STFldCharType.END);
            R fldCharRun2 = f.createR();
            fldCharRun2.getContent().add(fldChar2);
            tocField.getContent().add(fldCharRun2);
            
            pkg.getMainDocumentPart().addObject(tocField);
            
            // Hinweis
            P hint = f.createP();
            R hintRun = f.createR();
            org.docx4j.wml.Text hintText = f.createText();
            hintText.setValue("Hinweis: Rechtsklick auf das Inhaltsverzeichnis und 'Felder aktualisieren' wählen");
            hintRun.getContent().add(hintText);
            hint.getContent().add(hintRun);
            
            pkg.getMainDocumentPart().addObject(hint);
            
            // Seitenumbruch
            addPageBreak(pkg, f);
            
            logger.info("Echtes TOC-Feld erstellt - Word wird es beim Öffnen aktualisieren");
            
        } catch (Exception e) {
            logger.error("Fehler beim Erstellen des TOC: {}", e.getMessage());
        }
    }
    
    private static R createRunWithLang(ObjectFactory f, String text, String langCode) {
        R r = f.createR();
        RPr rpr = f.createRPr();
        CTLanguage lang = new CTLanguage();
        lang.setVal(langCode);
        rpr.setLang(lang);
        r.setRPr(rpr);
        org.docx4j.wml.Text t = f.createText();
        t.setValue(text);
        r.getContent().add(t);
        return r;
    }
    
    private static void addHeading(WordprocessingMLPackage pkg, ObjectFactory f, String text, int level, boolean firstH1Seen, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();
        p.setPPr(ppr);

        // Echte Word-Style für Überschriften
        PPrBase.PStyle pStyle = f.createPPrBasePStyle();
        pStyle.setVal("Heading" + Math.min(level, 9));
        ppr.setPStyle(pStyle);

        // Ausrichtung
        Jc jc = f.createJc();
        if (options != null && options.centerH1 && level == 1) {
            jc.setVal(JcEnumeration.CENTER);
        } else {
            jc.setVal(JcEnumeration.LEFT);
        }
        ppr.setJc(jc);

        // Neue Seite vor H1/H2, falls gewünscht (nicht für die erste)
        if (firstH1Seen) {
            if ((options != null && options.newPageBeforeH1 && level == 1) ||
                (options != null && options.newPageBeforeH2 && level == 2)) {
                BooleanDefaultTrue pbb = new BooleanDefaultTrue();
                ppr.setPageBreakBefore(pbb);
            }
        }

        // Text mit Formatierung
        R r = f.createR();
        RPr rpr = f.createRPr();
        
        // Sprache
        CTLanguage lang = new CTLanguage();
        lang.setVal("de-DE");
        rpr.setLang(lang);
        
        // Schriftart
        if (options != null) {
            RFonts rFonts = f.createRFonts();
            rFonts.setAscii(options.headingFont);
            rFonts.setHAnsi(options.headingFont);
            rFonts.setCs(options.headingFont);
            rpr.setRFonts(rFonts);
            
            // Schriftgröße
            HpsMeasure fontSize = new HpsMeasure();
            int size = 18;
            if (level == 1) size = options.heading1Size;
            else if (level == 2) size = options.heading2Size;
            else if (level == 3) size = options.heading3Size;
            fontSize.setVal(BigInteger.valueOf(size * 2)); // HpsMeasure ist in halben Punkten
            rpr.setSz(fontSize);
            rpr.setSzCs(fontSize);
            
            // Fett
            if (options.boldHeadings) {
                BooleanDefaultTrue bold = new BooleanDefaultTrue();
                rpr.setB(bold);
            }
            
            // Farbe
            if (options.headingColor != null && !options.headingColor.isEmpty()) {
                Color color = f.createColor();
                color.setVal(options.headingColor);
                rpr.setColor(color);
            }
        }
        
        r.setRPr(rpr);
        org.docx4j.wml.Text t = f.createText();
        t.setValue(text);
        r.getContent().add(t);
        p.getContent().add(r);
        
        pkg.getMainDocumentPart().addObject(p);
    }
    
    private static void addParagraph(WordprocessingMLPackage pkg, ObjectFactory f, String text, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();
        p.setPPr(ppr);

        // Blocksatz, falls gewünscht
        if (options != null && options.justifyText) {
            Jc jc = f.createJc();
            jc.setVal(JcEnumeration.BOTH);
            ppr.setJc(jc);
        }
        
        // Einrückung erste Zeile, falls gewünscht
        if (options != null && options.firstLineIndent) {
            PPrBase.Ind ind = f.createPPrBaseInd();
            // Konvertiere cm zu Twips (1 cm = 567 Twips)
            int indentTwips = (int)(options.firstLineIndentSize * 567);
            ind.setFirstLine(BigInteger.valueOf(indentTwips));
            ppr.setInd(ind);
        }

        // Text mit Formatierung
        R r = f.createR();
        RPr rpr = f.createRPr();
        
        // Sprache
        CTLanguage lang = new CTLanguage();
        lang.setVal("de-DE");
        rpr.setLang(lang);
        
        // Schriftart und -größe
        if (options != null) {
            RFonts rFonts = f.createRFonts();
            rFonts.setAscii(options.defaultFont);
            rFonts.setHAnsi(options.defaultFont);
            rFonts.setCs(options.defaultFont);
            rpr.setRFonts(rFonts);
            
            HpsMeasure fontSize = new HpsMeasure();
            fontSize.setVal(BigInteger.valueOf(options.defaultFontSize * 2));
            rpr.setSz(fontSize);
            rpr.setSzCs(fontSize);
        }
        
        r.setRPr(rpr);
        org.docx4j.wml.Text t = f.createText();
        t.setValue(text);
        r.getContent().add(t);
        p.getContent().add(r);
        
        pkg.getMainDocumentPart().addObject(p);
    }
    
    private static void addCodeBlock(WordprocessingMLPackage pkg, ObjectFactory f, String code, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();
        p.setPPr(ppr);

        Jc jc = f.createJc();
        jc.setVal(JcEnumeration.LEFT);
        ppr.setJc(jc);

        String[] lines = code.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            R r = f.createR();
            RPr rpr = f.createRPr();
            
            // Sprache
            CTLanguage lang = new CTLanguage();
            lang.setVal("de-DE");
            rpr.setLang(lang);
            
            // Monospace Schrift
            RFonts rFonts = f.createRFonts();
            String codeFont = options != null ? options.codeFont : "Consolas";
            rFonts.setAscii(codeFont);
            rFonts.setHAnsi(codeFont);
            rFonts.setCs(codeFont);
            rpr.setRFonts(rFonts);
            
            // Kleinere Schriftgröße für Code
            HpsMeasure fontSize = new HpsMeasure();
            fontSize.setVal(BigInteger.valueOf(10 * 2));
            rpr.setSz(fontSize);
            rpr.setSzCs(fontSize);
            
            r.setRPr(rpr);

            org.docx4j.wml.Text t = f.createText();
            t.setSpace("preserve");
            t.setValue(lines[i]);
            r.getContent().add(t);
            
            // Zeilenumbruch zwischen den Codezeilen
            if (i < lines.length - 1) {
                Br br = f.createBr();
                br.setType(STBrType.TEXT_WRAPPING);
                r.getContent().add(br);
            }
            p.getContent().add(r);
        }

        pkg.getMainDocumentPart().addObject(p);
    }
    
    private static void addParagraphWithoutIndent(WordprocessingMLPackage pkg, ObjectFactory f, String text, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();
        p.setPPr(ppr);

        // Blocksatz, falls gewünscht
        if (options != null && options.justifyText) {
            Jc jc = f.createJc();
            jc.setVal(JcEnumeration.BOTH);
            ppr.setJc(jc);
        }
        
        // KEINE Einrückung erste Zeile

        // Text mit Formatierung
        R r = f.createR();
        RPr rpr = f.createRPr();
        
        // Sprache
        CTLanguage lang = new CTLanguage();
        lang.setVal("de-DE");
        rpr.setLang(lang);
        
        // Schriftart und -größe
        if (options != null) {
            RFonts rFonts = f.createRFonts();
            rFonts.setAscii(options.defaultFont);
            rFonts.setHAnsi(options.defaultFont);
            rFonts.setCs(options.defaultFont);
            rpr.setRFonts(rFonts);
            
            HpsMeasure fontSize = new HpsMeasure();
            fontSize.setVal(BigInteger.valueOf(options.defaultFontSize * 2));
            rpr.setSz(fontSize);
            rpr.setSzCs(fontSize);
        }
        
        r.setRPr(rpr);
        org.docx4j.wml.Text t = f.createText();
        t.setValue(text);
        r.getContent().add(t);
        p.getContent().add(r);
        
        pkg.getMainDocumentPart().addObject(p);
    }
    
    private static void addSimpleText(WordprocessingMLPackage pkg, ObjectFactory f, String text, DocxOptions options) {
        P p = f.createP();
        
        // Text mit Formatierung (ohne Absatzformatierung)
        R r = f.createR();
        RPr rpr = f.createRPr();
        
        // Sprache
        CTLanguage lang = new CTLanguage();
        lang.setVal("de-DE");
        rpr.setLang(lang);
        
        // Schriftart und -größe
        if (options != null) {
            RFonts rFonts = f.createRFonts();
            rFonts.setAscii(options.defaultFont);
            rFonts.setHAnsi(options.defaultFont);
            rFonts.setCs(options.defaultFont);
            rpr.setRFonts(rFonts);
            
            HpsMeasure fontSize = new HpsMeasure();
            fontSize.setVal(BigInteger.valueOf(options.defaultFontSize * 2));
            rpr.setSz(fontSize);
            rpr.setSzCs(fontSize);
        }
        
        r.setRPr(rpr);
        org.docx4j.wml.Text t = f.createText();
        t.setValue(text);
        r.getContent().add(t);
        p.getContent().add(r);
        
        pkg.getMainDocumentPart().addObject(p);
    }
    
    private static void addList(WordprocessingMLPackage pkg, ObjectFactory f, Node listNode, DocxOptions options) {
        // TODO: Implementiere Listen-Verarbeitung
        logger.info("Listen-Verarbeitung noch nicht implementiert");
    }
    
    private static void addBlockQuote(WordprocessingMLPackage pkg, ObjectFactory f, Node quoteNode, DocxOptions options) {
        // TODO: Implementiere Blockquote-Verarbeitung
        logger.info("Blockquote-Verarbeitung noch nicht implementiert");
    }
    
    private static void addPageBreak(WordprocessingMLPackage pkg, ObjectFactory f) {
        P p = f.createP();
        R r = f.createR();
        Br br = f.createBr();
        br.setType(STBrType.PAGE);
        r.getContent().add(br);
        p.getContent().add(r);
        pkg.getMainDocumentPart().addObject(p);
    }
    
    private static void addHorizontalRule(WordprocessingMLPackage pkg, ObjectFactory f) {
        // Horizontale Linie mit ThematicBreak (hr-Element)
        P p = f.createP();
        PPr ppr = f.createPPr();
        p.setPPr(ppr);
        
        // Zentrierte Ausrichtung für die Linie
        Jc jc = f.createJc();
        jc.setVal(JcEnumeration.CENTER);
        ppr.setJc(jc);
        
        // Run mit horizontaler Linie
        R r = f.createR();
        RPr rpr = f.createRPr();
        r.setRPr(rpr);
        
        // Text für horizontale Linie (Unicode-Zeichen)
        org.docx4j.wml.Text t = f.createText();
        t.setValue("─".repeat(50)); // 50 horizontale Linien-Zeichen
        r.getContent().add(t);
        
        p.getContent().add(r);
        pkg.getMainDocumentPart().addObject(p);
        
        logger.info("Horizontale Linie hinzugefügt");
    }
    
    private static String extractTextFromParagraph(P paragraph) {
        StringBuilder text = new StringBuilder();
        
        logger.debug("Verarbeite Absatz mit {} Inhalten", paragraph.getContent().size());
        
        for (Object obj : paragraph.getContent()) {
            logger.debug("Absatz-Objekt: {}", obj.getClass().getSimpleName());
            
            if (obj instanceof R) {
                R run = (R) obj;
                logger.debug("Run mit {} Inhalten", run.getContent().size());
                
                // Prüfe Formatierung des Runs
                boolean isBold = false;
                boolean isItalic = false;
                
                if (run.getRPr() != null) {
                    RPr rpr = run.getRPr();
                    if (rpr.getB() != null) {
                        isBold = true;
                        logger.debug("Fett-Formatierung erkannt");
                    }
                    if (rpr.getI() != null) {
                        isItalic = true;
                        logger.debug("Kursiv-Formatierung erkannt");
                    }
                }
                
                // Markdown-Formatierung hinzufügen
                if (isBold && isItalic) {
                    text.append("***");
                } else if (isBold) {
                    text.append("**");
                } else if (isItalic) {
                    text.append("*");
                }
                
                for (Object runObj : run.getContent()) {
                    logger.debug("Run-Objekt: {}", runObj.getClass().getSimpleName());
                    
                    if (runObj instanceof org.docx4j.wml.Text) {
                        org.docx4j.wml.Text t = (org.docx4j.wml.Text) runObj;
                        String value = t.getValue();
                        text.append(value);
                        logger.debug("Extrahierter Text: '{}'", value);
                    } else if (runObj.getClass().getSimpleName().equals("JAXBElement")) {
                        try {
                            Object value = runObj.getClass().getMethod("getValue").invoke(runObj);
                            logger.debug("JAXBElement-Wert: {}", value.getClass().getSimpleName());
                            
                            if (value instanceof org.docx4j.wml.Text) {
                                org.docx4j.wml.Text t = (org.docx4j.wml.Text) value;
                                String textValue = t.getValue();
                                text.append(textValue);
                                logger.debug("Extrahierter Text aus JAXBElement: '{}'", textValue);
                            }
                        } catch (Exception e) {
                            logger.debug("Fehler beim Extrahieren aus JAXBElement: {}", e.getMessage());
                        }
                    } else if (runObj instanceof org.docx4j.wml.Br) {
                        // Zeilenumbruch
                        text.append("\n");
                        logger.debug("Zeilenumbruch hinzugefügt");
                    }
                }
                
                // Markdown-Formatierung schließen
                if (isBold && isItalic) {
                    text.append("***");
                } else if (isBold) {
                    text.append("**");
                } else if (isItalic) {
                    text.append("*");
                }
            }
        }
        
        String result = text.toString();
        logger.debug("Absatz-Inhalt: '{}'", result);
        return result;
    }
} 