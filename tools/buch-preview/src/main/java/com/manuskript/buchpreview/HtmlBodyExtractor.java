package com.manuskript.buchpreview;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Body-HTML und Klartext aus einem HTML5-Export.
 */
public final class HtmlBodyExtractor {

    private static final Pattern BODY = Pattern.compile(
            "(?is)<body[^>]*>(.*)</body>");
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern ABSTRACT_DIV = Pattern.compile(
            "(?is)<div[^>]*class=\"[^\"]*abstract[^\"]*\"[^>]*>(.*?)</div>");
    private static final Pattern EMPTY_P = Pattern.compile(
            "(?is)<p>(?:\\s|&nbsp;|&#160;|\u00a0)*</p>");
    private static final Pattern COVER_IMG = Pattern.compile(
            "(?is)<img[^>]*(?:class=\"[^\"]*cover-image[^\"]*\"|alt=\"Cover\")[^>]*/?>");

    private HtmlBodyExtractor() {
    }

    public static String bodyInnerHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Matcher matcher = BODY.matcher(html);
        String inner = matcher.find() ? matcher.group(1) : html;
        return SCRIPT_OR_STYLE.matcher(inner).replaceAll("");
    }

    /**
     * Body für die Anzeige wie die alte Editor-Vorschau:
     * Cover und leere Absätze weg, Text sonst unverändert.
     */
    public static String displayInnerHtml(String html) {
        String inner = bodyInnerHtml(html);
        inner = COVER_IMG.matcher(inner).replaceAll("");
        return EMPTY_P.matcher(inner).replaceAll("");
    }

    /** Body ohne Cover-Bild, leere Absätze und nicht darstellbare Zeichen. */
    public static String previewInnerHtml(String html) {
        return stripNonPrintable(replaceMissingGlyphs(displayInnerHtml(html)));
    }

    /**
     * JavaFX-WebView zeigt für manche Unicode-Zeichen leere Rechtecke.
     * Anführungszeichen und Gedankenstriche werden durch ASCII ersetzt.
     */
    static String replaceMissingGlyphs(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html
                .replace("&#8249;", "'")
                .replace("&#8250;", "'")
                .replace("&#x2039;", "'")
                .replace("&#x203a;", "'")
                .replace("&#x203A;", "'")
                .replace("&raquo;", "\"")
                .replace("&laquo;", "\"")
                .replace("&#187;", "\"")
                .replace("&#171;", "\"");
    }

    /**
     * Entfernt Steuer-/Format-/Private-Use-Zeichen und .notdef-Kästchen
     * aus Textknoten; HTML-Tags bleiben unverändert.
     */
    static String stripNonPrintable(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(html.length());
        boolean inTag = false;
        for (int i = 0; i < html.length(); ) {
            int cp = html.codePointAt(i);
            int n = Character.charCount(cp);
            if (!inTag && cp == '<') {
                inTag = true;
                out.append('<');
            } else if (inTag) {
                out.appendCodePoint(cp);
                if (cp == '>') {
                    inTag = false;
                }
            } else {
                appendPrintable(out, cp);
            }
            i += n;
        }
        return out.toString();
    }

    private static void appendPrintable(StringBuilder out, int cp) {
        if (cp == '\n' || cp == '\r' || cp == '\t' || cp == ' ') {
            out.appendCodePoint(cp);
            return;
        }
        if (cp == 0x00A0) {
            out.append(' ');
            return;
        }
        if (cp < 32 || cp == 127) {
            return;
        }
        int type = Character.getType(cp);
        if (type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.SURROGATE
                || type == Character.PRIVATE_USE
                || type == Character.UNASSIGNED) {
            return;
        }
        if (type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
            out.append('\n');
            return;
        }
        switch (cp) {
            case 0x2039, 0x203A, 0x2018, 0x2019, 0x00B4 -> {
                out.append('\'');
                return;
            }
            case 0x00AB, 0x00BB, 0x201C, 0x201D, 0x201E -> {
                out.append('"');
                return;
            }
            case 0x2013, 0x2014 -> {
                out.append('-');
                return;
            }
            case 0x2026 -> {
                out.append("...");
                return;
            }
            case 0x00AD, 0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF, 0xFFFD, 0xFFFC,
                 0x2764, 0xFE0F -> {
                return;
            }
            default -> {
            }
        }
        if ((cp >= 0x25A0 && cp <= 0x25FF) || cp == 0x2610 || cp == 0x2B1B || cp == 0x2B1C) {
            return;
        }
        out.appendCodePoint(cp);
    }

    public static String plainText(String html) {
        String inner = bodyInnerHtml(html);
        String withoutTags = TAG.matcher(inner).replaceAll(" ");
        String decoded = decodeBasicEntities(withoutTags);
        return decoded.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Klappentext aus dem HTML-Export ({@code div.abstract}), falls das Exportmodul leer ist. */
    public static String abstractText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Matcher matcher = ABSTRACT_DIV.matcher(html);
        if (!matcher.find()) {
            return "";
        }
        String withoutTags = TAG.matcher(matcher.group(1)).replaceAll(" ");
        return decodeBasicEntities(withoutTags).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    static String decodeBasicEntities(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }
}
