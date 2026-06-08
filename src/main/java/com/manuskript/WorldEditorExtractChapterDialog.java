package com.manuskript;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dialog zur Auswahl der Kapitel fuer „Aus Kapiteln“ im Welt-Editor.
 */
public final class WorldEditorExtractChapterDialog {

    private WorldEditorExtractChapterDialog() {
    }

    private static final class ChapterRow {
        private final String mdFileName;
        private final StringProperty displayName = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(true);

        ChapterRow(String mdFileName) {
            this.mdFileName = mdFileName;
            this.displayName.set(mdFileName.replace(".md", "").trim());
        }

        String getMdFileName() {
            return mdFileName;
        }

        StringProperty displayNameProperty() {
            return displayName;
        }

        BooleanProperty selectedProperty() {
            return selected;
        }
    }

    /**
     * Zeigt Kapitelauswahl; bei Abbrechen leer.
     *
     * @param availableMdFiles MD-Dateinamen unter data/ (Reihenfolge der Kapitelliste)
     */
    public static Optional<WorldEditorExtractScope> show(Window owner, List<String> availableMdFiles,
                                                         int themeIndex, boolean appendMode) {
        if (availableMdFiles == null || availableMdFiles.isEmpty()) {
            return Optional.empty();
        }

        ObservableList<ChapterRow> rows = FXCollections.observableArrayList();
        for (String md : availableMdFiles) {
            rows.add(new ChapterRow(md));
        }

        CustomStage dialogStage = StageManager.createStage("Kapitel fuer Extraktion");
        if (owner != null) {
            dialogStage.initOwner(owner);
        }
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setMinWidth(520);
        dialogStage.setMinHeight(420);

        Label intro = new Label(
                "Waehle die Kapitel, deren Volltext analysiert werden soll.\n"
                        + "Es werden die Markdown-Dateien aus data/ gelesen (chapter.txt wird ignoriert).");
        intro.setWrapText(true);

        TableView<ChapterRow> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(220);
        table.getStyleClass().add("alternating-list");

        TableColumn<ChapterRow, Boolean> colSelected = new TableColumn<>("Dabei");
        colSelected.setMaxWidth(70);
        colSelected.setCellValueFactory(data -> data.getValue().selectedProperty());
        colSelected.setCellFactory(CheckBoxTableCell.forTableColumn(colSelected));
        colSelected.setEditable(true);

        TableColumn<ChapterRow, String> colName = new TableColumn<>("Kapitel");
        colName.setCellValueFactory(data -> data.getValue().displayNameProperty());

        table.getColumns().addAll(colSelected, colName);
        table.setEditable(true);

        Button selectAll = new Button("Alle");
        Button selectNone = new Button("Keine");
        selectAll.setOnAction(e -> rows.forEach(r -> r.selectedProperty().set(true)));
        selectNone.setOnAction(e -> rows.forEach(r -> r.selectedProperty().set(false)));

        HBox selectBox = new HBox(8, selectAll, selectNone);
        selectBox.setAlignment(Pos.CENTER_LEFT);

        Label focusLabel = new Label("Figuren (optional, kommagetrennt):");
        TextField focusField = new TextField();
        focusField.setPromptText("z. B. Anna Mueller, Thomas Berg");
        focusField.setMaxWidth(Double.MAX_VALUE);

        Label focusHint = new Label(
                "Leer = alle erkennbaren Figuren/Fakten aus den gewaehlten Kapiteln. "
                        + "Mit Namen = gezieltes Ergaenzen/Aktualisieren dieser Figuren.");
        focusHint.setWrapText(true);
        focusHint.getStyleClass().add("status-label");

        Button okButton = new Button("Extrahieren");
        okButton.setDefaultButton(true);
        Button cancelButton = new Button("Abbrechen");
        cancelButton.setCancelButton(true);

        HBox buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));

        VBox root = new VBox(10, intro, table, selectBox, focusLabel, focusField, focusHint, buttonBox);
        if (appendMode) {
            Label appendHint = new Label(
                    "Modus „Ergaenzen“: Bestehender Tab-Inhalt bleibt erhalten; die KI aktualisiert anhand der Auswahl.");
            appendHint.setWrapText(true);
            appendHint.getStyleClass().add("status-label");
            root.getChildren().add(1, appendHint);
        }
        root.setPadding(new Insets(16));
        root.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);

        final WorldEditorExtractScope[] result = new WorldEditorExtractScope[1];

        okButton.setOnAction(e -> {
            List<String> selected = new ArrayList<>();
            for (ChapterRow row : rows) {
                if (row.selectedProperty().get()) {
                    selected.add(row.getMdFileName());
                }
            }
            if (selected.isEmpty()) {
                CustomAlert alert = new CustomAlert(CustomAlert.AlertType.WARNING);
                alert.setHeaderText("Keine Kapitel ausgewaehlt");
                alert.setContentText("Bitte mindestens ein Kapitel ankreuzen.");
                alert.applyTheme(themeIndex);
                alert.initOwner(dialogStage);
                alert.showAndWait();
                return;
            }
            result[0] = new WorldEditorExtractScope(List.copyOf(selected), focusField.getText());
            dialogStage.close();
        });

        cancelButton.setOnAction(e -> dialogStage.close());

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        applyTheme(root, themeIndex);
        dialogStage.setTitleBarTheme(themeIndex);
        dialogStage.setSceneWithTitleBar(scene);
        dialogStage.setFullTheme(themeIndex);
        DialogPositioning.centerWhenShown(dialogStage, owner != null ? owner : dialogStage);

        dialogStage.showAndWait();

        return result[0] != null ? Optional.of(result[0]) : Optional.empty();
    }

    private static void applyTheme(Parent root, int themeIndex) {
        root.getStyleClass().removeAll("weiss-theme", "theme-dark", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        switch (themeIndex) {
            case 1 -> root.getStyleClass().add("theme-dark");
            case 2 -> root.getStyleClass().add("pastell-theme");
            case 3 -> root.getStyleClass().add("blau-theme");
            case 4 -> root.getStyleClass().add("gruen-theme");
            case 5 -> root.getStyleClass().add("lila-theme");
            default -> root.getStyleClass().add("weiss-theme");
        }
    }
}
