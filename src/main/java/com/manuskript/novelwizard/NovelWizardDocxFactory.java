package com.manuskript.novelwizard;

import com.google.gson.Gson;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NovelWizardDocxFactory {
    private static final Pattern HEADING = Pattern.compile("(?m)^##\\s+(.+)$");

    private NovelWizardDocxFactory() {
    }

    public static List<Path> createChapterDocxFiles(Path projectDirectory, String chapterMarkdown) throws Exception {
        List<String> titles = extractChapterTitles(chapterMarkdown);
        List<Path> created = new ArrayList<>();
        int index = 1;
        for (String title : titles) {
            Path target = projectDirectory.resolve(String.format("kapitel-%02d-%s.docx", index, slug(title)));
            if (!Files.exists(target)) {
                createDocx(target, title);
            }
            created.add(target);
            index++;
        }
        if (!created.isEmpty()) {
            saveSelection(projectDirectory, created);
        }
        return created;
    }

    private static List<String> extractChapterTitles(String markdown) {
        List<String> titles = new ArrayList<>();
        Matcher matcher = HEADING.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            if (!title.isBlank() && !title.toLowerCase().contains("roman-assistent")) {
                titles.add(title);
            }
        }
        if (titles.isEmpty()) {
            titles.add("Kapitel 1");
        }
        return titles;
    }

    private static void createDocx(Path target, String title) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ObjectFactory factory = new ObjectFactory();
        addParagraph(pkg, factory, title, true, "28");
        addParagraph(pkg, factory, "", false, "12");
        addParagraph(pkg, factory, "Kapiteltext hier schreiben.", false, "12");
        Docx4J.save(pkg, target.toFile());
    }

    private static void addParagraph(WordprocessingMLPackage pkg, ObjectFactory factory, String text, boolean bold, String fontSize) {
        P paragraph = factory.createP();
        R run = factory.createR();
        RPr rPr = factory.createRPr();
        if (bold) {
            BooleanDefaultTrue b = factory.createBooleanDefaultTrue();
            b.setVal(true);
            rPr.setB(b);
        }
        org.docx4j.wml.HpsMeasure size = factory.createHpsMeasure();
        size.setVal(new java.math.BigInteger(fontSize));
        rPr.setSz(size);
        run.setRPr(rPr);
        Text t = factory.createText();
        t.setValue(text);
        run.getContent().add(t);
        paragraph.getContent().add(run);
        pkg.getMainDocumentPart().addObject(paragraph);
    }

    private static void saveSelection(Path projectDirectory, List<Path> docxFiles) throws Exception {
        Path dataDir = projectDirectory.resolve("data");
        Files.createDirectories(dataDir);
        List<String> names = docxFiles.stream()
                .map(path -> path.getFileName().toString())
                .toList();
        Files.writeString(dataDir.resolve(".manuskript_selection.json"),
                new Gson().toJson(names), StandardCharsets.UTF_8);
    }

    private static String slug(String value) {
        String slug = value == null ? "kapitel" : value.toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "kapitel" : slug;
    }
}
