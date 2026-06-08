package com.manuskript;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

/**
 * Ein-/Ausklapp-Pfeil für die Host-Toolbar im Kapitel-Editor (vertikal).
 */
public final class HostToolbarToggleSupport {

    private HostToolbarToggleSupport() {
    }

    public static void updateAppearance(Button button, boolean toolbarExpanded, int themeIndex) {
        if (button == null) {
            return;
        }
        button.setText(null);
        button.setGraphic(createChevron(toolbarExpanded, chevronColorForTheme(themeIndex)));
        button.setTooltip(new Tooltip(toolbarExpanded
                ? "Werkzeugleiste einklappen"
                : "Werkzeugleiste ausklappen"));
    }

    private static Color chevronColorForTheme(int themeIndex) {
        return switch (themeIndex) {
            case 0 -> Color.web("#555555");
            case 2 -> Color.web("#5c3d7a");
            default -> Color.web("#cccccc");
        };
    }

    /** {@code toolbarExpanded}: Pfeil nach unten; sonst nach oben (ausklappen). */
    private static StackPane createChevron(boolean toolbarExpanded, Color fill) {
        Path path = new Path();
        double w = 10;
        double h = 7;
        if (toolbarExpanded) {
            path.getElements().addAll(
                    new MoveTo(1, 1),
                    new LineTo(w / 2, h - 1),
                    new LineTo(w - 1, 1));
        } else {
            path.getElements().addAll(
                    new MoveTo(1, h - 1),
                    new LineTo(w / 2, 1),
                    new LineTo(w - 1, h - 1));
        }
        path.setFill(fill);
        path.setStrokeWidth(0);
        path.getStyleClass().add("host-toolbar-toggle-chevron");
        StackPane pane = new StackPane(path);
        pane.setAlignment(Pos.CENTER);
        pane.setMinSize(14, 14);
        pane.setMaxSize(14, 14);
        return pane;
    }
}
