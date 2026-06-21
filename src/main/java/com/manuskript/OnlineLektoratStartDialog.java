package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Start-Dialog für das Online-Lektorat (Kostenhinweis, Modell, Lektorat-Typ, optionale Kapitel-Einschätzung).
 */
public final class OnlineLektoratStartDialog {

    /** Ergebnis des Start-Dialogs. */
    public record StartOptions(boolean enableAssessment, String lektoratType) {
    }

    private OnlineLektoratStartDialog() {
    }

    public static Optional<StartOptions> show(Window owner, int themeIndex) {
        CustomStage dialogStage = StageManager.createModalStage("Online-Lektorat", owner);
        dialogStage.setWidth(520);
        dialogStage.setHeight(480);
        dialogStage.setTitleBarTheme(themeIndex);

        VBox dialogContent = new VBox(16);
        dialogContent.setPadding(new Insets(25));
        dialogContent.getStyleClass().add("dialog-container");
        applyTheme(dialogContent, themeIndex);

        Label titleLabel = new Label("Kostenpflichtiger Dienst");
        titleLabel.getStyleClass().add("dialog-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);

        Label infoLabel = new Label(
                "Das Online-Lektorat nutzt einen externen API-Dienst und kann je nach Nutzung Kosten verursachen.");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(460);

        Label modelLabel = new Label("Modell: " + OnlineLektoratService.currentModelDisplay());
        modelLabel.setWrapText(true);
        modelLabel.setMaxWidth(460);

        Label settingsHintLabel = new Label(OnlineLektoratService.SETTINGS_HINT);
        settingsHintLabel.setWrapText(true);
        settingsHintLabel.setMaxWidth(460);

        Label typeHeadingLabel = new Label("Lektorat-Typ");
        typeHeadingLabel.getStyleClass().add("param-key-label");

        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton rbAllgemein = typeRadio(typeGroup, "allgemein", "Allgemein");
        RadioButton rbStil = typeRadio(typeGroup, "stil", "Stil");
        RadioButton rbGrammatik = typeRadio(typeGroup, "grammatik", "Grammatik");
        RadioButton rbPlot = typeRadio(typeGroup, "plot", "Plot / Dramaturgie");

        String currentType = OnlineLektoratService.currentLektoratType();
        for (Toggle t : typeGroup.getToggles()) {
            if (currentType.equals(t.getUserData())) {
                typeGroup.selectToggle(t);
                break;
            }
        }
        if (typeGroup.getSelectedToggle() == null) {
            typeGroup.selectToggle(rbAllgemein);
        }

        HBox typeRow1 = new HBox(12, rbAllgemein, rbStil);
        HBox typeRow2 = new HBox(12, rbGrammatik, rbPlot);
        typeRow1.setAlignment(Pos.CENTER_LEFT);
        typeRow2.setAlignment(Pos.CENTER_LEFT);

        CheckBox assessmentCheckBox = new CheckBox("Zusätzliche Kapitel-Einschätzung erstellen");
        assessmentCheckBox.setTooltip(new Tooltip(
                "Parallel zum Lektorat eine Einschätzung des gesamten Kapitels anfordern (zusätzliche Kosten)"));
        assessmentCheckBox.setSelected(false);
        assessmentCheckBox.setWrapText(true);
        assessmentCheckBox.setMaxWidth(440);

        String apiKey = ResourceManager.getParameter("api.lektorat.api_key", "");
        boolean hasKey = apiKey != null && !apiKey.trim().isEmpty();
        Label noKeyLabel = null;
        if (!hasKey) {
            noKeyLabel = new Label("API-Key fehlt – bitte unter Parameter → Online-Lektorat eintragen.");
            noKeyLabel.setWrapText(true);
            noKeyLabel.setMaxWidth(460);
            noKeyLabel.getStyleClass().add("status-label");
        }

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));

        Button startButton = new Button("Ja, starten");
        startButton.setDisable(!hasKey);
        Button cancelButton = new Button("Abbrechen");
        buttonBox.getChildren().addAll(startButton, cancelButton);

        dialogContent.getChildren().addAll(
                titleLabel, infoLabel, modelLabel, settingsHintLabel,
                typeHeadingLabel, typeRow1, typeRow2, assessmentCheckBox);
        if (noKeyLabel != null) {
            dialogContent.getChildren().add(4, noKeyLabel);
        }
        dialogContent.getChildren().add(buttonBox);

        Scene scene = new Scene(dialogContent);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        dialogStage.setSceneWithTitleBar(scene);
        dialogStage.setFullTheme(themeIndex);

        final StartOptions[] result = new StartOptions[1];
        startButton.setOnAction(evt -> {
            Toggle selected = typeGroup.getSelectedToggle();
            String type = selected != null && selected.getUserData() instanceof String s
                    ? OnlineLektoratService.normalizeLektoratType(s)
                    : "allgemein";
            result[0] = new StartOptions(assessmentCheckBox.isSelected(), type);
            dialogStage.close();
        });
        cancelButton.setOnAction(evt -> dialogStage.close());

        dialogStage.showAndWait();
        return result[0] != null ? Optional.of(result[0]) : Optional.empty();
    }

    private static RadioButton typeRadio(ToggleGroup group, String id, String label) {
        RadioButton rb = new RadioButton(label);
        rb.setToggleGroup(group);
        rb.setUserData(id);
        return rb;
    }

    private static void applyTheme(VBox root, int themeIndex) {
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
