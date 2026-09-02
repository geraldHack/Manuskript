package com.manuskript.review;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class NiReviewHashes {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private NiReviewHashes() {
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt", e);
        }
    }

    public static String nowIso() {
        return ISO.format(Instant.now().atOffset(ZoneOffset.UTC));
    }

    public static String newRoundId() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
                .withZone(ZoneOffset.systemDefault())
                .format(Instant.now());
    }

    public static String contextBefore(String text, int start, int length) {
        if (text == null || start <= 0) {
            return "";
        }
        int from = Math.max(0, start - length);
        return text.substring(from, Math.min(start, text.length()));
    }

    public static String contextAfter(String text, int end, int length) {
        if (text == null || end >= text.length()) {
            return "";
        }
        int to = Math.min(text.length(), end + length);
        return text.substring(Math.max(0, end), to);
    }
}
