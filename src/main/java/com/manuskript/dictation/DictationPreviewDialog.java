package com.manuskript.dictation;

import com.manuskript.ChapterEditorHost;
import com.manuskript.CustomStage;
import com.manuskript.EditorDialogThemes;
import com.manuskript.StageManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * Vorschau-Dialog vor dem Einfügen eines Diktat-Ergebnisses.
 */
public final class DictationPreviewDialog {

    private DictationPreviewDialog() {
    }

    public static void show(ChapterEditorHost host, Stage ownerStage, int themeIndex,
                            DictationResult result, Consumer<String> onInsert) {
        if (host == null || result == null || onInsert == null) {
            return;
        }
        CustomStage dialogStage = StageManager.createModalStage("Diktat-Vorschau", ownerStage);
        dialogStage.setTitle("Diktat-Vorschau");
        dialogStage.setWidth(720);
        dialogStage.setHeight(520);
        dialogStage.setTitleBarTheme(themeIndex);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("dialog-container");
        EditorDialogThemes.applyToNode(root, themeIndex);

        Label rawLabel = dialogLabel(
                result.mode() == DictationMode.INSTRUCTION
                        ? "Rohtranskript (Anweisung erkannt):"
                        : "Rohtranskript (Spracherkennung):",
                themeIndex);
        TextArea rawArea = readOnlyArea(result.rawTranscript(), themeIndex, 3);

        Label processedLabel = dialogLabel(
                result.mode() == DictationMode.INSTRUCTION
                        ? "Generierter Text (wird eingefügt):"
                        : "Verarbeiteter Text (wird eingefügt):",
                themeIndex);
        TextArea processedArea = readOnlyArea(result.processedText(), themeIndex, 6);

        Button insertBtn = new Button("Einfügen");
        insertBtn.setDefaultButton(true);
        insertBtn.setOnAction(e -> {
            onInsert.accept(result.processedText());
            dialogStage.close();
        });

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> dialogStage.close());

        HBox buttons = new HBox(10, insertBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(rawLabel, rawArea, processedLabel, processedArea, buttons);
        VBox.setVgrow(processedArea, Priority.ALWAYS);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.web(EditorDialogThemes.color(themeIndex, 0)));
        dialogStage.setSceneWithTitleBar(scene);
        dialogStage.showAndWait();
    }

    private static Label dialogLabel(String text, int themeIndex) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(label, themeIndex);
        return label;
    }

    private static TextArea readOnlyArea(String content, int themeIndex, int rows) {
        TextArea area = new TextArea(content != null ? content : "");
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(rows);
        area.getStyleClass().add("dialog-text-area");
        EditorDialogThemes.applyToNode(area, themeIndex);
        return area;
    }
}
