package com.manuskript.publish;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

/**
 * Liest Cover-Maße ohne das ganze Bild zu dekodieren, wo möglich.
 */
public final class CoverAnalyzer {

    public record CoverInfo(
            Path path,
            boolean exists,
            int width,
            int height,
            long bytes,
            String format,
            String error
    ) {
        public boolean readable() {
            return exists && error == null && width > 0 && height > 0;
        }

        public int shortSide() {
            return Math.min(width, height);
        }

        public boolean portrait() {
            return height >= width;
        }
    }

    private CoverAnalyzer() {
    }

    public static CoverInfo analyze(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return new CoverInfo(null, false, 0, 0, 0, "", "Kein Cover-Pfad gesetzt");
        }
        return analyze(Path.of(pathText.trim()));
    }

    public static CoverInfo analyze(Path path) {
        if (path == null) {
            return new CoverInfo(null, false, 0, 0, 0, "", "Kein Cover-Pfad gesetzt");
        }
        if (!Files.isRegularFile(path)) {
            return new CoverInfo(path, false, 0, 0, 0, "", "Datei nicht gefunden");
        }
        try {
            long bytes = Files.size(path);
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            String formatHint = extension(name);
            int[] size = readSize(path);
            if (size == null) {
                return new CoverInfo(path, true, 0, 0, bytes, formatHint, "Bild konnte nicht gelesen werden");
            }
            return new CoverInfo(path, true, size[0], size[1], bytes, formatHint, null);
        } catch (IOException e) {
            return new CoverInfo(path, true, 0, 0, 0, "", e.getMessage());
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    private static int[] readSize(Path path) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }
}
