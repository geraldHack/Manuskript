package com.manuskript.buchpreview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Schreibt die Vorschau-HTML in den HTML5-Exportordner (UTF-8),
 * mit {@code <base href>} auf diesen Ordner.
 */
final class PreviewShell {

    static final String FILE_NAME = "_taschenbuch_preview.html";

    private PreviewShell() {
    }

    static Path write(Path folder, String template, String bootJs) throws IOException {
        if (folder == null || template == null) {
            throw new IOException("Vorschau-Ordner oder Template fehlt.");
        }
        Files.createDirectories(folder);
        String html = template
                .replace("<head>", "<head>\n  <meta charset=\"UTF-8\">\n  <base href=\""
                        + baseHref(folder) + "\">")
                .replace("/*BP_BOOT*/", bootJs == null ? "" : bootJs);
        Path file = folder.resolve(FILE_NAME);
        Files.writeString(file, "\uFEFF" + html, StandardCharsets.UTF_8);
        return file;
    }

    static String baseHref(Path folder) {
        if (folder == null) {
            return "";
        }
        String base = folder.toAbsolutePath().normalize().toUri().toString();
        return base.endsWith("/") ? base : base + "/";
    }
}
