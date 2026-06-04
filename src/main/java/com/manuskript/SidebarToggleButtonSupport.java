package com.manuskript;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

/**
 * Ein-/Ausklapp-Pfeil für die Kapitel-Seitenleiste (links am Editor).
 */
public final class SidebarToggleButtonSupport {

    private SidebarToggleButtonSupport() {
    }

    public static void updateAppearance(Button button, boolean sidebarExpanded, int themeIndex) {
        if (button == null) {
            return;
        }
        button.setText(null);
        button.setGraphic(createChevron(sidebarExpanded, chevronColorForTheme(themeIndex)));
        button.setTooltip(new Tooltip(sidebarExpanded
                ? "Kapitel-Seitenleiste einklappen"
                : "Kapitel-Seitenleiste ausklappen"));
    }

    private static Color chevronColorForTheme(int themeIndex) {
        return switch (themeIndex) {
            case 0 -> Color.web("#555555");
            case 2 -> Color.web("#5c3d7a");
            default -> Color.web("#cccccc");
        };
    }

  /** {@code sidebarExpanded}: Pfeil nach links; sonst nach rechts (ausklappen). */
    private static StackPane createChevron(boolean sidebarExpanded, Color fill) {
        Path path = new Path();
        double w = 7;
        double h = 10;
        if (sidebarExpanded) {
            path.getElements().addAll(
                    new MoveTo(w, 1),
                    new LineTo(1, h / 2),
                    new LineTo(w, h - 1));
        } else {
            path.getElements().addAll(
                    new MoveTo(1, 1),
                    new LineTo(w, h / 2),
                    new LineTo(1, h - 1));
        }
        path.setFill(fill);
        path.setStrokeWidth(0);
        path.getStyleClass().add("sidebar-toggle-chevron");
        StackPane pane = new StackPane(path);
        pane.setMinSize(12, 12);
        pane.setMaxSize(12, 12);
        return pane;
    }
}
