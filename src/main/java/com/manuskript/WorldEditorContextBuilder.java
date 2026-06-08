package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sammelt Manuskript- und Welt-Datei-Kontext fuer KI-Aktionen im Welt-Editor.
 */
public final class WorldEditorContextBuilder {

    private static final Logger logger = LoggerFactory.getLogger(WorldEditorContextBuilder.class);

    /** Max. Zeichen aus Kapitelquellen (Summaries oder MD-Volltext). */
    public static final int DEFAULT_CHAPTER_MAX_CHARS = 80_000;
    /** Max. Zeichen pro Welt-Datei im Kontext. */
    public static final int DEFAULT_WORLD_FILE_MAX_CHARS = 6_000;

    private static final String[] WORLD_FILES = {
            "context.txt",
            "style.txt",
            "worldbuilding.txt",
            "characters.txt",
            "outline.txt",
            "akte.txt",
            "synopsis.txt",
            "chapter.txt"
    };

    private static final String[] WORLD_LABELS = {
            "Brainstorm",
            "Schreibstil",
            "Worldbuilding",
            "Charaktere",
            "Handlung (Outline)",
            "Akte",
            "Synopsis",
            "Kapitel-Zusammenfassungen"
    };

    private WorldEditorContextBuilder() {
    }

    /**
     * Baut Kontext aus Kapiteln (Summaries bevorzugt) und anderen Welt-Tabs.
     *
     * @param excludeFilename aktuelle Datei (wird nicht als Quelle mitgeliefert)
     */
    public static String build(String projectDirectory, MainController mainController, String excludeFilename) {
        return build(projectDirectory, mainController, excludeFilename, null);
    }

    public static String build(String projectDirectory, MainController mainController, String excludeFilename,
                               WorldEditorExtractScope extractScope) {
        return build(projectDirectory, mainController, excludeFilename, extractScope,
                DEFAULT_CHAPTER_MAX_CHARS, DEFAULT_WORLD_FILE_MAX_CHARS);
    }

    public static String build(String projectDirectory, MainController mainController, String excludeFilename,
                               int chapterMaxChars, int worldFileMaxChars) {
        return build(projectDirectory, mainController, excludeFilename, null, chapterMaxChars, worldFileMaxChars);
    }

    public static String build(String projectDirectory, MainController mainController, String excludeFilename,
                               WorldEditorExtractScope extractScope, int chapterMaxChars, int worldFileMaxChars) {
        StringBuilder sb = new StringBuilder();
        String chapters = collectChapterSources(projectDirectory, mainController, chapterMaxChars, extractScope);
        if (!chapters.isBlank()) {
            sb.append("=== MANUSKRIPT (Kapitel) ===\n").append(chapters.trim()).append("\n\n");
        }
        String world = collectWorldFiles(projectDirectory, excludeFilename, worldFileMaxChars);
        if (!world.isBlank()) {
            sb.append("=== BEREITS VORHANDENE WELT-DATEIEN ===\n").append(world.trim()).append("\n");
        }
        return sb.toString().trim();
    }

    public static boolean hasChapterSources(String projectDirectory, MainController mainController) {
        if (readFile(projectDirectory, "chapter.txt").length() > 200) {
            return true;
        }
        if (mainController == null) {
            return false;
        }
        List<String> mdFiles = mainController.getMarkdownFilesInOrder();
        if (mdFiles == null || mdFiles.isEmpty()) {
            return false;
        }
        Path dataDir = Paths.get(projectDirectory, "data");
        for (String mdFileName : mdFiles) {
            if (Files.exists(dataDir.resolve(mdFileName))) {
                return true;
            }
        }
        return false;
    }

    /** MD-Dateinamen aus data/, die in der Kapitelliste existieren. */
    public static List<String> listAvailableMdFiles(String projectDirectory, MainController mainController) {
        if (mainController == null) {
            return List.of();
        }
        List<String> mdFiles = mainController.getMarkdownFilesInOrder();
        if (mdFiles == null || mdFiles.isEmpty()) {
            return List.of();
        }
        Path dataDir = Paths.get(projectDirectory, "data");
        List<String> available = new ArrayList<>();
        for (String mdFileName : mdFiles) {
            if (Files.exists(dataDir.resolve(mdFileName))) {
                available.add(mdFileName);
            }
        }
        return available;
    }

    private static String collectChapterSources(String projectDirectory, MainController mainController,
                                                int maxChars, WorldEditorExtractScope extractScope) {
        if (extractScope != null && extractScope.hasChapterSelection()) {
            logger.info("Welt-Editor-Kontext: {} ausgewaehlte Kapitel-MDs (Volltext)",
                    extractScope.selectedMdFileNames().size());
            return loadMdFiles(projectDirectory, extractScope.selectedMdFileNames(), maxChars, "Kapitel-Volltext (Auswahl)");
        }
        String summaries = readFile(projectDirectory, "chapter.txt");
        if (summaries.length() > 200) {
            logger.info("Welt-Editor-Kontext: chapter.txt ({} Zeichen)", summaries.length());
            return truncate(summaries, maxChars, "Kapitel-Zusammenfassungen");
        }
        if (mainController == null) {
            return "";
        }
        List<String> mdFiles = mainController.getMarkdownFilesInOrder();
        if (mdFiles == null || mdFiles.isEmpty()) {
            return "";
        }
        return loadMdFiles(projectDirectory, mdFiles, maxChars, "Kapitel-Volltext");
    }

    private static String loadMdFiles(String projectDirectory, List<String> mdFileNames, int maxChars, String label) {
        StringBuilder sb = new StringBuilder();
        Path dataDir = Paths.get(projectDirectory, "data");
        Set<String> seen = new LinkedHashSet<>();
        for (String mdFileName : mdFileNames) {
            if (mdFileName == null || mdFileName.isBlank() || !seen.add(mdFileName)) {
                continue;
            }
            Path mdPath = dataDir.resolve(mdFileName);
            if (!Files.exists(mdPath)) {
                logger.warn("Kapitel-MD nicht gefunden: {}", mdPath);
                continue;
            }
            try {
                String name = mdFileName.replace(".md", "").trim();
                String content = Files.readString(mdPath).replaceAll("(?m)^---+$", "");
                sb.append("\n### ").append(name).append("\n").append(content.trim()).append("\n");
            } catch (IOException e) {
                logger.warn("Konnte {} nicht lesen: {}", mdPath, e.getMessage());
            }
        }
        return truncate(sb.toString().trim(), maxChars, label);
    }

    private static String collectWorldFiles(String projectDirectory, String excludeFilename, int maxPerFile) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WORLD_FILES.length; i++) {
            String fileName = WORLD_FILES[i];
            if (fileName.equals(excludeFilename)) {
                continue;
            }
            String content = readFile(projectDirectory, fileName);
            if (content.isBlank()) {
                continue;
            }
            sb.append("\n## ").append(WORLD_LABELS[i]).append(" (").append(fileName).append(")\n");
            sb.append(truncate(content.trim(), maxPerFile, WORLD_LABELS[i])).append("\n");
        }
        return sb.toString().trim();
    }

    private static String readFile(String projectDirectory, String fileName) {
        try {
            Path path = Paths.get(projectDirectory, fileName);
            if (!Files.exists(path)) {
                return "";
            }
            return Files.readString(path);
        } catch (IOException e) {
            logger.warn("Konnte {} nicht lesen: {}", fileName, e.getMessage());
            return "";
        }
    }

    static String truncate(String text, int maxChars, String label) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars) + "\n\n[… " + label + " gekuerzt, "
                + (text.length() - maxChars) + " Zeichen weggelassen …]";
    }
}
