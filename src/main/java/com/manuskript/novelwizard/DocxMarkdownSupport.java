package com.manuskript.novelwizard;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Text;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Einfaches Markdown → DOCX (Ueberschriften, **fett**, *kursiv*, Absaetze). */
final class DocxMarkdownSupport {
    private static final Pattern HEADING = Pattern.compile("^(#{1,4})\\s+(.+)$");
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|_(.+?)_|([^*_]+)");

    private DocxMarkdownSupport() {
    }

    static void appendMarkdownBlock(WordprocessingMLPackage pkg, ObjectFactory factory, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return;
        }
        for (String line : markdown.split("\\R")) {
            appendMarkdownLine(pkg, factory, line);
        }
    }

    private static void appendMarkdownLine(WordprocessingMLPackage pkg, ObjectFactory factory, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            addEmptyParagraph(pkg, factory);
            return;
        }
        if ("---".equals(trimmed)) {
            addEmptyParagraph(pkg, factory);
            return;
        }
        Matcher heading = HEADING.matcher(trimmed);
        if (heading.matches()) {
            int level = heading.group(1).length();
            String size = switch (level) {
                case 1 -> "26";
                case 2 -> "22";
                case 3 -> "18";
                default -> "14";
            };
            addStyledParagraph(pkg, factory, heading.group(2).trim(), true, false, size);
            return;
        }
        addInlineMarkdownParagraph(pkg, factory, trimmed, false, false, "12");
    }

    static void appendPlainParagraph(WordprocessingMLPackage pkg, ObjectFactory factory, String text, boolean bold, String fontSize) {
        if (text == null || text.isBlank()) {
            return;
        }
        addInlineMarkdownParagraph(pkg, factory, text.trim(), bold, false, fontSize);
    }

    private static void addInlineMarkdownParagraph(WordprocessingMLPackage pkg, ObjectFactory factory,
                                                   String text, boolean defaultBold, boolean defaultItalic, String fontSize) {
        P paragraph = factory.createP();
        Matcher matcher = INLINE.matcher(text);
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            if (matcher.group(1) != null) {
                paragraph.getContent().add(run(factory, matcher.group(1), true, defaultItalic, fontSize));
            } else if (matcher.group(2) != null) {
                paragraph.getContent().add(run(factory, matcher.group(2), defaultBold, true, fontSize));
            } else if (matcher.group(3) != null) {
                paragraph.getContent().add(run(factory, matcher.group(3), defaultBold, true, fontSize));
            } else if (matcher.group(4) != null) {
                paragraph.getContent().add(run(factory, matcher.group(4), defaultBold, defaultItalic, fontSize));
            }
        }
        if (!matched) {
            paragraph.getContent().add(run(factory, text, defaultBold, defaultItalic, fontSize));
        }
        pkg.getMainDocumentPart().addObject(paragraph);
    }

    private static void addStyledParagraph(WordprocessingMLPackage pkg, ObjectFactory factory,
                                           String text, boolean bold, boolean italic, String fontSize) {
        P paragraph = factory.createP();
        paragraph.getContent().add(run(factory, text, bold, italic, fontSize));
        pkg.getMainDocumentPart().addObject(paragraph);
    }

    private static void addEmptyParagraph(WordprocessingMLPackage pkg, ObjectFactory factory) {
        addStyledParagraph(pkg, factory, "", false, false, "12");
    }

    private static R run(ObjectFactory factory, String text, boolean bold, boolean italic, String fontSize) {
        R run = factory.createR();
        RPr rPr = factory.createRPr();
        if (bold) {
            BooleanDefaultTrue b = factory.createBooleanDefaultTrue();
            b.setVal(true);
            rPr.setB(b);
        }
        if (italic) {
            BooleanDefaultTrue i = factory.createBooleanDefaultTrue();
            i.setVal(true);
            rPr.setI(i);
        }
        org.docx4j.wml.HpsMeasure size = factory.createHpsMeasure();
        size.setVal(new BigInteger(fontSize));
        rPr.setSz(size);
        run.setRPr(rPr);
        Text t = factory.createText();
        t.setValue(text);
        run.getContent().add(t);
        return run;
    }

    /** Haelt Absatzgrenzen (Leerzeilen) bei Fliesstext. */
    static List<String> splitParagraphBlocks(String markdown) {
        List<String> blocks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return blocks;
        }
        StringBuilder current = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.trim().isEmpty()) {
                if (current.length() > 0) {
                    blocks.add(current.toString().trim());
                    current.setLength(0);
                }
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (current.length() > 0) {
            blocks.add(current.toString().trim());
        }
        return blocks;
    }
}
