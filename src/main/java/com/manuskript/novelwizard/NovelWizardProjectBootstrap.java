package com.manuskript.novelwizard;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bestehende Manuskript-Projekte (DOCX ohne gefuellte Welt-Dateien) fuer den Roman-Assistenten.
 */
public final class NovelWizardProjectBootstrap {

    private static final Gson GSON = new Gson();
    private static final Pattern NUMBERED_DOCX = Pattern.compile(
            "^(\\d+)\\.\\s*(.+)\\.docx$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WIZARD_DOCX = Pattern.compile(
            "^Kapitel\\s+(\\d+)\\s*-\\s*(.+)\\.docx$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private NovelWizardProjectBootstrap() {
    }

    public record ExistingChapterRef(int number, String subtitle, String fileName) {
        String markdownHeading() {
            return "#### Kapitel " + number + ": " + subtitle;
        }
    }

    public static List<ExistingChapterRef> listExistingChapters(Path projectDirectory) throws IOException {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            return List.of();
        }
        List<String> orderedNames = readSelectionFileNames(projectDirectory);
        List<ExistingChapterRef> chapters = new ArrayList<>();
        if (!orderedNames.isEmpty()) {
            int fallback = 1;
            for (String name : orderedNames) {
                ExistingChapterRef ref = parseDocxFileName(name, fallback);
                if (ref != null) {
                    chapters.add(ref);
                    fallback = ref.number() + 1;
                }
            }
        } else {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectDirectory, "*.docx")) {
                List<String> names = new ArrayList<>();
                for (Path path : stream) {
                    names.add(path.getFileName().toString());
                }
                names.sort(Comparator.naturalOrder());
                int fallback = 1;
                for (String name : names) {
                    ExistingChapterRef ref = parseDocxFileName(name, fallback);
                    if (ref != null) {
                        chapters.add(ref);
                        fallback = ref.number() + 1;
                    }
                }
            }
        }
        chapters.sort(Comparator.comparingInt(ExistingChapterRef::number));
        return chapters;
    }

    public static String buildChapterOutlineMarkdown(List<ExistingChapterRef> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Kapitelübersicht und Struktur\n\n");
        sb.append("Bestehendes Manuskript: ").append(chapters.size())
                .append(" Kapitel liegen bereits als DOCX im Projektordner.\n\n");
        for (ExistingChapterRef chapter : chapters) {
            sb.append(chapter.markdownHeading()).append("\n");
            sb.append("(Kapiteltext in `").append(chapter.fileName()).append("`)\n\n");
        }
        return sb.toString().trim();
    }

    public static String describeExistingManuscript(Path projectDirectory) {
        try {
            List<ExistingChapterRef> chapters = listExistingChapters(projectDirectory);
            if (chapters.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(chapters.size()).append(" Kapitel-DOCX im Projekt:\n");
            for (ExistingChapterRef chapter : chapters) {
                sb.append("- Kapitel ").append(chapter.number()).append(": ")
                        .append(chapter.subtitle())
                        .append(" (").append(chapter.fileName()).append(")\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    static ExistingChapterRef parseDocxFileName(String fileName, int fallbackNumber) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String normalized = fileName.trim();
        Matcher numbered = NUMBERED_DOCX.matcher(normalized);
        if (numbered.matches()) {
            return new ExistingChapterRef(
                    Integer.parseInt(numbered.group(1)),
                    numbered.group(2).trim(),
                    normalized);
        }
        Matcher wizard = WIZARD_DOCX.matcher(normalized);
        if (wizard.matches()) {
            return new ExistingChapterRef(
                    Integer.parseInt(wizard.group(1)),
                    wizard.group(2).trim(),
                    normalized);
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return null;
        }
        String title = normalized.substring(0, normalized.length() - 5).trim();
        if (title.isEmpty()) {
            return null;
        }
        return new ExistingChapterRef(fallbackNumber, title, normalized);
    }

    private static List<String> readSelectionFileNames(Path projectDirectory) throws IOException {
        Path selection = projectDirectory.resolve("data").resolve(".manuskript_selection.json");
        if (!Files.exists(selection)) {
            return List.of();
        }
        String json = Files.readString(selection, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return List.of();
        }
        List<String> names = GSON.fromJson(json, new TypeToken<List<String>>() {
        }.getType());
        return names == null ? List.of() : names.stream().filter(s -> s != null && !s.isBlank()).toList();
    }
}
