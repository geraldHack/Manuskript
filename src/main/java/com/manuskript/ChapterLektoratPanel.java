package com.manuskript;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Rechtes Lektorat-Panel für den Canvas-Kapitel-Editor (Vorschläge, Begründung).
 */
public class ChapterLektoratPanel {

    private final VBox container;
    private final SplitPane splitPane;
    private final IntSupplier themeIndex;
    private final BiConsumer<Node, Integer> themeApplier;

    public ChapterLektoratPanel(VBox container, SplitPane splitPane,
                                IntSupplier themeIndex, BiConsumer<Node, Integer> themeApplier) {
        this.container = container;
        this.splitPane = splitPane;
        this.themeIndex = themeIndex;
        this.themeApplier = themeApplier;
        container.setMaxWidth(Double.MAX_VALUE);
        container.getStyleClass().add("lektorat-panel");
    }

    public void ensureVisible(boolean visible) {
        if (splitPane == null || container == null) {
            return;
        }
        ObservableList<Node> items = splitPane.getItems();
        boolean hasPanel = items.contains(container);
        if (visible && !hasPanel) {
            items.add(container);
            double[] current = splitPane.getDividerPositions();
            double sidebarPos = current.length > 0 ? current[0] : 0.18;
            if (current.length >= 2) {
                splitPane.setDividerPositions(sidebarPos, 0.72);
            } else {
                splitPane.setDividerPositions(sidebarPos, 0.72, 0.88);
            }
        } else if (!visible && hasPanel) {
            items.remove(container);
            double[] current = splitPane.getDividerPositions();
            double sidebarPos = current.length > 0 ? current[0] : 0.18;
            splitPane.setDividerPositions(sidebarPos);
        }
    }

    public void showHint(boolean hasMatches) {
        if (container == null) {
            return;
        }
        container.getChildren().clear();
        Label hint = new Label(hasMatches
                ? "Klicken Sie auf eine Markierung im Text."
                : "Keine Vorschläge.");
        hint.setWrapText(true);
        hint.getStyleClass().add("lektorat-panel-hint");
        themeApplier.accept(hint, themeIndex.getAsInt());
        container.getChildren().add(hint);
        ensureVisible(hasMatches);
    }

    public void showMatch(LektoratMatch match, Consumer<String> onApplySuggestion) {
        if (container == null || match == null) {
            return;
        }
        ensureVisible(true);
        container.getChildren().clear();

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(10);
        content.setPadding(new Insets(0, 5, 0, 0));
        content.setMaxWidth(Double.MAX_VALUE);

        int theme = themeIndex.getAsInt();

        Label origLabel = sectionLabel("Original", theme);
        Label origText = bodyLabel(match.getOriginal(), theme);
        content.getChildren().addAll(origLabel, origText);

        if (match.getSuggestions() != null && !match.getSuggestions().isEmpty()) {
            content.getChildren().add(sectionLabel("Vorschläge", theme));
            for (int i = 0; i < match.getSuggestions().size(); i++) {
                String suggestion = match.getSuggestions().get(i);
                Button btn = new Button("Vorschlag " + (i + 1) + ": " + suggestion);
                btn.setWrapText(true);
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setMinHeight(Region.USE_PREF_SIZE);
                btn.getStyleClass().add("lektorat-suggestion-button");
                themeApplier.accept(btn, theme);
                String repl = suggestion;
                btn.setOnAction(e -> onApplySuggestion.accept(repl));
                content.getChildren().add(btn);
            }
        }

        Label reasonLabel = sectionLabel("Begründung", theme);
        Label reasonText = bodyLabel(match.getReason() != null ? match.getReason() : "", theme);
        content.getChildren().addAll(reasonLabel, reasonText);

        Label weightLabel = new Label("Gewichtung: " + match.getWeight() + "/5");
        weightLabel.getStyleClass().add("lektorat-panel-weight");
        themeApplier.accept(weightLabel, theme);
        content.getChildren().add(weightLabel);

        scroll.setContent(content);
        themeApplier.accept(scroll, theme);
        container.getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
    }

    public void showAppliedHint() {
        if (container == null) {
            return;
        }
        container.getChildren().clear();
        Label hint = new Label("Vorschlag übernommen. Bei Bedarf Online-Lektorat erneut starten.");
        hint.setWrapText(true);
        themeApplier.accept(hint, themeIndex.getAsInt());
        container.getChildren().add(hint);
    }

    public void clear() {
        ensureVisible(false);
        if (container != null) {
            container.getChildren().clear();
        }
    }

    private Label sectionLabel(String text, int theme) {
        Label label = new Label(text);
        label.getStyleClass().add("lektorat-panel-section");
        themeApplier.accept(label, theme);
        return label;
    }

    private Label bodyLabel(String text, int theme) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        themeApplier.accept(label, theme);
        return label;
    }
}
