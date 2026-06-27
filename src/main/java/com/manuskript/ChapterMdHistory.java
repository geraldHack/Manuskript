package com.manuskript;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Versionshistorie für Kapitel-MD-Dateien in {@code data/.history/{Kapitelname}/}.
 * Bleibt erhalten, wenn die MD-Datei gelöscht wird; gilt wieder bei gleichem Dateinamen.
 */
public final class ChapterMdHistory {

    private static final Logger logger = LoggerFactory.getLogger(ChapterMdHistory.class);
    private static final String HISTORY_DIR_NAME = ".history";
    private static final String INDEX_FILE = "index.json";
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ChapterMdHistory() {
    }

    public enum Reason {
        SAVE("Speichern"),
        DELETE("Vor Löschung"),
        BEFORE_RESTORE("Vor Wiederherstellung"),
        OVERWRITE("Vor Übernahme");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Reason fromKey(String key) {
            if (key == null || key.isBlank()) {
                return SAVE;
            }
            try {
                return Reason.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return SAVE;
            }
        }
    }

    public static final class Entry {
        private final String fileName;
        private final long timestampMillis;
        private final Reason reason;
        private final String contentHash;

        public Entry(String fileName, long timestampMillis, Reason reason, String contentHash) {
            this.fileName = fileName;
            this.timestampMillis = timestampMillis;
            this.reason = reason != null ? reason : Reason.SAVE;
            this.contentHash = contentHash != null ? contentHash : "";
        }

        public String fileName() {
            return fileName;
        }

        public long timestampMillis() {
            return timestampMillis;
        }

        public Reason reason() {
            return reason;
        }

        public String contentHash() {
            return contentHash;
        }

        public String displayLabel() {
            LocalDateTime dt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestampMillis), ZoneId.systemDefault());
            return dt.format(DISPLAY_FORMAT) + " — " + reason.label();
        }

        @Override
        public String toString() {
            return displayLabel();
        }
    }

    private static final class HistoryIndex {
        String chapterBaseName;
        List<StoredEntry> entries = new ArrayList<>();
    }

    private static final class StoredEntry {
        String fileName;
        long timestampMillis;
        String reason;
        String contentHash;
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(ResourceManager.getParameter("editor.md_history.enabled", "true"));
    }

    public static int getMaxVersions() {
        try {
            int max = Integer.parseInt(ResourceManager.getParameter("editor.md_history.max_versions", "15"));
            if (max < 0) {
                return 0;
            }
            return Math.min(max, 100);
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    public static String baseNameFromMd(File mdFile) {
        if (mdFile == null) {
            return null;
        }
        String baseName = mdFile.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) {
            baseName = baseName.substring(0, idx);
        }
        return baseName;
    }

    public static File historyDirForMd(File mdFile) {
        if (mdFile == null) {
            return null;
        }
        File dataDir = mdFile.getParentFile();
        if (dataDir == null) {
            return null;
        }
        String baseName = baseNameFromMd(mdFile);
        if (baseName == null || baseName.isBlank()) {
            return null;
        }
        return new File(new File(dataDir, HISTORY_DIR_NAME), baseName);
    }

    public static void snapshotOnSave(File mdFile, String content) {
        if (!isEnabled() || mdFile == null) {
            return;
        }
        int max = getMaxVersions();
        if (max <= 0) {
            return;
        }
        String normalized = content != null ? content : "";
        String hash = contentHash(normalized);
        File historyDir = historyDirForMd(mdFile);
        if (historyDir == null) {
            return;
        }
        try {
            HistoryIndex index = loadIndex(historyDir, baseNameFromMd(mdFile));
            if (!index.entries.isEmpty()) {
                StoredEntry latest = index.entries.get(0);
                if (hash.equals(latest.contentHash)) {
                    return;
                }
            }
            writeSnapshot(historyDir, index, normalized, hash, Reason.SAVE, max);
        } catch (IOException e) {
            logger.warn("MD-Historie konnte nicht gespeichert werden für {}: {}", mdFile.getName(), e.getMessage());
        }
    }

    public static void snapshotFromFile(File mdFile, Reason reason) {
        if (!isEnabled() || mdFile == null || !mdFile.isFile()) {
            return;
        }
        try {
            String content = Files.readString(mdFile.toPath(), StandardCharsets.UTF_8);
            snapshotWithContent(mdFile, content, reason);
        } catch (IOException e) {
            logger.warn("MD-Historie konnte nicht aus Datei gelesen werden {}: {}", mdFile.getName(), e.getMessage());
        }
    }

    public static void snapshotWithContent(File mdFile, String content, Reason reason) {
        if (!isEnabled() || mdFile == null) {
            return;
        }
        int max = getMaxVersions();
        if (max <= 0) {
            return;
        }
        String normalized = content != null ? content : "";
        String hash = contentHash(normalized);
        File historyDir = historyDirForMd(mdFile);
        if (historyDir == null) {
            return;
        }
        try {
            HistoryIndex index = loadIndex(historyDir, baseNameFromMd(mdFile));
            if (!index.entries.isEmpty()) {
                StoredEntry latest = index.entries.get(0);
                if (hash.equals(latest.contentHash) && reason == Reason.fromKey(latest.reason)) {
                    return;
                }
            }
            writeSnapshot(historyDir, index, normalized, hash, reason, max);
        } catch (IOException e) {
            logger.warn("MD-Historie-Snapshot fehlgeschlagen für {}: {}", mdFile.getName(), e.getMessage());
        }
    }

    public static List<Entry> listVersions(File mdFile) {
        File historyDir = historyDirForMd(mdFile);
        if (historyDir == null || !historyDir.isDirectory()) {
            return List.of();
        }
        try {
            HistoryIndex index = loadIndex(historyDir, baseNameFromMd(mdFile));
            List<Entry> result = new ArrayList<>();
            for (StoredEntry stored : index.entries) {
                if (stored.fileName == null || stored.fileName.isBlank()) {
                    continue;
                }
                File snapshotFile = new File(historyDir, stored.fileName);
                if (!snapshotFile.isFile()) {
                    continue;
                }
                result.add(new Entry(
                        stored.fileName,
                        stored.timestampMillis,
                        Reason.fromKey(stored.reason),
                        stored.contentHash != null ? stored.contentHash : ""));
            }
            return result;
        } catch (IOException e) {
            logger.warn("MD-Historie konnte nicht gelesen werden: {}", e.getMessage());
            return List.of();
        }
    }

    public static boolean hasVersions(File mdFile) {
        return !listVersions(mdFile).isEmpty();
    }

    public static String readEntryContent(File mdFile, Entry entry) {
        if (mdFile == null || entry == null || entry.fileName() == null) {
            return "";
        }
        File historyDir = historyDirForMd(mdFile);
        if (historyDir == null) {
            return "";
        }
        File snapshotFile = new File(historyDir, entry.fileName());
        if (!snapshotFile.isFile()) {
            return "";
        }
        try {
            return Files.readString(snapshotFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("MD-Historie-Version konnte nicht gelesen werden: {}", e.getMessage());
            return "";
        }
    }

    private static void writeSnapshot(File historyDir, HistoryIndex index, String content, String hash,
                                      Reason reason, int maxVersions) throws IOException {
        Files.createDirectories(historyDir.toPath());
        long now = System.currentTimeMillis();
        String fileName = now + "_" + reason.name().toLowerCase(Locale.ROOT) + ".md";
        Files.writeString(new File(historyDir, fileName).toPath(), content, StandardCharsets.UTF_8);

        StoredEntry stored = new StoredEntry();
        stored.fileName = fileName;
        stored.timestampMillis = now;
        stored.reason = reason.name();
        stored.contentHash = hash;
        index.entries.add(0, stored);
        prune(index, historyDir, maxVersions);
        saveIndex(historyDir, index);
    }

    private static void prune(HistoryIndex index, File historyDir, int maxVersions) {
        while (index.entries.size() > maxVersions) {
            StoredEntry removed = index.entries.remove(index.entries.size() - 1);
            if (removed.fileName != null) {
                File old = new File(historyDir, removed.fileName);
                if (old.isFile() && !old.delete()) {
                    logger.debug("Alte MD-Historie konnte nicht gelöscht werden: {}", old.getName());
                }
            }
        }
    }

    private static HistoryIndex loadIndex(File historyDir, String chapterBaseName) throws IOException {
        File indexFile = new File(historyDir, INDEX_FILE);
        if (!indexFile.isFile()) {
            return rebuildIndexFromFiles(historyDir, chapterBaseName);
        }
        try {
            String json = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
            HistoryIndex index = GSON.fromJson(json, HistoryIndex.class);
            if (index == null) {
                index = new HistoryIndex();
            }
            if (index.entries == null) {
                index.entries = new ArrayList<>();
            }
            index.chapterBaseName = chapterBaseName;
            index.entries.sort(Comparator.comparingLong((StoredEntry e) -> e.timestampMillis).reversed());
            return index;
        } catch (Exception e) {
            logger.warn("MD-Historie index.json defekt, neu aufgebaut: {}", e.getMessage());
            return rebuildIndexFromFiles(historyDir, chapterBaseName);
        }
    }

    private static HistoryIndex rebuildIndexFromFiles(File historyDir, String chapterBaseName) {
        HistoryIndex index = new HistoryIndex();
        index.chapterBaseName = chapterBaseName;
        index.entries = new ArrayList<>();
        File[] files = historyDir.listFiles((dir, name) -> name.endsWith(".md"));
        if (files == null) {
            return index;
        }
        for (File file : files) {
            StoredEntry stored = new StoredEntry();
            stored.fileName = file.getName();
            stored.timestampMillis = file.lastModified();
            stored.reason = Reason.SAVE.name();
            try {
                stored.contentHash = contentHash(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                stored.contentHash = "";
            }
            index.entries.add(stored);
        }
        index.entries.sort(Comparator.comparingLong((StoredEntry e) -> e.timestampMillis).reversed());
        return index;
    }

    private static void saveIndex(File historyDir, HistoryIndex index) throws IOException {
        Files.createDirectories(historyDir.toPath());
        Files.writeString(new File(historyDir, INDEX_FILE).toPath(), GSON.toJson(index), StandardCharsets.UTF_8);
    }

    private static String contentHash(String content) {
        CRC32 crc = new CRC32();
        crc.update((content != null ? content : "").getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }
}
