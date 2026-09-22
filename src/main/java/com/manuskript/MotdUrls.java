package com.manuskript;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Erlaubte MOTD-URL: HTTPS auf spoteroxe.de unter {@code /downloads/manuskript-motd.txt}.
 */
public final class MotdUrls {

    public static final String MOTD_URL = "https://spoteroxe.de/downloads/manuskript-motd.txt";

    private MotdUrls() {
    }

    public static URI motdUri() {
        return URI.create(MOTD_URL);
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
        if (path == null || path.contains("..")) {
            return false;
        }
        String query = normalized.getQuery();
        if (query != null && !query.isEmpty()) {
            return false;
        }
        String fragment = normalized.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            return false;
        }
        return Objects.equals(path, "/downloads/manuskript-motd.txt");
    }
}
