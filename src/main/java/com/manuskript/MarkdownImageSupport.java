package com.manuskript;

import javafx.scene.image.Image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hilfsfunktionen für Markdown-Bilder im Manuskript-Format.
 * Bilder werden als Dateiname relativ zum Arbeitsverzeichnis gespeichert,
 * optional mit zentrierter Beschriftung als {@code ><c>Text</c>}.
 */
public final class MarkdownImageSupport {

    private static final Pattern IMAGE_BLOCK = Pattern.compile(
            "!\\[([^\\]]*)]\\(([^)]+)\\)(?:\\{\\s*width\\s*=\\s*(\\d+)%\\s*})?"
                    + "(?:\\r?\\n(?:\\r?\\n)*\\s*(?:><c>|><center>)(.*?)(?:</c>|</center>))?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private MarkdownImageSupport() {
    }

    public record ParsedBlock(int start, int end, String altText, String imagePath, int widthPercent, String caption) {
    }

    public static List<ParsedBlock> parseBlocks(String markdown) {
        List<ParsedBlock> blocks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return blocks;
        }

        Matcher matcher = IMAGE_BLOCK.matcher(markdown);
        while (matcher.find()) {
            String path = stripWidthSuffix(matcher.group(2));
            int widthPercent = 0;
            if (matcher.group(3) != null && !matcher.group(3).isBlank()) {
                try {
                    widthPercent = Integer.parseInt(matcher.group(3).trim());
                } catch (NumberFormatException ignored) {
                    widthPercent = 0;
                }
            }
            String caption = matcher.group(4);
            if (caption != null) {
                caption = caption.trim();
            }
            blocks.add(new ParsedBlock(
                    matcher.start(),
                    matcher.end(),
                    matcher.group(1),
                    path.trim(),
                    widthPercent,
                    caption));
        }
        return blocks;
    }

    public static String buildMarkdown(String fileName, String caption) {
        return buildMarkdown(fileName, caption, 80);
    }

    public static String buildMarkdown(String fileName, String caption, int widthPercent) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String normalizedName = fileName.trim();
        int width = Math.max(10, Math.min(100, widthPercent > 0 ? widthPercent : 80));
        String imageLine = "![](" + normalizedName + "){ width=" + width + "% }";
        if (caption != null && !caption.isBlank()) {
            return imageLine + "\n\n><c>" + caption.trim() + "</c>";
        }
        return imageLine;
    }

    public static File resolveImageFile(String imagePath, File mdDirectory, File projectDirectory) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        String normalizedPath = imagePath.trim();
        if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
            return null;
        }

        File direct = new File(normalizedPath);
        if (direct.isAbsolute()) {
            return direct.exists() ? direct : null;
        }

        File[] candidates = {
                mdDirectory != null ? new File(mdDirectory, normalizedPath) : null,
                projectDirectory != null ? new File(projectDirectory, normalizedPath) : null,
                mdDirectory != null && mdDirectory.getParentFile() != null
                        ? new File(mdDirectory.getParentFile(), normalizedPath) : null,
                new File(System.getProperty("user.dir", "."), normalizedPath)
        };

        for (File candidate : candidates) {
            if (candidate != null && candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    public static File copyImageToProjectDirectory(File sourceImage, File projectDirectory) throws IOException {
        if (sourceImage == null || !sourceImage.isFile()) {
            throw new IOException("Bilddatei existiert nicht: " + sourceImage);
        }
        if (projectDirectory == null) {
            throw new IOException("Kein Arbeitsverzeichnis gesetzt");
        }
        if (!projectDirectory.exists() && !projectDirectory.mkdirs()) {
            throw new IOException("Arbeitsverzeichnis konnte nicht erstellt werden: " + projectDirectory);
        }

        File targetFile = new File(projectDirectory, sourceImage.getName());
        if (targetFile.exists()
                && !targetFile.getCanonicalPath().equals(sourceImage.getCanonicalPath())) {
            String base = sourceImage.getName();
            int dot = base.lastIndexOf('.');
            String name = dot > 0 ? base.substring(0, dot) : base;
            String ext = dot > 0 ? base.substring(dot) : "";
            for (int i = 1; i < 1000; i++) {
                targetFile = new File(projectDirectory, name + "_" + i + ext);
                if (!targetFile.exists()) {
                    break;
                }
            }
        }

        if (!targetFile.getCanonicalPath().equals(sourceImage.getCanonicalPath())) {
            Files.copy(sourceImage.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile;
    }

    public static Image loadImage(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            Image image = new Image(file.toURI().toString(), false);
            if (image.isError() || image.getWidth() <= 0) {
                return null;
            }
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stripWidthSuffix(String path) {
        if (path == null) {
            return "";
        }
        int widthIndex = path.indexOf("{ width=");
        if (widthIndex >= 0) {
            return path.substring(0, widthIndex).trim();
        }
        return path.trim();
    }
}
