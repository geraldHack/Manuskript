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

/**
 * Seitenpanel für Agenten-Ergebnisse im Editor.
 * Zeigt Findings mit klickbaren Zitaten an.
 */
public class AgentPanel extends VBox {

    private final ToggleButton realtimeToggle;
    private final Button analyzeButton;
    private final VBox findingsList;
    private final Label statusLabel;
    private final Label emptyLabel;

    private Consumer<String> onQuoteClicked;
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

        ScrollPane scrollPane = new ScrollPane(findingsList);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("agent-scroll");
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
        card.setMaxWidth(Double.MAX_VALUE); // Füllt verfügbare Breite

        // Schweregrad
        Label severityLabel = new Label(f.getSeverityStars());
        severityLabel.getStyleClass().add("finding-severity");
        severityLabel.setWrapText(true);
        severityLabel.setMaxWidth(Double.MAX_VALUE);

        // Problem (Label mit Breiten-Bindung für Wrap)
        Label problemLabel = new Label(f.getProblem());
        problemLabel.getStyleClass().add("finding-problem");
        problemLabel.setWrapText(true);
        problemLabel.setMaxWidth(Double.MAX_VALUE);
        problemLabel.minWidthProperty().bind(card.widthProperty().subtract(20));

        // Zitat (klickbar, Label mit Breiten-Bindung für Wrap)
        Label quoteLabel = new Label("Zitat: " + f.getQuote());
        quoteLabel.getStyleClass().add("finding-quote-text");
        quoteLabel.setWrapText(true);
        quoteLabel.setMaxWidth(Double.MAX_VALUE);
        quoteLabel.minWidthProperty().bind(card.widthProperty().subtract(20));
        // Theme-abhängige Farbe setzen
        String theme = com.manuskript.ResourceManager.getParameter("main_window_theme", "0");
        boolean isDarkTheme = theme.equals("1") || theme.equals("3"); // 1=Dunkel, 3=Lila
        String quoteColor = isDarkTheme ? "#ffb74d" : "#666";
        quoteLabel.setStyle("-fx-text-fill: " + quoteColor + ";");
        quoteLabel.setCursor(javafx.scene.Cursor.HAND);
        quoteLabel.setOnMouseClicked(e -> {
            if (onQuoteClicked != null && f.getQuote() != null && !f.getQuote().isEmpty()) {
                onQuoteClicked.accept(f.getQuote());
            }
        });

        // Vorschlag (Label mit Breiten-Bindung für Wrap)
        Label suggestionLabel = new Label("Vorschlag: " + f.getSuggestion());
        suggestionLabel.getStyleClass().add("finding-suggestion");
        suggestionLabel.setWrapText(true);
        suggestionLabel.setMaxWidth(Double.MAX_VALUE);
        suggestionLabel.minWidthProperty().bind(card.widthProperty().subtract(20));

        card.getChildren().addAll(severityLabel, problemLabel, quoteLabel, suggestionLabel);
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
