package com.manuskript.buchpreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Findet HTML5-Exportordner ({@code *_html/*.html}) im Projekt und im
 * Exportordner aus {@code pandoc_metadata.json}. Überspringt {@code template.html}.
 */
public final class HtmlExportFinder {

    private HtmlExportFinder() {
    }

    public record HtmlExport(Path htmlFile, Path folder) {
        public String label() {
            Path parent = folder != null ? folder.getFileName() : htmlFile.getParent();
            String folderName = parent == null ? htmlFile.getFileName().toString() : parent.toString();
            return folderName + " / " + htmlFile.getFileName();
        }

        @Override
        public String toString() {
            return label();
        }
    }

    public static List<HtmlExport> find(Path projectRoot) {
        return find(projectRoot, false);
    }

    public static List<HtmlExport> find(Path projectRoot, boolean includeKnownPlaces) {
        Map<Path, HtmlExport> found = new LinkedHashMap<>();
        ExportMetadata meta = ExportMetadata.load(projectRoot, includeKnownPlaces);
        addFromRoot(projectRoot, found, 6);
        Path outputDir = meta.resolveOutputDirectory(projectRoot);
        if (outputDir != null) {
            addFromRoot(outputDir, found, 3);
            if (isHtmlExportDir(outputDir)) {
                addHtmlFiles(outputDir, found);
            }
        }
        List<Path> extras = new ArrayList<>(ExportMetadata.relocateForeignPath(meta.outputDirectory, projectRoot));
        if (includeKnownPlaces) {
            extras.addAll(meta.searchRoots(projectRoot));
            String home = System.getProperty("user.home", "");
            if (!home.isBlank()) {
                extras.add(Path.of(home, "export"));
                extras.add(Path.of(home, "exporte"));
            }
        }
        for (Path root : extras) {
            addFromRoot(root, found, 3);
            if (isHtmlExportDir(root)) {
                addHtmlFiles(root, found);
            }
        }
        List<HtmlExport> list = new ArrayList<>(found.values());
        list.sort(Comparator
                .comparingInt((HtmlExport e) -> matchScore(e, projectRoot, meta)).reversed()
                .thenComparing(Comparator.comparingLong((HtmlExport e) -> mtime(e.htmlFile)).reversed())
                .thenComparing(Comparator.comparingLong((HtmlExport e) -> size(e.htmlFile)).reversed()));
        return list;
    }

    /**
     * Höher = besser zum offenen Projekt. Verhindert, dass ein fremdes
     * {@code Held_neu} nur weil die HTML-Datei größer ist vor Gott landet,
     * und dass ein alter Titel in {@code pandoc_metadata.json} (z. B. Gott
     * in Kuppelwelt 0) das Export-HTML eines anderen Buchs gewinnt.
     */
    static int matchScore(HtmlExport export, Path projectRoot, ExportMetadata meta) {
        if (export == null || export.htmlFile() == null) {
            return 0;
        }
        int score = 0;
        Path html = export.htmlFile().toAbsolutePath().normalize();
        Path folder = export.folder() == null ? html.getParent() : export.folder();
        String folderSlug = slug(stripHtmlSuffix(folder == null ? "" : name(folder)));
        String fileSlug = slug(stripExtension(name(html)));
        String projectSlug = "";
        String parentSlug = "";
        if (projectRoot != null && Files.exists(projectRoot)) {
            Path root = projectRoot.toAbsolutePath().normalize();
            if (html.startsWith(root)) {
                score += 1000;
            }
            projectSlug = slug(name(root));
            Path parent = root.getParent();
            if (parent != null) {
                parentSlug = slug(name(parent));
            }
            score += nameScore(fileSlug, projectSlug, 500);
            score += nameScore(folderSlug, projectSlug, 500);
            if (!parentSlug.isBlank() && parentSlug.length() >= 5) {
                score += nameScore(fileSlug, parentSlug, 180);
                score += nameScore(folderSlug, parentSlug, 180);
            }
        }
        if (meta != null) {
            String titleSlug = slug(meta.title);
            boolean titleFitsProject = projectSlug.isBlank()
                    || overlaps(titleSlug, projectSlug)
                    || (!parentSlug.isBlank() && overlaps(titleSlug, parentSlug));
            if (!titleSlug.isBlank() && titleFitsProject) {
                score += nameScore(fileSlug, titleSlug, 300);
                score += nameScore(folderSlug, titleSlug, 200);
            }
            Path output = meta.resolveOutputDirectory(projectRoot);
            if (output != null && Files.isDirectory(output)) {
                Path out = output.toAbsolutePath().normalize();
                if (html.startsWith(out)) {
                    score += 80;
                }
            }
        }
        return score;
    }

    static int nameScore(String candidate, String project, int exact) {
        if (candidate.isBlank() || project.isBlank()) {
            return 0;
        }
        if (candidate.equals(project)) {
            return exact;
        }
        String a = stripTrailingVolume(candidate);
        String b = stripTrailingVolume(project);
        if (!a.isBlank() && a.equals(b)) {
            return exact - 40;
        }
        if (overlaps(candidate, project) || overlaps(a, b)) {
            return exact / 2;
        }
        return 0;
    }

    static String stripTrailingVolume(String slug) {
        if (slug == null || slug.length() < 6) {
            return slug == null ? "" : slug;
        }
        return slug.replaceFirst("\\d+$", "");
    }

    static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private static boolean overlaps(String a, String b) {
        return !a.isBlank() && !b.isBlank() && (a.contains(b) || b.contains(a));
    }

    private static String name(Path path) {
        Path fileName = path == null ? null : path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String stripHtmlSuffix(String folderName) {
        if (folderName.toLowerCase(Locale.ROOT).endsWith("_html")) {
            return folderName.substring(0, folderName.length() - 5);
        }
        return folderName;
    }

    static boolean isTemplate(Path htmlFile) {
        if (htmlFile == null || htmlFile.getFileName() == null) {
            return true;
        }
        String name = htmlFile.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("template.html") || name.endsWith(".template.html")
                || name.equals("_buch_preview.html")
                || name.equals("_taschenbuch_preview.html");
    }

    private static void addFromRoot(Path root, Map<Path, HtmlExport> found, int depth) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root, depth)) {
            walk.filter(Files::isDirectory)
                    .filter(HtmlExportFinder::isHtmlExportDir)
                    .filter(dir -> !isSkipped(root, dir))
                    .forEach(dir -> addHtmlFiles(dir, found));
        } catch (IOException ignored) {
            // Ordner unlesbar
        }
    }

    private static boolean isHtmlExportDir(Path dir) {
        return dir != null && dir.getFileName() != null
                && dir.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_html");
    }

    private static void addHtmlFiles(Path dir, Map<Path, HtmlExport> found) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html"))
                    .filter(p -> !isTemplate(p))
                    .forEach(p -> found.putIfAbsent(p.toAbsolutePath().normalize(), new HtmlExport(p, dir)));
        } catch (IOException ignored) {
        }
    }

    private static boolean isSkipped(Path root, Path dir) {
        Path relative;
        try {
            relative = root.relativize(dir);
        } catch (Exception e) {
            return true;
        }
        for (Path part : relative) {
            String name = part.toString();
            if (name.startsWith(".") || "node_modules".equals(name) || "target".equals(name)
                    || ".history".equals(name) || "plugins".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static long mtime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }
}
