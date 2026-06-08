package com.manuskript;

import com.manuskript.agent.SelectionRevisionSupport;
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
import java.util.prefs.Preferences;

/**
 * Kurzer Dialog vor dem Überarbeiten-Agenten: Markierung + optionale Anweisung.
 */
public final class SelectionRevisionDialog {

    private static final String PREF_INSTRUCTION = "instruction";

    private SelectionRevisionDialog() {
    }

    public static void show(
            ChapterEditorHost host,
            Stage ownerStage,
            int themeIndex,
            String selectedText,
            String defaultInstruction,
            Consumer<String> onRun) {
        if (host == null || onRun == null) {
            return;
        }
        CustomStage dialogStage = StageManager.createModalStage("Überarbeiten (Agent)", ownerStage);
        dialogStage.setTitle("Überarbeiten (Agent)");
        dialogStage.setWidth(720);
        dialogStage.setHeight(560);
        dialogStage.setTitleBarTheme(themeIndex);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("dialog-container");
        EditorDialogThemes.applyToNode(root, themeIndex);

        Label selectionLabel = new Label("Markierter Text:");
        selectionLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(selectionLabel, themeIndex);
        TextArea selectionArea = new TextArea(selectedText != null ? selectedText : "");
        selectionArea.setEditable(false);
        selectionArea.setWrapText(true);
        selectionArea.setPrefRowCount(6);
        selectionArea.getStyleClass().add("dialog-text-area");
        EditorDialogThemes.applyToNode(selectionArea, themeIndex);

        Label instructionLabel = new Label("Anweisung (optional):");
        instructionLabel.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(instructionLabel, themeIndex);
        TextArea instructionArea = new TextArea(defaultInstruction != null ? defaultInstruction : "");
        instructionArea.setPromptText("z.B. Name der Gruppe ergänzen, klarer formulieren");
        instructionArea.setWrapText(true);
        instructionArea.setPrefRowCount(4);
        instructionArea.setMaxWidth(Double.MAX_VALUE);
        instructionArea.getStyleClass().add("dialog-text-area");
        EditorDialogThemes.applyToNode(instructionArea, themeIndex);

        Label hint = new Label("Der Agent prüft nur die Markierung und schlägt ggf. zwei überarbeitete Versionen vor.");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-label");
        EditorDialogThemes.applyToNode(hint, themeIndex);

        Button btnRun = new Button("Prüfen");
        btnRun.getStyleClass().add("button");
        EditorDialogThemes.applyToNode(btnRun, themeIndex);
        Button btnCancel = new Button("Abbrechen");
        btnCancel.getStyleClass().add("button");
        EditorDialogThemes.applyToNode(btnCancel, themeIndex);
        HBox buttons = new HBox(10, btnRun, btnCancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(selectionLabel, selectionArea, instructionLabel, instructionArea, hint, buttons);
        VBox.setVgrow(selectionArea, Priority.SOMETIMES);
        VBox.setVgrow(instructionArea, Priority.NEVER);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.web(EditorDialogThemes.color(themeIndex, 0)));
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        dialogStage.setSceneWithTitleBar(scene);

        btnCancel.setOnAction(e -> dialogStage.close());
        btnRun.setOnAction(e -> {
            String instruction = instructionArea.getText() != null ? instructionArea.getText().trim() : "";
            persistInstruction(instruction);
            dialogStage.close();
            onRun.accept(instruction);
        });

        dialogStage.show();
        instructionArea.requestFocus();
    }

    public static String loadPersistedInstruction() {
        return Preferences.userNodeForPackage(SelectionRevisionSupport.class)
                .get(PREF_INSTRUCTION, "");
    }

    public static void syncInstruction(String instruction) {
        persistInstruction(instruction);
    }

    private static void persistInstruction(String instruction) {
        Preferences.userNodeForPackage(SelectionRevisionSupport.class)
                .put(PREF_INSTRUCTION, instruction != null ? instruction : "");
    }
}
