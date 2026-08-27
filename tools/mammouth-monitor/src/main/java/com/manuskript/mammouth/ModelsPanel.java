package com.manuskript.mammouth;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab mit öffentlichen Mammouth-Modellen und Preisen.
 */
public class ModelsPanel extends VBox {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mammouth-models");
        t.setDaemon(true);
        return t;
    });

    private final MammouthClient client;
    private final TableView<Row> table = new TableView<>();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private HBox loadingBox;
    private volatile boolean refreshInProgress;

    public ModelsPanel(MammouthClient client) {
        this.client = client;
        setSpacing(12);
        setPadding(new Insets(16));
        buildUi();
    }

    private void buildUi() {
        Label title = new Label("Modelle & Preise");
        title.setFont(Font.font(null, FontWeight.BOLD, 18));

        TableColumn<Row, String> idCol = new TableColumn<>("Modell");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(340);

        TableColumn<Row, String> inCol = new TableColumn<>("Input");
        inCol.setCellValueFactory(new PropertyValueFactory<>("input"));
        inCol.setPrefWidth(140);

        TableColumn<Row, String> outCol = new TableColumn<>("Output");
        outCol.setCellValueFactory(new PropertyValueFactory<>("output"));
        outCol.setPrefWidth(140);

        table.getColumns().add(idCol);
        table.getColumns().add(inCol);
        table.getColumns().add(outCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        statusLabel.setWrapText(true);

        loadingBox = new HBox(10, progress, new Label("Modelle werden geladen…"));
        loadingBox.setAlignment(Pos.CENTER_LEFT);
        loadingBox.setVisible(false);
        loadingBox.setManaged(false);
        progress.setVisible(false);
        progress.setManaged(false);

        Label hint = new Label("Quelle: https://api.mammouth.ai/public/models — Preise pro 1 Million Tokens.");
        hint.setWrapText(true);

        getChildren().addAll(title, hint, loadingBox, table, statusLabel);
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
                List<MammouthClient.ModelInfo> models = client.getPublicModels();
                List<Row> rows = new ArrayList<>();
                for (MammouthClient.ModelInfo model : models) {
                    rows.add(new Row(
                            model.id(),
                            MammouthClient.formatPerMillion(model.inputPerMillion()),
                            MammouthClient.formatPerMillion(model.outputPerMillion())));
                }
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList(rows));
                    statusLabel.setText(rows.size() + " Modelle geladen.");
                    setLoading(false);
                    refreshInProgress = false;
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Modelle nicht verfügbar: " + e.getMessage());
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

    public static final class Row {
        private final String id;
        private final String input;
        private final String output;

        public Row(String id, String input, String output) {
            this.id = id;
            this.input = input;
            this.output = output;
        }

        public String getId() {
            return id;
        }

        public String getInput() {
            return input;
        }

        public String getOutput() {
            return output;
        }
    }
}
