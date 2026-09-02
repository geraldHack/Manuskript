package com.manuskript.plugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dateiname einer Plugin-JAR auf dem Server, optional mit Version:
 * {@code projekt-backup-1.0.1.jar} oder {@code projekt-backup.jar}.
 */
public final class PluginJarName {

    private static final Pattern VERSIONED = Pattern.compile(
            "^(.+)-(\\d+(?:\\.\\d+)*)\\.jar$", Pattern.CASE_INSENSITIVE);

    private PluginJarName() {
    }

    public record Parsed(String id, String version, String localFileName) {
    }

    public static Parsed parse(String fileName) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return null;
        }
        String trimmed = fileName.trim();
        Matcher matcher = VERSIONED.matcher(trimmed);
        if (matcher.matches()) {
            String id = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!PluginCatalogUrls.isAllowedId(id)) {
                return null;
            }
            return new Parsed(id, matcher.group(2), id + ".jar");
        }
        String base = trimmed.substring(0, trimmed.length() - 4);
        String id = base.toLowerCase(Locale.ROOT);
        if (!PluginCatalogUrls.isAllowedId(id)) {
            return null;
        }
        return new Parsed(id, "", id + ".jar");
    }

    public static String notesFileName(String jarFileName) {
        if (jarFileName == null || !jarFileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return "";
        }
        return jarFileName.substring(0, jarFileName.length() - 4) + ".txt";
    }
}
