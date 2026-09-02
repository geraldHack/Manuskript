package com.manuskript.review;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Projektseitige Review-Dateien unter {@code lektorat/}. Nie die Live-MD.
 */
public final class NiReviewStore {

    private static final Logger logger = LoggerFactory.getLogger(NiReviewStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile File currentBookDirectory;

    private NiReviewStore() {
    }

    public static void setCurrentBookDirectory(File bookDir) {
        currentBookDirectory = bookDir != null && bookDir.isDirectory() ? bookDir.getAbsoluteFile() : null;
    }

    public static File currentBookDirectory() {
        return currentBookDirectory;
    }

    public static File lektoratDir(File bookDir) {
        if (bookDir == null) {
            return null;
        }
        return new File(bookDir, "lektorat");
    }

    public static File statusFile(File bookDir) {
        File dir = lektoratDir(bookDir);
        return dir == null ? null : new File(dir, "status.json");
    }

    public static File reviewFile(File bookDir, String chapterKey) {
        File dir = lektoratDir(bookDir);
        if (dir == null || chapterKey == null || chapterKey.isBlank()) {
            return null;
        }
        String safe = chapterKey.endsWith(".md")
                ? chapterKey.substring(0, chapterKey.length() - 3)
                : chapterKey;
        return new File(dir, safe + ".review.json");
    }

    public static NiReviewProjectStatus loadStatus(File bookDir) {
        File file = statusFile(bookDir);
        if (file == null || !file.isFile()) {
            return new NiReviewProjectStatus();
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            NiReviewProjectStatus status = GSON.fromJson(json, NiReviewProjectStatus.class);
            return status != null ? status : new NiReviewProjectStatus();
        } catch (Exception e) {
            logger.warn("NI-Lektorat-Status unlesbar: {}", file, e);
            return new NiReviewProjectStatus();
        }
    }

    public static void saveStatus(File bookDir, NiReviewProjectStatus status) throws IOException {
        File file = statusFile(bookDir);
        if (file == null) {
            throw new IOException("Kein Projektordner für NI-Lektorat");
        }
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), GSON.toJson(status), StandardCharsets.UTF_8);
    }

    public static NiReviewDocument loadReview(File bookDir, String chapterKey) {
        File file = reviewFile(bookDir, chapterKey);
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return readDocument(file.toPath());
        } catch (Exception e) {
            logger.warn("Review-JSON unlesbar: {}", file, e);
            return null;
        }
    }

    public static void saveReview(File bookDir, String chapterKey, NiReviewDocument document) throws IOException {
        File file = reviewFile(bookDir, chapterKey);
        if (file == null) {
            throw new IOException("Kein Review-Pfad für " + chapterKey);
        }
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), GSON.toJson(document), StandardCharsets.UTF_8);
    }

    public static void deleteReview(File bookDir, String chapterKey) {
        File file = reviewFile(bookDir, chapterKey);
        if (file != null && file.isFile() && !file.delete()) {
            logger.warn("Review-JSON nicht löschbar: {}", file);
        }
        NiReviewProjectStatus status = loadStatus(bookDir);
        if (status.getChapters().remove(chapterKey) != null) {
            try {
                if (status.getChapters().isEmpty()) {
                    File statusFile = statusFile(bookDir);
                    if (statusFile != null && statusFile.isFile()) {
                        Files.deleteIfExists(statusFile.toPath());
                    }
                } else {
                    saveStatus(bookDir, status);
                }
            } catch (IOException e) {
                logger.warn("Status nach Review-Löschen nicht speicherbar", e);
            }
        }
    }

    public static boolean isInLektorat(File bookDir, String chapterKey) {
        if (bookDir == null || chapterKey == null) {
            return false;
        }
        NiReviewProjectStatus.Entry entry = loadStatus(bookDir).getChapters().get(chapterKey);
        return entry != null;
    }

    public static boolean isReturned(File bookDir, String chapterKey) {
        if (bookDir == null || chapterKey == null) {
            return false;
        }
        NiReviewProjectStatus.Entry entry = loadStatus(bookDir).getChapters().get(chapterKey);
        return entry != null && NiReviewProjectStatus.STATE_RETURNED.equals(entry.getState());
    }

    public static void markOutForReview(File bookDir, String chapterKey, String roundId, String baseHash) throws IOException {
        NiReviewProjectStatus status = loadStatus(bookDir);
        NiReviewProjectStatus.Entry entry = new NiReviewProjectStatus.Entry();
        entry.setRoundId(roundId);
        entry.setBaseHash(baseHash);
        entry.setState(NiReviewProjectStatus.STATE_OUT);
        entry.setSentAt(NiReviewHashes.nowIso());
        status.getChapters().put(chapterKey, entry);
        saveStatus(bookDir, status);
    }

    public static void markReturned(File bookDir, String chapterKey) throws IOException {
        NiReviewProjectStatus status = loadStatus(bookDir);
        NiReviewProjectStatus.Entry entry = status.getChapters().get(chapterKey);
        if (entry != null) {
            entry.setState(NiReviewProjectStatus.STATE_RETURNED);
            saveStatus(bookDir, status);
        }
    }

    public static void deleteAllInCurrentProject() {
        deleteAll(currentBookDirectory);
    }

    public static void deleteAll(File bookDir) {
        File dir = lektoratDir(bookDir);
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && (file.getName().endsWith(".json") || file.getName().endsWith(".review.json"))) {
                if (!file.delete()) {
                    logger.warn("Konnte Review-Datei nicht löschen: {}", file);
                }
            }
        }
    }

    public static NiReviewDocument readDocument(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        NiReviewDocument document = GSON.fromJson(json, NiReviewDocument.class);
        return document != null ? document : new NiReviewDocument();
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }
}
