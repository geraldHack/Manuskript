package com.manuskript.openrouter;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab-Inhalt für OpenRouter Credits und Key-Nutzung.
 */
public class CreditsPanel extends VBox {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "openrouter-credits");
        t.setDaemon(true);
        return t;
    });

    private final OpenRouterClient client;
    private final Label statusLabel = new Label();
    private final Label accountRemainingLabel = new Label("—");
    private final Label accountUsedLabel = new Label("—");
    private final Label keyRemainingLabel = new Label("—");
    private final Label keyUsedLabel = new Label("—");
    private final Label dailyLabel = new Label("—");
    private final Label weeklyLabel = new Label("—");
    private final Label monthlyLabel = new Label("—");
    private final Label keyNameLabel = new Label("");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label accountHintLabel = new Label();
    private HBox loadingBox;
    private volatile boolean refreshInProgress;

    public CreditsPanel(OpenRouterClient client) {
        this.client = client;
        setSpacing(16);
        setPadding(new Insets(20));
        buildUi();
    }

    private void buildUi() {
        Label title = new Label("Guthaben & Verbrauch");
        title.setFont(Font.font(null, FontWeight.BOLD, 18));

        accountRemainingLabel.setFont(Font.font(null, FontWeight.BOLD, 28));
        accountUsedLabel.setFont(Font.font(null, FontWeight.BOLD, 28));
        keyRemainingLabel.setFont(Font.font(null, FontWeight.BOLD, 22));
        keyUsedLabel.setFont(Font.font(null, FontWeight.BOLD, 22));

        GridPane accountGrid = metricGrid("Konto (OpenRouter Credits)",
                "Verbleibend", accountRemainingLabel,
                "Verbraucht gesamt", accountUsedLabel);

        GridPane keyGrid = metricGrid("API-Key (aktueller Inference-Key)",
                "Limit verbleibend", keyRemainingLabel,
                "Verbraucht gesamt", keyUsedLabel);

        GridPane periodGrid = new GridPane();
        periodGrid.setHgap(24);
        periodGrid.setVgap(8);
        periodGrid.add(new Label("Heute:"), 0, 0);
        periodGrid.add(dailyLabel, 1, 0);
        periodGrid.add(new Label("Diese Woche:"), 0, 1);
        periodGrid.add(weeklyLabel, 1, 1);
        periodGrid.add(new Label("Dieser Monat:"), 0, 2);
        periodGrid.add(monthlyLabel, 1, 2);

        accountHintLabel.setWrapText(true);
        accountHintLabel.getStyleClass().add("hint-label");

        statusLabel.setWrapText(true);

        loadingBox = new HBox(10, progress, new Label("Daten werden geladen…"));
        loadingBox.setAlignment(Pos.CENTER_LEFT);
        loadingBox.setVisible(false);
        loadingBox.setManaged(false);
        progress.setVisible(false);
        progress.setManaged(false);

        getChildren().addAll(title, loadingBox, accountGrid, accountHintLabel, keyGrid, keyNameLabel, periodGrid, statusLabel);
        VBox.setVgrow(statusLabel, Priority.ALWAYS);
    }

    private GridPane metricGrid(String sectionTitle, String leftCaption, Label leftValue,
                                String rightCaption, Label rightValue) {
        Label section = new Label(sectionTitle);
        section.setFont(Font.font(null, FontWeight.SEMI_BOLD, 14));

        GridPane grid = new GridPane();
        grid.setHgap(32);
        grid.setVgap(8);
        grid.add(section, 0, 0, 2, 1);
        grid.add(new Label(leftCaption), 0, 1);
        grid.add(leftValue, 0, 2);
        grid.add(new Label(rightCaption), 1, 1);
        grid.add(rightValue, 1, 2);
        return grid;
    }

    public void refresh() {
        if (refreshInProgress) {
            return;
        }
        refreshInProgress = true;
        setLoading(true);
        statusLabel.setText("");
        CompletableFuture.runAsync(() -> {
            try {
                String accountHint = "";
                String accountRemaining = "—";
                String accountUsed = "—";
                String keyRemaining = "—";
                String keyUsed = "—";
                String daily = "—";
                String weekly = "—";
                String monthly = "—";
                String keyName = "";
                String status = "";

                try {
                    OpenRouterClient.AccountCredits credits = client.getAccountCredits();
                    accountRemaining = OpenRouterClient.formatUsd(credits.getRemaining());
                    accountUsed = OpenRouterClient.formatUsd(credits.getTotalUsage());
                } catch (OpenRouterClient.ApiException e) {
                    if (e.isAuthError()) {
                        accountHint = "Kontoguthaben erfordert einen OpenRouter Management API Key "
                                + "(oben eingeben und speichern). "
                                + "Key-Statistiken unten nutzen den Inference-Key aus Manuskript.";
                    } else {
                        accountHint = "Kontoguthaben nicht verfügbar: " + e.getMessage();
                    }
                } catch (Exception e) {
                    accountHint = "Kontoguthaben nicht verfügbar: " + e.getMessage();
                }

                try {
                    OpenRouterClient.KeyInfo key = client.getKeyInfo();
                    if (key.getLimitRemaining() != null) {
                        keyRemaining = OpenRouterClient.formatUsd(key.getLimitRemaining());
                    } else {
                        keyRemaining = "unbegrenzt";
                    }
                    keyUsed = OpenRouterClient.formatUsd(key.getUsage());
                    daily = OpenRouterClient.formatUsd(key.getUsageDaily());
                    weekly = OpenRouterClient.formatUsd(key.getUsageWeekly());
                    monthly = OpenRouterClient.formatUsd(key.getUsageMonthly());
                    if (key.getLabel() != null && !key.getLabel().isBlank()) {
                        keyName = "Key: " + key.getLabel() + (key.isFreeTier() ? " (Free Tier)" : "");
                    }
                } catch (OpenRouterClient.ApiException e) {
                    status = "API-Key-Statistiken nicht verfügbar: " + e.getMessage();
                } catch (Exception e) {
                    status = "API-Key-Statistiken nicht verfügbar: " + e.getMessage();
                }

                final String fAccountHint = accountHint;
                final String fAccountRemaining = accountRemaining;
                final String fAccountUsed = accountUsed;
                final String fKeyRemaining = keyRemaining;
                final String fKeyUsed = keyUsed;
                final String fDaily = daily;
                final String fWeekly = weekly;
                final String fMonthly = monthly;
                final String fKeyName = keyName;
                final String fStatus = status;

                Platform.runLater(() -> {
                    accountRemainingLabel.setText(fAccountRemaining);
                    accountUsedLabel.setText(fAccountUsed);
                    keyRemainingLabel.setText(fKeyRemaining);
                    keyUsedLabel.setText(fKeyUsed);
                    dailyLabel.setText(fDaily);
                    weeklyLabel.setText(fWeekly);
                    monthlyLabel.setText(fMonthly);
                    keyNameLabel.setText(fKeyName);
                    accountHintLabel.setText(fAccountHint);
                    statusLabel.setText(fStatus);
                    setLoading(false);
                    refreshInProgress = false;
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Laden fehlgeschlagen: " + e.getMessage());
                    setLoading(false);
                    refreshInProgress = false;
                });
            }
        }, EXECUTOR);
    }

    private void setLoading(boolean loading) {
        if (loadingBox != null) {
            loadingBox.setVisible(loading);
            loadingBox.setManaged(loading);
        }
    }
}
