package com.manuskript.publish;

/**
 * Plattform-Grenzwerte für KDP eBook und Tolino Media (Stand 2026 — bei Änderung anpassen).
 */
public final class PlatformRules {

    public static final int KDP_KEYWORD_SLOTS = 7;
    public static final int KDP_KEYWORD_MAX_CHARS = 50;
    public static final int KDP_DESCRIPTION_MAX_CHARS = 4000;

    /** Empfohlen: Höhe × Breite. */
    public static final int KDP_COVER_HEIGHT = 2560;
    public static final int KDP_COVER_WIDTH = 1600;
    public static final double KDP_COVER_RATIO = 2560.0 / 1600.0; // 1.6
    public static final double KDP_COVER_RATIO_TOLERANCE = 0.08;

    public static final int KDP_COVER_MIN_SHORT_SIDE = 1000;
    public static final int KDP_COVER_HARD_MIN_SHORT_SIDE = 625;
    public static final long KDP_COVER_MAX_BYTES = 50L * 1024 * 1024;

    public static final int TOLINO_COVER_MIN_WIDTH = 1600;
    public static final long TOLINO_COVER_MAX_BYTES = 5L * 1024 * 1024;

    public static final String HELP_KDP_COVER =
            "https://kdp.amazon.com/help/topic/G6GTK3T3NUHKLEFX";
    public static final String HELP_KDP_KEYWORDS =
            "https://kdp.amazon.com/help/topic/G201298050";
    public static final String HELP_KDP_HOME =
            "https://kdp.amazon.com/";
    public static final String HELP_TOLINO =
            "https://tolinomedia.com/";

    private PlatformRules() {
    }

    public static int keywordLength(String keyword) {
        return keyword == null ? 0 : keyword.length();
    }

    public static boolean keywordOk(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isEmpty()) {
            return true;
        }
        return value.length() <= KDP_KEYWORD_MAX_CHARS;
    }

    public static boolean descriptionOk(String blurb) {
        return blurbChars(blurb) <= KDP_DESCRIPTION_MAX_CHARS;
    }

    public static int blurbChars(String blurb) {
        return blurb == null ? 0 : blurb.length();
    }

    public static double coverRatio(int width, int height) {
        if (width <= 0) {
            return 0;
        }
        return (double) height / (double) width;
    }

    public static boolean ratioNearKdp(int width, int height) {
        double ratio = coverRatio(width, height);
        return Math.abs(ratio - KDP_COVER_RATIO) <= KDP_COVER_RATIO_TOLERANCE;
    }
}
