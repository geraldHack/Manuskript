package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Dialog: Felder und Kontext für KI-Ausfüllen einer Character Card.
 */
public final class CharacterCardAiDialog {

    private CharacterCardAiDialog() {
    }

    public static Optional<CharacterCardAiOptions> show(
            Window owner,
            int themeIndex,
            String characterName,
            String currentChapterHint) {
        CustomStage dialogStage = StageManager.createStage("KI für Figur");
        if (owner != null) {
            dialogStage.initOwner(owner);
        }
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setMinWidth(480);
        dialogStage.setMinHeight(420);

        String heading = characterName == null || characterName.isBlank()
                ? "Neue Figur"
                : characterName.trim();
        Label intro = new Label("KI-Ausfüllen nur für: " + heading);
        intro.setWrapText(true);
        intro.getStyleClass().add("character-card-ai-intro");

        Map<String, CheckBox> fieldChecks = new LinkedHashMap<>();
        FlowPane fieldPane = new FlowPane(10, 8);
        fieldPane.getStyleClass().add("character-card-ai-fields");
        for (String label : CharacterSheetDocument.STANDARD_FIELD_LABELS) {
            CheckBox box = new CheckBox(label);
            box.setSelected(true);
            fieldChecks.put(label, box);
            fieldPane.getChildren().add(box);
        }

        Button allFields = new Button("Alle Felder");
        Button noFields = new Button("Keine");
        allFields.setOnAction(e -> fieldChecks.values().forEach(cb -> cb.setSelected(true)));
        noFields.setOnAction(e -> fieldChecks.values().forEach(cb -> cb.setSelected(false)));

        CheckBox worldContext = new CheckBox("Welt-Editor-Kontext (Brainstorm, Worldbuilding, Outline, …)");
        worldContext.setSelected(true);
        worldContext.setWrapText(true);

        CheckBox chapterContext = new CheckBox("Aktuelles Kapitel");
        chapterContext.setSelected(currentChapterHint != null && !currentChapterHint.isBlank());
        if (currentChapterHint != null && !currentChapterHint.isBlank()) {
            chapterContext.setText("Aktuelles Kapitel: " + currentChapterHint);
        } else {
            chapterContext.setText("Aktuelles Kapitel (keins ausgewählt/geöffnet)");
            chapterContext.setDisable(true);
        }

        CheckBox onlyEmpty = new CheckBox("Nur leere Felder überschreiben");
        onlyEmpty.setSelected(true);

        Label notesLabel = new Label("Zusätzliche Anweisung (optional):");
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("z. B. Fokus auf inneren Konflikt nach Kapitel 12 …");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);
        VBox.setVgrow(notesArea, Priority.ALWAYS);

        final CharacterCardAiOptions[] result = new CharacterCardAiOptions[1];

        Button okButton = new Button("KI starten");
        okButton.setDefaultButton(true);
        Button cancelButton = new Button("Abbrechen");
        cancelButton.setCancelButton(true);

        okButton.setOnAction(e -> {
            Set<String> selected = new LinkedHashSet<>();
            for (Map.Entry<String, CheckBox> entry : fieldChecks.entrySet()) {
                if (entry.getValue().isSelected()) {
                    selected.add(entry.getKey());
                }
            }
            if (selected.isEmpty()) {
                CustomAlert alert = new CustomAlert(CustomAlert.AlertType.WARNING);
                alert.setHeaderText("Keine Felder gewählt");
                alert.setContentText("Bitte mindestens ein Feld ankreuzen.");
                alert.applyTheme(themeIndex);
                alert.initOwner(dialogStage);
                alert.showAndWait();
                return;
            }
            result[0] = new CharacterCardAiOptions(
                    selected,
                    worldContext.isSelected(),
                    chapterContext.isSelected() && !chapterContext.isDisabled(),
                    onlyEmpty.isSelected(),
                    notesArea.getText());
            dialogStage.close();
        });
        cancelButton.setOnAction(e -> dialogStage.close());

        HBox fieldButtons = new HBox(8, allFields, noFields);
        fieldButtons.setAlignment(Pos.CENTER_LEFT);

        HBox buttonBox = new HBox(10, okButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));

        VBox root = new VBox(10,
                intro,
                new Label("Felder erzeugen:"),
                fieldButtons,
                fieldPane,
                worldContext,
                chapterContext,
                onlyEmpty,
                notesLabel,
                notesArea,
                buttonBox);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("character-card-ai-dialog");

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

    private static void applyTheme(javafx.scene.Parent root, int themeIndex) {
        root.getStyleClass().removeAll(
                "weiss-theme", "theme-dark", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
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
