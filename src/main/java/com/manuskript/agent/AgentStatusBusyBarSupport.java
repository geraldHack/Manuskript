package com.manuskript.agent;

import javafx.scene.control.ProgressBar;

/**
 * Gemeinsame Busy-Balken-Konfiguration (Statuszeile im Kapitel-Editor).
 */
public final class AgentStatusBusyBarSupport {

    private AgentStatusBusyBarSupport() {
    }

    public static ProgressBar createBusyBar() {
        ProgressBar bar = new ProgressBar();
        bar.getStyleClass().add("tts-generation-busy-bar");
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
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
        bar.setVisible(active);
        bar.setManaged(active);
        if (active) {
            bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        } else {
            bar.setProgress(0);
        }
    }
}
