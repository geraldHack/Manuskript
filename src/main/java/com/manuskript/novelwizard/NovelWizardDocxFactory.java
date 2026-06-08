package com.manuskript.novelwizard;

import com.google.gson.Gson;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ObjectFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NovelWizardDocxFactory {
    private static final Pattern INDIVIDUAL_CHAPTER = Pattern.compile(
            "^#{4}\\s+Kapitel\\s+(\\d+)\\s*[:.]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern GROUP_HEADING = Pattern.compile("^#{3}\\s+(.+)$");
    private static final Pattern ACT_HEADING = Pattern.compile("^#{2}\\s+(.+)$");
    private static final Pattern TOP_HEADING = Pattern.compile("^#\\s+(.+)$");

    private NovelWizardDocxFactory() {
    }

    public static NovelWizardDocxResult createChapterDocxFiles(Path projectDirectory, String chapterMarkdown) throws Exception {
        List<ChapterEntry> chapters = extractChapters(chapterMarkdown);
        List<String> titles = chapters.stream().map(ChapterEntry::title).toList();
        List<Path> paths = new ArrayList<>();
        int created = 0;
        int updated = 0;
        for (ChapterEntry chapter : chapters) {
            Path target = projectDirectory.resolve(chapterDocxFileName(chapter));
            boolean existed = Files.exists(target);
            writeDocx(target, chapter);
            if (existed) {
                updated++;
            } else {
                created++;
            }
            paths.add(target);
        }
        if (!paths.isEmpty()) {
            saveSelection(projectDirectory, paths);
        }
        return new NovelWizardDocxResult(titles, paths, created, updated);
    }

    static List<ChapterEntry> extractChapters(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        ChapterOutlineParser parser = new ChapterOutlineParser();
        for (String line : markdown.split("\\R")) {
            parser.accept(line);
        }
        parser.finish();
        return parser.chapters().stream()
                .sorted(Comparator.comparingInt(ChapterEntry::number))
                .toList();
    }

    static List<String> extractChapterTitles(String markdown) {
        return extractChapters(markdown).stream().map(ChapterEntry::title).toList();
    }

    /** Parst chapter.txt-Hierarchie: Uebersicht → Akt → Abschnitt → Einzelkapitel. */
    private static final class ChapterOutlineParser {
        private final Map<Integer, ChapterEntry> chapters = new LinkedHashMap<>();

        private String overview = "";
        private String actHeading = "";
        private String actBody = "";
        private String groupHeading = "";
        private String groupBody = "";
        private StringBuilder buffer = new StringBuilder();

        private Integer chapterNum;
        private String chapterTitle;
        private StringBuilder chapterSummary = new StringBuilder();

        private enum Mode { OVERVIEW, ACT, GROUP, CHAPTER }

        private Mode mode = Mode.OVERVIEW;

        void accept(String rawLine) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || "---".equals(trimmed)) {
                appendBuffer("");
                return;
            }

            Matcher top = TOP_HEADING.matcher(trimmed);
            if (top.matches() && isSkippedHeading(top.group(1).trim())) {
                return;
            }

            Matcher chapter = INDIVIDUAL_CHAPTER.matcher(trimmed);
            if (chapter.matches()) {
                commitBuffer();
                flushChapter();
                mode = Mode.CHAPTER;
                chapterNum = Integer.parseInt(chapter.group(1));
                chapterTitle = "Kapitel " + chapterNum + ": " + chapter.group(2).trim();
                chapterSummary = new StringBuilder();
                return;
            }

            Matcher act = ACT_HEADING.matcher(trimmed);
            if (act.matches()) {
                flushChapter();
                commitBuffer();
                String heading = act.group(1).trim();
                if (isOverviewHeading(heading)) {
                    mode = Mode.OVERVIEW;
                    buffer = new StringBuilder();
                } else if (heading.toUpperCase().startsWith("AKT ") || heading.toUpperCase().startsWith("AKT:")) {
                    actHeading = heading;
                    actBody = "";
                    groupHeading = "";
                    groupBody = "";
                    mode = Mode.ACT;
                    buffer = new StringBuilder();
                } else if (!isSkippedHeading(heading)) {
                    mode = Mode.OVERVIEW;
                    appendBuffer("## " + heading);
                }
                return;
            }

            Matcher group = GROUP_HEADING.matcher(trimmed);
            if (group.matches()) {
                flushChapter();
                commitBuffer();
                groupHeading = group.group(1).trim();
                groupBody = "";
                mode = Mode.GROUP;
                buffer = new StringBuilder();
                return;
            }

            if (mode == Mode.CHAPTER && chapterNum != null) {
                appendTo(chapterSummary, trimmed);
            } else {
                appendBuffer(trimmed);
            }
        }

        void finish() {
            flushChapter();
            commitBuffer();
        }

        private void commitBuffer() {
            String text = buffer.toString().trim();
            buffer = new StringBuilder();
            if (text.isEmpty()) {
                return;
            }
            switch (mode) {
                case OVERVIEW -> overview = mergeMarkdown(overview, text);
                case ACT -> actBody = mergeMarkdown(actBody, text);
                case GROUP -> groupBody = mergeMarkdown(groupBody, text);
                default -> { }
            }
        }

        private void flushChapter() {
            if (chapterNum == null || chapterTitle == null) {
                return;
            }
            chapters.put(chapterNum, new ChapterEntry(
                    chapterNum,
                    chapterTitle,
                    chapterSummary.toString().trim(),
                    overview.trim(),
                    actHeading,
                    actBody.trim(),
                    groupHeading,
                    groupBody.trim()));
            chapterNum = null;
            chapterTitle = null;
            chapterSummary = new StringBuilder();
            mode = Mode.GROUP;
        }

        private void appendBuffer(String line) {
            appendTo(buffer, line);
        }

        private static void appendTo(StringBuilder sb, String line) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }

        private static String mergeMarkdown(String existing, String addition) {
            if (existing == null || existing.isBlank()) {
                return addition;
            }
            if (addition == null || addition.isBlank()) {
                return existing;
            }
            return existing + "\n\n" + addition;
        }

        List<ChapterEntry> chapters() {
            return new ArrayList<>(chapters.values());
        }
    }

    private static boolean isOverviewHeading(String title) {
        String lower = title.toLowerCase();
        return lower.contains("kapitelübersicht") || lower.contains("struktur");
    }

    private static boolean isSkippedHeading(String title) {
        String lower = title.toLowerCase();
        return lower.contains("roman-assistent")
                || lower.startsWith("kapitelstruktur")
                || lower.startsWith("erzählweise")
                || lower.equals("kapitel")
                || lower.equals("übersicht")
                || lower.equals("eigene notizen")
                || lower.equals("traumwelt");
    }

    private static void writeDocx(Path target, ChapterEntry chapter) throws Exception {
        Files.createDirectories(target.getParent());
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ObjectFactory factory = new ObjectFactory();

        if (!chapter.overviewMarkdown().isBlank()) {
            DocxMarkdownSupport.appendPlainParagraph(pkg, factory, "Kapitelübersicht", true, "20");
            DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, chapter.overviewMarkdown());
            DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, "");
        }

        if (!chapter.actHeading().isBlank()) {
            DocxMarkdownSupport.appendPlainParagraph(pkg, factory, chapter.actHeading(), true, "22");
            if (!chapter.actMarkdown().isBlank()) {
                DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, chapter.actMarkdown());
            }
            DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, "");
        }

        if (!chapter.groupHeading().isBlank()) {
            DocxMarkdownSupport.appendPlainParagraph(pkg, factory, chapter.groupHeading(), true, "18");
            if (!chapter.groupMarkdown().isBlank()) {
                DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, chapter.groupMarkdown());
            }
            DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, "");
        }

        DocxMarkdownSupport.appendPlainParagraph(pkg, factory, chapter.title(), true, "28");

        if (!chapter.summary().isBlank()) {
            DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, chapter.summary());
        }

        DocxMarkdownSupport.appendMarkdownBlock(pkg, factory, "");
        DocxMarkdownSupport.appendPlainParagraph(pkg, factory, "Kapiteltext hier schreiben.", false, "12");

        Docx4J.save(pkg, target.toFile());
    }

    static String chapterDocxFileName(ChapterEntry chapter) {
        String subtitle = chapterSubtitle(chapter);
        return sanitizeDocxFileName(String.format("Kapitel %d - %s.docx", chapter.number(), subtitle));
    }

    private static String chapterSubtitle(ChapterEntry chapter) {
        if (chapter == null || chapter.title() == null || chapter.title().isBlank()) {
            return "Ohne Titel";
        }
        Matcher subtitle = Pattern.compile(
                "^Kapitel\\s+\\d+\\s*[:.\\-–—]\\s*(.+)$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(chapter.title().trim());
        if (subtitle.matches()) {
            String name = subtitle.group(1).trim();
            return name.isEmpty() ? "Ohne Titel" : name;
        }
        return chapter.title().trim();
    }

    private static String sanitizeDocxFileName(String fileName) {
        String cleaned = fileName
                .replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return cleaned.isEmpty() ? "Kapitel.docx" : cleaned;
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
}
