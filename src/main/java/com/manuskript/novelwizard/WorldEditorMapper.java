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
    public static final String AKTE_FILE = "akte.txt";

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
            writeIfMissing(NovelManager.OUTLINE_FILE, "# Outline / Handlung fuer " + projectName + "\n\n");
            writeIfMissing(AKTE_FILE, "# Akte und Dramaturgie fuer " + projectName + "\n\n");
            writeIfMissing(NovelManager.WORLDBUILDING_FILE, "# Worldbuilding fuer " + projectName + "\n\n");
            writeIfMissing(STYLE_FILE, "# Schreibstil fuer " + projectName + "\n\n"
                    + "## Eigene Notizen\n\n"
                    + "(Freier Text – wird vom Roman-Assistenten nicht ueberschrieben.)\n\n");
            writeIfMissing(CHAPTER_FILE, "# Kapitel-Zusammenfassungen fuer " + projectName + "\n\n");
            migrateAkteFromOutlineIfNeeded();
        } catch (IOException ignored) {
            // UI meldet spaeter konkrete Speichern-Fehler, der Bootstrap soll nicht hart abbrechen.
        }
    }

    /**
     * Verschiebt einen aelteren Akte-Block aus outline.txt nach akte.txt (einmalig, wenn akte.txt noch leer ist).
     */
    private void migrateAkteFromOutlineIfNeeded() throws IOException {
        Path outlinePath = projectDirectory.resolve(NovelManager.OUTLINE_FILE);
        Path aktePath = projectDirectory.resolve(AKTE_FILE);
        if (!Files.exists(outlinePath)) {
            return;
        }
        String outline = Files.readString(outlinePath, StandardCharsets.UTF_8);
        String heading = "Roman-Assistent: Akte";
        String marker = "## " + heading;
        if (!outline.contains(marker)) {
            return;
        }
        String akteExisting = Files.exists(aktePath) ? Files.readString(aktePath, StandardCharsets.UTF_8) : "";
        if (hasPersistableContent(akteExisting)) {
            return;
        }
        java.util.regex.Pattern blockPattern = java.util.regex.Pattern.compile(
                "(?s)(## " + java.util.regex.Pattern.quote(heading) + "\\R.*?)(?=\\R## |\\z)");
        java.util.regex.Matcher matcher = blockPattern.matcher(outline);
        if (!matcher.find()) {
            return;
        }
        String block = matcher.group(1).trim();
        String akteHeader = Files.exists(aktePath) ? akteExisting.stripTrailing() : "# Akte und Dramaturgie\n\n";
        Files.writeString(aktePath, akteHeader + "\n\n" + block + "\n", StandardCharsets.UTF_8);
        String newOutline = outline.replaceAll(
                "(?s)\\R\\R## " + java.util.regex.Pattern.quote(heading) + "\\R.*?(?=\\R## |\\z)",
                "");
        Files.writeString(outlinePath, newOutline.stripTrailing() + "\n", StandardCharsets.UTF_8);
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
        if (phase == null || phase == NovelWizardPhase.BOOTSTRAP) {
            return;
        }
        if (content == null || !hasPersistableContent(content)) {
            return;
        }
        switch (phase) {
            case BRAINSTORM -> persistBrainstorm(content);
            case WORLD -> appendSection(NovelManager.WORLDBUILDING_FILE, "Roman-Assistent: Welt", content);
            case CHARACTERS -> persistCharacterSheets(content);
            case PLOT -> appendSection(NovelManager.OUTLINE_FILE, "Roman-Assistent: Handlung", content);
            case STRUCTURE -> appendSection(AKTE_FILE, "Roman-Assistent: Akte", content);
            case SYNOPSIS -> appendSection(NovelManager.SYNOPSIS_FILE, "Roman-Assistent: Synopsis", content);
            case CHAPTERS -> appendSection(CHAPTER_FILE, "Roman-Assistent: Kapitel", content);
            default -> {
            }
        }
    }

    private static boolean hasPersistableContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") && trimmed.length() > 2) {
                return true;
            }
            if (trimmed.length() > 0
                    && !trimmed.startsWith("#")
                    && !trimmed.startsWith("**Frage:**")
                    && !trimmed.equals("---")) {
                return true;
            }
        }
        return false;
    }

    private static final String CHARACTER_SHEETS_HEADING = "Character Sheets";
    private static final String LEGACY_CHARACTERS_HEADING = "Roman-Assistent: Figuren";

    /**
     * Schreibt strukturierte Character Sheets. Entfernt aeltere Interview-Bloecke aus der Figuren-Phase.
     */
    private void persistCharacterSheets(String content) throws IOException {
        Path file = projectDirectory.resolve(NovelManager.CHARACTERS_FILE);
        String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        String withoutLegacy = removeSectionBody(existing, LEGACY_CHARACTERS_HEADING);
        appendSectionToText(withoutLegacy, NovelManager.CHARACTERS_FILE, CHARACTER_SHEETS_HEADING, content);
    }

    private void appendSectionToText(String baseText, String fileName, String heading, String wizardContent)
            throws IOException {
        Path file = projectDirectory.resolve(fileName);
        Files.createDirectories(file.getParent());
        SectionParts parts = splitAroundSection(baseText, heading);
        String manualInSection = extractManualLines(parts.sectionBody());
        String mergedBody = buildMergedSectionBody(manualInSection, wizardContent.trim());
        String block = "## " + heading + "\n\n" + mergedBody + "\n";

        StringBuilder result = new StringBuilder();
        if (!parts.before().isBlank()) {
            result.append(parts.before().stripTrailing()).append("\n\n");
        }
        result.append(block.stripTrailing());
        if (!parts.after().isBlank()) {
            result.append("\n\n").append(parts.after().strip());
        }
        Files.writeString(file, result.toString().strip() + "\n", StandardCharsets.UTF_8);
    }

    private static String removeSectionBody(String fullText, String heading) {
        SectionParts parts = splitAroundSection(fullText, heading);
        if (parts.sectionBody().isBlank() && parts.before().equals(fullText == null ? "" : fullText)) {
            return fullText == null ? "" : fullText;
        }
        StringBuilder result = new StringBuilder();
        if (!parts.before().isBlank()) {
            result.append(parts.before().stripTrailing());
        }
        if (!parts.after().isBlank()) {
            if (!result.isEmpty()) {
                result.append("\n\n");
            }
            result.append(parts.after().strip());
        }
        return result.toString().strip();
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

    /**
     * Schreibt den Roman-Assistenten-Block. Manuelle Zeilen in derselben Datei (ausserhalb des Blocks
     * oder freie Textzeilen innerhalb des Blocks) bleiben erhalten.
     */
    private void appendSection(String fileName, String heading, String wizardContent) throws IOException {
        Path file = projectDirectory.resolve(fileName);
        Files.createDirectories(file.getParent());
        String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        SectionParts parts = splitAroundSection(existing, heading);
        String manualInSection = extractManualLines(parts.sectionBody());
        String mergedBody = buildMergedSectionBody(manualInSection, wizardContent.trim());
        String block = "## " + heading + "\n\n" + mergedBody + "\n";

        StringBuilder result = new StringBuilder();
        if (!parts.before().isBlank()) {
            result.append(parts.before().stripTrailing()).append("\n\n");
        }
        result.append(block.stripTrailing());
        if (!parts.after().isBlank()) {
            result.append("\n\n").append(parts.after().strip());
        }
        Files.writeString(file, result.toString().strip() + "\n", StandardCharsets.UTF_8);
    }

    private static String buildMergedSectionBody(String manualNotes, String wizardContent) {
        StringBuilder sb = new StringBuilder();
        if (manualNotes != null && !manualNotes.isBlank()) {
            sb.append(manualNotes.trim()).append("\n\n");
        }
        if (wizardContent != null && !wizardContent.isBlank()) {
            sb.append(wizardContent.trim());
        }
        return sb.toString().trim();
    }

    private static String extractManualLines(String sectionBody) {
        if (sectionBody == null || sectionBody.isBlank()) {
            return "";
        }
        StringBuilder manual = new StringBuilder();
        for (String line : sectionBody.split("\\R")) {
            if (!isWizardGeneratedLine(line)) {
                manual.append(line).append("\n");
            }
        }
        return manual.toString().trim();
    }

    private static boolean isWizardGeneratedLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.startsWith("**Frage:**")
                || trimmed.startsWith("- ")
                || trimmed.startsWith("### ")
                || trimmed.startsWith(BrainstormGrunddaten.KURZ_HEADING)
                || trimmed.equals("---");
    }

    private static SectionParts splitAroundSection(String fullText, String heading) {
        String marker = "## " + heading;
        if (fullText == null || fullText.isBlank() || !fullText.contains(marker)) {
            return new SectionParts(fullText == null ? "" : fullText, "", "");
        }
        int markerIndex = fullText.indexOf(marker);
        String before = fullText.substring(0, markerIndex).stripTrailing();
        int bodyStart = markerIndex + marker.length();
        while (bodyStart < fullText.length() && (fullText.charAt(bodyStart) == '\n' || fullText.charAt(bodyStart) == '\r')) {
            bodyStart++;
        }
        int nextHeading = indexOfNextH2(fullText, bodyStart);
        String body;
        String after;
        if (nextHeading < 0) {
            body = fullText.substring(bodyStart).trim();
            after = "";
        } else {
            body = fullText.substring(bodyStart, nextHeading).trim();
            after = fullText.substring(nextHeading).trim();
        }
        return new SectionParts(before, body, after);
    }

    private static int indexOfNextH2(String text, int fromIndex) {
        int idx = text.indexOf("\n## ", fromIndex);
        if (idx < 0 && fromIndex > 0) {
            return -1;
        }
        return idx >= 0 ? idx + 1 : -1;
    }

    private record SectionParts(String before, String sectionBody, String after) {
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

    public String readChapterContentForDocx() {
        String full = read(CHAPTER_FILE);
        if (full == null || full.isBlank()) {
            return "";
        }
        SectionParts parts = splitAroundSection(full, "Roman-Assistent: Kapitel");
        if (parts.sectionBody() != null && !parts.sectionBody().isBlank()) {
            // Kapitel-Outline enthaelt viele ##/###/#### – nicht am ersten ## abschneiden
            StringBuilder block = new StringBuilder(parts.sectionBody().trim());
            if (parts.after() != null && !parts.after().isBlank()) {
                block.append("\n\n").append(parts.after().trim());
            }
            return block.toString();
        }
        return full;
    }

    public static String tabLabel(String fileName) {
        return worldFiles().getOrDefault(fileName, fileName);
    }

    private static Map<String, String> worldFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(NovelManager.CONTEXT_FILE, "Brainstorm");
        files.put(STYLE_FILE, "Schreibstil");
        files.put(NovelManager.WORLDBUILDING_FILE, "Worldbuilding");
        files.put(NovelManager.CHARACTERS_FILE, "Charaktere");
        files.put(NovelManager.OUTLINE_FILE, "Handlung (Outline)");
        files.put(AKTE_FILE, "Akte");
        files.put(NovelManager.SYNOPSIS_FILE, "Synopsis");
        files.put(CHAPTER_FILE, "Kapitel");
        return files;
    }
}
