package com.manuskript;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalisiert KI-Antworten, in denen Modelle (z. B. jobautomation/OpenEuroLLM-German)
 * JSON-Unicode-Escapes als sichtbaren Text ausgeben (u003c, u00e4, …).
 */
public final class ModelTextNormalizer {

    /** JSON-Form: Backslash-u plus vier Hex-Ziffern. */
    private static final Pattern ESCAPED_UNICODE =
            Pattern.compile("\\\\+u([0-9a-fA-F]{4})");

    /**
     * Bare u plus vier Hex-Ziffern ohne Backslash (Modell-Quirk).
     * Ohne Lookbehind/Lookahead: sonst scheitern Sequenzen wie u00fcu003c.
     */
    private static final Pattern BARE_UNICODE =
            Pattern.compile("u([0-9a-fA-F]{4})");

    private ModelTextNormalizer() {
    }

    /**
     * Dekodiert JSON-aehnliche Unicode-Escapes zu normalen Zeichen.
     * Idempotent: bereits normales Deutsch bleibt unveraendert.
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf('u') < 0 && text.indexOf('U') < 0 && text.indexOf('\\') < 0) {
            return text;
        }
        String s = decodePattern(text, ESCAPED_UNICODE);
        String prev;
        int guard = 0;
        do {
            prev = s;
            s = decodePattern(s, ESCAPED_UNICODE);
            s = decodePattern(s, BARE_UNICODE);
        } while (!s.equals(prev) && ++guard < 4);
        return s;
    }

    private static String decodePattern(String input, Pattern pattern) {
        Matcher m = pattern.matcher(input);
        if (!m.find()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        m.reset();
        while (m.find()) {
            int code = Integer.parseInt(m.group(1), 16);
            String replacement = new String(Character.toChars(code));
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
