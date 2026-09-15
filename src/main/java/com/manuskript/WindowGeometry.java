package com.manuskript;

import javafx.geometry.Rectangle2D;

import java.util.List;

/**
 * Reine Geometrie für Multi-Monitor-Fenster. Ohne JavaFX-{@code Screen}, damit Tests
 * ohne Display laufen.
 */
final class WindowGeometry {

    static final double MIN_VISIBLE_FRACTION = 0.30;

    private WindowGeometry() {
    }

    static boolean containsPoint(Rectangle2D bounds, double x, double y) {
        return bounds != null
                && x >= bounds.getMinX()
                && x < bounds.getMaxX()
                && y >= bounds.getMinY()
                && y < bounds.getMaxY();
    }

    static double overlapArea(double x, double y, double width, double height, Rectangle2D screen) {
        if (screen == null || width <= 0 || height <= 0) {
            return 0;
        }
        double overlapX = Math.max(0,
                Math.min(x + width, screen.getMaxX()) - Math.max(x, screen.getMinX()));
        double overlapY = Math.max(0,
                Math.min(y + height, screen.getMaxY()) - Math.max(y, screen.getMinY()));
        return overlapX * overlapY;
    }

    /**
     * Sichtbar genug: Fenstermitte auf einem Bildschirm oder mindestens 30 % Fläche.
     * Ein paar Pixel am Rand (typisch nach Monitorwechsel) reichen nicht.
     */
    static boolean isSubstantiallyVisible(double x, double y, double width, double height,
                                          List<Rectangle2D> screens) {
        if (screens == null || screens.isEmpty() || width <= 0 || height <= 0) {
            return false;
        }
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        double windowArea = width * height;
        for (Rectangle2D screen : screens) {
            if (screen == null) {
                continue;
            }
            if (containsPoint(screen, cx, cy)) {
                return true;
            }
            if (windowArea > 0 && overlapArea(x, y, width, height, screen) / windowArea >= MIN_VISIBLE_FRACTION) {
                return true;
            }
        }
        return false;
    }

    static Rectangle2D centerOn(Rectangle2D screen, double width, double height) {
        if (screen == null) {
            return new Rectangle2D(0, 0, Math.max(1, width), Math.max(1, height));
        }
        double w = width;
        double h = height;
        if (w > screen.getWidth()) {
            w = screen.getWidth() * 0.9;
        }
        if (h > screen.getHeight()) {
            h = screen.getHeight() * 0.9;
        }
        w = Math.max(1, w);
        h = Math.max(1, h);
        double x = screen.getMinX() + (screen.getWidth() - w) / 2.0;
        double y = screen.getMinY() + (screen.getHeight() - h) / 2.0;
        x = Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - w));
        y = Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - h));
        return new Rectangle2D(x, y, w, h);
    }

    static Rectangle2D snapIfNeeded(double x, double y, double width, double height,
                                    List<Rectangle2D> screens, Rectangle2D fallback) {
        if (isSubstantiallyVisible(x, y, width, height, screens)) {
            return new Rectangle2D(x, y, width, height);
        }
        Rectangle2D target = fallback;
        if (target == null && screens != null && !screens.isEmpty()) {
            target = screens.get(0);
        }
        return centerOn(target, width, height);
    }
}
