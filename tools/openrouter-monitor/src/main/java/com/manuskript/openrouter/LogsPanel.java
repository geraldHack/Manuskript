package com.manuskript.openrouter;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab-Inhalt für OpenRouter API-Logs (Analytics).
 */
public class LogsPanel extends VBox {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "openrouter-logs");
        t.setDaemon(true);
        return t;
    });

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final OpenRouterClient client;
    private final ComboBox<Integer> daysCombo = new ComboBox<>();
    private final TextField modelFilter = new TextField();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final TableView<LogRow> table = new TableView<>();
    private HBox loadingBox;
    private volatile boolean refreshInProgress;

    public LogsPanel(OpenRouterClient client) {
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
        modelCol.setPrefWidth(280);

        TableColumn<LogRow, String> tokensCol = new TableColumn<>("Tokens");
        tokensCol.setCellValueFactory(new PropertyValueFactory<>("tokensDisplay"));
        tokensCol.setPrefWidth(100);

        TableColumn<LogRow, String> costCol = new TableColumn<>("Kosten");
        costCol.setCellValueFactory(new PropertyValueFactory<>("costDisplay"));
        costCol.setPrefWidth(100);

        table.getColumns().addAll(timeCol, modelCol, tokensCol, costCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                LogRow row = table.getSelectionModel().getSelectedItem();
                if (row != null) {
                    showGenerationDetail(row.getGenerationId());
                }
            }
        });

        modelFilter.textProperty().addListener((obs, old, val) -> applyFilter());
        statusLabel.setWrapText(true);

        getChildren().addAll(title, filters, loadingBox, table, statusLabel);
    }

    private FilteredList<LogRow> filteredRows;

    public void refresh() {
        if (!client.hasManagementApiKey()) {
            statusLabel.setText("Bitte oben den Management-Key eingeben und „Key speichern“ klicken.");
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
        statusLabel.setText("Lade Logs und Details…");

        CompletableFuture.runAsync(() -> {
            try {
                OpenRouterClient.LogsQueryResult result = client.queryLogsWithMeta(days, 200);
                List<LogRow> rows = result.getEntries().stream().map(LogRow::from).toList();
                Platform.runLater(() -> {
                    var backing = FXCollections.observableArrayList(rows);
                    filteredRows = new FilteredList<>(backing, p -> true);
                    table.setItems(filteredRows);
                    applyFilter();
                    if (rows.isEmpty()) {
                        statusLabel.setText("Keine Einträge im gewählten Zeitraum.");
                    } else {
                        String msg = rows.size() + " Einträge geladen";
                        if (result.isTruncated()) {
                            msg += " (gekürzt – Zeitraum verkleinern oder Limit erhöhen)";
                        }
                        statusLabel.setText(msg);
                    }
                    finishRefresh();
                });
            } catch (OpenRouterClient.ApiException e) {
                String msg = e.isAuthError()
                        ? "Ungültiger Management-Key oder keine Berechtigung für Logs."
                        : "Logs nicht verfügbar: " + e.getMessage();
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList());
                    statusLabel.setText(msg);
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
        if (filteredRows == null) return;
        String filter = modelFilter.getText() == null ? "" : modelFilter.getText().trim().toLowerCase();
        filteredRows.setPredicate(row -> filter.isEmpty()
                || (row.getModel() != null && row.getModel().toLowerCase().contains(filter)));
    }

    private void showGenerationDetail(String generationId) {
        if (generationId == null || generationId.isBlank()) return;
        CompletableFuture.runAsync(() -> {
            try {
                OpenRouterClient.GenerationDetail detail = client.getGeneration(generationId);
                StringBuilder text = new StringBuilder();
                text.append("ID: ").append(detail.getId()).append("\n");
                text.append("Modell: ").append(detail.getModel()).append("\n");
                if (detail.getCreatedAt() != null && !detail.getCreatedAt().isBlank()) {
                    text.append("Zeit: ").append(detail.getCreatedAt()).append("\n");
                }
                if (detail.getTotalCost() != null) {
                    text.append("Kosten: ").append(OpenRouterClient.formatUsd(detail.getTotalCost())).append("\n");
                }
                if (detail.getTokensPrompt() != null) {
                    text.append("Prompt-Tokens: ").append(detail.getTokensPrompt()).append("\n");
                }
                if (detail.getTokensCompletion() != null) {
                    text.append("Completion-Tokens: ").append(detail.getTokensCompletion()).append("\n");
                }
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Generation-Detail");
                    alert.setHeaderText("OpenRouter Request");
                    alert.setContentText(text.toString());
                    alert.showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Generation-Detail");
                    alert.setHeaderText("Detail konnte nicht geladen werden");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
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

    public static class LogRow {
        private final String generationId;
        private final String model;
        private final String timeDisplay;
        private final String tokensDisplay;
        private final String costDisplay;

        public LogRow(String generationId, String model, String timeDisplay, String tokensDisplay, String costDisplay) {
            this.generationId = generationId;
            this.model = model;
            this.timeDisplay = timeDisplay;
            this.tokensDisplay = tokensDisplay;
            this.costDisplay = costDisplay;
        }

        public static LogRow from(OpenRouterClient.LogEntry entry) {
            String time = formatTime(entry.getCreatedAt());
            String tokens = entry.getTokensTotal() > 0 ? String.valueOf(entry.getTokensTotal()) : "—";
            String cost = entry.getTotalUsage() > 0 ? OpenRouterClient.formatUsd(entry.getTotalUsage()) : "—";
            return new LogRow(
                    entry.getGenerationId(),
                    entry.getModel().isBlank() ? "—" : entry.getModel(),
                    time,
                    tokens,
                    cost
            );
        }

        private static String formatTime(String raw) {
            if (raw == null || raw.isBlank()) return "—";
            try {
                return DISPLAY_TIME.format(Instant.parse(raw));
            } catch (Exception e) {
                return raw;
            }
        }

        public String getGenerationId() { return generationId; }
        public String getModel() { return model; }
        public String getTimeDisplay() { return timeDisplay; }
        public String getTokensDisplay() { return tokensDisplay; }
        public String getCostDisplay() { return costDisplay; }
    }
}
