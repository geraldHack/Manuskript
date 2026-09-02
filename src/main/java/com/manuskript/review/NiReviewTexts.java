package com.manuskript.review;

/**
 * Anzeige-Hilfen für die Lektorat-Spalte: Diffs kürzen, Kommentare nicht.
 */
public final class NiReviewTexts {

    static final int CHANGE_PREVIEW_CHARS = 90;

    private NiReviewTexts() {
    }

    public static String collapse(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
    }

    public static String previewChange(String raw) {
        return previewChange(raw, CHANGE_PREVIEW_CHARS);
    }

    public static String previewChange(String raw, int maxChars) {
        String collapsed = collapse(raw);
        if (collapsed.isEmpty() || maxChars <= 1 || collapsed.length() <= maxChars) {
            return collapsed;
        }
        int cut = Math.max(1, maxChars - 1);
        return collapsed.substring(0, cut).trim() + "…";
    }

    public static boolean wasTruncated(String raw) {
        return collapse(raw).length() > CHANGE_PREVIEW_CHARS;
    }
}
