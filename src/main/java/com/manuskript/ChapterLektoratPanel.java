package com.manuskript;

import com.manuskript.agent.AgentFontSizeSupport;
import javafx.application.Platform;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Rechtes Lektorat-Panel für den Canvas-Kapitel-Editor (Vorschläge, Begründung).
 */
public class ChapterLektoratPanel {

    private static final Logger logger = LoggerFactory.getLogger(ChapterLektoratPanel.class);

    private final VBox container;
    private final SplitPane splitPane;
    private final IntSupplier themeIndex;
    private final BiConsumer<Node, Integer> themeApplier;
    private int fontSizePx = 16;

    public ChapterLektoratPanel(VBox container, SplitPane splitPane,
                                IntSupplier themeIndex, BiConsumer<Node, Integer> themeApplier,
                                int initialFontSizePx) {
        this.container = container;
        this.splitPane = splitPane;
        this.themeIndex = themeIndex;
        this.themeApplier = themeApplier;
        this.fontSizePx = initialFontSizePx;
        container.setMaxWidth(Double.MAX_VALUE);
        container.getStyleClass().add("lektorat-panel");
    }

    public void applyFontSize(int size) {
        if (size < 8) {
            size = 8;
        } else if (size > 72) {
            size = 72;
        }
        fontSizePx = size;
        if (container != null) {
            AgentFontSizeSupport.apply(container, size);
        }
    }

    public void ensureVisible(boolean visible) {
        if (splitPane == null || container == null) {
            return;
        }
        ObservableList<Node> items = splitPane.getItems();
        boolean hasPanel = items.contains(container);
        if (visible && !hasPanel) {
            items.add(container);
            ChapterEditorSplitPreferences.apply(splitPane);
        } else if (!visible && hasPanel) {
            items.remove(container);
            ChapterEditorSplitPreferences.apply(splitPane);
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
        applyFontSize(fontSizePx);
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
        applyFontSize(fontSizePx);
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
        applyFontSize(fontSizePx);
    }

    public void clear() {
        ensureVisible(false);
        if (container != null) {
            container.getChildren().clear();
        }
    }

    /**
     * Zeigt die Kapitel-Einschätzung unterhalb des Lektorat-Inhalts (parallel zum Lektorat-Lauf).
     */
    public void appendChapterAssessment(String chapterText) {
        if (container == null) {
            return;
        }
        ensureVisible(true);
        int theme = themeIndex.getAsInt();

        Label assessmentLabel = sectionLabel("Kapitel-Einschätzung", theme);
        VBox assessmentBox = new VBox(8);
        assessmentBox.getStyleClass().add("dialog-container");
        assessmentBox.getStyleClass().add("theme-" + theme);
        assessmentBox.setPadding(new Insets(10));
        assessmentBox.setMaxWidth(Double.MAX_VALUE);
        themeApplier.accept(assessmentBox, theme);

        Label assessmentLoading = new Label("Einschätzung wird geladen...");
        assessmentLoading.setWrapText(true);
        assessmentLoading.setMaxWidth(Double.MAX_VALUE);
        themeApplier.accept(assessmentLoading, theme);
        assessmentBox.getChildren().add(assessmentLoading);
        container.getChildren().addAll(assessmentLabel, assessmentBox);
        applyFontSize(fontSizePx);

        if (chapterText == null || chapterText.isBlank()) {
            assessmentBox.getChildren().clear();
            Label noText = new Label("Kein Text für Einschätzung vorhanden.");
            noText.setWrapText(true);
            noText.setMaxWidth(Double.MAX_VALUE);
            themeApplier.accept(noText, theme);
            assessmentBox.getChildren().add(noText);
            return;
        }

        OnlineLektoratService service = new OnlineLektoratService();
        service.runChapterAssessment(chapterText)
                .thenAccept(assessment -> Platform.runLater(() -> {
                    assessmentBox.getChildren().clear();
                    Label assessmentText = new Label(assessment);
                    assessmentText.setWrapText(true);
                    assessmentText.setMaxWidth(Double.MAX_VALUE);
                    themeApplier.accept(assessmentText, theme);
                    assessmentBox.getChildren().add(assessmentText);
                    applyFontSize(fontSizePx);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        assessmentBox.getChildren().clear();
                        Label errorText = new Label("Einschätzung fehlgeschlagen:\n" + ex.getMessage());
                        errorText.setWrapText(true);
                        errorText.setMaxWidth(Double.MAX_VALUE);
                        themeApplier.accept(errorText, theme);
                        assessmentBox.getChildren().add(errorText);
                        applyFontSize(fontSizePx);
                    });
                    logger.error("Kapitel-Einschätzung", ex);
                    return null;
                });
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
