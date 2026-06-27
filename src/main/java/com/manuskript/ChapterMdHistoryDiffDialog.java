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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Diff zwischen einer MD-Historien-Version und dem aktuellen Editor-Inhalt.
 */
public final class ChapterMdHistoryDiffDialog {

    private static final double COLUMN_GAP = 12;

    private ChapterMdHistoryDiffDialog() {
    }

    public static void show(Window owner, int themeIndex, String chapterLabel, String versionLabel,
                            String historyContent, String currentText, Consumer<String> onRestore) {
        String baseline = ChapterMarkdownFormat.normalizeParagraphSpacing(historyContent);
        String current = ChapterMarkdownFormat.normalizeParagraphSpacing(currentText);

        DiffProcessor.DiffResult diff = DiffProcessor.createDiff(baseline, current);

        CustomStage stage = StageManager.createDiffStage(
                "MD-Version: " + chapterLabel, owner);
        stage.setTitleBarTheme(themeIndex);

        String bg = EditorDialogThemes.color(themeIndex, 0);
        String text = EditorDialogThemes.color(themeIndex, 1);
        String border = EditorDialogThemes.color(themeIndex, 3);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle(String.format("-fx-background-color: %s;", bg));
        EditorDialogThemes.applyToNode(root, themeIndex);

        Label titleLabel = new Label("MD-Version " + versionLabel);
        titleLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: %s;", text));

        Label hintLabel = new Label(
                "Vergleich der gewählten MD-Historien-Version (links) mit dem aktuellen Editor-Text (rechts).");
        hintLabel.setWrapText(true);
        hintLabel.setStyle(String.format("-fx-font-size: 12px; -fx-text-fill: %s; -fx-opacity: 0.85;", text));

        CheckBox hideUnchanged = new CheckBox("Unveränderte Zeilen ausblenden");
        hideUnchanged.setSelected(false);
        hideUnchanged.setStyle(String.format("-fx-text-fill: %s;", text));

        if (!diff.hasChanges()) {
            Label sameLabel = new Label("Nach Normalisierung entspricht der Editor-Text dieser Version.");
            sameLabel.setWrapText(true);
            sameLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-style: italic;", text));
            root.getChildren().addAll(titleLabel, hintLabel, sameLabel);
        }

        GridPane headerGrid = createTwoColumnGrid(border);
        Label leftHeader = columnHeader("Historien-Version", text);
        Label rightHeader = columnHeader("Aktueller Editor", text);
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

        if (diff.hasChanges()) {
            root.getChildren().addAll(titleLabel, hintLabel, hideUnchanged, headerGrid, scroll);
        } else {
            root.getChildren().addAll(headerGrid, scroll);
        }
        addButtons(root, stage, onRestore, baseline, text);
        showStage(stage, root, themeIndex, bg);
    }

    private static void addButtons(VBox root, CustomStage stage, Consumer<String> onRestore,
                                   String baseline, String themeTextColor) {
        Button closeButton = new Button("Schließen");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        if (onRestore != null) {
            Button restoreButton = new Button("Diese Version wiederherstellen");
            restoreButton.setOnAction(e -> {
                onRestore.accept(baseline);
                stage.close();
            });
            buttons.getChildren().add(0, restoreButton);
        }

        Label restoreHint = new Label(
                "Wiederherstellen lädt die Version in den Editor (ungespeichert, bis Sie Speichern drücken).");
        restoreHint.setWrapText(true);
        restoreHint.setStyle(String.format("-fx-font-size: 11px; -fx-text-fill: %s; -fx-opacity: 0.8;", themeTextColor));

        root.getChildren().addAll(restoreHint, buttons);
    }

    private static void showStage(CustomStage stage, VBox root, int themeIndex, String bg) {
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

    private static Label columnHeader(String labelText, String themeTextColor) {
        Label label = new Label(labelText);
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
        int i = 0;
        while (i < lines.size()) {
            DiffProcessor.DiffLine line = lines.get(i);
            if (hideUnchanged && line.getType() == DiffProcessor.DiffType.UNCHANGED) {
                i++;
                continue;
            }

            if (line.getType() == DiffProcessor.DiffType.DELETED) {
                List<DiffProcessor.DiffLine> deleted = new ArrayList<>();
                while (i < lines.size() && lines.get(i).getType() == DiffProcessor.DiffType.DELETED) {
                    deleted.add(lines.get(i++));
                }
                List<DiffProcessor.DiffLine> added = new ArrayList<>();
                while (i < lines.size() && lines.get(i).getType() == DiffProcessor.DiffType.ADDED) {
                    added.add(lines.get(i++));
                }
                rowIdx = addPairedChangeRows(grid, rowIdx, deleted, added, themeTextColor, borderColor);
                continue;
            }

            if (line.getType() == DiffProcessor.DiffType.ADDED) {
                while (i < lines.size() && lines.get(i).getType() == DiffProcessor.DiffType.ADDED) {
                    DiffProcessor.DiffLine addedLine = lines.get(i++);
                    addDiffRow(grid, rowIdx++, 0, "", null,
                            addedLine.getRightLineNumber(), addedLine.getNewText(),
                            DiffProcessor.DiffType.ADDED, themeTextColor, borderColor);
                }
                continue;
            }

            addDiffRow(grid, rowIdx++, line.getLeftLineNumber(), line.getOriginalText(),
                    DiffProcessor.DiffType.UNCHANGED, line.getRightLineNumber(), line.getNewText(),
                    DiffProcessor.DiffType.UNCHANGED, themeTextColor, borderColor);
            i++;
        }
        if (rowIdx == 0) {
            Label empty = new Label("Keine Zeilen zum Anzeigen.");
            empty.setStyle(String.format("-fx-text-fill: %s; -fx-font-style: italic;", themeTextColor));
            GridPane.setColumnSpan(empty, 2);
            grid.add(empty, 0, 0);
        }
    }

    /** Koppelt aufeinanderfolgende DELETE-/INSERT-Läufe zeilenweise in einer Grid-Zeile. */
    private static int addPairedChangeRows(GridPane grid, int rowIdx,
                                           List<DiffProcessor.DiffLine> deleted,
                                           List<DiffProcessor.DiffLine> added,
                                           String themeTextColor, String borderColor) {
        int maxSize = Math.max(deleted.size(), added.size());
        for (int j = 0; j < maxSize; j++) {
            DiffProcessor.DiffLine d = j < deleted.size() ? deleted.get(j) : null;
            DiffProcessor.DiffLine a = j < added.size() ? added.get(j) : null;
            addDiffRow(grid, rowIdx++,
                    d != null ? d.getLeftLineNumber() : 0,
                    d != null ? d.getOriginalText() : "",
                    d != null ? DiffProcessor.DiffType.DELETED : null,
                    a != null ? a.getRightLineNumber() : 0,
                    a != null ? a.getNewText() : "",
                    a != null ? DiffProcessor.DiffType.ADDED : null,
                    themeTextColor, borderColor);
        }
        return rowIdx;
    }

    private static void addDiffRow(GridPane grid, int rowIdx,
                                   int leftLineNumber, String leftText, DiffProcessor.DiffType leftHighlight,
                                   int rightLineNumber, String rightText, DiffProcessor.DiffType rightHighlight,
                                   String themeTextColor, String borderColor) {
        VBox leftCell = buildSideCell(leftLineNumber, leftText, leftHighlight, true, themeTextColor, borderColor);
        VBox rightCell = buildSideCell(rightLineNumber, rightText, rightHighlight, false, themeTextColor, borderColor);
        grid.add(leftCell, 0, rowIdx);
        grid.add(rightCell, 1, rowIdx);
    }

    private static VBox buildSideCell(int lineNumber, String lineText, DiffProcessor.DiffType highlightType,
                                      boolean leftColumn, String themeTextColor, String borderColor) {
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
        if (highlightType == null) {
            content.setStyle(baseStyle + " -fx-text-fill: " + themeTextColor + ";");
        } else {
            switch (highlightType) {
                case DELETED -> content.setStyle(baseStyle + " -fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
                case ADDED -> content.setStyle(baseStyle + " -fx-background-color: #d4edda; -fx-text-fill: #155724;");
                case UNCHANGED -> content.setStyle(baseStyle + " -fx-text-fill: " + themeTextColor
                        + "; -fx-opacity: 0.55;");
                default -> content.setStyle(baseStyle + " -fx-text-fill: " + themeTextColor + ";");
            }
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
}
