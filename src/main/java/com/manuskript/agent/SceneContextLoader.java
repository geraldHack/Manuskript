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

    private static final int MAX_BLOCK_CHARS = 2000;
    private static final int MAX_SCENE_OUTLINE_CHARS = 16000;
    private static final int MAX_CHAPTER_CHARS = 8000;
    private static final int MAX_RECENT_CHAPTERS = 10;
    private static final int MAX_CHARS_PER_RECENT_CHAPTER = 4000;

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
    }

    public static Context load(
            File projectDir,
            File currentDocx,
            File currentMdFile,
            String currentEditorText,
            List<DocxFile> chapterOrder,
            String instruction
    ) {
        return load(projectDir, currentDocx, currentMdFile, currentEditorText, chapterOrder, instruction, null);
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
        Context ctx = new Context();
        ctx.instruction = instruction != null ? instruction.trim() : "";

        if (currentDocx != null) {
            String docxPath = currentDocx.getAbsolutePath();
            ctx.style = limitText(readProjectFile(projectDir, "style.txt"));
            ctx.worldbuilding = limitText(NovelManager.loadWorldbuilding(docxPath));
            ctx.characters = limitText(NovelManager.loadCharacters(docxPath));
            ctx.synopsis = limitText(NovelManager.loadSynopsis(docxPath));
            ctx.romanOutline = limitText(NovelManager.loadOutline(docxPath));

            if (sceneOutlineOverride != null) {
                ctx.sceneOutline = limitSceneOutline(sceneOutlineOverride.trim());
            } else {
                File scenesFile = SceneOutlinePaths.scenesFileForDocx(currentDocx);
                ctx.sceneOutline = limitSceneOutline(readFile(scenesFile));
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
            ctx.currentChapter = limitChapterText(currentEditorText);
        } else if (currentMdFile != null) {
            ctx.currentChapter = limitChapterText(readFile(currentMdFile));
        }

        ctx.previousChapter = loadRecentChapters(projectDir, currentDocx, chapterOrder);
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
            sb.append("=== LETZTE KAPITEL (bis zu ").append(MAX_RECENT_CHAPTERS)
                .append(", Stimmung/Kontinuität) ===\n").append(ctx.previousChapter).append("\n\n");
        }
        if (!ctx.currentChapter.isBlank()) {
            sb.append("=== AKTUELLES KAPITEL (bisheriger Text) ===\n").append(ctx.currentChapter).append("\n\n");
        }
        return sb.toString().trim();
    }

    private static String loadRecentChapters(File projectDir, File currentDocx, List<DocxFile> chapterOrder) {
        if (currentDocx == null || chapterOrder == null || chapterOrder.isEmpty()) {
            return limitText(readProjectFile(projectDir, "chapter.txt"));
        }
        int idx = findChapterIndex(currentDocx, chapterOrder);
        if (idx <= 0) {
            return "";
        }
        int start = Math.max(0, idx - MAX_RECENT_CHAPTERS);
        StringBuilder sb = new StringBuilder();
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
            sb.append(limitRecentChapterText(content));
        }
        return sb.toString().trim();
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

    private static String limitText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > MAX_BLOCK_CHARS ? text.substring(0, MAX_BLOCK_CHARS) + "\n..." : text;
    }

    private static String limitSceneOutline(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > MAX_SCENE_OUTLINE_CHARS
            ? text.substring(0, MAX_SCENE_OUTLINE_CHARS) + "\n..."
            : text;
    }

    private static String limitChapterText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > MAX_CHAPTER_CHARS ? text.substring(0, MAX_CHAPTER_CHARS) + "\n..." : text;
    }

    private static String limitRecentChapterText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > MAX_CHARS_PER_RECENT_CHAPTER
            ? text.substring(0, MAX_CHARS_PER_RECENT_CHAPTER) + "\n..."
            : text;
    }
}
