package com.manuskript.agent;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Einheitliche Höhe für „Jetzt prüfen“ (Button) und „Echtzeit“ (ToggleButton).
 * ToggleButton hat in JavaFX zusätzliche Background-Insets – daher gemeinsame Höhe
 * aus der Schriftgröße statt Bindung an die Button-Höhe.
 */
final class AgentActionButtonSupport {

    /** Vertikales Padding aus CSS (6 + 6). */
    private static final double VERTICAL_PADDING = 12;
    /** Zusatz für ToggleButton-Rahmen/Insets bei großen Schriften. */
    private static final double TOGGLE_INSET_ALLOWANCE = 10;

    private AgentActionButtonSupport() {
    }

    static void configureRow(HBox row, Button analyzeButton, ToggleButton realtimeToggle) {
        row.setAlignment(Pos.CENTER_LEFT);
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
        control.setMaxHeight(Region.USE_PREF_SIZE);
    }

    private static void unbindHeight(ToggleButton toggle) {
        toggle.minHeightProperty().unbind();
        toggle.prefHeightProperty().unbind();
        toggle.maxHeightProperty().unbind();
    }
}
