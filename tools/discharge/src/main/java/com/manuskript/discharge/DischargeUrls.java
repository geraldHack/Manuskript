package com.manuskript.discharge;

/**
 * Erlaubte Chat-API-Basis: HTTPS auf spoteroxe.de unter {@code /chat/}.
 */
public final class DischargeUrls {

    public static final String DEFAULT_BASE = "https://spoteroxe.de/chat/";

    private DischargeUrls() {
    }

    public static boolean isAllowedBase(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String u = baseUrl.trim().toLowerCase();
        return u.startsWith("https://spoteroxe.de/chat")
                || u.startsWith("https://www.spoteroxe.de/chat");
    }
}
