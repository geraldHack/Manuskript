package com.manuskript;

import com.manuskript.agent.AgentFontSizeSupport;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
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
    /** Lektorat-Status, Hinweise und Vorschlags-Details (wird bei jedem Panel-Wechsel geleert). */
    private final VBox contentArea = new VBox(8);
    /** Kapitel-Einschätzung (eigener Tab, bleibt bis Lektorat beendet). */
    private final VBox assessmentArea = new VBox(8);
    private final TabPane bodyTabs;
    private final Tab lektoratTab;
    private final Tab assessmentTab;
    private final SplitPane splitPane;
    private final IntSupplier themeIndex;
    private final BiConsumer<Node, Integer> themeApplier;
    private final IntSupplier fontSizeSupplier;
    private final Button exitButton = new Button("Lektorat beenden");
    private int fontSizePx = 16;
    private Runnable onExit;

    public ChapterLektoratPanel(VBox container, SplitPane splitPane,
                                IntSupplier themeIndex, BiConsumer<Node, Integer> themeApplier,
                                IntSupplier fontSizeSupplier) {
        this.container = container;
        this.splitPane = splitPane;
        this.themeIndex = themeIndex;
        this.themeApplier = themeApplier;
        this.fontSizeSupplier = fontSizeSupplier;
        this.fontSizePx = fontSizeSupplier.getAsInt();
        container.setMaxWidth(Double.MAX_VALUE);
        container.getStyleClass().add("lektorat-panel");

        exitButton.setOnAction(e -> {
            if (onExit != null) {
                onExit.run();
            }
        });
        Label titleLabel = new Label("Online-Lektorat");
        titleLabel.getStyleClass().add("lektorat-panel-section");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleLabel, spacer, exitButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 8, 0));

        contentArea.setMaxWidth(Double.MAX_VALUE);
        contentArea.setMaxHeight(Double.MAX_VALUE);
        VBox lektoratWrap = new VBox(contentArea);
        lektoratWrap.setPadding(new Insets(4, 0, 0, 0));
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        lektoratWrap.setMaxWidth(Double.MAX_VALUE);
        lektoratWrap.setMaxHeight(Double.MAX_VALUE);

        assessmentArea.setMaxWidth(Double.MAX_VALUE);
        assessmentArea.setMaxHeight(Double.MAX_VALUE);
        VBox assessmentWrap = new VBox(assessmentArea);
        assessmentWrap.setPadding(new Insets(4, 0, 0, 0));
        VBox.setVgrow(assessmentArea, Priority.ALWAYS);
        assessmentWrap.setMaxWidth(Double.MAX_VALUE);
        assessmentWrap.setMaxHeight(Double.MAX_VALUE);

        lektoratTab = new Tab("Lektorat");
        lektoratTab.setClosable(false);
        lektoratTab.setContent(lektoratWrap);

        assessmentTab = new Tab("Kapitel-Einschätzung");
        assessmentTab.setClosable(false);
        assessmentTab.setContent(assessmentWrap);

        bodyTabs = new TabPane();
        bodyTabs.getStyleClass().add("lektorat-panel-tabs");
        bodyTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        bodyTabs.getTabs().add(lektoratTab);
        bodyTabs.setMaxWidth(Double.MAX_VALUE);
        bodyTabs.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(bodyTabs, Priority.ALWAYS);

        container.getChildren().setAll(header, bodyTabs);
        applyThemeToHeader(titleLabel);
        themeApplier.accept(bodyTabs, themeIndex.getAsInt());
    }

    public void setOnExit(Runnable onExit) {
        this.onExit = onExit;
    }

    public void setExitInProgress(boolean inProgress) {
        exitButton.setText(inProgress ? "Abbrechen" : "Lektorat beenden");
    }

    /** {@code true}, wenn das Panel aktuell im Editor-SplitPane eingeblendet ist. */
    public boolean isVisible() {
        return splitPane != null && container != null && splitPane.getItems().contains(container);
    }

    /** Blendet das Panel aus, ohne Inhalt zu löschen. */
    public void hide() {
        ensureVisible(false);
    }

    public void applyFontSize(int size) {
        if (size < 8) {
            size = 8;
        } else if (size > 72) {
            size = 72;
        }
        fontSizePx = size;
        if (container != null) {
            container.setStyle(String.format("-fx-font-size: %dpx;", size));
            AgentFontSizeSupport.apply(container, size);
        }
    }

    private void applyCurrentEditorFontSize() {
        if (fontSizeSupplier != null) {
            applyFontSize(fontSizeSupplier.getAsInt());
        } else {
            applyFontSize(fontSizePx);
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

    public void showRunning() {
        ensureVisible(true);
        setExitInProgress(true);
        bodyTabs.getSelectionModel().select(lektoratTab);
        clearContent();
        clearAssessment();
        Label hint = new Label("Lektorat wird erstellt – bitte warten …");
        hint.setWrapText(true);
        hint.getStyleClass().add("lektorat-panel-hint");
        themeApplier.accept(hint, themeIndex.getAsInt());
        contentArea.getChildren().add(hint);
        applyCurrentEditorFontSize();
    }

    public void showHint(boolean hasMatches) {
        if (container == null) {
            return;
        }
        ensureVisible(true);
        setExitInProgress(false);
        clearContent();
        Label hint = new Label(hasMatches
                ? "Klicken Sie auf eine Markierung im Text."
                : "Keine Vorschläge.");
        hint.setWrapText(true);
        hint.getStyleClass().add("lektorat-panel-hint");
        themeApplier.accept(hint, themeIndex.getAsInt());
        contentArea.getChildren().add(hint);
        applyCurrentEditorFontSize();
    }

    public void showMatch(LektoratMatch match, Consumer<String> onApplySuggestion) {
        if (container == null || match == null) {
            return;
        }
        ensureVisible(true);
        setExitInProgress(false);
        bodyTabs.getSelectionModel().select(lektoratTab);
        clearContent();

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
        contentArea.getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        applyCurrentEditorFontSize();
    }

    public void showAppliedHint() {
        if (container == null) {
            return;
        }
        setExitInProgress(false);
        clearContent();
        Label hint = new Label("Vorschlag übernommen. Bei Bedarf Online-Lektorat erneut starten.");
        hint.setWrapText(true);
        themeApplier.accept(hint, themeIndex.getAsInt());
        contentArea.getChildren().add(hint);
        applyCurrentEditorFontSize();
    }

    public void clear() {
        ensureVisible(false);
        clearContent();
        clearAssessment();
        setExitInProgress(false);
    }

    /**
     * Lädt die Kapitel-Einschätzung im Tab „Kapitel-Einschätzung“ (parallel zum Lektorat-Lauf).
     */
    public void appendChapterAssessment(String chapterText) {
        if (container == null) {
            return;
        }
        ensureVisible(true);
        clearAssessment();
        setAssessmentTabVisible(true);
        int theme = themeIndex.getAsInt();

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

        ScrollPane assessmentScroll = new ScrollPane(assessmentBox);
        assessmentScroll.setFitToWidth(true);
        assessmentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        assessmentScroll.setMaxWidth(Double.MAX_VALUE);
        assessmentScroll.setMaxHeight(Double.MAX_VALUE);
        themeApplier.accept(assessmentScroll, theme);

        assessmentArea.getChildren().add(assessmentScroll);
        VBox.setVgrow(assessmentScroll, Priority.ALWAYS);
        applyCurrentEditorFontSize();

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
                    if (assessmentArea.getChildren().isEmpty()) {
                        return;
                    }
                    assessmentBox.getChildren().clear();
                    Label assessmentText = new Label(assessment);
                    assessmentText.setWrapText(true);
                    assessmentText.setMaxWidth(Double.MAX_VALUE);
                    themeApplier.accept(assessmentText, theme);
                    assessmentBox.getChildren().add(assessmentText);
                    applyCurrentEditorFontSize();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        if (assessmentArea.getChildren().isEmpty()) {
                            return;
                        }
                        assessmentBox.getChildren().clear();
                        Label errorText = new Label("Einschätzung fehlgeschlagen:\n" + ex.getMessage());
                        errorText.setWrapText(true);
                        errorText.setMaxWidth(Double.MAX_VALUE);
                        themeApplier.accept(errorText, theme);
                        assessmentBox.getChildren().add(errorText);
                        applyCurrentEditorFontSize();
                    });
                    logger.error("Kapitel-Einschätzung", ex);
                    return null;
                });
    }

    private void setAssessmentTabVisible(boolean visible) {
        ObservableList<Tab> tabs = bodyTabs.getTabs();
        if (visible) {
            if (!tabs.contains(assessmentTab)) {
                tabs.add(assessmentTab);
            }
        } else {
            tabs.remove(assessmentTab);
            if (bodyTabs.getSelectionModel().getSelectedItem() == assessmentTab) {
                bodyTabs.getSelectionModel().select(lektoratTab);
            }
        }
    }

    private void clearContent() {
        contentArea.getChildren().clear();
    }

    private void clearAssessment() {
        assessmentArea.getChildren().clear();
        setAssessmentTabVisible(false);
    }

    private void applyThemeToHeader(Label titleLabel) {
        int theme = themeIndex.getAsInt();
        themeApplier.accept(titleLabel, theme);
        themeApplier.accept(exitButton, theme);
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
