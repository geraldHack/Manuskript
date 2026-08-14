package com.manuskript.agent;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * ScrollPane-Einstellungen für Agenten-Panel (Fundlisten, gesamter Tab).
 * <p>
 * Beim Aufklappen von Konfiguration (oder wachsendem Kontext-Chrome) muss
 * {@code fitToHeight=false} sein und die Content-Höhe zurückgesetzt werden –
 * sonst bleibt die Höhe auf dem Viewport stecken und Felder werden abgeschnitten.
 */
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

    /** Äußere ScrollPane für einen Agent-Tab. */
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
     * {@code configExpanded=true}: Tab scrollt (Config/Kontext vollständig sichtbar).
     * {@code false}: Findings/Chat füllen die Höhe.
     */
    public static void applyConfigExpandedLayout(ScrollPane tabScroll, VBox contentRoot,
            Region flexibleRegion, boolean configExpanded) {
        if (tabScroll == null || contentRoot == null) {
            return;
        }
        if (configExpanded) {
            enableOverflowScroll(tabScroll, contentRoot, flexibleRegion);
            Platform.runLater(() -> {
                contentRoot.requestLayout();
                tabScroll.layout();
                tabScroll.setVvalue(0);
            });
        } else {
            enableFillLayout(tabScroll, contentRoot, flexibleRegion);
        }
    }

    /**
     * Nach Wachstum von Kontext-UI (z. B. „Davor/Danach“): Overflow-Scroll aktivieren,
     * damit nichts unten abgeschnitten wird. Respektiert offene Konfiguration.
     */
    public static void ensureOverflowForChrome(Node anyChildInTab) {
        ScrollPane tabScroll = findParentScrollPane(anyChildInTab);
        if (tabScroll == null || !(tabScroll.getContent() instanceof VBox contentRoot)) {
            return;
        }
        Region flexible = findFlexibleRegion(contentRoot);
        enableOverflowScroll(tabScroll, contentRoot, flexible);
        Platform.runLater(() -> {
            contentRoot.requestLayout();
            tabScroll.layout();
        });
    }

    /**
     * Wenn weder Config noch extra Kontext-Chrome offen sind: wieder Fill-Layout.
     */
    public static void restoreFillIfChromeCollapsed(Node anyChildInTab) {
        ScrollPane tabScroll = findParentScrollPane(anyChildInTab);
        if (tabScroll == null || !(tabScroll.getContent() instanceof VBox contentRoot)) {
            return;
        }
        if (isConfigBoxOpen(contentRoot) || hasExtraContextChrome(contentRoot)) {
            return;
        }
        Region flexible = findFlexibleRegion(contentRoot);
        enableFillLayout(tabScroll, contentRoot, flexible);
    }

    private static void enableOverflowScroll(ScrollPane tabScroll, VBox contentRoot, Region flexibleRegion) {
        contentRoot.minHeightProperty().unbind();
        // fitToHeight=true setzt die Content-Höhe fest – ohne Reset bleibt sie stecken.
        contentRoot.setMinHeight(Region.USE_COMPUTED_SIZE);
        contentRoot.setPrefHeight(Region.USE_COMPUTED_SIZE);
        contentRoot.setMaxHeight(Double.MAX_VALUE);
        tabScroll.setFitToHeight(false);
        if (flexibleRegion != null) {
            VBox.setVgrow(flexibleRegion, Priority.NEVER);
            if (flexibleRegion instanceof ScrollPane findingsScroll) {
                findingsScroll.setMinHeight(120);
                findingsScroll.setPrefViewportHeight(200);
            } else {
                flexibleRegion.setMinHeight(120);
                flexibleRegion.setPrefHeight(Region.USE_COMPUTED_SIZE);
            }
        }
        contentRoot.requestLayout();
        tabScroll.requestLayout();
    }

    private static void enableFillLayout(ScrollPane tabScroll, VBox contentRoot, Region flexibleRegion) {
        if (flexibleRegion != null) {
            VBox.setVgrow(flexibleRegion, Priority.ALWAYS);
            if (flexibleRegion instanceof ScrollPane findingsScroll) {
                findingsScroll.setMinHeight(0);
                findingsScroll.setPrefViewportHeight(Region.USE_COMPUTED_SIZE);
            } else {
                flexibleRegion.setMinHeight(0);
                flexibleRegion.setPrefHeight(Region.USE_COMPUTED_SIZE);
            }
        }
        tabScroll.setFitToHeight(true);
        contentRoot.setPrefHeight(Region.USE_COMPUTED_SIZE);
        contentRoot.setMaxHeight(Double.MAX_VALUE);
        contentRoot.minHeightProperty().unbind();
        contentRoot.minHeightProperty().bind(tabScroll.heightProperty());
    }

    private static boolean isConfigBoxOpen(VBox contentRoot) {
        for (Node node : contentRoot.getChildren()) {
            if (node != null
                    && node.getStyleClass().contains("agent-config-box")
                    && node.isManaged()
                    && node.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExtraContextChrome(VBox contentRoot) {
        for (Node node : contentRoot.getChildren()) {
            if (node == null || !node.isManaged()) {
                continue;
            }
            // Nachbarkapitel-Spinner-Zeile in ChatbotContextPane / ChatbotAgentTab
            if (node instanceof VBox pane && pane.getStyleClass().contains("chatbot-context-pane")) {
                for (Node child : pane.getChildren()) {
                    if (child != null && child.isManaged() && child.isVisible()
                            && child instanceof javafx.scene.layout.HBox) {
                        // grob: sichtbare HBox unter Kontext = Davor/Danach
                        return true;
                    }
                }
            }
            if (node instanceof javafx.scene.layout.HBox hbox && hbox.isVisible()) {
                // ChatbotAgentTab: neighborSpinnersRow direkt im contentRoot
                String text = hbox.getChildren().stream()
                        .filter(c -> c instanceof javafx.scene.control.Label)
                        .map(c -> ((javafx.scene.control.Label) c).getText())
                        .reduce("", String::concat);
                if (text.contains("Davor") || text.contains("Danach")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Region findFlexibleRegion(VBox contentRoot) {
        Region fallback = null;
        for (Node node : contentRoot.getChildren()) {
            if (node instanceof ScrollPane scroll
                    && !scroll.getStyleClass().contains("agent-tab-scroll")) {
                // Findings-Scroll bevorzugt
                if (scroll.getStyleClass().contains("agent-scroll")) {
                    return scroll;
                }
                fallback = scroll;
            }
            if (node != null && VBox.getVgrow(node) == Priority.ALWAYS && node instanceof Region region) {
                fallback = region;
            }
        }
        return fallback;
    }

    private static ScrollPane findParentScrollPane(Node node) {
        Node n = node;
        while (n != null) {
            if (n instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
            n = n.getParent();
        }
        return null;
    }
}
