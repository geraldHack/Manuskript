package com.manuskript.review;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Lektor-Arbeitskopie: ZIP wird ein eigenes Buch (Cover, Bilder, Kapitel).
 */
public final class NiReviewProject {

    static final Set<String> IMAGE_SUFFIXES = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");
    static final String CHAPTER_ORDER_FILE = ".manuskript_selection.json";
    private static final String SNAPSHOT_DIR = "snapshots";
    private static final Gson GSON = new Gson();

    private NiReviewProject() {
    }

    /**
     * Arbeitskopien des Lektors: immer {@code ~/Documents/Lektorat}, nie der Werke-Stamm.
     */
    public static File lektorWorkingCopiesDirectory() {
        File homeDocuments = new File(System.getProperty("user.home", "."), "Documents");
        File documents = homeDocuments.isDirectory() || homeDocuments.mkdirs()
                ? homeDocuments
                : com.manuskript.ApplicationPaths.userDocumentsDirectory();
        return lektorWorkingCopiesDirectory(documents);
    }

    static File lektorWorkingCopiesDirectory(File documents) {
        if (documents == null) {
            throw new IllegalStateException("Kein Dokumente-Ordner");
        }
        File parent = new File(documents, "Lektorat");
        if (!parent.isDirectory()) {
            parent.mkdirs();
        }
        return parent;
    }

    public static File defaultAuthorZip(File bookDir) {
        File documents = com.manuskript.ApplicationPaths.userDocumentsDirectory();
        if (documents != null && !documents.isDirectory()) {
            documents.mkdirs();
        }
        return new File(documents, authorZipFileName(bookDir));
    }

    public static String authorZipFileName(File bookDir) {
        return sanitizeProjectName(bookDir != null ? bookDir.getName() : "Lektorat") + "-lektorat.ni.zip";
    }

    public static String returnZipFileName() {
        return "lektorat-rueckgabe.ni.zip";
    }

    public static File withNiZipExtension(File chosen) {
        if (chosen == null) {
            return null;
        }
        String name = chosen.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".ni.zip")) {
            return chosen;
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            String stem = name.substring(0, name.length() - 4);
            return new File(chosen.getParentFile(), stem + ".ni.zip");
        }
        return new File(chosen.getParentFile(), name + ".ni.zip");
    }

    public static String sanitizeProjectName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isBlank() || name.equalsIgnoreCase("lektorat.ni.zip") || name.equalsIgnoreCase("lektorat")) {
            name = "Lektorat";
        }
        name = name.replaceAll("[<>:\"/\\\\|?*]", "_").replaceAll("\\s+", " ").trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name.substring(0, name.length() - 4).trim();
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".ni")) {
            name = name.substring(0, name.length() - 3).trim();
        }
        return name.isBlank() ? "Lektorat" : name;
    }

    public static Path uniqueProjectDirectory(Path parent, String baseName) throws IOException {
        if (parent == null) {
            throw new IOException("Kein Zielordner für das Lektor-Projekt");
        }
        Files.createDirectories(parent);
        String clean = sanitizeProjectName(baseName);
        Path first = parent.resolve(clean);
        if (!Files.exists(first)) {
            return first;
        }
        for (int n = 2; n < 1000; n++) {
            Path next = parent.resolve(clean + " " + n);
            if (!Files.exists(next)) {
                return next;
            }
        }
        throw new IOException("Kein freier Projektname für " + clean);
    }

    public static Map<String, byte[]> collectAssets(File bookDir, List<NiReviewActions.ChapterSource> chapters)
            throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        if (bookDir == null || !bookDir.isDirectory()) {
            return assets;
        }
        Path root = bookDir.toPath();
        addImagesIn(root, root, assets);
        Path data = root.resolve("data");
        if (Files.isDirectory(data)) {
            addImagesIn(data, root, assets);
        }
        if (chapters != null) {
            for (NiReviewActions.ChapterSource chapter : chapters) {
                addReferencedImages(root, chapter == null ? "" : chapter.markdown(), assets);
            }
        }
        return assets;
    }

    public static Path materialize(Path zipFile, Path parentDir) throws IOException {
        NiReviewZip.Loaded loaded = NiReviewZip.read(zipFile);
        String name = loaded.manifest().getProjectName();
        if (name == null || name.isBlank()) {
            name = zipFile.getFileName().toString();
        }
        Path project = uniqueProjectDirectory(parentDir, name);
        Files.createDirectories(project.resolve("data"));
        List<String> chapterOrder = new ArrayList<>();
        for (NiReviewManifest.ChapterRef ref : loaded.manifest().getChapters()) {
            String key = ref.getChapterKey();
            String stem = stemOf(key, ref.getDocxHint());
            String md = loaded.snapshots().getOrDefault(key, "");
            Path mdFile = project.resolve("data").resolve(stem + ".md");
            Files.writeString(mdFile, md, StandardCharsets.UTF_8);
            String docxName = stem + ".docx";
            writeStubDocx(project.resolve(docxName), stem);
            chapterOrder.add(docxName);
            Path snapshot = NiReviewStore.lektoratDir(project.toFile()).toPath().resolve(SNAPSHOT_DIR).resolve(key);
            Files.createDirectories(snapshot.getParent());
            Files.writeString(snapshot, md, StandardCharsets.UTF_8);
            NiReviewDocument review = loaded.reviews().get(key);
            if (review != null) {
                NiReviewStore.saveReview(project.toFile(), key, review);
            }
        }
        writeChapterOrder(project.toFile(), chapterOrder);
        for (Map.Entry<String, byte[]> asset : loaded.assets().entrySet()) {
            Path dest = project.resolve(asset.getKey()).normalize();
            if (!dest.startsWith(project)) {
                continue;
            }
            Files.createDirectories(dest.getParent());
            Files.write(dest, asset.getValue());
        }
        return project;
    }

    public static boolean hasAuthorSnapshots(File bookDir) {
        Path dir = snapshotDir(bookDir);
        return dir != null && Files.isDirectory(dir);
    }

    public static String readChapterSnapshot(File bookDir, String chapterKey) {
        Path dir = snapshotDir(bookDir);
        if (dir == null || chapterKey == null) {
            return null;
        }
        Path file = dir.resolve(chapterKey);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeReturnZip(File bookDir, Path zipFile) throws IOException {
        if (bookDir == null || !bookDir.isDirectory()) {
            throw new IOException("Kein Lektor-Projekt geöffnet");
        }
        Path data = bookDir.toPath().resolve("data");
        if (!Files.isDirectory(data)) {
            throw new IOException("Keine Kapitel im Lektor-Projekt");
        }
        NiReviewManifest manifest = new NiReviewManifest();
        manifest.setProjectName(bookDir.getName());
        manifest.setAuthor(NiReviewRole.reviewerName());
        manifest.setCreated(NiReviewHashes.nowIso());
        manifest.setStatus(NiReviewManifest.STATUS_RETURNED);
        Map<String, String> snapshots = new LinkedHashMap<>();
        Map<String, NiReviewDocument> reviews = new LinkedHashMap<>();
        for (String chapterKey : orderedChapterKeys(bookDir)) {
            Path mdFile = resolveMdFile(bookDir, chapterKey);
            if (mdFile == null) {
                continue;
            }
            String live = Files.readString(mdFile, StandardCharsets.UTF_8);
            String original = readSnapshot(bookDir, chapterKey, live);
            String stem = chapterKey.endsWith(".md")
                    ? chapterKey.substring(0, chapterKey.length() - 3)
                    : chapterKey;
            NiReviewManifest.ChapterRef ref = new NiReviewManifest.ChapterRef();
            ref.setChapterKey(chapterKey);
            ref.setDocxHint(NiReviewActions.docxHintForChapterKey(chapterKey));
            ref.setMdFile("chapters/" + stem + ".md");
            ref.setReviewFile("chapters/" + stem + ".review.json");
            ref.setBaseHash(NiReviewHashes.sha256(original));
            manifest.getChapters().add(ref);
            snapshots.put(chapterKey, original);
            reviews.put(chapterKey, reviewFor(bookDir, chapterKey, original, live));
        }
        if (manifest.getChapters().isEmpty()) {
            throw new IOException("Keine Kapitel im Lektor-Projekt");
        }
        NiReviewZip.write(zipFile, manifest, snapshots, reviews, collectAssets(bookDir, List.of()));
    }

    public static void writeChapterOrder(File bookDir, List<String> docxFileNames) throws IOException {
        if (bookDir == null || docxFileNames == null || docxFileNames.isEmpty()) {
            return;
        }
        Path data = bookDir.toPath().resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve(CHAPTER_ORDER_FILE), GSON.toJson(docxFileNames), StandardCharsets.UTF_8);
    }

    public static List<String> readChapterOrder(File bookDir) {
        if (bookDir == null) {
            return List.of();
        }
        Path file = bookDir.toPath().resolve("data").resolve(CHAPTER_ORDER_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<String> names = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<String>>() { }.getType());
            return names != null ? names : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    static List<String> orderedChapterKeys(File bookDir) throws IOException {
        Path snapDir = snapshotDir(bookDir);
        Map<String, String> keyByDocx = new LinkedHashMap<>();
        if (snapDir != null && Files.isDirectory(snapDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapDir)) {
                for (Path snap : stream) {
                    String key = snap.getFileName().toString();
                    if (key.startsWith(".")) {
                        continue;
                    }
                    keyByDocx.put(NiReviewActions.docxHintForChapterKey(key), key);
                }
            }
        }
        List<String> ordered = new ArrayList<>();
        for (String docx : readChapterOrder(bookDir)) {
            String key = keyByDocx.remove(docx);
            if (key != null) {
                ordered.add(key);
            }
        }
        ordered.addAll(keyByDocx.values());
        if (!ordered.isEmpty()) {
            return ordered;
        }
        List<String> fallback = new ArrayList<>();
        for (Path md : markdownChaptersInOrder(bookDir)) {
            fallback.add(md.getFileName().toString());
        }
        return fallback;
    }

    static Path resolveMdFile(File bookDir, String chapterKey) {
        if (bookDir == null || chapterKey == null || chapterKey.isBlank()) {
            return null;
        }
        Path data = bookDir.toPath().resolve("data");
        String fileName = chapterKey.endsWith(".md") ? chapterKey : chapterKey + ".md";
        Path exact = data.resolve(fileName);
        if (Files.isRegularFile(exact)) {
            return exact;
        }
        Path legacy = data.resolve(legacySanitizedStem(chapterKey) + ".md");
        if (Files.isRegularFile(legacy)) {
            return legacy;
        }
        return null;
    }

    static String legacySanitizedStem(String chapterKey) {
        String stem = chapterKey.endsWith(".md") ? chapterKey.substring(0, chapterKey.length() - 3) : chapterKey;
        return stem.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    static List<Path> markdownChaptersInOrder(File bookDir) throws IOException {
        Path data = bookDir.toPath().resolve("data");
        Map<String, Path> byDocxName = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(data, "*.md")) {
            for (Path md : stream) {
                String name = md.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                String stem = name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
                byDocxName.put(stem + ".docx", md);
            }
        }
        List<Path> ordered = new ArrayList<>();
        for (String docxName : readChapterOrder(bookDir)) {
            Path md = byDocxName.remove(docxName);
            if (md != null) {
                ordered.add(md);
            }
        }
        ordered.addAll(byDocxName.values());
        return ordered;
    }

    static Path snapshotDir(File bookDir) {
        File lektorat = NiReviewStore.lektoratDir(bookDir);
        return lektorat == null ? null : lektorat.toPath().resolve(SNAPSHOT_DIR);
    }

    private static String readSnapshot(File bookDir, String chapterKey, String fallback) throws IOException {
        Path dir = snapshotDir(bookDir);
        if (dir == null) {
            return fallback;
        }
        Path file = dir.resolve(chapterKey);
        if (Files.isRegularFile(file)) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        return fallback;
    }

    private static NiReviewDocument reviewFor(File bookDir, String chapterKey, String original, String live) {
        NiReviewDocument stored = NiReviewStore.loadReview(bookDir, chapterKey);
        if (stored != null && stored.hasOpenItems()) {
            return stored;
        }
        NiReviewDocument document = stored != null ? stored : new NiReviewDocument();
        document.setChapterKey(chapterKey);
        document.setBaseHash(NiReviewHashes.sha256(original));
        if (original.equals(live)) {
            return document;
        }
        NiReviewChange change = new NiReviewChange();
        change.setKind(NiReviewChange.KIND_REPLACE);
        change.setStart(0);
        change.setEnd(original.length());
        change.setOldText(original);
        change.setNewText(live);
        change.setPrefix("");
        change.setSuffix("");
        change.setAuthor(NiReviewRole.reviewerName());
        change.setCreated(NiReviewHashes.nowIso());
        document.getChanges().add(change);
        return document;
    }

    private static void addImagesIn(Path folder, Path bookRoot, Map<String, byte[]> assets) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path) || !isImage(path.getFileName().toString())) {
                    continue;
                }
                Path relative = bookRoot.relativize(path);
                assets.putIfAbsent(relative.toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
    }

    private static void addReferencedImages(Path bookRoot, String markdown, Map<String, byte[]> assets)
            throws IOException {
        if (markdown == null || markdown.isBlank()) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "!\\[[^\\]]*\\]\\(([^)]+)\\)").matcher(markdown);
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            int brace = raw.indexOf('{');
            if (brace >= 0) {
                raw = raw.substring(0, brace).trim();
            }
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                continue;
            }
            Path resolved = resolveImage(bookRoot, raw);
            if (resolved == null) {
                continue;
            }
            Path relative = bookRoot.relativize(resolved);
            assets.putIfAbsent(relative.toString().replace('\\', '/'), Files.readAllBytes(resolved));
        }
    }

    private static Path resolveImage(Path bookRoot, String imagePath) {
        Path direct = Path.of(imagePath);
        if (direct.isAbsolute() && Files.isRegularFile(direct) && direct.normalize().startsWith(bookRoot)) {
            return direct;
        }
        Path[] candidates = {
                bookRoot.resolve(imagePath),
                bookRoot.resolve("data").resolve(imagePath)
        };
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (normalized.startsWith(bookRoot) && Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static boolean isImage(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String suffix : IMAGE_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String stemOf(String chapterKey, String docxHint) {
        if (docxHint != null && !docxHint.isBlank()) {
            String name = docxHint;
            if (name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                name = name.substring(0, name.length() - 5);
            }
            return name;
        }
        if (chapterKey != null && chapterKey.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return chapterKey.substring(0, chapterKey.length() - 3);
        }
        return chapterKey == null || chapterKey.isBlank() ? "Kapitel" : chapterKey;
    }

    static void writeStubDocx(Path dest, String title) throws IOException {
        String safe = title == null || title.isBlank() ? "Kapitel" : title;
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body>
                </w:document>
                """.formatted(escapeXml(safe));
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            put(zip, "word/_rels/document.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                    """);
            put(zip, "word/document.xml", document);
            zip.finish();
            Files.write(dest, bytes.toByteArray());
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
