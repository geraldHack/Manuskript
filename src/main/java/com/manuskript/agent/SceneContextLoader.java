package com.manuskript.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.manuskript.DocxFile;
import com.manuskript.NovelManager;
import com.manuskript.SceneOutlinePaths;

/**
 * Lädt Kontextquellen für den Szene-Schreiben-Agenten.
 */
public class SceneContextLoader {

    private static final Logger logger = LoggerFactory.getLogger(SceneContextLoader.class);

    private SceneContextLoader() {}

    public static class Context {
        public String instruction = "";
        public String sceneOutline = "";
        public Integer targetSceneNumber;
        public String targetScene = "";
        public String style = "";
        public String worldbuilding = "";
        public String characters = "";
        public String synopsis = "";
        public String romanOutline = "";
        public String previousChapter = "";
        public String currentChapter = "";
        public SceneContextSize contextSize = SceneContextSize.COMPACT;
        public int recentChaptersIncluded;
        public boolean allPreviousChapters;
    }

    public static Context load(
            File projectDir,
            File currentDocx,
            File currentMdFile,
            String currentEditorText,
            List<DocxFile> chapterOrder,
            String instruction
    ) {
        return load(projectDir, currentDocx, currentMdFile, currentEditorText, chapterOrder,
                instruction, null, SceneContextSize.COMPACT);
    }

    public static Context load(
            File projectDir,
            File currentDocx,
            File currentMdFile,
            String currentEditorText,
            List<DocxFile> chapterOrder,
            String instruction,
            String sceneOutlineOverride
    ) {
        return load(projectDir, currentDocx, currentMdFile, currentEditorText, chapterOrder,
                instruction, sceneOutlineOverride, SceneContextSize.COMPACT);
    }

    public static Context load(
            File projectDir,
            File currentDocx,
            File currentMdFile,
            String currentEditorText,
            List<DocxFile> chapterOrder,
            String instruction,
            String sceneOutlineOverride,
            SceneContextSize contextSize
    ) {
        SceneContextSize size = contextSize != null ? contextSize : SceneContextSize.COMPACT;
        Context ctx = new Context();
        ctx.contextSize = size;
        ctx.instruction = instruction != null ? instruction.trim() : "";

        if (currentDocx != null) {
            String docxPath = currentDocx.getAbsolutePath();
            ctx.style = truncate(readProjectFile(projectDir, "style.txt"), size.maxBlockChars(), "Stil");
            ctx.worldbuilding = truncate(NovelManager.loadWorldbuilding(docxPath), size.maxBlockChars(), "Worldbuilding");
            ctx.characters = truncate(NovelManager.loadCharacters(docxPath), size.maxBlockChars(), "Charaktere");
            ctx.synopsis = truncate(NovelManager.loadSynopsis(docxPath), size.maxBlockChars(), "Synopsis");
            ctx.romanOutline = truncate(NovelManager.loadOutline(docxPath), size.maxBlockChars(), "Outline");

            if (sceneOutlineOverride != null && !sceneOutlineOverride.isBlank()) {
                ctx.sceneOutline = truncate(sceneOutlineOverride.trim(), size.maxSceneOutlineChars(), "Szenen-Outline");
            } else {
                File scenesFile = SceneOutlinePaths.scenesFileForDocx(currentDocx);
                ctx.sceneOutline = truncate(readFile(scenesFile), size.maxSceneOutlineChars(), "Szenen-Outline");
            }
        }

        ctx.targetSceneNumber = SceneOutlineParser.parseSceneNumberFromInstruction(ctx.instruction);
        if (ctx.targetSceneNumber != null && !ctx.sceneOutline.isBlank()) {
            ctx.targetScene = SceneOutlineParser.extractScene(ctx.sceneOutline, ctx.targetSceneNumber);
        }

        if (ctx.targetSceneNumber != null && ctx.targetScene.isBlank()) {
            logger.warn("Szene {} in Anweisung, aber nicht in Outline gefunden (Outline {} Zeichen)",
                ctx.targetSceneNumber, ctx.sceneOutline.length());
        } else if (ctx.targetSceneNumber != null) {
            logger.info("Ziel-Szene {}: {}", ctx.targetSceneNumber, ctx.targetScene);
        } else {
            logger.info("Szenen-Outline übergeben: {} Zeichen", ctx.sceneOutline.length());
        }

        if (currentEditorText != null && !currentEditorText.isBlank()) {
            ctx.currentChapter = truncate(currentEditorText, size.maxChapterChars(), "Aktuelles Kapitel");
        } else if (currentMdFile != null) {
            ctx.currentChapter = truncate(readFile(currentMdFile), size.maxChapterChars(), "Aktuelles Kapitel");
        }

        RecentChaptersResult recent = loadRecentChapters(projectDir, currentDocx, chapterOrder, size);
        ctx.previousChapter = recent.text;
        ctx.recentChaptersIncluded = recent.count;
        ctx.allPreviousChapters = recent.allPrevious;

        logger.info("Szene-Kontext ({}): User-Message-Vorschau {} Zeichen Kapitel, {} Vorkapitel",
                size.name(), ctx.currentChapter.length(), ctx.recentChaptersIncluded);
        return ctx;
    }

    public static String buildUserMessage(Context ctx) {
        StringBuilder sb = new StringBuilder();

        if (!ctx.targetScene.isBlank()) {
            sb.append("=== ZIEL-SZENE (PFLICHT — STRENG BEFOLGEN) ===\n");
            sb.append("Schreibe ausschließlich diese eine Szene als Fließprosa.\n");
            sb.append("Halte dich wortgetreu an Handlung, Figuren, Ort und Stimmung aus dieser Zeile.\n");
            sb.append("Erfinde keine andere Szene; greife nicht auf spätere Outline-Punkte vor.\n\n");
            sb.append(ctx.targetScene).append("\n\n");
        } else if (ctx.targetSceneNumber != null) {
            sb.append("=== HINWEIS ===\n");
            sb.append("Anweisung verlangt Szene ").append(ctx.targetSceneNumber)
                .append(", aber diese Nummer fehlt in der Szenen-Outline.\n\n");
        }

        if (!ctx.instruction.isBlank()) {
            sb.append("=== ANWEISUNG ===\n").append(ctx.instruction).append("\n\n");
        }
        if (!ctx.sceneOutline.isBlank()) {
            sb.append("=== SZENEN-OUTLINE (aktuelles Kapitel, vollständig) ===\n")
                .append(ctx.sceneOutline).append("\n\n");
        }
        if (!ctx.style.isBlank()) {
            sb.append("=== STIL ===\n").append(ctx.style).append("\n\n");
        }
        if (!ctx.worldbuilding.isBlank()) {
            sb.append("=== WORLDBUILDING ===\n").append(ctx.worldbuilding).append("\n\n");
        }
        if (!ctx.characters.isBlank()) {
            sb.append("=== CHARAKTERE ===\n").append(ctx.characters).append("\n\n");
        }
        if (!ctx.synopsis.isBlank()) {
            sb.append("=== SYNOPSIS ===\n").append(ctx.synopsis).append("\n\n");
        }
        if (!ctx.romanOutline.isBlank()) {
            sb.append("=== OUTLINE (Roman) ===\n").append(ctx.romanOutline).append("\n\n");
        }
        if (!ctx.previousChapter.isBlank()) {
            String chapterHint = ctx.allPreviousChapters
                    ? "alle bisherigen"
                    : "bis zu " + ctx.recentChaptersIncluded;
            sb.append("=== LETZTE KAPITEL (").append(chapterHint)
                .append(", Stimmung/Kontinuität) ===\n").append(ctx.previousChapter).append("\n\n");
        }
        if (!ctx.currentChapter.isBlank()) {
            sb.append("=== AKTUELLES KAPITEL (bisheriger Text) ===\n").append(ctx.currentChapter).append("\n\n");
        }
        return sb.toString().trim();
    }

    private record RecentChaptersResult(String text, int count, boolean allPrevious) {}

    private static RecentChaptersResult loadRecentChapters(
            File projectDir, File currentDocx, List<DocxFile> chapterOrder, SceneContextSize size) {
        if (currentDocx == null || chapterOrder == null || chapterOrder.isEmpty()) {
            String fallback = truncate(readProjectFile(projectDir, "chapter.txt"), size.maxBlockChars(), "Kapitel");
            return new RecentChaptersResult(fallback, fallback.isBlank() ? 0 : 1, false);
        }
        int idx = findChapterIndex(currentDocx, chapterOrder);
        if (idx <= 0) {
            return new RecentChaptersResult("", 0, false);
        }
        boolean allPrevious = size.unlimited(size.maxRecentChapters());
        int maxChapters = allPrevious ? idx : size.maxRecentChapters();
        int start = allPrevious ? 0 : Math.max(0, idx - maxChapters);
        StringBuilder sb = new StringBuilder();
        int included = 0;
        for (int i = start; i < idx; i++) {
            DocxFile df = chapterOrder.get(i);
            if (df == null || df.getFile() == null) {
                continue;
            }
            File md = mdFileForDocx(df.getFile());
            if (md == null || !md.exists()) {
                continue;
            }
            String content = readFile(md);
            if (content.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            String chapterLabel = df.getDisplayFileName();
            sb.append("--- ").append(chapterLabel).append(" ---\n");
            sb.append(truncate(content, size.maxCharsPerRecentChapter(), chapterLabel));
            included++;
        }
        return new RecentChaptersResult(sb.toString().trim(), included, allPrevious);
    }

    private static int findChapterIndex(File currentDocx, List<DocxFile> chapterOrder) {
        for (int i = 0; i < chapterOrder.size(); i++) {
            DocxFile df = chapterOrder.get(i);
            if (df != null && df.getFile() != null
                    && df.getFile().getAbsolutePath().equals(currentDocx.getAbsolutePath())) {
                return i;
            }
        }
        return -1;
    }

    private static File mdFileForDocx(File docx) {
        if (docx == null) {
            return null;
        }
        String baseName = docx.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) {
            baseName = baseName.substring(0, idx);
        }
        File dataDir = new File(docx.getParentFile(), "data");
        return new File(dataDir, baseName + ".md");
    }

    private static String readProjectFile(File projectDir, String name) {
        if (projectDir == null) {
            return "";
        }
        return readFile(new File(projectDir, name));
    }

    private static String readFile(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    static String truncate(String text, int maxChars, String label) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (maxChars < 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n\n[… " + label + " gekürzt, "
                + (text.length() - maxChars) + " Zeichen weggelassen …]";
    }
}
