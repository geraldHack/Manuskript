package com.manuskript.agent;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Einheitliche Höhe für „Jetzt prüfen“ (Button) und „Echtzeit“ (ToggleButton).
 * Die Zeile darf vom umgebenden Layout nicht auf einen schmalen Streifen
 * zusammengeschoben werden — sonst wirken die Buttons leer und abgeschnitten.
 */
final class AgentActionButtonSupport {

    /** Vertikales Padding aus CSS (6 + 6). */
    private static final double VERTICAL_PADDING = 12;
    /** Zusatz für ToggleButton-Rahmen/Insets. */
    private static final double TOGGLE_INSET_ALLOWANCE = 10;

    private AgentActionButtonSupport() {
    }

    static void configureRow(Pane row, Button analyzeButton, ToggleButton realtimeToggle) {
        if (row instanceof HBox hbox) {
            hbox.setAlignment(Pos.CENTER_LEFT);
        } else if (row instanceof FlowPane flow) {
            flow.setAlignment(Pos.CENTER_LEFT);
            flow.setPrefWrapLength(200);
        }
        if (!row.getStyleClass().contains("agent-action-row")) {
            row.getStyleClass().add("agent-action-row");
        }
        row.setMinHeight(Region.USE_PREF_SIZE);
        row.setPrefHeight(Region.USE_COMPUTED_SIZE);
        row.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(row, Priority.NEVER);
        applyFontSize(12, analyzeButton, realtimeToggle);
    }

    static void applyFontSize(int fontSize, Button analyzeButton, ToggleButton realtimeToggle) {
        if (analyzeButton == null || realtimeToggle == null) {
            return;
        }
        unbindHeight(realtimeToggle);
        double height = heightForFontSize(fontSize);
        applyHeight(analyzeButton, height);
        applyHeight(realtimeToggle, height);
    }

    private static double heightForFontSize(int fontSize) {
        int size = Math.max(8, Math.min(72, fontSize));
        return size + VERTICAL_PADDING + TOGGLE_INSET_ALLOWANCE;
    }

    private static void applyHeight(Control control, double height) {
        control.setMinHeight(height);
        control.setPrefHeight(height);
        control.setMaxHeight(Double.MAX_VALUE);
    }

    private static void unbindHeight(ToggleButton toggle) {
        toggle.minHeightProperty().unbind();
        toggle.prefHeightProperty().unbind();
        toggle.maxHeightProperty().unbind();
    }
}
