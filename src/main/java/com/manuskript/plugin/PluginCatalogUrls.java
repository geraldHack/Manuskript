package com.manuskript.plugin;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Erlaubte Katalog- und JAR-URLs: nur HTTPS auf spoteroxe.de unter {@code /downloads/}.
 */
public final class PluginCatalogUrls {

    public static final String INDEX_URL = "https://spoteroxe.de/downloads/manuskript-plugins.json";

    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.jar");
    private static final Pattern PLUGIN_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private PluginCatalogUrls() {
    }

    public static URI indexUri() {
        return URI.create(INDEX_URL);
    }

    public static boolean isAllowed(URI uri) {
        if (uri == null) {
            return false;
        }
        URI normalized;
        try {
            normalized = uri.normalize();
        } catch (Exception e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(normalized.getScheme())) {
            return false;
        }
        if (normalized.getUserInfo() != null && !normalized.getUserInfo().isEmpty()) {
            return false;
        }
        int port = normalized.getPort();
        if (port != -1 && port != 443) {
            return false;
        }
        String host = normalized.getHost();
        if (host == null) {
            return false;
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (!hostLower.equals("spoteroxe.de") && !hostLower.equals("www.spoteroxe.de")) {
            return false;
        }
        String path = normalized.getPath();
        if (path == null || !path.startsWith("/downloads/") || path.contains("..")) {
            return false;
        }
        if (path.length() <= "/downloads/".length()) {
            return false;
        }
        String query = normalized.getQuery();
        if (query != null && !query.isEmpty()) {
            return false;
        }
        String fragment = normalized.getFragment();
        return fragment == null || fragment.isEmpty();
    }

    public static boolean isAllowedFileName(String fileName) {
        return fileName != null && FILE_NAME.matcher(fileName).matches();
    }

    public static boolean isAllowedId(String id) {
        return id != null && PLUGIN_ID.matcher(id).matches();
    }

    public static boolean isAllowedSha256(String sha256) {
        return sha256 != null && SHA256.matcher(sha256).matches();
    }

    public static String normalizeSha256(String sha256) {
        return sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
    }
}
