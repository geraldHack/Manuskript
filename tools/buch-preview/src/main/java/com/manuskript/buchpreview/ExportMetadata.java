package com.manuskript.buchpreview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * Liest Klappentext, Cover und Exportordner aus {@code data/pandoc_metadata.json}
 * (Exportmodul), mit Fallbacks auf Publish-Paket und {@code cover_image.png}.
 */
public final class ExportMetadata {

    public final String title;
    public final String author;
    public final String authorInfo;
    public final String blurb;
    public final String coverPath;
    public final String outputDirectory;

    public ExportMetadata(String title, String author, String blurb, String coverPath) {
        this(title, author, blurb, coverPath, "");
    }

    public ExportMetadata(String title, String author, String blurb, String coverPath, String outputDirectory) {
        this(title, author, blurb, coverPath, outputDirectory, "");
    }

    public ExportMetadata(
            String title,
            String author,
            String blurb,
            String coverPath,
            String outputDirectory,
            String authorInfo) {
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.blurb = blurb == null ? "" : blurb;
        this.coverPath = coverPath == null ? "" : coverPath;
        this.outputDirectory = outputDirectory == null ? "" : outputDirectory;
        this.authorInfo = authorInfo == null ? "" : authorInfo;
    }

    public static Path metadataFile(Path projectRoot) {
        return projectRoot.resolve("data").resolve("pandoc_metadata.json");
    }

    public static ExportMetadata load(Path projectRoot) {
        return load(projectRoot, false);
    }

    public static ExportMetadata load(Path projectRoot, boolean includeUserPrefs) {
        String title = "";
        String author = "";
        String authorInfo = "";
        String blurb = "";
        String coverPath = "";
        String outputDirectory = "";
        if (projectRoot != null) {
            Path file = findMetadataFile(projectRoot);
            if (file != null && Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    title = text(obj, "title");
                    author = text(obj, "author");
                    authorInfo = first(obj, "authorNote", "author-note", "authorBio", "author-bio");
                    blurb = text(obj, "abstract");
                    coverPath = first(obj, "coverImage", "cover-image", "cover");
                    outputDirectory = text(obj, "outputDirectory");
                } catch (Exception ignored) {
                    // Fallbacks unten
                }
            }
            if (blurb.isBlank()) {
                blurb = blurbFromPublishPackage(projectRoot);
            }
            if (authorInfo.isBlank()) {
                authorInfo = authorInfoFromPublishPackage(projectRoot);
            }
            if (coverPath.isBlank()) {
                Path fallback = defaultCoverFile(projectRoot);
                if (fallback != null) {
                    coverPath = fallback.toString();
                }
            }
        }
        if (includeUserPrefs) {
            ExportMetadata prefs = fromUserPreferences();
            title = firstNonBlank(title, prefs.title);
            author = firstNonBlank(author, prefs.author);
            authorInfo = firstNonBlank(authorInfo, prefs.authorInfo);
            blurb = firstNonBlank(blurb, prefs.blurb);
            coverPath = firstNonBlank(coverPath, prefs.coverPath);
            if (outputDirectory.isBlank() || foreignWindowsPath(outputDirectory)) {
                if (!prefs.outputDirectory.isBlank() && !foreignWindowsPath(prefs.outputDirectory)) {
                    outputDirectory = prefs.outputDirectory;
                } else if (outputDirectory.isBlank()) {
                    outputDirectory = prefs.outputDirectory;
                }
            }
        }
        return new ExportMetadata(title, author, blurb, coverPath, outputDirectory, authorInfo);
    }

    static Path findMetadataFile(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        Path standard = metadataFile(projectRoot);
        if (Files.isRegularFile(standard)) {
            return standard;
        }
        Path loose = projectRoot.resolve("pandoc_metadata.json");
        if (Files.isRegularFile(loose)) {
            return loose;
        }
        Path dir = projectRoot.getParent();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path up = dir.resolve("data").resolve("pandoc_metadata.json");
            if (Files.isRegularFile(up)) {
                return up;
            }
            dir = dir.getParent();
        }
        return null;
    }

    public static ExportMetadata loadForExport(Path projectRoot, Path htmlFile) {
        ExportMetadata fromProject = load(projectRoot, true);
        if (!fromProject.blurb.isBlank() && findMetadataFile(projectRoot) != null) {
            return fromProject;
        }
        Path bookDir = findBookDirForHtml(htmlFile);
        if (bookDir != null) {
            ExportMetadata fromBook = load(bookDir, true);
            return new ExportMetadata(
                    firstNonBlank(fromProject.title, fromBook.title),
                    firstNonBlank(fromProject.author, fromBook.author),
                    firstNonBlank(fromProject.blurb, fromBook.blurb),
                    firstNonBlank(fromProject.coverPath, fromBook.coverPath),
                    firstNonBlank(fromProject.outputDirectory, fromBook.outputDirectory),
                    firstNonBlank(fromProject.authorInfo, fromBook.authorInfo));
        }
        if (fromProject.blurb.isBlank() && htmlFile != null) {
            try {
                String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
                String fromHtml = HtmlBodyExtractor.abstractText(html);
                if (!fromHtml.isBlank()) {
                    return new ExportMetadata(fromProject.title, fromProject.author, fromHtml,
                            fromProject.coverPath, fromProject.outputDirectory, fromProject.authorInfo);
                }
            } catch (Exception ignored) {
            }
        }
        return fromProject;
    }

    static Path findBookDirForHtml(Path htmlFile) {
        if (htmlFile == null || htmlFile.getFileName() == null) {
            return null;
        }
        String fileName = htmlFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        final String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        final String spaced = stem.replace('_', ' ');
        String home = System.getProperty("user.home", "");
        if (home.isBlank()) {
            return null;
        }
        for (String booksName : List.of("manuskripte", "Manuskripte")) {
            Path books = Path.of(home, booksName);
            if (!Files.isDirectory(books)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(books, 4)) {
                Path match = walk.filter(Files::isDirectory)
                        .filter(dir -> dir.getFileName() != null)
                        .filter(dir -> {
                            String name = dir.getFileName().toString();
                            return name.equalsIgnoreCase(spaced) || name.equalsIgnoreCase(stem);
                        })
                        .filter(dir -> Files.isRegularFile(dir.resolve("data").resolve("pandoc_metadata.json")))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    return match;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static ExportMetadata fromUserPreferences() {
        try {
            Preferences prefs = Preferences.userRoot().node("com/manuskript");
            return new ExportMetadata(
                    prefs.get("pandoc_title", ""),
                    prefs.get("pandoc_author", ""),
                    prefs.get("pandoc_abstract", ""),
                    prefs.get("pandoc_cover_image", ""),
                    prefs.get("pandoc_output_directory", ""),
                    prefs.get("pandoc_author_note", ""));
        } catch (Exception e) {
            return new ExportMetadata("", "", "", "", "");
        }
    }

    public List<Path> searchRoots(Path projectRoot) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addDir(roots, projectRoot);
        addDir(roots, existingDirectory(outputDirectory, projectRoot));
        for (Path relocated : relocateForeignPath(outputDirectory, projectRoot)) {
            addDir(roots, relocated);
        }
        for (Path extra : knownExportPlaces(projectRoot)) {
            addDir(roots, extra);
        }
        return new ArrayList<>(roots);
    }

    static List<Path> relocateForeignPath(String raw, Path projectRoot) {
        List<Path> found = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return found;
        }
        String last = lastPathSegment(raw);
        List<Path> bases = new ArrayList<>();
        addDirList(bases, projectRoot);
        if (projectRoot != null) {
            addDirList(bases, projectRoot.getParent());
        }
        for (Path base : bases) {
            if (last != null && !last.isBlank()) {
                addDirList(found, base.resolve(last));
                for (String alias : folderAliases(last)) {
                    addDirList(found, base.resolve(alias));
                }
            }
            addDirList(found, base.resolve(raw));
        }
        return found;
    }

    static List<String> folderAliases(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String lower = name.toLowerCase();
        if (lower.equals("exporte") || lower.equals("exports")) {
            return List.of("export", "exporte", "exports");
        }
        if (lower.equals("export")) {
            return List.of("exporte", "exports");
        }
        return List.of();
    }

    static List<Path> knownExportPlaces(Path projectRoot) {
        List<Path> places = new ArrayList<>();
        List<Path> bases = new ArrayList<>();
        addDirList(bases, projectRoot);
        String cwd = System.getProperty("user.dir", "");
        if (!cwd.isBlank()) {
            addDirList(bases, Path.of(cwd));
        }
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            Path homePath = Path.of(home);
            addDirList(bases, homePath);
            addDirList(bases, homePath.resolve("Documents"));
            addDirList(bases, homePath.resolve("Dokumente"));
            addDirList(bases, homePath.resolve("Desktop"));
        }
        for (Path base : bases) {
            addDirList(places, base.resolve("export"));
            addDirList(places, base.resolve("exporte"));
            addDirList(places, base.resolve("G:\\exporte"));
            addDirList(places, base.resolve("exports"));
        }
        return places;
    }

    /** Kurzer Bericht, wo Metadaten und HTML gesucht werden. */
    public static String describeSearch(Path projectRoot, ExportMetadata meta) {
        StringBuilder sb = new StringBuilder();
        if (projectRoot == null) {
            sb.append("Kein Buchordner geöffnet.");
            return sb.toString();
        }
        Path metaFile = findMetadataFile(projectRoot);
        sb.append("Buch: ").append(projectRoot);
        if (metaFile != null) {
            sb.append(" · Metadaten: ").append(metaFile);
        } else {
            sb.append(" · keine ").append(metadataFile(projectRoot));
        }
        if (meta != null && !meta.outputDirectory.isBlank()) {
            sb.append(" · Ziel im Exportmodul: ").append(meta.outputDirectory);
        }
        List<Path> roots = meta != null ? meta.searchRoots(projectRoot) : List.of(projectRoot);
        if (!roots.isEmpty()) {
            sb.append(" · HTML-Suche:");
            int n = 0;
            for (Path root : roots) {
                if (n >= 6) {
                    sb.append(" …");
                    break;
                }
                sb.append(" ").append(root);
                n++;
            }
        }
        return sb.toString();
    }

    static String lastPathSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.trim().replace('/', '\\');
        while (normalized.endsWith("\\")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = normalized.lastIndexOf('\\');
        String last = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (last.length() >= 2 && last.charAt(1) == ':') {
            return "";
        }
        return last;
    }

    private static void addDir(LinkedHashSet<Path> roots, Path dir) {
        if (dir != null && Files.isDirectory(dir)) {
            roots.add(dir.toAbsolutePath().normalize());
        }
    }

    private static void addDirList(List<Path> list, Path dir) {
        if (dir != null && Files.isDirectory(dir)) {
            list.add(dir);
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    public Path resolveCover(Path projectRoot) {
        Path fromField = existingFile(coverPath, projectRoot);
        if (fromField != null) {
            return fromField;
        }
        return defaultCoverFile(projectRoot);
    }

    public Path resolveOutputDirectory(Path projectRoot) {
        return existingDirectory(outputDirectory, projectRoot);
    }

    static Path defaultCoverFile(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        for (String name : List.of("cover_image.png", "cover.png", "cover.jpg", "cover.jpeg", "cover.webp")) {
            Path candidate = projectRoot.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static Path coverInFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return null;
        }
        for (String name : List.of("cover_image.png", "cover.png", "cover.jpg", "cover.jpeg",
                "cover.webp", "cover.gif")) {
            Path candidate = folder.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static Path existingFile(String raw, Path projectRoot) {
        if (raw == null || raw.isBlank() || foreignWindowsPath(raw)) {
            return null;
        }
        Path cover = Path.of(raw);
        if (cover.isAbsolute() && Files.isRegularFile(cover)) {
            return cover;
        }
        if (projectRoot != null) {
            Path relative = projectRoot.resolve(raw);
            if (Files.isRegularFile(relative)) {
                return relative;
            }
        }
        return Files.isRegularFile(cover) ? cover : null;
    }

    static Path existingDirectory(String raw, Path projectRoot) {
        if (raw == null || raw.isBlank() || foreignWindowsPath(raw)) {
            return null;
        }
        Path dir = Path.of(raw);
        if (Files.isDirectory(dir)) {
            return dir;
        }
        if (projectRoot != null) {
            Path relative = projectRoot.resolve(raw);
            if (Files.isDirectory(relative)) {
                return relative;
            }
            Path slashed = projectRoot.resolve(raw.replace('\\', '/'));
            if (Files.isDirectory(slashed)) {
                return slashed;
            }
        }
        return Files.isDirectory(dir) ? dir : null;
    }

    static boolean foreignWindowsPath(String raw) {
        if (raw == null || raw.isBlank() || File.separatorChar == '\\') {
            return false;
        }
        String trimmed = raw.trim();
        return trimmed.length() >= 3
                && Character.isLetter(trimmed.charAt(0))
                && trimmed.charAt(1) == ':'
                && (trimmed.charAt(2) == '\\' || trimmed.charAt(2) == '/');
    }

    private static String blurbFromPublishPackage(Path projectRoot) {
        Path file = projectRoot.resolve("data").resolve("publish_package.json");
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            return text(obj, "blurb");
        } catch (Exception e) {
            return "";
        }
    }

    private static String authorInfoFromPublishPackage(Path projectRoot) {
        Path file = projectRoot.resolve("data").resolve("publish_package.json");
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            return first(obj, "authorBio", "authorNote", "author-bio");
        } catch (Exception e) {
            return "";
        }
    }

    private static String first(JsonObject obj, String... keys) {
        for (String key : keys) {
            String value = text(obj, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
