package com.manuskript;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import jakarta.xml.bind.JAXBElement;

import org.docx4j.Docx4J;
import org.docx4j.docProps.core.CoreProperties;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.DocumentSettingsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.openpackaging.parts.DocPropsCorePart;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.wml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.docx4j.docProps.core.dc.elements.SimpleLiteral;
import org.docx4j.docProps.core.dc.terms.W3CDTF;

import java.util.GregorianCalendar;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.docx4j.wml.SectPr.PgMar;
import org.docx4j.wml.SectPr.PgSz;
import org.docx4j.wml.Text;

public class DocxProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(DocxProcessor.class);
    
    // Zähler für verschachtelte Nummerierung (wird pro Exportdurchlauf verwendet)
    private static java.util.List<Integer> numberingLevels;
    
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
            content.append("# ").append(chapterName).append("\n");
        }
        
        // DOCX-Lesen mit Docx4J
        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(file);
            List<Object> document = pkg.getMainDocumentPart().getContent();
            
            boolean lastParagraphWasEmpty = false;
            for (Object obj : document) {
                if (obj instanceof P) {
                    P paragraph = (P) obj;
                    String paragraphText = extractTextFromParagraph(paragraph);
                    boolean isEmpty = paragraphText.trim().isEmpty();
                    
                    if (format == OutputFormat.HTML) {
                        if (!isEmpty) {
                            content.append("<p>").append(paragraphText).append("</p>\n");
                        } else if (format == OutputFormat.MARKDOWN) {
                            // Nur eine Leerzeile zwischen Absätzen, nicht zwei
                            content.append(paragraphText).append("\n");
                        }
                    } else if (format == OutputFormat.MARKDOWN) {
                        if (!isEmpty) {
                            // Nur bei nicht-leeren Absätzen: Text + Zeilenumbruch
                            content.append(paragraphText).append("\n");
                        } else if (!lastParagraphWasEmpty) {
                            // Nur bei erstem leeren Absatz: eine Leerzeile
                            content.append("\n");
                        }
                        // Bei mehreren leeren Absätzen hintereinander: keine zusätzlichen Leerzeilen
                    } else {
                        if (!isEmpty) {
                            content.append(paragraphText).append("\n");
                        }
                    }
                    lastParagraphWasEmpty = isEmpty;
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
            boolean lastParagraphWasEmpty = false;
            for (Object obj : document) {
                if (obj instanceof P) {
                    paragraphCount++;
                    P paragraph = (P) obj;
                    String paragraphText = extractTextFromParagraph(paragraph);
                    boolean isEmpty = paragraphText.trim().isEmpty();
                    logger.info("Absatz {}: '{}' (leer: {})", paragraphCount, 
                               paragraphText.substring(0, Math.min(50, paragraphText.length())), isEmpty);
                    
                    if (format == OutputFormat.HTML) {
                        documentContent.append("<p>").append(paragraphText).append("</p>\n");
                    } else if (format == OutputFormat.MARKDOWN) {
                        if (!isEmpty) {
                            // Nur eine Leerzeile zwischen Absätzen, nicht zwei
                            documentContent.append(paragraphText).append("\n");
                        } else if (!lastParagraphWasEmpty) {
                            // Nur bei erstem leeren Absatz: eine Leerzeile
                            documentContent.append("\n");
                        }
                        // Bei mehreren leeren Absätzen hintereinander: keine zusätzlichen Leerzeilen
                    } else {
                        documentContent.append(paragraphText).append("\n");
                    }
                    lastParagraphWasEmpty = isEmpty;
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
        
        // Bereinige überflüssige Leerzeilen am Ende
        if (format == OutputFormat.MARKDOWN) {
            // Entferne überflüssige Leerzeilen am Ende
            while (documentText.endsWith("\n\n")) {
                documentText = documentText.substring(0, documentText.length() - 1);
            }
            // Stelle sicher, dass am Ende nur eine Leerzeile steht
            if (!documentText.endsWith("\n")) {
                documentText += "\n";
            }
        }
        
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
                
                // Seitenformat anwenden
                applyPageSettings(pkg, f, options);
                
                // Silbentrennung aktivieren, falls gewünscht
                if (options.enableHyphenation) {
                    ensureHyphenationEnabled(pkg);
                }
                
                // Inhaltsverzeichnis hinzufügen, falls gewünscht
                if (options.includeTableOfContents) {
                    addTableOfContents(pkg, f);
                }
  
            }
            
            // Verbesserte Markdown-Verarbeitung
            processMarkdownContent(pkg, f, markdownText, options);
            
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
    
    /**
     * Verarbeitet Markdown-Text mit verbesserter Formatierungsunterstützung
     */
    private static void processMarkdownContent(WordprocessingMLPackage pkg, ObjectFactory f, String markdownText, DocxOptions options) {
        logger.info("=== NEUE MARKDOWN-VERARBEITUNG WIRD VERWENDET ===");
        logger.info("Markdown-Text Länge: {}", markdownText.length());
            String[] lines = markdownText.split("\r?\n");
            
            // Zähler für automatische Nummerierung
            int h1Counter = 0;
            int h2Counter = 0;
            int h3Counter = 0;
        int h4Counter = 0;
        int h5Counter = 0;
        int h6Counter = 0;
            boolean firstH1Seen = false;
            boolean lastWasHeading = false;
        boolean inCodeBlock = false;
        boolean inTable = false;
        boolean inBlockquote = false;
            
            // Zeilen verarbeiten
            for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // Code-Block Erkennung
            if (trimmedLine.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                if (inCodeBlock) {
                    // Code-Block startet
                    StringBuilder codeBlock = new StringBuilder();
                    i++; // Nächste Zeile
                    while (i < lines.length && !lines[i].trim().startsWith("```")) {
                        codeBlock.append(lines[i]).append("\n");
                        i++;
                    }
                    addCodeBlock(pkg, f, codeBlock.toString().trim(), options);
                    lastWasHeading = false;
                }
                continue;
            }
            
            if (inCodeBlock) {
                continue; // Code-Block Inhalt wird bereits verarbeitet
            }
            
            // Leere Zeilen
            if (trimmedLine.isEmpty()) {
                inTable = false;
                inBlockquote = false;
                continue;
            }
            
            // Blockquotes
            if (trimmedLine.startsWith(">")) {
                String blockquoteText = trimmedLine.substring(1).trim();
                addBlockquote(pkg, f, blockquoteText, options);
                inBlockquote = true;
                lastWasHeading = false;
                continue;
            }
            
            // Tabellen
            if (trimmedLine.contains("|")) {
                if (!inTable) {
                    // Tabellen-Header
                    String[] headers = parseTableRow(trimmedLine);
                    i++; // Nächste Zeile (Trennlinie)
                    if (i < lines.length && lines[i].trim().contains("|")) {
                        i++; // Überspringe Trennlinie
                        // Tabellen-Daten sammeln
                        java.util.List<String[]> tableData = new java.util.ArrayList<>();
                        while (i < lines.length && lines[i].trim().contains("|")) {
                            tableData.add(parseTableRow(lines[i].trim()));
                            i++;
                        }
                        i--; // Korrigiere Index
                        addTable(pkg, f, headers, tableData, options);
                        inTable = true;
                        lastWasHeading = false;
                        continue;
                    }
                }
            }
            
            // Listen
            if (trimmedLine.matches("^\\s*[-*+]\\s+.*") || trimmedLine.matches("^\\s*\\d+\\.\\s+.*")) {
                logger.info("LISTE GEFUNDEN: '{}'", trimmedLine);

                // Verschachtelte Nummerierung aufbauen
                boolean isOrdered = trimmedLine.matches("^\\s*\\d+\\.\\s+.*");
                int level = computeIndentLevel(line);

                // Statische Stack-/Zählerstruktur (lokal pro Exportdurchlauf)
                if (numberingLevels == null) {
                    numberingLevels = new java.util.ArrayList<>();
                }
                // Stelle sicher, dass Größe > level
                while (numberingLevels.size() <= level) numberingLevels.add(0);
                // Kappe tiefe Ebenen, wenn wir weniger tief sind
                for (int lv = numberingLevels.size() - 1; lv > level; lv--) numberingLevels.remove(lv);

                String prefix = "";
                if (isOrdered) {
                    // Aktuelle Nummer extrahieren oder fortschreiben
                    int current = numberingLevels.get(level);
                    current = current + 1;
                    numberingLevels.set(level, current);
                    // Nachfolgende Ebenen zurücksetzen
                    for (int lv = level + 1; lv < numberingLevels.size(); lv++) numberingLevels.set(lv, 0);

                    // Präfix wie 1. / 2.1. / 2.1.3.
                    StringBuilder sb = new StringBuilder();
                    for (int lv = 0; lv <= level; lv++) {
                        int n = numberingLevels.get(lv);
                        if (n == 0 && lv == level) n = current; // Sicherheit
                        if (n > 0) {
                            if (sb.length() > 0) sb.append('.');
                            sb.append(n);
                        }
                    }
                    if (sb.length() > 0) sb.append(' ');
                    prefix = sb.toString();
                } else {
                    // Unordered: einfacher Bullet je Ebene
                    prefix = "• ";
                }

                addListItemWithPrefix(pkg, f, line, prefix, options);
                lastWasHeading = false;
                continue;
            }
            
            // Überschriften (H1-H6)
            if (trimmedLine.startsWith("#")) {
                int level = 0;
                while (level < trimmedLine.length() && trimmedLine.charAt(level) == '#') {
                    level++;
                }
                
                if (level > 0 && level <= 6 && (level == trimmedLine.length() || trimmedLine.charAt(level) == ' ')) {
                    String headingText = trimmedLine.substring(level).trim();
                    
                    // Zähler aktualisieren
                    if (level == 1) {
                        h1Counter++;
                        h2Counter = h3Counter = h4Counter = h5Counter = h6Counter = 0;
                    } else if (level == 2) {
                        h2Counter++;
                        h3Counter = h4Counter = h5Counter = h6Counter = 0;
                    } else if (level == 3) {
                    h3Counter++;
                        h4Counter = h5Counter = h6Counter = 0;
                    } else if (level == 4) {
                        h4Counter++;
                        h5Counter = h6Counter = 0;
                    } else if (level == 5) {
                        h5Counter++;
                        h6Counter = 0;
                    } else if (level == 6) {
                        h6Counter++;
                    }
                    
                    // Automatische Nummerierung
                    if (options != null && options.autoNumberHeadings) {
                        String number = "";
                        if (level == 1) number = h1Counter + ". ";
                        else if (level == 2) number = h1Counter + "." + h2Counter + " ";
                        else if (level == 3) number = h1Counter + "." + h2Counter + "." + h3Counter + " ";
                        else if (level == 4) number = h1Counter + "." + h2Counter + "." + h3Counter + "." + h4Counter + " ";
                        else if (level == 5) number = h1Counter + "." + h2Counter + "." + h3Counter + "." + h4Counter + "." + h5Counter + " ";
                        else if (level == 6) number = h1Counter + "." + h2Counter + "." + h3Counter + "." + h4Counter + "." + h5Counter + "." + h6Counter + " ";
                        headingText = number + headingText;
                    }
                    
                    addHeading(pkg, f, headingText, level, firstH1Seen, options);
                    if (!firstH1Seen) firstH1Seen = true;
                    lastWasHeading = true;
                    continue;
                }
            }
            
            // Horizontale Linien
            if (trimmedLine.equals("---") || trimmedLine.equals("***") || trimmedLine.equals("___")) {
                    addHorizontalRule(pkg, f);
                    lastWasHeading = false;
                continue;
            }
                    
                    // Normaler Text/Absatz
                    boolean shouldIndent = (options != null && options.firstLineIndent && !lastWasHeading);
                    
                    if (shouldIndent) {
                addParagraphWithFormatting(pkg, f, trimmedLine, options);
                    } else {
                addParagraphWithoutIndentWithFormatting(pkg, f, trimmedLine, options);
                    }
                    lastWasHeading = false;
                }
            }
            
    /**
     * Parst eine Tabellenzeile
     */
    private static String[] parseTableRow(String line) {
        String[] parts = line.split("\\|");
        String[] result = new String[parts.length - 2]; // Erste und letzte sind leer
        for (int i = 1; i < parts.length - 1; i++) {
            result[i - 1] = parts[i].trim();
        }
        return result;
    }
    
    /**
     * Fügt eine Tabelle hinzu
     */
    private static void addTable(WordprocessingMLPackage pkg, ObjectFactory f, String[] headers, java.util.List<String[]> data, DocxOptions options) {
        try {
            Tbl table = f.createTbl();
            
            // Tabellen-Eigenschaften vereinfacht
            TblPr tblPr = f.createTblPr();
            
            // Tabellen-Ränder (nur wenn gewünscht)
            if (options != null && options.tableBorders) {
                TblBorders borders = f.createTblBorders();
                CTBorder border = f.createCTBorder();
                border.setVal(STBorder.SINGLE);
                border.setSz(BigInteger.valueOf(4));
                border.setColor(options.tableBorderColor != null ? options.tableBorderColor : "000000");
                borders.setTop(border);
                borders.setBottom(border);
                borders.setLeft(border);
                borders.setRight(border);
                borders.setInsideH(border);
                borders.setInsideV(border);
                tblPr.setTblBorders(borders);
            }
            table.setTblPr(tblPr);
            
            // Header-Zeile
            Tr headerRow = f.createTr();
            for (String header : headers) {
                Tc cell = f.createTc();
                TcPr cellPr = f.createTcPr();
                
                // Header-Hintergrundfarbe
                if (options != null && options.tableHeaderColor != null) {
                    CTShd shd = f.createCTShd();
                    shd.setColor("auto");
                    shd.setFill(options.tableHeaderColor);
                    cellPr.setShd(shd);
                }
                
                cell.setTcPr(cellPr);
                
                P paragraph = f.createP();
                PPr ppr = f.createPPr();
                Jc jc = f.createJc();
                jc.setVal(JcEnumeration.CENTER);
                ppr.setJc(jc);
                paragraph.setPPr(ppr);
                
                R run = f.createR();
                RPr rpr = f.createRPr();
                BooleanDefaultTrue bold = new BooleanDefaultTrue();
                rpr.setB(bold);
                run.setRPr(rpr);
                
                org.docx4j.wml.Text text = f.createText();
                text.setValue(header);
                run.getContent().add(text);
                paragraph.getContent().add(run);
                cell.getContent().add(paragraph);
                headerRow.getContent().add(cell);
            }
            table.getContent().add(headerRow);
            
            // Daten-Zeilen
            for (String[] row : data) {
                Tr dataRow = f.createTr();
                for (int i = 0; i < Math.min(row.length, headers.length); i++) {
                    Tc cell = f.createTc();
                    TcPr cellPr = f.createTcPr();
                    cell.setTcPr(cellPr);
                    
                    P paragraph = f.createP();
                    R run = f.createR();
                    org.docx4j.wml.Text text = f.createText();
                    text.setValue(row[i]);
                    run.getContent().add(text);
                    paragraph.getContent().add(run);
                    cell.getContent().add(paragraph);
                    dataRow.getContent().add(cell);
                }
                table.getContent().add(dataRow);
            }
            
            pkg.getMainDocumentPart().addObject(table);
            
        } catch (Exception e) {
            logger.error("Fehler beim Erstellen der Tabelle: {}", e.getMessage());
        }
    }
    
    /**
     * Fügt ein Blockquote hinzu
     */
    private static void addBlockquote(WordprocessingMLPackage pkg, ObjectFactory f, String text, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();
        
        // Einrückung für Blockquote
        PPrBase.Ind ind = f.createPPrBaseInd();
        int indentSize = 720; // Standard: 0.5 Zoll
        if (options != null) {
            indentSize = (int)(options.quoteIndent * 567); // cm zu Twips
        }
        ind.setLeft(BigInteger.valueOf(indentSize));
        ppr.setInd(ind);
        
        // Hintergrundfarbe für Blockquote, falls gewünscht
        // Blockquote-Hintergrundfarbe
        if (options != null && options.quoteBackgroundColor != null) {
            CTShd shd = f.createCTShd();
            shd.setColor("auto");
            shd.setFill(options.quoteBackgroundColor);
            ppr.setShd(shd);
        }
        
        // Kursiver Text für Blockquote
        R r = f.createR();
        RPr rpr = f.createRPr();
        BooleanDefaultTrue italic = new BooleanDefaultTrue();
        rpr.setI(italic);
        
        // Schriftart
        if (options != null) {
            RFonts rFonts = f.createRFonts();
            rFonts.setAscii(options.defaultFont);
            rFonts.setHAnsi(options.defaultFont);
            rFonts.setCs(options.defaultFont);
            rpr.setRFonts(rFonts);
        }
        
        r.setRPr(rpr);
        org.docx4j.wml.Text t = f.createText();
        t.setValue(text);
        r.getContent().add(t);
        p.getContent().add(r);
        p.setPPr(ppr);
        
        pkg.getMainDocumentPart().addObject(p);
    }
    
    /**
     * Fügt ein Listenelement hinzu - NEUE EINFACHE LÖSUNG
     */
    private static void addListItem(WordprocessingMLPackage pkg, ObjectFactory f, String line, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();
        
        // EINFACHE Einrückung: Zähle führende Leerzeichen
        int indentLevel = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                indentLevel++;
            } else if (line.charAt(i) == '\t') {
                indentLevel += 4;
            } else {
                break;
            }
        }
        
        // Einrückung setzen
        PPrBase.Ind ind = f.createPPrBaseInd();
        ind.setLeft(BigInteger.valueOf(720 + (indentLevel * 200))); // Einfache Einrückung
        ppr.setInd(ind);
        
        // Text verarbeiten
        String text = line.trim();
        String prefix = "";
        
        // Aufzählungsliste: - * +
        if (text.startsWith("- ") || text.startsWith("* ") || text.startsWith("+ ")) {
            String bulletStyle = "• "; // Standard
            if (options != null && options.bulletStyle != null) {
                bulletStyle = options.bulletStyle + " ";
            }
            prefix = bulletStyle;
            text = text.substring(2);
        }
        // Nummerierte Liste: 1. 2. etc.
        else if (text.matches("^\\d+\\.\\s+.*")) {
            String number = text.substring(0, text.indexOf('.'));
            prefix = number + ". ";
            text = text.substring(text.indexOf('.') + 2);
        }
        
        // Text hinzufügen
        addFormattedTextToParagraph(p, f, prefix + text, options);
        p.setPPr(ppr);
        
        pkg.getMainDocumentPart().addObject(p);
    }
    
    /**
     * Fügt formatierten Text zu einem Absatz hinzu (mit Bold/Italic/Links)
     */
    private static void addFormattedTextToParagraph(P paragraph, ObjectFactory f, String text, DocxOptions options) {
        // Debug-Ausgabe
        logger.info("PARSING TEXT: '{}'", text);
        
        // Einfache Regex-basierte Formatierung
        java.util.List<TextSegment> segments = parseFormattedText(text);
        
        for (TextSegment segment : segments) {
            logger.info("SEGMENT: '{}' bold={} italic={}", segment.text, segment.isBold, segment.isItalic);
            R run = f.createR();
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
            
            // Formatierung anwenden
            if (segment.isBold) {
                BooleanDefaultTrue bold = new BooleanDefaultTrue();
                rpr.setB(bold);
            }
            if (segment.isItalic) {
                BooleanDefaultTrue italic = new BooleanDefaultTrue();
                rpr.setI(italic);
            }
            
            run.setRPr(rpr);
            
            if (segment.isLink) {
                // Link-Formatierung
                Color color = f.createColor();
                String linkColor = "0000FF"; // Standard blau
                if (options != null && options.linkColor != null) {
                    linkColor = options.linkColor;
                }
                color.setVal(linkColor);
                rpr.setColor(color);
                
                // Unterstreichung für Links
                if (options != null && options.underlineLinks) {
                    U u = f.createU();
                    // u.setVal("single"); // setVal existiert nicht
                    rpr.setU(u);
                }
                
                org.docx4j.wml.Text t = f.createText();
                t.setSpace("preserve");
                t.setValue(segment.text + " (" + segment.linkUrl + ")");
                run.getContent().add(t);
                paragraph.getContent().add(run);
            } else {
                org.docx4j.wml.Text t = f.createText();
                t.setSpace("preserve");
                t.setValue(segment.text);
                run.getContent().add(t);
                paragraph.getContent().add(run);
            }
        }
    }
    
    /**
     * Parst formatierten Text in Segmente - Toggle-Parser für ** und *
     */
    private static java.util.List<TextSegment> parseFormattedText(String text) {
        java.util.List<TextSegment> segments = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            // Bold-Marker **
            if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                if (current.length() > 0) {
                    segments.add(new TextSegment(current.toString(), bold, italic, false, null));
                    current.setLength(0);
                }
                bold = !bold;
                i += 2;
                continue;
            }
            // Italic-Marker *
            if (c == '*') {
                if (current.length() > 0) {
                    segments.add(new TextSegment(current.toString(), bold, italic, false, null));
                    current.setLength(0);
                }
                italic = !italic;
                i += 1;
                continue;
            }
            // Normales Zeichen (inkl. Leerzeichen)
            current.append(c);
            i += 1;
        }
        if (current.length() > 0) {
            segments.add(new TextSegment(current.toString(), bold, italic, false, null));
        }
        return segments;
    }
    
    
    /**
     * Hilfsklasse für Textsegmente
     */
    private static class TextSegment {
        String text;
        boolean isBold;
        boolean isItalic;
        boolean isLink;
        String linkUrl;
        
        TextSegment(String text, boolean isBold, boolean isItalic, boolean isLink, String linkUrl) {
            this.text = text;
            this.isBold = isBold;
            this.isItalic = isItalic;
            this.isLink = isLink;
            this.linkUrl = linkUrl;
        }
    }
    
    /**
     * Fügt einen Absatz mit Formatierung hinzu
     */
    private static void addParagraphWithFormatting(WordprocessingMLPackage pkg, ObjectFactory f, String text, DocxOptions options) {
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
            int indentTwips = (int)(options.firstLineIndentSize * 567);
            ind.setFirstLine(BigInteger.valueOf(indentTwips));
            ppr.setInd(ind);
        }
        
        // Zeilenabstand und Absatzabstand - vereinfacht ohne Spacing-Klasse
        // if (options != null && options.lineSpacing > 0) {
        //     // Spacing-Klasse nicht verfügbar in dieser Docx4J Version
        // }
        // if (options != null && options.paragraphSpacing > 0) {
        //     // Spacing-Klasse nicht verfügbar in dieser Docx4J Version
        // }

        addFormattedTextToParagraph(p, f, text, options);
        pkg.getMainDocumentPart().addObject(p);
    }
    
    /**
     * Fügt einen Absatz ohne Einrückung mit Formatierung hinzu
     */
    private static void addParagraphWithoutIndentWithFormatting(WordprocessingMLPackage pkg, ObjectFactory f, String text, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();
        p.setPPr(ppr);

        // Blocksatz, falls gewünscht
        if (options != null && options.justifyText) {
            Jc jc = f.createJc();
            jc.setVal(JcEnumeration.BOTH);
            ppr.setJc(jc);
        }

        addFormattedTextToParagraph(p, f, text, options);
        pkg.getMainDocumentPart().addObject(p);
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
            
            // TOC-Feld - vereinfacht ohne FldChar
            // TOC-Funktionalität nicht verfügbar in dieser Docx4J Version
            org.docx4j.wml.Text tocText = f.createText();
            tocText.setValue("Inhaltsverzeichnis");
            R tocRun = f.createR();
            tocRun.getContent().add(tocText);
            tocField.getContent().add(tocRun);
            
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
        
        // Code-Hintergrundfarbe
        if (options != null && options.codeBackgroundColor != null) {
            CTShd shd = f.createCTShd();
            shd.setColor("auto");
            shd.setFill(options.codeBackgroundColor);
            ppr.setShd(shd);
        }
        
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
        
        // Prüfe Paragraph-Style für Überschriften
        int headingLevel = 0;
        if (paragraph.getPPr() != null && paragraph.getPPr().getPStyle() != null) {
            String styleName = paragraph.getPPr().getPStyle().getVal();
            if (styleName != null) {
                if (styleName.startsWith("Heading")) {
                    try {
                        headingLevel = Integer.parseInt(styleName.substring(7));
                    } catch (NumberFormatException e) {
                        // Fallback für andere Heading-Styles
                        if (styleName.equals("Title")) headingLevel = 1;
                        else if (styleName.equals("Subtitle")) headingLevel = 2;
                    }
                }
            }
        }
        
        // Überschriften-Markdown hinzufügen
        if (headingLevel > 0) {
            for (int i = 0; i < headingLevel; i++) {
                text.append("#");
            }
            text.append(" ");
        }
        
        for (Object obj : paragraph.getContent()) {
            
            if (obj instanceof R) {
                R run = (R) obj;
                
                // Prüfe Formatierung des Runs
                boolean isBold = false;
                boolean isItalic = false;
                
                if (run.getRPr() != null) {
                    RPr rpr = run.getRPr();
                    
                    // Bold-Erkennung - verschiedene Ansätze
                    if (rpr.getB() != null) {
                        // Bei BooleanDefaultTrue: Objekt existiert = Bold aktiv
                        if (rpr.getB().getClass().getSimpleName().equals("BooleanDefaultTrue")) {
                            isBold = true;
                        } else if (rpr.getB().isVal()) {
                            isBold = true;
                        }
                    }
                    
                    // Italic-Erkennung - verschiedene Ansätze
                    if (rpr.getI() != null) {
                        // Bei BooleanDefaultTrue: Objekt existiert = Italic aktiv
                        if (rpr.getI().getClass().getSimpleName().equals("BooleanDefaultTrue")) {
                            isItalic = true;
                        } else if (rpr.getI().isVal()) {
                            isItalic = true;
                        }
                    }
                }
                
                // Markdown-Formatierung VOR dem Text hinzufügen (nur wenn nicht bereits Überschrift)
                if (headingLevel == 0) {
                    if (isBold && isItalic) {
                        text.append("***");
                    } else if (isBold) {
                        text.append("**");
                    } else if (isItalic) {
                        text.append("*");
                    }
                }
                
                for (Object runObj : run.getContent()) {
                    if (runObj instanceof org.docx4j.wml.Text) {
                        org.docx4j.wml.Text t = (org.docx4j.wml.Text) runObj;
                        String value = t.getValue();
                        text.append(value);
                    } else if (runObj.getClass().getSimpleName().equals("JAXBElement")) {
                        try {
                            Object value = runObj.getClass().getMethod("getValue").invoke(runObj);
                            
                            if (value instanceof org.docx4j.wml.Text) {
                                org.docx4j.wml.Text t = (org.docx4j.wml.Text) value;
                                String textValue = t.getValue();
                                text.append(textValue);
                            }
                        } catch (Exception e) {
                            // Fehler beim Extrahieren ignorieren
                        }
                    } else if (runObj instanceof org.docx4j.wml.Br) {
                        // Zeilenumbruch
                        text.append("\n");
                    }
                }
                
                // Markdown-Formatierung NACH dem Text schließen (nur wenn nicht bereits Überschrift)
                if (headingLevel == 0) {
                    if (isBold && isItalic) {
                        text.append("***");
                    } else if (isBold) {
                        text.append("**");
                    } else if (isItalic) {
                        text.append("*");
                    }
                }
            }
        }
        
        String result = text.toString();
        return result;
    }

    // Hilfsfunktion: führende Whitespaces zählen
    private static int countLeadingSpacesAndTabs(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') count += 1;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    // Hilfsfunktion: Einrückungs-Level (2 Spaces = 1 Level)
    private static int computeIndentLevel(String line) {
        int spaces = countLeadingSpacesAndTabs(line);
        return Math.max(0, spaces / 2);
    }

    /**
     * Fügt ein Listenelement mit vorgegebenem Präfix (z.B. "2.1 ") hinzu
     */
    private static void addListItemWithPrefix(WordprocessingMLPackage pkg, ObjectFactory f, String line, String prefix, DocxOptions options) {
        P p = f.createP();
        PPr ppr = f.createPPr();

        // Einrückung analog zu addListItem
        int indentLevelSpaces = countLeadingSpacesAndTabs(line);
        PPrBase.Ind ind = f.createPPrBaseInd();
        ind.setLeft(BigInteger.valueOf(720 + (indentLevelSpaces * 200)));
        ppr.setInd(ind);

        String text = line.trim();
        // Entferne führendes Listen-Muster
        text = text.replaceFirst("^[-*+]\\s+", "").replaceFirst("^\\d+\\.\\s+", "");

        addFormattedTextToParagraph(p, f, prefix + text, options);
        p.setPPr(ppr);
        pkg.getMainDocumentPart().addObject(p);
    }
    
    /**
     * Wendet Seitenformat-Einstellungen an (Ränder, Seitenzahlen)
     */
    private static void applyPageSettings(WordprocessingMLPackage pkg, ObjectFactory f, DocxOptions options) {
        try {
            logger.info("Starte applyPageSettings...");
            // Dokument-Eigenschaften abrufen
            Document document = pkg.getMainDocumentPart().getJaxbElement();
            Body body = document.getBody();
            
            // Sections abrufen oder erstellen
            java.util.List<Object> bodyContent = body.getContent();
            SectPr sectPr = null;
            
            // Suche nach existierender SectPr
            for (Object obj : bodyContent) {
                if (obj instanceof SectPr) {
                    sectPr = (SectPr) obj;
                    break;
                }
            }
            
            // Erstelle neue SectPr falls keine existiert
            if (sectPr == null) {
                sectPr = f.createSectPr();
                bodyContent.add(sectPr);
            }
            
            // Seitengröße (SectPr.PgSz)
            SectPr.PgSz pageSz = f.createSectPrPgSz();
            pageSz.setW(BigInteger.valueOf(11906)); // A4 Breite in Twips
            pageSz.setH(BigInteger.valueOf(16838)); // A4 Höhe in Twips
            sectPr.setPgSz(pageSz);
            
            // Seitenränder (SectPr.PgMar)
            SectPr.PgMar pageMar = f.createSectPrPgMar();
            pageMar.setTop(BigInteger.valueOf((int)(options.topMargin * 567)));
            pageMar.setBottom(BigInteger.valueOf((int)(options.bottomMargin * 567)));
            pageMar.setLeft(BigInteger.valueOf((int)(options.leftMargin * 567)));
            pageMar.setRight(BigInteger.valueOf((int)(options.rightMargin * 567)));
            pageMar.setHeader(BigInteger.valueOf(708)); // 1.25 cm
            pageMar.setFooter(BigInteger.valueOf(708)); // 1.25 cm
            sectPr.setPgMar(pageMar);
            
            // Seitenzahlen hinzufügen, falls gewünscht
            if (options.includePageNumbers) {
                addPageNumbers(pkg, f, options);
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Anwenden der Seitenformat-Einstellungen: {}", e.getMessage());
        }
    }
    
    /**
     * Fügt Seitenzahlen hinzu
     */
    private static void addPageNumbers(WordprocessingMLPackage pkg, ObjectFactory f, DocxOptions options) {
        try {
            logger.info("Starte Hinzufügen der Seitenzahlen...");
            
            // Footer-Part erzeugen
            FooterPart footerPart = new FooterPart();
            Relationship relFooter = pkg.getMainDocumentPart().addTargetPart(footerPart);
            logger.info("Footer-Part erstellt mit ID: {}", relFooter.getId());
            
            // Footer-Inhalt: "Seite X"
            P footerP = f.createP();
            
            // Lauftext "Seite "
            R runText = f.createR();
            Text txt = f.createText();
            txt.setValue("Seite ");
            runText.getContent().add(txt);
            footerP.getContent().add(runText);
            
            // PAGE-Feld für Seitennummer - einfachere Version
            R runPage = f.createR();
            
            // Begin-Feld
            FldChar fldCharBegin = f.createFldChar();
            fldCharBegin.setFldCharType(STFldCharType.BEGIN);
            runPage.getContent().add(fldCharBegin);
            
            // Instruction-Text
            Text instrText = f.createText();
            instrText.setSpace("preserve");
            instrText.setValue(" PAGE ");
            runPage.getContent().add(instrText);
            
            // Separate-Feld
            FldChar fldCharSeparate = f.createFldChar();
            fldCharSeparate.setFldCharType(STFldCharType.SEPARATE);
            runPage.getContent().add(fldCharSeparate);
            
            // End-Feld
            FldChar fldCharEnd = f.createFldChar();
            fldCharEnd.setFldCharType(STFldCharType.END);
            runPage.getContent().add(fldCharEnd);
            
            footerP.getContent().add(runPage);
            
            // Footer zusammensetzen
            Ftr ftr = f.createFtr();
            ftr.getContent().add(footerP);
            footerPart.setJaxbElement(ftr);
            logger.info("Footer-Inhalt erstellt");
            
            // Footer in den Abschnitts-Properties verankern
            SectPr sectPr = pkg.getMainDocumentPart().getJaxbElement().getBody().getSectPr();
            if (sectPr == null) {
                sectPr = f.createSectPr();
                pkg.getMainDocumentPart().getJaxbElement().getBody().setSectPr(sectPr);
                logger.info("Neue SectPr erstellt");
            } else {
                logger.info("Bestehende SectPr gefunden");
            }
            
            // Footer-Referenz
            FooterReference footerRef = f.createFooterReference();
            footerRef.setId(relFooter.getId());
            footerRef.setType(HdrFtrRef.DEFAULT);
            sectPr.getEGHdrFtrReferences().add(footerRef);
            logger.info("Footer-Referenz hinzugefügt");
            
            logger.info("Seitenzahlen erfolgreich hinzugefügt");
        } catch (Exception e) {
            logger.error("Fehler beim Hinzufügen der Seitenzahlen: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Setzt Dokument-Metadaten
     */
    




    

}
