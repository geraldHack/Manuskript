package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Start-Dialog für das Online-Lektorat (Kostenhinweis, Modell, Lektorat-Typ, optionale Kapitel-Einschätzung).
 */
public final class OnlineLektoratStartDialog {

    /** Ergebnis des Start-Dialogs. {@code lektoratType} kann mehrere Fokusse komma-getrennt enthalten. */
    public record StartOptions(boolean enableAssessment, String lektoratType, String extraPrompt) {
    }

    private OnlineLektoratStartDialog() {
    }

    public static Optional<StartOptions> show(Window owner, int themeIndex) {
        CustomStage dialogStage = StageManager.createModalStage("Online-Lektorat", owner);
        dialogStage.setWidth(520);
        dialogStage.setHeight(640);
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

        Label typeHeadingLabel = new Label("Lektorat-Fokus (mehrere möglich)");
        typeHeadingLabel.getStyleClass().add("param-key-label");

        CheckBox cbStil = new CheckBox("Stil");
        CheckBox cbGrammatik = new CheckBox("Grammatik");
        CheckBox cbPlot = new CheckBox("Plot / Dramaturgie");
        List<String> currentTypes = OnlineLektoratService.currentLektoratTypes();
        cbStil.setSelected(currentTypes.contains("stil"));
        cbGrammatik.setSelected(currentTypes.contains("grammatik"));
        cbPlot.setSelected(currentTypes.contains("plot"));

        Label typeHintLabel = new Label("Keine Auswahl = Allgemein (alle Register).");
        typeHintLabel.setWrapText(true);
        typeHintLabel.setMaxWidth(460);
        typeHintLabel.getStyleClass().add("param-help-label");

        HBox typeRow1 = new HBox(12, cbStil, cbGrammatik);
        HBox typeRow2 = new HBox(12, cbPlot);
        typeRow1.setAlignment(Pos.CENTER_LEFT);
        typeRow2.setAlignment(Pos.CENTER_LEFT);

        Label extraPromptLabel = new Label("Zusätzliche Anweisungen (optional)");
        extraPromptLabel.getStyleClass().add("param-key-label");
        TextArea extraPromptArea = new TextArea();
        extraPromptArea.setPromptText("z.B. Figurenstimmen erhalten, keine Glättung der Dialoge …");
        extraPromptArea.setWrapText(true);
        extraPromptArea.setPrefRowCount(4);
        extraPromptArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(extraPromptArea, Priority.ALWAYS);
        String defaultExtra = ResourceManager.getParameter("api.lektorat.extra_prompt", "");
        if (defaultExtra != null && !defaultExtra.isBlank()) {
            extraPromptArea.setText(defaultExtra);
        }
        extraPromptArea.setTooltip(new Tooltip(
                "Wird beim Start gespeichert und beim nächsten Lektorat wieder vorausgefüllt."));

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
                typeHeadingLabel, typeRow1, typeRow2, typeHintLabel,
                extraPromptLabel, extraPromptArea,
                assessmentCheckBox);
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
            List<String> selected = new ArrayList<>();
            if (cbStil.isSelected()) {
                selected.add("stil");
            }
            if (cbGrammatik.isSelected()) {
                selected.add("grammatik");
            }
            if (cbPlot.isSelected()) {
                selected.add("plot");
            }
            String type = OnlineLektoratService.serializeLektoratTypes(selected);
            String extra = extraPromptArea.getText() != null ? extraPromptArea.getText().trim() : "";
            ResourceManager.saveParameter("api.lektorat.type", type);
            ResourceManager.saveParameter("api.lektorat.extra_prompt", extra);
            result[0] = new StartOptions(assessmentCheckBox.isSelected(), type, extra);
            dialogStage.close();
        });
        cancelButton.setOnAction(evt -> dialogStage.close());

        dialogStage.showAndWait();
        return result[0] != null ? Optional.of(result[0]) : Optional.empty();
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
