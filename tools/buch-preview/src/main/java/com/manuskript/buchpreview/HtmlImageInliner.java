package com.manuskript.buchpreview;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bild-Pfade aus dem HTML5-Export: relative Dateien als {@code file://},
 * damit die WebView sie laden kann, ohne Megabyte-Base64 in JavaScript.
 */
final class HtmlImageInliner {

    private static final Pattern IMG_SRC = Pattern.compile(
            "(?is)(<img\\b[^>]*?\\bsrc\\s*=\\s*)(['\"])([^'\"]+)\\2");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private HtmlImageInliner() {
    }

    static String toFileUrls(String html, Path folder) {
        if (html == null || html.isBlank() || folder == null) {
            return html == null ? "" : html;
        }
        Path root = folder.toAbsolutePath().normalize();
        Matcher matcher = IMG_SRC.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String src = matcher.group(3).trim();
            String fileUrl = toFileUrl(root, src);
            if (fileUrl.isEmpty()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(
                        matcher.group(1) + matcher.group(2) + fileUrl + matcher.group(2)));
            }
        }
        matcher.appendTail(out);
        return dropUninlined(out.toString());
    }

    static String inline(String html, Path folder) {
        if (html == null || html.isBlank() || folder == null) {
            return html == null ? "" : html;
        }
        Path root = folder.toAbsolutePath().normalize();
        Matcher matcher = Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String tag = matcher.group();
            Matcher src = IMG_SRC.matcher(tag);
            String replacement = "";
            if (src.find()) {
                String value = src.group(3).trim();
                String lower = value.toLowerCase(Locale.ROOT);
                if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
                    replacement = tag;
                } else {
                    PreviewImage.Embedded embedded = PreviewImage.embed(resolveLocal(root, value));
                    if (embedded != null) {
                        replacement = "<img class=\"bp-image\" src=\"" + embedded.dataUri()
                                + "\" width=\"" + embedded.width()
                                + "\" height=\"" + embedded.height()
                                + "\" alt=\"\">";
                    }
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String dropUninlined(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher matcher = Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String tag = matcher.group();
            Matcher src = IMG_SRC.matcher(tag);
            boolean keep = false;
            if (src.find()) {
                String value = src.group(3).trim().toLowerCase(Locale.ROOT);
                keep = value.startsWith("data:")
                        || value.startsWith("http://")
                        || value.startsWith("https://")
                        || value.startsWith("file:");
            }
            matcher.appendReplacement(out, keep ? Matcher.quoteReplacement(tag) : "");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static int countImgTags(String html) {
        if (html == null || html.isEmpty()) {
            return 0;
        }
        Matcher matcher = Pattern.compile("(?i)<img\\b").matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    static int countDataImages(String html) {
        if (html == null || html.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while ((from = html.indexOf("data:image", from)) >= 0) {
            count++;
            from += 10;
        }
        return count;
    }

    private static String toFileUrl(Path root, String src) {
        Path resolved = resolveLocal(root, src);
        if (resolved == null) {
            return "";
        }
        return resolved.toUri().toString();
    }

    private static String toDataUri(Path root, String src) {
        PreviewImage.Embedded embedded = PreviewImage.embed(resolveLocal(root, src));
        return embedded == null ? "" : embedded.dataUri();
    }

    private static Path resolveLocal(Path root, String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String lower = src.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:") || lower.startsWith("http://")
                || lower.startsWith("https://") || lower.startsWith("file:")) {
            return null;
        }
        String relative = src.replace('\\', '/');
        int hash = relative.indexOf('#');
        if (hash >= 0) {
            relative = relative.substring(0, hash);
        }
        int query = relative.indexOf('?');
        if (query >= 0) {
            relative = relative.substring(0, query);
        }
        if (relative.startsWith("/")) {
            return null;
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        try {
            if (!java.nio.file.Files.isRegularFile(resolved)
                    || java.nio.file.Files.size(resolved) > MAX_BYTES) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        return resolved;
    }
}
