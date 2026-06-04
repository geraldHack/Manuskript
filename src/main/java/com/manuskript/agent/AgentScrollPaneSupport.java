package com.manuskript.agent;

import javafx.scene.control.ScrollPane;

/** ScrollPane-Einstellungen für Plothole-Fundlisten (sichtbare vertikale Scrollbar). */
public final class AgentScrollPaneSupport {

    private AgentScrollPaneSupport() {
    }

    public static void configureFindingsScrollPane(ScrollPane scroll) {
        if (scroll == null) {
            return;
        }
        if (!scroll.getStyleClass().contains("agent-scroll")) {
            scroll.getStyleClass().add("agent-scroll");
        }
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(false);
    }
}
