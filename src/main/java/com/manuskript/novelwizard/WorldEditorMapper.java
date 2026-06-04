package com.manuskript.novelwizard;

import com.manuskript.NovelManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorldEditorMapper {
    public static final String CHAPTER_FILE = "chapter.txt";
    public static final String STYLE_FILE = "style.txt";

    private final Path projectDirectory;

    public WorldEditorMapper(Path projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    public void ensureWorldFiles(String projectName) {
        try {
            Files.createDirectories(projectDirectory.resolve("data"));
            writeIfMissing(NovelManager.CONTEXT_FILE, "# Kontext fuer " + projectName + "\n\n");
            writeIfMissing(NovelManager.CHARACTERS_FILE, "# Charaktere fuer " + projectName + "\n\n");
            writeIfMissing(NovelManager.SYNOPSIS_FILE, "# Synopsis fuer " + projectName + "\n\n");
            writeIfMissing(NovelManager.OUTLINE_FILE, "# Outline fuer " + projectName + "\n\n");
            writeIfMissing(NovelManager.WORLDBUILDING_FILE, "# Worldbuilding fuer " + projectName + "\n\n");
            writeIfMissing(STYLE_FILE, "# Schreibstil fuer " + projectName + "\n\n");
            writeIfMissing(CHAPTER_FILE, "# Kapitel-Zusammenfassungen fuer " + projectName + "\n\n");
        } catch (IOException ignored) {
            // UI meldet spaeter konkrete Speichern-Fehler, der Bootstrap soll nicht hart abbrechen.
        }
    }

    public String readExistingContext() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : worldFiles().entrySet()) {
            String text = read(entry.getKey());
            if (!text.isBlank()) {
                sb.append("## ").append(entry.getValue()).append("\n")
                        .append(text).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    public Map<String, String> readWorldFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : worldFiles().entrySet()) {
            files.put(entry.getKey(), read(entry.getKey()));
        }
        return files;
    }

    public void persistPhase(NovelWizardPhase phase, String content) throws IOException {
        if (content == null || content.isBlank()) {
            return;
        }
        switch (phase) {
            case BRAINSTORM -> persistBrainstorm(content);
            case WORLD -> appendSection(NovelManager.WORLDBUILDING_FILE, "Roman-Assistent: Welt", content);
            case CHARACTERS -> appendSection(NovelManager.CHARACTERS_FILE, "Roman-Assistent: Figuren", content);
            case PLOT, STRUCTURE -> appendSection(NovelManager.OUTLINE_FILE, "Roman-Assistent: " + phase.getTitle(), content);
            case SYNOPSIS -> appendSection(NovelManager.SYNOPSIS_FILE, "Roman-Assistent: Synopsis", content);
            case CHAPTERS -> appendSection(CHAPTER_FILE, "Roman-Assistent: Kapitel", content);
            default -> {
            }
        }
    }

    private void persistBrainstorm(String content) throws IOException {
        appendSection(NovelManager.CONTEXT_FILE, "Roman-Assistent: Grunddaten", content);

        String styleContent = extractStyleContent(content);
        if (!styleContent.isBlank()) {
            appendSection(STYLE_FILE, "Roman-Assistent: Stil und Ton", styleContent);
        }
    }

    private String extractStyleContent(String content) {
        StringBuilder sb = new StringBuilder();
        String activeQuestion = "";
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("**Frage:**")) {
                activeQuestion = trimmed.toLowerCase();
                continue;
            }
            if (isStyleRelated(activeQuestion) || isStyleRelated(trimmed.toLowerCase())) {
                if (!activeQuestion.isBlank() && sb.isEmpty()) {
                    sb.append(activeQuestion.replace("**frage:**", "### Frage").trim()).append("\n");
                }
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private boolean isStyleRelated(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("stil")
                || text.contains("ton")
                || text.contains("erzähl")
                || text.contains("perspektive")
                || text.contains("sprache")
                || text.contains("stimme")
                || text.contains("atmosphäre")
                || text.contains("tempo")
                || text.contains("poetisch")
                || text.contains("nüchtern")
                || text.contains("humor");
    }

    private void appendSection(String fileName, String heading, String content) throws IOException {
        Path file = projectDirectory.resolve(fileName);
        Files.createDirectories(file.getParent());
        String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        String marker = "## " + heading;
        String block = marker + "\n\n" + content.trim() + "\n";
        if (existing.contains(marker)) {
            existing = existing.replaceAll("(?s)## " + java.util.regex.Pattern.quote(heading) + "\\R.*?(?=\\R## |\\z)", java.util.regex.Matcher.quoteReplacement(block));
        } else {
            existing = existing.stripTrailing() + "\n\n" + block;
        }
        Files.writeString(file, existing.stripLeading(), StandardCharsets.UTF_8);
    }

    private void writeIfMissing(String fileName, String content) throws IOException {
        Path file = projectDirectory.resolve(fileName);
        if (!Files.exists(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
    }

    private String read(String fileName) {
        try {
            Path file = projectDirectory.resolve(fileName);
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static Map<String, String> worldFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(CHAPTER_FILE, "Kapitel-Zusammenfassungen");
        files.put(NovelManager.CHARACTERS_FILE, "Charaktere");
        files.put(NovelManager.CONTEXT_FILE, "Kontext");
        files.put(NovelManager.OUTLINE_FILE, "Outline");
        files.put(STYLE_FILE, "Schreibstil");
        files.put(NovelManager.SYNOPSIS_FILE, "Synopsis");
        files.put(NovelManager.WORLDBUILDING_FILE, "Worldbuilding");
        return files;
    }
}
