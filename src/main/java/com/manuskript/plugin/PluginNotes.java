package com.manuskript.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Beschreibung und Release-Notes neben einer Plugin-JAR
 * ({@code name.jar} + {@code name.txt}).
 *
 * <pre>
 * Label
 * 1.0.1
 *
 * Freitext / Release Notes
 * </pre>
 */
public final class PluginNotes {

    private final String label;
    private final String version;
    private final String requires;
    private final String description;

    public PluginNotes(String label, String version, String requires, String description) {
        this.label = label == null ? "" : label.trim();
        this.version = version == null ? "" : version.trim();
        this.requires = requires == null ? "" : requires.trim();
        this.description = description == null ? "" : description.strip();
    }

    public String label() {
        return label;
    }

    public String version() {
        return version;
    }

    public String requires() {
        return requires;
    }

    public String description() {
        return description;
    }

    public static PluginNotes empty() {
        return new PluginNotes("", "", "", "");
    }

    public static PluginNotes loadBeside(Path jarFile) {
        if (jarFile == null) {
            return empty();
        }
        return load(beside(jarFile));
    }

    public static Path beside(Path jarFile) {
        if (jarFile == null) {
            return null;
        }
        String name = jarFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return jarFile.resolveSibling(base + ".txt");
    }

    public static PluginNotes load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return empty();
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return empty();
        }
    }

    public static PluginNotes parse(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String label = "";
        String version = "";
        String requires = "";
        int index = 0;
        while (index < lines.length && lines[index].isBlank()) {
            index++;
        }
        if (index < lines.length) {
            label = lines[index].trim();
            index++;
        }
        while (index < lines.length && lines[index].isBlank()) {
            index++;
        }
        if (index < lines.length && looksLikeVersion(lines[index].trim())) {
            version = lines[index].trim();
            index++;
        }
        while (index < lines.length) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                index++;
                break;
            }
            if (line.regionMatches(true, 0, "requires:", 0, "requires:".length())) {
                requires = line.substring("requires:".length()).trim();
                index++;
                continue;
            }
            break;
        }
        StringBuilder body = new StringBuilder();
        for (; index < lines.length; index++) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(lines[index]);
        }
        return new PluginNotes(label, version, requires, body.toString().strip());
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        if (!label.isBlank()) {
            out.append(label).append('\n');
        }
        if (!version.isBlank()) {
            out.append(version).append('\n');
        }
        if (!requires.isBlank()) {
            out.append("requires: ").append(requires).append('\n');
        }
        out.append('\n');
        if (!description.isBlank()) {
            out.append(description);
            if (!description.endsWith("\n")) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    static boolean looksLikeVersion(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return line.matches("\\d+(?:\\.\\d+)*");
    }
}
