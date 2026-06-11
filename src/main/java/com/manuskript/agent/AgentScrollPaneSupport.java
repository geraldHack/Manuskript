package com.manuskript.agent;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** ScrollPane-Einstellungen für Agenten-Panel (Fundlisten, gesamter Tab). */
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

    /** Äußere ScrollPane für einen Agent-Tab (scrollt den gesamten Inhalt). */
    public static void configureEntireTabScroll(ScrollPane tabScroll) {
        if (tabScroll == null) {
            return;
        }
        if (!tabScroll.getStyleClass().contains("agent-tab-scroll")) {
            tabScroll.getStyleClass().add("agent-tab-scroll");
        }
        tabScroll.setFitToWidth(true);
        tabScroll.setFitToHeight(true);
        tabScroll.setMinHeight(0);
        tabScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tabScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabScroll.setPannable(false);
    }

    /**
     * Konfiguration zu: gesamter Tab scrollt. Zu: flexibler Bereich (Findings/Chat) füllt die Höhe.
     */
    public static void applyConfigExpandedLayout(ScrollPane tabScroll, VBox contentRoot,
            Region flexibleRegion, boolean configExpanded) {
        if (tabScroll == null || contentRoot == null) {
            return;
        }
        tabScroll.setFitToHeight(!configExpanded);
        if (configExpanded) {
            contentRoot.minHeightProperty().unbind();
            if (flexibleRegion != null) {
                VBox.setVgrow(flexibleRegion, Priority.NEVER);
                if (flexibleRegion instanceof ScrollPane findingsScroll) {
                    findingsScroll.setMinHeight(120);
                    findingsScroll.setPrefViewportHeight(200);
                } else {
                    flexibleRegion.setMinHeight(120);
                }
            }
        } else {
            if (flexibleRegion != null) {
                VBox.setVgrow(flexibleRegion, Priority.ALWAYS);
                if (flexibleRegion instanceof ScrollPane findingsScroll) {
                    findingsScroll.setMinHeight(0);
                    findingsScroll.setPrefViewportHeight(Region.USE_COMPUTED_SIZE);
                } else {
                    flexibleRegion.setMinHeight(Region.USE_COMPUTED_SIZE);
                }
            }
            contentRoot.minHeightProperty().bind(tabScroll.heightProperty());
        }
    }
}
