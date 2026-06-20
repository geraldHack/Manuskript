package com.manuskript.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.manuskript.DocxFile;
import com.manuskript.MainController;

/**
 * Baut den Kontext-Block fuer den Chatbot aus aktiven Pills und Groessen-Budget.
 */
public final class ChatbotContextBuilder {

    private static final String[] WORLD_FILES = {
            "context.txt", "style.txt", "worldbuilding.txt", "characters.txt",
            "outline.txt", "akte.txt", "synopsis.txt"
    };

    private static final String[] WORLD_LABELS = {
            "Brainstorm", "Schreibstil", "Worldbuilding", "Charaktere",
            "Handlung (Outline)", "Akte", "Synopsis"
    };

    private ChatbotContextBuilder() {
    }

    public static String build(
            File projectDir,
            MainController main,
            String editorKey,
            String editorText,
            File currentMdFile,
            File currentDocx,
            List<DocxFile> chapterOrder,
            ChatbotContextConfig config) {
        if (config == null) {
            return "";
        }
        ChatbotContextSize size = config.getContextSize();
        StringBuilder sb = new StringBuilder();
        int budget = size.maxTotalChars();

        if (config.hasSource(ChatbotContextSource.CURRENT_CHAPTER)) {
            appendBlock(sb, "=== AKTUELLES KAPITEL ===",
                    currentChapterText(editorText, currentMdFile, editorKey, projectDir),
                    size.maxChapterChars(), budget);
            budget = remainingBudget(budget, sb.length());
        }

        if (config.hasSource(ChatbotContextSource.CHAPTERS_BEFORE)
                || config.hasSource(ChatbotContextSource.CHAPTERS_AFTER)) {
            int before = config.hasSource(ChatbotContextSource.CHAPTERS_BEFORE)
                    ? config.getChaptersBefore() : 0;
            int after = config.hasSource(ChatbotContextSource.CHAPTERS_AFTER)
                    ? config.getChaptersAfter() : 0;
            String neighbors = loadNeighborChapters(projectDir, currentDocx, chapterOrder, before, after, size);
            appendBlock(sb, "=== NACHBAR-KAPITEL ===", neighbors, size.maxBlockChars(), budget);
            budget = remainingBudget(budget, sb.length());
        }

        if (config.hasSource(ChatbotContextSource.ALL_CHAPTERS)) {
            String allChapters = loadAllChaptersFromOrder(chapterOrder, null);
            if (allChapters.isBlank() && main != null) {
                allChapters = main.loadAllChapters();
            }
            appendBlock(sb, "=== ALLE KAPITEL ===", allChapters, size.maxBlockChars(), budget);
            budget = remainingBudget(budget, sb.length());
        }

        if (config.hasSource(ChatbotContextSource.WORLD_EDITOR)) {
            appendWorldFiles(sb, projectDir, size, budget);
        }

        return sb.toString().trim();
    }

    static String loadNeighborChapters(
            File projectDir,
            File currentDocx,
            List<DocxFile> chapterOrder,
            int before,
            int after,
            ChatbotContextSize size) {
        if (projectDir == null || chapterOrder == null || chapterOrder.isEmpty() || currentDocx == null) {
            return "";
        }
        int idx = findChapterIndex(currentDocx, chapterOrder);
        if (idx < 0) {
            return "";
        }
        int start = Math.max(0, idx - before);
        int end = Math.min(chapterOrder.size() - 1, idx + after);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i == idx) {
                continue;
            }
            DocxFile df = chapterOrder.get(i);
            if (df == null || df.getFile() == null) {
                continue;
            }
            File md = mdFileForDocx(projectDir, df.getFile());
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
            String label = df.getDisplayFileName();
            sb.append("--- ").append(label).append(" ---\n");
            sb.append(truncate(content, size.maxChapterChars(), label));
        }
        return sb.toString().trim();
    }

    private static void appendWorldFiles(StringBuilder sb, File projectDir, ChatbotContextSize size, int budget) {
        if (projectDir == null) {
            return;
        }
        for (int i = 0; i < WORLD_FILES.length; i++) {
            if (budget == 0) {
                break;
            }
            File file = new File(projectDir, WORLD_FILES[i]);
            if (!file.exists()) {
                continue;
            }
            String content = readFile(file);
            if (content.isBlank()) {
                continue;
            }
            String label = WORLD_LABELS[i];
            appendBlock(sb, "=== " + label.toUpperCase() + " ===",
                    truncate(content, size.maxBlockChars(), label), size.maxBlockChars(), budget);
            budget = remainingBudget(budget, sb.length());
        }
    }

    private static String currentChapterText(String editorText, File mdFile, String editorKey, File projectDir) {
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        if (mdFile != null && mdFile.exists()) {
            return readFile(mdFile);
        }
        if (projectDir != null && editorKey != null && !editorKey.isBlank()) {
            File dataMd = new File(new File(projectDir, "data"), editorKey);
            if (dataMd.exists()) {
                return readFile(dataMd);
            }
        }
        return "";
    }

    private static void appendBlock(StringBuilder sb, String header, String content, int maxBlock, int totalBudget) {
        if (content == null || content.isBlank()) {
            return;
        }
        String block = truncate(content, maxBlock, header);
        if (totalBudget >= 0 && sb.length() + block.length() + header.length() + 4 > totalBudget) {
            int allowed = totalBudget - sb.length() - header.length() - 4;
            if (allowed <= 0) {
                return;
            }
            block = truncate(block, allowed, header);
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(header).append("\n").append(block);
    }

    private static int remainingBudget(int budget, int used) {
        if (budget < 0) {
            return -1;
        }
        return Math.max(0, budget - used);
    }

    static int findChapterIndex(File currentDocx, List<DocxFile> chapterOrder) {
        for (int i = 0; i < chapterOrder.size(); i++) {
            DocxFile df = chapterOrder.get(i);
            if (df != null && df.getFile() != null
                    && df.getFile().getAbsolutePath().equals(currentDocx.getAbsolutePath())) {
                return i;
            }
        }
        return -1;
    }

  static File mdFileForDocx(File projectDir, File docx) {
        if (docx == null) {
            return null;
        }
        String baseName = docx.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        File parent = docx.getParentFile();
        if (parent == null) {
            return projectDir != null ? new File(new File(projectDir, "data"), baseName + ".md") : null;
        }
        return new File(new File(parent, "data"), baseName + ".md");
    }

    /** Lädt alle Kapitel-MDs in Projekt-Reihenfolge (gleicher Pfad wie {@code deriveMdFileFor} im MainController). */
    static String loadAllChaptersFromOrder(List<DocxFile> chapterOrder, String excludeMdFileName) {
        if (chapterOrder == null || chapterOrder.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (DocxFile df : chapterOrder) {
            if (df == null || df.getFile() == null) {
                continue;
            }
            File md = mdFileForDocx(null, df.getFile());
            if (md == null || !md.isFile()) {
                continue;
            }
            if (excludeMdFileName != null && excludeMdFileName.equalsIgnoreCase(md.getName())) {
                continue;
            }
            String content = readFile(md);
            if (content.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            String label = df.getDisplayFileName();
            sb.append("=== ").append(label != null && !label.isBlank() ? label : md.getName()).append(" ===\n");
            sb.append(content);
        }
        return sb.toString().trim();
    }

    static String readFile(File file) {
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

    static List<ChatTurn> truncateHistory(List<ChatTurn> turns, int maxTurns) {
        if (turns == null || turns.isEmpty() || maxTurns <= 0) {
            return List.of();
        }
        int start = Math.max(0, turns.size() - maxTurns);
        return new ArrayList<>(turns.subList(start, turns.size()));
    }
}
