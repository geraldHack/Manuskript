package com.manuskript.agent;

import javafx.application.Platform;
import javafx.scene.control.ProgressBar;

/**
 * Gemeinsame Busy-Balken-Konfiguration (Statuszeile im Kapitel-Editor).
 */
public final class AgentStatusBusyBarSupport {

    private AgentStatusBusyBarSupport() {
    }

    public static ProgressBar createBusyBar() {
        ProgressBar bar = new ProgressBar(0);
        bar.getStyleClass().add("tts-generation-busy-bar");
        bar.setPrefHeight(6);
        bar.setMinHeight(6);
        bar.setMaxHeight(6);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setVisible(false);
        bar.setManaged(false);
        return bar;
    }

    public static void setActive(ProgressBar bar, boolean active) {
        if (bar == null) {
            return;
        }
        if (active) {
            boolean alreadyShowing = bar.isVisible() && bar.isManaged()
                    && bar.getProgress() < 0;
            bar.setManaged(true);
            bar.setVisible(true);
            if (alreadyShowing) {
                return;
            }
            // JavaFX startet die Indeterminate-Animation oft nicht, wenn progress
            // schon -1 war während der Balken unsichtbar/nicht im Layout war.
            bar.setProgress(0);
            Platform.runLater(() -> {
                if (bar.isVisible() && bar.isManaged()) {
                    bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    bar.requestLayout();
                }
            });
        } else {
            bar.setProgress(0);
            bar.setVisible(false);
            bar.setManaged(false);
        }
    }
}
