package com.manuskript.mammouth;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab für Mammouth/LiteLLM Spend-Logs.
 */
public class LogsPanel extends VBox {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mammouth-logs");
        t.setDaemon(true);
        return t;
    });

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MammouthClient client;
    private final ComboBox<Integer> daysCombo = new ComboBox<>();
    private final TextField modelFilter = new TextField();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final TableView<LogRow> table = new TableView<>();
    private HBox loadingBox;
    private volatile boolean refreshInProgress;
    private FilteredList<LogRow> filteredRows;

    public LogsPanel(MammouthClient client) {
        this.client = client;
        setSpacing(12);
        setPadding(new Insets(20));
        buildUi();
    }

    private void buildUi() {
        Label title = new Label("API-Logs");
        title.setFont(Font.font(null, FontWeight.BOLD, 18));

        daysCombo.setItems(FXCollections.observableArrayList(1, 7, 30));
        daysCombo.getSelectionModel().select(Integer.valueOf(1));
        daysCombo.setPrefWidth(100);
        daysCombo.setOnAction(e -> refresh());

        modelFilter.setPromptText("Modell filtern…");
        modelFilter.setPrefWidth(220);
        HBox.setHgrow(modelFilter, Priority.ALWAYS);

        HBox filters = new HBox(12,
                new Label("Zeitraum:"), daysCombo, new Label("Tage"),
                new Label("Modell:"), modelFilter);
        filters.setAlignment(Pos.CENTER_LEFT);

        loadingBox = new HBox(10, progress, new Label("Logs werden geladen…"));
        loadingBox.setAlignment(Pos.CENTER_LEFT);
        loadingBox.setVisible(false);
        loadingBox.setManaged(false);
        progress.setVisible(false);
        progress.setManaged(false);

        TableColumn<LogRow, String> timeCol = new TableColumn<>("Zeit");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timeDisplay"));
        timeCol.setPrefWidth(170);

        TableColumn<LogRow, String> modelCol = new TableColumn<>("Modell");
        modelCol.setCellValueFactory(new PropertyValueFactory<>("model"));
        modelCol.setPrefWidth(260);

        TableColumn<LogRow, String> tokensCol = new TableColumn<>("Tokens");
        tokensCol.setCellValueFactory(new PropertyValueFactory<>("tokensDisplay"));
        tokensCol.setPrefWidth(100);

        TableColumn<LogRow, String> costCol = new TableColumn<>("Kosten");
        costCol.setCellValueFactory(new PropertyValueFactory<>("costDisplay"));
        costCol.setPrefWidth(100);

        table.getColumns().addAll(timeCol, modelCol, tokensCol, costCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        modelFilter.textProperty().addListener((obs, old, val) -> applyFilter());
        statusLabel.setWrapText(true);

        Label hint = new Label("Quelle: Mammouth GET /user/daily/activity (wie auf der Website unter Nutzungsprotokolle).");
        hint.setWrapText(true);

        getChildren().addAll(title, hint, filters, loadingBox, table, statusLabel);
    }

    public void refresh() {
        if (!client.hasApiKey()) {
            statusLabel.setText("Kein API-Key — bitte oben eintragen.");
            table.setItems(FXCollections.observableArrayList());
            setLoading(false);
            return;
        }
        if (refreshInProgress) {
            return;
        }
        refreshInProgress = true;
        int days = daysCombo.getSelectionModel().getSelectedItem() != null
                ? daysCombo.getSelectionModel().getSelectedItem() : 1;
        setLoading(true);
        statusLabel.setText("Lade Logs…");

        CompletableFuture.runAsync(() -> {
            try {
                List<LogRow> rows = client.getSpendLogs(days).stream().map(LogRow::from).toList();
                Platform.runLater(() -> {
                    var backing = FXCollections.observableArrayList(rows);
                    filteredRows = new FilteredList<>(backing, p -> true);
                    table.setItems(filteredRows);
                    applyFilter();
                    statusLabel.setText(rows.isEmpty()
                            ? "Keine Einträge im gewählten Zeitraum."
                            : rows.size() + " Einträge geladen.");
                    finishRefresh();
                });
            } catch (MammouthClient.ApiException e) {
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList());
                    statusLabel.setText(e.isAuthError()
                            ? "API-Key ungültig oder ohne Berechtigung für Logs: " + e.getMessage()
                            : "Logs nicht verfügbar: " + e.getMessage());
                    finishRefresh();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList());
                    statusLabel.setText("Logs nicht verfügbar: " + e.getMessage());
                    finishRefresh();
                });
            }
        }, EXECUTOR);
    }

    private void finishRefresh() {
        setLoading(false);
        refreshInProgress = false;
    }

    private void applyFilter() {
        if (filteredRows == null) {
            return;
        }
        String filter = modelFilter.getText() == null ? "" : modelFilter.getText().trim().toLowerCase();
        filteredRows.setPredicate(row -> filter.isEmpty()
                || (row.getModel() != null && row.getModel().toLowerCase().contains(filter)));
    }

    private void setLoading(boolean loading) {
        if (loadingBox != null) {
            loadingBox.setVisible(loading);
            loadingBox.setManaged(loading);
        }
    }

    public static class LogRow {
        private final String model;
        private final String timeDisplay;
        private final String tokensDisplay;
        private final String costDisplay;

        public LogRow(String model, String timeDisplay, String tokensDisplay, String costDisplay) {
            this.model = model;
            this.timeDisplay = timeDisplay;
            this.tokensDisplay = tokensDisplay;
            this.costDisplay = costDisplay;
        }

        public static LogRow from(MammouthClient.SpendLog entry) {
            String tokens = "—";
            if (entry.promptTokens() != null || entry.completionTokens() != null) {
                long prompt = entry.promptTokens() != null ? entry.promptTokens() : 0L;
                long completion = entry.completionTokens() != null ? entry.completionTokens() : 0L;
                long total = entry.totalTokens() != null ? entry.totalTokens() : prompt + completion;
                tokens = total + " (" + prompt + " + " + completion + ")";
            } else if (entry.totalTokens() != null && entry.totalTokens() > 0) {
                tokens = String.valueOf(entry.totalTokens());
            }
            return new LogRow(
                    entry.model() == null || entry.model().isBlank() ? "—" : entry.model(),
                    formatTime(entry.startedAt()),
                    tokens,
                    MammouthClient.formatUsd(entry.spend())
            );
        }

        private static String formatTime(String raw) {
            if (raw == null || raw.isBlank()) {
                return "—";
            }
            try {
                return DISPLAY_TIME.format(Instant.parse(raw));
            } catch (Exception ignored) {
            }
            try {
                return DISPLAY_TIME.format(LocalDateTime.parse(raw));
            } catch (Exception ignored) {
            }
            try {
                return DISPLAY_DATE.format(LocalDate.parse(raw));
            } catch (Exception ignored) {
            }
            return raw.replace('T', ' ');
        }

        public String getModel() {
            return model;
        }

        public String getTimeDisplay() {
            return timeDisplay;
        }

        public String getTokensDisplay() {
            return tokensDisplay;
        }

        public String getCostDisplay() {
            return costDisplay;
        }
    }
}
