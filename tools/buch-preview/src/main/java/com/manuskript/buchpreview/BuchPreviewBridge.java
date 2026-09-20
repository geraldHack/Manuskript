package com.manuskript.buchpreview;

/** JavaScript-Bridge für WebView (öffentliche Klasse, String-Argumente). */
public final class BuchPreviewBridge {

    private final BuchPreviewWindow window;

    public BuchPreviewBridge(BuchPreviewWindow window) {
        this.window = window;
    }

    public void onPaginated(String pages, String spineMm) {
        int pageCount = parseInt(pages, 1);
        double spine = parseDouble(spineMm, 0);
        window.onPaginated(pageCount, spine);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
