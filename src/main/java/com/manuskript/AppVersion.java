package com.manuskript;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Liest die Build-Version aus {@code /manuskript.version} (Classpath).
 * Die Datei wird beim Deploy automatisch hochgezählt.
 */
public final class AppVersion {

    private static final String RESOURCE = "/manuskript.version";
    private static final String FALLBACK = "0.0.0";

    private AppVersion() {
    }

    public static String current() {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return FALLBACK;
            }
            String value = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? FALLBACK : value;
        } catch (Exception e) {
            return FALLBACK;
        }
    }
}
