package com.manuskript;

import com.manuskript.agent.AgentMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Benennt ein Kapitel um: DOCX und alle dazugehörigen Dateien unter {@code data/}.
 */
public final class ChapterRenameService {

    private static final Logger logger = LoggerFactory.getLogger(ChapterRenameService.class);

    private static final String[] DATA_FILE_SUFFIXES = {
            ".md",
            ".txt",
            ".html",
            ".notes",
            ".status",
            "-scenes.txt",
            "-tts-segments.json",
            "-tts-content.md",
            "-tts-original-hash.txt"
    };

    private ChapterRenameService() {
    }

    public static String baseNameOf(File docxFile) {
        if (docxFile == null) {
            return "";
        }
        String name = docxFile.getName();
        if (name.toLowerCase().endsWith(".docx")) {
            return name.substring(0, name.length() - 5);
        }
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    /** Wie beim Anlegen eines Kapitels: Zeichen, die in Dateinamen ungültig sind. */
    public static String sanitizeChapterFileName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim();
        if (name.toLowerCase().endsWith(".docx")) {
            name = name.substring(0, name.length() - 5).trim();
        }
        name = name.replaceAll("[<>:\"/\\\\|?*]", "_");
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        return name;
    }

    public static final class Result {
        public final File newDocxFile;
        public final List<String> moved;
        public final List<String> warnings;

        Result(File newDocxFile, List<String> moved, List<String> warnings) {
            this.newDocxFile = newDocxFile;
            this.moved = moved;
            this.warnings = warnings;
        }
    }

    public static Result rename(File docxFile, String requestedName) throws IOException {
        if (docxFile == null || !docxFile.isFile()) {
            throw new IOException("Kapitel-Datei nicht gefunden.");
        }
        String oldBase = baseNameOf(docxFile);
        String newBase = sanitizeChapterFileName(requestedName);
        if (newBase.isBlank()) {
            throw new IOException("Bitte einen gültigen Kapitelnamen eingeben.");
        }
        if (newBase.equals(oldBase)) {
            throw new IOException("Der Name ist unverändert.");
        }
        File projectDir = docxFile.getParentFile();
        if (projectDir == null) {
            throw new IOException("Kein Projektordner für die Kapitel-Datei.");
        }
        File newDocx = new File(projectDir, newBase + ".docx");
        boolean caseOnly = oldBase.equalsIgnoreCase(newBase);
        if (!caseOnly && newDocx.exists()) {
            throw new IOException("Es existiert bereits ein Kapitel namens „" + newBase + "“.");
        }

        File dataDir = new File(projectDir, "data");
        List<Move> moves = new ArrayList<>();
        moves.add(new Move(docxFile, newDocx));
        collectDataMoves(dataDir, oldBase, newBase, moves);

        List<String> moved = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Move move : moves) {
            if (!move.from.exists()) {
                continue;
            }
            try {
                movePath(move.from.toPath(), move.to.toPath(), caseOnly);
                moved.add(move.from.getName() + " → " + move.to.getName());
            } catch (IOException e) {
                String msg = move.from.getName() + ": " + e.getMessage();
                if (move.from.equals(docxFile)) {
                    throw new IOException("DOCX konnte nicht umbenannt werden: " + e.getMessage(), e);
                }
                warnings.add(msg);
                logger.warn("Kapitel-Umbenennung: {}", msg);
            }
        }
        maybeUpdateMarkdownHeading(new File(dataDir, newBase + ".md"), oldBase, newBase);
        return new Result(newDocx, moved, warnings);
    }

    static List<File> sidecarFiles(File dataDir, String base) {
        List<File> files = new ArrayList<>();
        if (dataDir == null || base == null || base.isBlank()) {
            return files;
        }
        for (String suffix : DATA_FILE_SUFFIXES) {
            files.add(new File(dataDir, base + suffix));
        }
        files.add(new File(dataDir, base + ".docx.meta"));
        files.add(new File(dataDir, base + "-tts"));
        files.add(new File(new File(dataDir, ".history"), base));
        return files;
    }

    private static void collectDataMoves(File dataDir, String oldBase, String newBase, List<Move> moves) {
        if (dataDir == null || !dataDir.isDirectory()) {
            return;
        }
        List<File> froms = sidecarFiles(dataDir, oldBase);
        List<File> tos = sidecarFiles(dataDir, newBase);
        for (int i = 0; i < froms.size(); i++) {
            File from = froms.get(i);
            File to = tos.get(i);
            if (from.exists() && !from.getAbsolutePath().equals(to.getAbsolutePath())) {
                moves.add(new Move(from, to));
            }
        }
        File agentsRoot = new File(dataDir, "agents");
        if (!agentsRoot.isDirectory()) {
            return;
        }
        String oldAgent = AgentMemory.sanitizeChapterName(oldBase);
        String newAgent = AgentMemory.sanitizeChapterName(newBase);
        if (oldAgent.equals(newAgent) || "global".equals(oldAgent)) {
            return;
        }
        File[] agentDirs = agentsRoot.listFiles(File::isDirectory);
        if (agentDirs == null) {
            return;
        }
        for (File agentDir : agentDirs) {
            File from = new File(agentDir, oldAgent);
            File to = new File(agentDir, newAgent);
            if (from.isDirectory() && !from.getAbsolutePath().equals(to.getAbsolutePath())) {
                moves.add(new Move(from, to));
            }
        }
    }

    private static void movePath(Path from, Path to, boolean caseOnly) throws IOException {
        Files.createDirectories(to.getParent());
        if (caseOnly && from.getParent() != null && from.getParent().equals(to.getParent())) {
            Path tmp = from.getParent().resolve(from.getFileName() + ".renaming-tmp");
            try {
                Files.move(from, tmp, StandardCopyOption.ATOMIC_MOVE);
                Files.move(tmp, to, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(from, tmp);
                Files.move(tmp, to);
            }
            return;
        }
        if (Files.exists(to)) {
            throw new IOException("Ziel existiert bereits: " + to.getFileName());
        }
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(from, to);
        }
    }

    private static void maybeUpdateMarkdownHeading(File mdFile, String oldBase, String newBase) {
        if (mdFile == null || !mdFile.isFile()) {
            return;
        }
        try {
            String content = Files.readString(mdFile.toPath(), StandardCharsets.UTF_8);
            String prefix = "# " + oldBase;
            if (content.startsWith(prefix + "\n") || content.equals(prefix) || content.startsWith(prefix + "\r\n")) {
                String updated = "# " + newBase + content.substring(prefix.length());
                Files.writeString(mdFile.toPath(), updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Markdown-Überschrift nicht angepasst: {}", e.getMessage());
        }
    }

    private record Move(File from, File to) {
    }
}
