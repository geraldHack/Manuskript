package com.manuskript.agent;

import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seitenpanel für Agenten-Ergebnisse im Editor.
 * Zeigt Findings mit klickbaren Zitaten an.
 */
public class AgentPanel extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(AgentPanel.class);

    private final ToggleButton realtimeToggle;
    private final Button analyzeButton;
    private final VBox findingsList;
    private ScrollPane scrollPane;
    private final Label statusLabel;
    private final Label emptyLabel;

    private Consumer<String> onQuoteClicked;
    private Consumer<Finding> onSuggestionClicked;
    private boolean realtimeEnabled = false;
    private boolean analyzing = false;

    public AgentPanel() {
        setPadding(new Insets(8));
        setSpacing(8);
        setPrefWidth(280);
        setMinWidth(200);
        getStyleClass().add("agent-panel");

        // Header
        Label title = new Label("\uD83D\uDD0D Plothole-Agent");
        title.getStyleClass().add("agent-panel-title");

        // Status
        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("agent-status");

        // Buttons
        analyzeButton = new Button("\u25B6 Jetzt pruefen");
        analyzeButton.setMaxWidth(Double.MAX_VALUE);
        analyzeButton.getStyleClass().add("agent-analyze-btn");
        analyzeButton.setOnAction(e -> {
            if (onAnalyzeClicked != null) onAnalyzeClicked.run();
        });

        realtimeToggle = new ToggleButton("\u26A1 Echtzeit");
        realtimeToggle.setMaxWidth(Double.MAX_VALUE);
        realtimeToggle.setTooltip(new Tooltip("Beim Tippen automatisch auf Widersprueche pruefen"));
        realtimeToggle.getStyleClass().add("agent-realtime-btn");
        realtimeToggle.setOnAction(e -> {
            realtimeEnabled = realtimeToggle.isSelected();
            if (onRealtimeToggled != null) onRealtimeToggled.accept(realtimeEnabled);
        });

        HBox buttonRow = new HBox(6);
        buttonRow.getChildren().addAll(analyzeButton, realtimeToggle);
        HBox.setHgrow(analyzeButton, Priority.ALWAYS);
        HBox.setHgrow(realtimeToggle, Priority.ALWAYS);

        // Findings-Liste
        findingsList = new VBox(6);
        findingsList.setPadding(new Insets(4, 0, 4, 0));
        findingsList.getStyleClass().add("agent-findings-list");
        findingsList.setPrefWidth(260); // Feste Breite für Umbruch

        emptyLabel = new Label("Noch keine Analyse.\nKlicke \u25B6 oder aktiviere \u26A1.");
        emptyLabel.getStyleClass().add("agent-empty-label");
        emptyLabel.setWrapText(true);
        findingsList.getChildren().add(emptyLabel);

        scrollPane = new ScrollPane(findingsList);
        AgentScrollPaneSupport.configureFindingsScrollPane(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(title, statusLabel, buttonRow, scrollPane);
    }

    // Callbacks
    private Runnable onAnalyzeClicked;
    private Consumer<Boolean> onRealtimeToggled;

    public void setOnAnalyzeClicked(Runnable callback) {
        this.onAnalyzeClicked = callback;
    }

    public void setOnRealtimeToggled(Consumer<Boolean> callback) {
        this.onRealtimeToggled = callback;
    }

    public void setOnQuoteClicked(Consumer<String> callback) {
        this.onQuoteClicked = callback;
    }

    public void setOnSuggestionClicked(Consumer<Finding> callback) {
        this.onSuggestionClicked = callback;
    }

    public boolean isRealtimeEnabled() {
        return realtimeEnabled;
    }

    public void setRealtimeEnabled(boolean enabled) {
        this.realtimeEnabled = enabled;
        realtimeToggle.setSelected(enabled);
    }

    public boolean isAnalyzing() {
        return analyzing;
    }

    /**
     * Zeigt die Findings im Panel an.
     */
    public void showFindings(List<Finding> findings) {
        Platform.runLater(() -> {
            findingsList.getChildren().clear();

            if (findings.isEmpty()) {
                Label ok = new Label("\u2705 Keine Widersprueche gefunden.");
                ok.getStyleClass().add("agent-ok-label");
                findingsList.getChildren().add(ok);
                statusLabel.setText("Analyse abgeschlossen - keine Probleme");
                statusLabel.getStyleClass().removeAll("agent-status-error");
                statusLabel.getStyleClass().add("agent-status-ok");
            } else {
                statusLabel.setText(findings.size() + " Widersprueche gefunden");
                statusLabel.getStyleClass().removeAll("agent-status-ok");
                statusLabel.getStyleClass().add("agent-status-error");

                for (Finding f : findings) {
                    VBox card = createFindingCard(f);
                    findingsList.getChildren().add(card);
                }
            }
            analyzing = false;
            analyzeButton.setDisable(false);
        });
    }

    private VBox createFindingCard(Finding f) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(6));
        card.getStyleClass().add("finding-card");
        card.setMaxWidth(Double.MAX_VALUE);

        // Schriftgröße aus Preferences lesen (wie im Editor)
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(com.manuskript.MainController.class);
        int editorFontSize = prefs.getInt("fontSize", 12);

        Label severityLabel = new Label(f.getSeverityStars());
        severityLabel.getStyleClass().add("finding-severity");
        severityLabel.setWrapText(true);
        severityLabel.setMinWidth(0);
        severityLabel.setMaxWidth(Double.MAX_VALUE);
        severityLabel.setStyle(severityLabel.getStyle() + String.format("-fx-font-size: %dpx;", editorFontSize));

        Text problemText = new Text(f.getProblem());
        problemText.setStyle(AgentFindingStyles.problemTextStyle(editorFontSize));
        TextFlow problemFlow = new TextFlow(problemText);
        problemFlow.getStyleClass().add("finding-problem");
        problemFlow.setMinWidth(0);
        problemFlow.setMaxWidth(Double.MAX_VALUE);
        problemFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));

        Text quoteText = new Text("Zitat: " + f.getQuote());
        quoteText.setStyle(AgentFindingStyles.quoteTextStyle(editorFontSize));
        TextFlow quoteFlow = new TextFlow(quoteText);
        quoteFlow.getStyleClass().add("finding-quote-text");
        quoteFlow.setMinWidth(0);
        quoteFlow.setMaxWidth(Double.MAX_VALUE);
        quoteFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));
        quoteFlow.setCursor(javafx.scene.Cursor.HAND);
        quoteFlow.setOnMouseClicked(e -> {
            if (onQuoteClicked != null && f.getQuote() != null && !f.getQuote().isEmpty()) {
                onQuoteClicked.accept(f.getQuote());
            }
        });

        // Vorschläge anzeigen (mehrere möglich)
        VBox suggestionsBox = new VBox();
        suggestionsBox.setSpacing(4);

        logger.info("Finding hat {} Vorschläge, SuggestionIndex: {}", 
            f.getSuggestions() != null ? f.getSuggestions().size() : 0, 
            f.getSuggestionIndex());

        if (f.getSuggestions() != null && !f.getSuggestions().isEmpty()) {
            for (int i = 0; i < f.getSuggestions().size(); i++) {
                String suggestion = f.getSuggestions().get(i);
                logger.info("Vorschlag {}: {}", i + 1, suggestion);
                Text suggestionTextNode = new Text("Vorschlag " + (i + 1) + ": " + suggestion);
                String suggestionTheme = com.manuskript.ResourceManager.getParameter("main_window_theme", "0");
                boolean suggestionIsDarkTheme = suggestionTheme.equals("1") || suggestionTheme.equals("3");
                String suggestionColor;
                if (f.getSuggestionIndex() >= 0) {
                    suggestionColor = suggestionIsDarkTheme ? "#81c784" : "#2e7d32";
                } else {
                    suggestionColor = suggestionIsDarkTheme ? "#90caf9" : "#1976d2";
                }
                String suggestionStyle = String.format("-fx-fill: %s; -fx-font-size: %dpx;", suggestionColor, editorFontSize);
                suggestionTextNode.setStyle(suggestionStyle);
                TextFlow suggestionFlow = new TextFlow(suggestionTextNode);
                suggestionFlow.getStyleClass().add("finding-suggestion");
                suggestionFlow.setMinWidth(0);
                suggestionFlow.setMaxWidth(Double.MAX_VALUE);
                suggestionFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));

                if (f.getSuggestionIndex() >= 0) {
                    suggestionFlow.setCursor(javafx.scene.Cursor.HAND);
                    final String finalSuggestion = suggestion;
                    suggestionFlow.setOnMouseClicked(e -> {
                        if (onSuggestionClicked != null && finalSuggestion != null && !finalSuggestion.isEmpty()) {
                            Finding tempFinding = new Finding(f.getSeverity(), f.getQuote(), f.getProblem(), finalSuggestion);
                            tempFinding.setSuggestionIndex(f.getSuggestionIndex());
                            onSuggestionClicked.accept(tempFinding);
                        }
                    });
                }

                suggestionsBox.getChildren().add(suggestionFlow);
            }
        } else {
            Text suggestionTextNode = new Text("Vorschlag: " + f.getSuggestion());
            String suggestionTheme = com.manuskript.ResourceManager.getParameter("main_window_theme", "0");
            boolean suggestionIsDarkTheme = suggestionTheme.equals("1") || suggestionTheme.equals("3");
            String suggestionColor;
            if (f.getSuggestionIndex() >= 0) {
                suggestionColor = suggestionIsDarkTheme ? "#81c784" : "#2e7d32";
            } else {
                suggestionColor = suggestionIsDarkTheme ? "#90caf9" : "#1976d2";
            }
            String suggestionStyle = String.format("-fx-fill: %s; -fx-font-size: %dpx;", suggestionColor, editorFontSize);
            suggestionTextNode.setStyle(suggestionStyle);
            TextFlow suggestionFlow = new TextFlow(suggestionTextNode);
            suggestionFlow.getStyleClass().add("finding-suggestion");
            suggestionFlow.setMinWidth(0);
            suggestionFlow.setMaxWidth(Double.MAX_VALUE);
            suggestionFlow.prefWidthProperty().bind(scrollPane.widthProperty().subtract(40));

            if (f.getSuggestionIndex() >= 0) {
                suggestionFlow.setCursor(javafx.scene.Cursor.HAND);
                suggestionFlow.setOnMouseClicked(e -> {
                    if (onSuggestionClicked != null && f.getSuggestion() != null && !f.getSuggestion().isEmpty()) {
                        onSuggestionClicked.accept(f);
                    }
                });
            }
            suggestionsBox.getChildren().add(suggestionFlow);
        }

        card.getChildren().addAll(severityLabel, problemFlow, quoteFlow, suggestionsBox);
        return card;
    }

    /**
     * Setzt das Panel auf "analysiere"-Status.
     */
    public void setAnalyzing(boolean analyzing) {
        this.analyzing = analyzing;
        Platform.runLater(() -> {
            analyzeButton.setDisable(analyzing);
            if (analyzing) {
                statusLabel.setText("Analysiere...");
                statusLabel.getStyleClass().removeAll("agent-status-ok", "agent-status-error");
                statusLabel.getStyleClass().add("agent-status-running");
            }
        });
    }

    /**
     * Leert die Findings-Liste.
     */
    public void clearFindings() {
        Platform.runLater(() -> {
            findingsList.getChildren().clear();
            findingsList.getChildren().add(emptyLabel);
            statusLabel.setText("Bereit");
            statusLabel.getStyleClass().removeAll("agent-status-ok", "agent-status-error", "agent-status-running");
        });
    }

    /**
     * Zeigt einen Fehlerstatus an.
     */
    public void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("Fehler: " + message);
            statusLabel.getStyleClass().removeAll("agent-status-ok", "agent-status-running");
            statusLabel.getStyleClass().add("agent-status-error");
            analyzing = false;
            analyzeButton.setDisable(false);
        });
    }
}
