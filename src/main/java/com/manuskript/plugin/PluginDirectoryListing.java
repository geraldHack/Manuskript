package com.manuskript.plugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dateinamen aus einem Apache-/nginx-Verzeichnislisting.
 */
public final class PluginDirectoryListing {

    private static final Pattern HREF = Pattern.compile(
            "href\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private PluginDirectoryListing() {
    }

    public static List<String> fileNames(String html) {
        Set<String> names = new LinkedHashSet<>();
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Matcher matcher = HREF.matcher(html);
        while (matcher.find()) {
            String raw = matcher.group(1);
            String name = fileName(raw);
            if (name != null) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    static String fileName(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String value = href.trim();
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        if (value.isEmpty() || value.equals("../") || value.equals("/")) {
            return null;
        }
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        if (value.isEmpty() || value.contains("..")) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".txt")) {
            return value;
        }
        return null;
    }
}
