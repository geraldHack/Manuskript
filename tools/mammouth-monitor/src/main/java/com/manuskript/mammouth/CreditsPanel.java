package com.manuskript.mammouth;

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
import java.util.stream.Collectors;

/**
 * Tab für Mammouth-Guthaben (LiteLLM {@code /key/info}).
 */
public class CreditsPanel extends VBox {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mammouth-credits");
        t.setDaemon(true);
        return t;
    });

    private final MammouthClient client;
    private final Label remainingLabel = new Label("—");
    private final Label spendLabel = new Label("—");
    private final Label budgetLabel = new Label("—");
    private final Label resetLabel = new Label("—");
    private final Label durationLabel = new Label("—");
    private final Label aliasLabel = new Label("");
    private final Label modelsLabel = new Label("");
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private HBox loadingBox;
    private volatile boolean refreshInProgress;

    public CreditsPanel(MammouthClient client) {
        this.client = client;
        setSpacing(16);
        setPadding(new Insets(20));
        buildUi();
    }

    private void buildUi() {
        Label title = new Label("Guthaben & Verbrauch");
        title.setFont(Font.font(null, FontWeight.BOLD, 18));

        remainingLabel.setFont(Font.font(null, FontWeight.BOLD, 28));
        spendLabel.setFont(Font.font(null, FontWeight.BOLD, 22));
        budgetLabel.setFont(Font.font(null, FontWeight.BOLD, 22));

        GridPane main = new GridPane();
        main.setHgap(32);
        main.setVgap(8);
        main.add(new Label("Verbleibend"), 0, 0);
        main.add(remainingLabel, 0, 1);
        main.add(new Label("Verbraucht (aktuelles Fenster)"), 1, 0);
        main.add(spendLabel, 1, 1);
        main.add(new Label("Max. Budget"), 0, 2);
        main.add(budgetLabel, 0, 3);

        GridPane extra = new GridPane();
        extra.setHgap(24);
        extra.setVgap(8);
        extra.add(new Label("Budget-Dauer:"), 0, 0);
        extra.add(durationLabel, 1, 0);
        extra.add(new Label("Nächster Reset:"), 0, 1);
        extra.add(resetLabel, 1, 1);

        aliasLabel.setWrapText(true);
        modelsLabel.setWrapText(true);
        statusLabel.setWrapText(true);

        loadingBox = new HBox(10, progress, new Label("Daten werden geladen…"));
        loadingBox.setAlignment(Pos.CENTER_LEFT);
        loadingBox.setVisible(false);
        loadingBox.setManaged(false);
        progress.setVisible(false);
        progress.setManaged(false);

        getChildren().addAll(title, loadingBox, main, extra, aliasLabel, modelsLabel, statusLabel);
        VBox.setVgrow(statusLabel, Priority.ALWAYS);
    }

    public void refresh() {
        if (refreshInProgress) {
            return;
        }
        if (!client.hasApiKey()) {
            statusLabel.setText("Kein API-Key — bitte oben eintragen.");
            remainingLabel.setText("—");
            spendLabel.setText("—");
            budgetLabel.setText("—");
            return;
        }
        refreshInProgress = true;
        setLoading(true);
        statusLabel.setText("");
        CompletableFuture.runAsync(() -> {
            try {
                MammouthClient.KeyInfo info = client.getKeyInfo();
                Platform.runLater(() -> {
                    remainingLabel.setText(MammouthClient.formatUsd(info.remaining()));
                    spendLabel.setText(MammouthClient.formatUsd(info.spend()));
                    budgetLabel.setText(info.maxBudget() == null ? "unbegrenzt" : MammouthClient.formatUsd(info.maxBudget()));
                    durationLabel.setText(blankToDash(info.budgetDuration()));
                    resetLabel.setText(blankToDash(info.budgetResetAt()));
                    String alias = !isBlank(info.keyAlias()) ? info.keyAlias() : info.keyName();
                    aliasLabel.setText(isBlank(alias) ? "" : "Key: " + alias);
                    if (info.allowedModels() != null && !info.allowedModels().isEmpty()) {
                        modelsLabel.setText("Erlaubte Modelle: " + info.allowedModels().stream()
                                .limit(12)
                                .collect(Collectors.joining(", "))
                                + (info.allowedModels().size() > 12 ? " …" : ""));
                    } else {
                        modelsLabel.setText("Erlaubte Modelle: alle (kein Filter am Key)");
                    }
                    statusLabel.setText("");
                    setLoading(false);
                    refreshInProgress = false;
                });
            } catch (MammouthClient.ApiException e) {
                Platform.runLater(() -> {
                    statusLabel.setText(e.isAuthError()
                            ? "API-Key ungültig oder ohne Berechtigung: " + e.getMessage()
                            : "Credits nicht verfügbar: " + e.getMessage());
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToDash(String value) {
        return isBlank(value) ? "—" : value;
    }
}
