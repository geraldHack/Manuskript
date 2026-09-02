package com.manuskript.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Lektoratspaket: Snapshot-MD, Review-JSON und Bilder. Schreibt niemals ins Autor-Projekt.
 */
public final class NiReviewZip {

    public static final String ASSETS_PREFIX = "assets/";

    public record Loaded(
            NiReviewManifest manifest,
            Map<String, String> snapshots,
            Map<String, NiReviewDocument> reviews,
            Map<String, byte[]> assets) {
        public Loaded {
            snapshots = snapshots == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshots);
            reviews = reviews == null ? new LinkedHashMap<>() : new LinkedHashMap<>(reviews);
            assets = assets == null ? new LinkedHashMap<>() : new LinkedHashMap<>(assets);
        }
    }

    private NiReviewZip() {
    }

    public static void write(Path zipFile, NiReviewManifest manifest,
                             Map<String, String> snapshots,
                             Map<String, NiReviewDocument> reviews) throws IOException {
        write(zipFile, manifest, snapshots, reviews, Map.of());
    }

    public static void write(Path zipFile, NiReviewManifest manifest,
                             Map<String, String> snapshots,
                             Map<String, NiReviewDocument> reviews,
                             Map<String, byte[]> assets) throws IOException {
        if (zipFile.getParent() != null) {
            Files.createDirectories(zipFile.getParent());
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            put(zip, "manifest.json", NiReviewStore.toJson(manifest));
            for (NiReviewManifest.ChapterRef ref : manifest.getChapters()) {
                String md = snapshots != null ? snapshots.get(ref.getChapterKey()) : null;
                if (md != null) {
                    put(zip, ref.getMdFile(), md);
                }
                NiReviewDocument review = reviews != null ? reviews.get(ref.getChapterKey()) : null;
                if (review != null) {
                    put(zip, ref.getReviewFile(), NiReviewStore.toJson(review));
                }
            }
            if (assets != null) {
                for (Map.Entry<String, byte[]> asset : assets.entrySet()) {
                    if (asset.getKey() == null || asset.getValue() == null) {
                        continue;
                    }
                    putBytes(zip, ASSETS_PREFIX + asset.getKey().replace('\\', '/'), asset.getValue());
                }
            }
        }
    }

    public static Loaded read(Path zipFile) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                files.put(entry.getName().replace('\\', '/'), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        byte[] manifestBytes = files.get("manifest.json");
        if (manifestBytes == null) {
            throw new IOException("Kein manifest.json im Lektoratspaket");
        }
        NiReviewManifest manifest = NiReviewStore.fromJson(
                new String(manifestBytes, StandardCharsets.UTF_8), NiReviewManifest.class);
        if (manifest == null || manifest.getChapters().isEmpty()) {
            throw new IOException("Lektoratspaket enthält keine Kapitel");
        }
        Map<String, String> snapshots = new LinkedHashMap<>();
        Map<String, NiReviewDocument> reviews = new LinkedHashMap<>();
        for (NiReviewManifest.ChapterRef ref : manifest.getChapters()) {
            byte[] md = files.get(normalize(ref.getMdFile()));
            if (md != null) {
                snapshots.put(ref.getChapterKey(), new String(md, StandardCharsets.UTF_8));
            }
            byte[] reviewJson = files.get(normalize(ref.getReviewFile()));
            if (reviewJson != null) {
                NiReviewDocument document = NiReviewStore.fromJson(
                        new String(reviewJson, StandardCharsets.UTF_8), NiReviewDocument.class);
                if (document != null) {
                    reviews.put(ref.getChapterKey(), document);
                }
            }
        }
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String name = file.getKey();
            if (name.startsWith(ASSETS_PREFIX) && name.length() > ASSETS_PREFIX.length()) {
                assets.put(name.substring(ASSETS_PREFIX.length()), file.getValue());
            }
        }
        return new Loaded(manifest, snapshots, reviews, assets);
    }

    public static void writeReturned(Path zipFile, Loaded loaded) throws IOException {
        NiReviewManifest manifest = loaded.manifest();
        manifest.setStatus(NiReviewManifest.STATUS_RETURNED);
        write(zipFile, manifest, loaded.snapshots(), loaded.reviews(), loaded.assets());
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        putBytes(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name.replace('\\', '/'));
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }
}
