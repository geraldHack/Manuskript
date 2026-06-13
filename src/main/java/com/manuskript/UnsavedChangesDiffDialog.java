package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Read-only Diff zwischen gespeicherter Datei und aktuellem Editor-Inhalt
 * (ungespeicherte Änderungen).
 */
public final class UnsavedChangesDiffDialog {

    private static final double COLUMN_GAP = 12;

    private UnsavedChangesDiffDialog() {
    }

    public static void showForChapterEditor(ChapterEditorHost host) {
        if (host == null || host.getStage() == null) {
            return;
        }
        String baseline = loadSavedBaseline(host);
        String current = host.getText() != null ? host.getText() : "";
        String title = host.getEditorKey() != null ? host.getEditorKey() : "Kapitel";
        show(host.getStage(), host.getThemeIndex(), title, baseline, current);
    }

    static String loadSavedBaseline(ChapterEditorHost host) {
        if (host == null) {
            return "";
        }
        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas != null) {
            return canvas.readSavedChapterFileContent();
        }
        EditorWindow legacy = host.asLegacyEditorWindow();
        if (legacy != null) {
            return legacy.readSavedChapterFileContent();
        }
        return "";
    }

    public static void show(Window owner, int themeIndex, String chapterLabel,
                            String savedBaseline, String currentText) {
        String baseline = savedBaseline != null ? savedBaseline : "";
        String current = currentText != null ? currentText : "";

        DiffProcessor.DiffResult diff = DiffProcessor.createDiff(baseline, current);
        if (!diff.hasChanges()) {
            CustomAlert alert = new CustomAlert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Keine Änderungen");
            alert.setHeaderText(null);
            alert.setContentText("Der aktuelle Text entspricht der gespeicherten Version.");
            alert.applyTheme(themeIndex);
            if (owner instanceof javafx.stage.Stage ownerStage) {
                alert.initOwner(ownerStage);
            }
            alert.showAndWait();
            return;
        }

        CustomStage stage = StageManager.createDiffStage(
                "Änderungen seit Speichern: " + chapterLabel, owner);
        stage.setTitleBarTheme(themeIndex);

        String bg = EditorDialogThemes.color(themeIndex, 0);
        String text = EditorDialogThemes.color(themeIndex, 1);
        String border = EditorDialogThemes.color(themeIndex, 3);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle(String.format("-fx-background-color: %s;", bg));
        EditorDialogThemes.applyToNode(root, themeIndex);

        Label titleLabel = new Label("Ungespeicherte Änderungen in " + chapterLabel);
        titleLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: %s;", text));

        Label hintLabel = new Label(
                "Links: zuletzt gespeicherte Version · Rechts: aktueller Editor-Text");
        hintLabel.setWrapText(true);
        hintLabel.setStyle(String.format("-fx-font-size: 12px; -fx-text-fill: %s; -fx-opacity: 0.85;", text));

        CheckBox hideUnchanged = new CheckBox("Unveränderte Zeilen ausblenden");
        hideUnchanged.setSelected(true);
        hideUnchanged.setStyle(String.format("-fx-text-fill: %s;", text));

        GridPane headerGrid = createTwoColumnGrid(border);
        Label leftHeader = columnHeader("💾 Gespeichert", text);
        Label rightHeader = columnHeader("✏️ Aktuell (ungespeichert)", text);
        headerGrid.add(wrapColumnHeader(leftHeader, border, true), 0, 0);
        headerGrid.add(wrapColumnHeader(rightHeader, border, false), 1, 0);

        GridPane diffGrid = createTwoColumnGrid(border);
        buildDiffRows(diffGrid, diff.getDiffLines(), text, border, hideUnchanged.isSelected());

        hideUnchanged.selectedProperty().addListener((obs, oldVal, hide) -> {
            diffGrid.getChildren().clear();
            diffGrid.getRowConstraints().clear();
            buildDiffRows(diffGrid, diff.getDiffLines(), text, border, hide);
        });

        ScrollPane scroll = new ScrollPane(diffGrid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button closeButton = new Button("Schließen");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> stage.close());
        HBox buttons = new HBox(closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(titleLabel, hintLabel, hideUnchanged, headerGrid, scroll, buttons);

        Scene scene = new Scene(root, 1200, 720);
        scene.setFill(javafx.scene.paint.Color.web(bg));
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        stage.showAndWait();
    }

    private static GridPane createTwoColumnGrid(String borderColor) {
        GridPane grid = new GridPane();
        grid.setHgap(COLUMN_GAP);
        grid.setVgap(0);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(50);
        leftCol.setHgrow(Priority.ALWAYS);

        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(50);
        rightCol.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(leftCol, rightCol);
        return grid;
    }

    private static Label columnHeader(String text, String themeTextColor) {
        Label label = new Label(text);
        label.setStyle(String.format("-fx-font-weight: bold; -fx-text-fill: %s;", themeTextColor));
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static VBox wrapColumnHeader(Label header, String borderColor, boolean leftColumn) {
        VBox box = new VBox(header);
        box.setPadding(new Insets(4, 8, 6, 8));
        box.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(box, Priority.ALWAYS);
        GridPane.setFillWidth(box, true);
        String borderStyle = leftColumn
                ? String.format("-fx-border-color: %s; -fx-border-width: 0 1 1 0;", borderColor)
                : String.format("-fx-border-color: %s; -fx-border-width: 0 0 1 0;", borderColor);
        box.setStyle(borderStyle + " -fx-background-color: transparent;");
        return box;
    }

    private static void buildDiffRows(GridPane grid, List<DiffProcessor.DiffLine> lines,
                                      String themeTextColor, String borderColor, boolean hideUnchanged) {
        int rowIdx = 0;
        for (DiffProcessor.DiffLine line : lines) {
            if (hideUnchanged && line.getType() == DiffProcessor.DiffType.UNCHANGED) {
                continue;
            }
            VBox leftCell = buildLineCell(
                    line.getLeftLineNumber(), line.getOriginalText(), line.getType(), true, themeTextColor, borderColor, true);
            VBox rightCell = buildLineCell(
                    line.getRightLineNumber(), line.getNewText(), line.getType(), false, themeTextColor, borderColor, false);
            grid.add(leftCell, 0, rowIdx);
            grid.add(rightCell, 1, rowIdx);
            rowIdx++;
        }
        if (rowIdx == 0) {
            Label empty = new Label("Keine geänderten Zeilen (nur unveränderte Abschnitte).");
            empty.setStyle(String.format("-fx-text-fill: %s; -fx-font-style: italic;", themeTextColor));
            GridPane.setColumnSpan(empty, 2);
            grid.add(empty, 0, 0);
        }
    }

    private static VBox buildLineCell(int lineNumber, String lineText, DiffProcessor.DiffType type,
                                      boolean leftSide, String themeTextColor, String borderColor,
                                      boolean leftColumn) {
        Label num = new Label(lineNumber > 0 ? String.format("%4d", lineNumber) : "    ");
        num.setMinWidth(40);
        num.setMaxWidth(40);
        num.setAlignment(Pos.TOP_RIGHT);
        num.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #888;");

        Label content = new Label(lineText != null ? lineText : "");
        content.setWrapText(true);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setMinHeight(Region.USE_PREF_SIZE);
        content.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);

        String baseStyle = "-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;";
        switch (type) {
            case DELETED -> content.setStyle(leftSide
                    ? baseStyle + " -fx-background-color: #f8d7da; -fx-text-fill: #721c24;"
                    : baseStyle + " -fx-text-fill: " + themeTextColor + ";");
            case ADDED -> content.setStyle(leftSide
                    ? baseStyle + " -fx-text-fill: " + themeTextColor + ";"
                    : baseStyle + " -fx-background-color: #d4edda; -fx-text-fill: #155724;");
            case UNCHANGED -> content.setStyle(baseStyle + " -fx-text-fill: " + themeTextColor
                    + "; -fx-opacity: 0.55;");
            default -> content.setStyle(baseStyle + " -fx-text-fill: " + themeTextColor + ";");
        }

        HBox lineBox = new HBox(6, num, content);
        lineBox.setAlignment(Pos.TOP_LEFT);
        lineBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(content, Priority.ALWAYS);
        content.prefWidthProperty().bind(lineBox.widthProperty().subtract(46));

        VBox cell = new VBox(lineBox);
        cell.setPadding(new Insets(2, 8, 2, 8));
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMinHeight(Region.USE_PREF_SIZE);
        GridPane.setHgrow(cell, Priority.ALWAYS);
        GridPane.setFillWidth(cell, true);

        String borderStyle = leftColumn
                ? String.format("-fx-border-color: %s; -fx-border-width: 0 1 0 0;", borderColor)
                : "";
        cell.setStyle(borderStyle + " -fx-background-color: transparent;");
        return cell;
    }

    static String readFileContent(java.io.File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
