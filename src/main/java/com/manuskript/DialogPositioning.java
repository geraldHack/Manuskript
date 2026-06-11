package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * Zentriert Dialog-Fenster auf einem Owner (typisch Hauptfenster oder Eltern-Stage).
 */
public final class DialogPositioning {

    private DialogPositioning() {
    }

    /**
     * 
     * Zentriert die Stage einmal nach dem Anzeigen (wenn Breite/Höhe bekannt sind).
     */
    public static void centerWhenShown(Stage stage, Window owner) {
        if (stage == null || owner == null) {
            return;
        }
        stage.setOnShown(e -> Platform.runLater(() -> centerOnOwner(stage, owner)));
    }

    /**
     * Zentriert ein Dialog-Fenster auf dem Owner-Fenster und hält es im sichtbaren Bildschirmbereich.
     */
    public static void centerOnOwner(Window dialog, Window owner) {
        if (dialog == null || owner == null) {
            return;
        }
        double ownerW = owner.getWidth();
        double ownerH = owner.getHeight();
        if (ownerW <= 0 || ownerH <= 0) {
            return;
        }

        double dialogW = effectiveWidth(dialog);
        double dialogH = effectiveHeight(dialog);
        if (dialogW <= 0 || dialogH <= 0) {
            return;
        }

        double centerX = owner.getX() + ownerW / 2;
        double centerY = owner.getY() + ownerH / 2;
        double x = centerX - dialogW / 2;
        double y = centerY - dialogH / 2;

        Rectangle2D screen = screenFor(owner);
        x = clamp(x, screen.getMinX(), screen.getMaxX() - dialogW);
        y = clamp(y, screen.getMinY(), screen.getMaxY() - dialogH);

        dialog.setX(x);
        dialog.setY(y);
    }

    private static double effectiveWidth(Window dialog) {
        if (dialog.getWidth() > 0) {
            return dialog.getWidth();
        }
        if (dialog.getScene() != null && dialog.getScene().getWidth() > 0) {
            return dialog.getScene().getWidth();
        }
        if (dialog instanceof Stage stage && stage.getMinWidth() > 0) {
            return stage.getMinWidth();
        }
        return 0;
    }

    private static double effectiveHeight(Window dialog) {
        if (dialog.getHeight() > 0) {
            return dialog.getHeight();
        }
        if (dialog.getScene() != null && dialog.getScene().getHeight() > 0) {
            return dialog.getScene().getHeight();
        }
        if (dialog instanceof Stage stage && stage.getMinHeight() > 0) {
            return stage.getMinHeight();
        }
        return 0;
    }

    private static Rectangle2D screenFor(Window owner) {
        List<Screen> screens = Screen.getScreensForRectangle(
                owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        return screen.getVisualBounds();
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}
