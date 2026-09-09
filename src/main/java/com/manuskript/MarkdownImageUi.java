package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Bild einfügen/bearbeiten/löschen für den Canvas-Editor.
 * Die Datei wird ins Arbeitsverzeichnis kopiert; im Markdown steht nur der Dateiname.
 */
final class MarkdownImageUi {

    private static final String PREF_LAST_IMAGE_PATH = CanvasEditorPrefs.key("last_image_path");
    private static final String PREF_LAST_IMAGE_ALT = CanvasEditorPrefs.key("last_image_alt");
    private static final String PREF_LAST_IMAGE_DIRECTORY = CanvasEditorPrefs.key("last_image_directory");
    private static final String PREF_LAST_IMAGE_WIDTH = CanvasEditorPrefs.key("last_image_width");

    private MarkdownImageUi() {
    }

    enum Outcome {
        CANCELLED, DONE, FAILED
    }

    record ActionResult(Outcome outcome, String message) {
        static ActionResult cancelled() {
            return new ActionResult(Outcome.CANCELLED, null);
        }

        static ActionResult done(String message) {
            return new ActionResult(Outcome.DONE, message);
        }

        static ActionResult failed(String message) {
            return new ActionResult(Outcome.FAILED, message);
        }
    }

    static ActionResult insert(Window owner, int themeIndex, Preferences preferences,
                               File projectDirectory, File mdDirectory, ManuskriptTextEditor editor) {
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
        alert.setTitle("Bild einfügen");
        alert.setHeaderText("Bild einfügen");
        alert.initOwner(owner);

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));

        Label pathLabel = new Label("Pfad:");
        TextField pathField = new TextField();
        pathField.setPromptText("Pfad zum Bild");
        HBox.setHgrow(pathField, Priority.ALWAYS);
        if (preferences != null) {
            pathField.setText(preferences.get(PREF_LAST_IMAGE_PATH, ""));
        }

        Label textLabel = new Label("Beschriftung:");
        TextField textField = new TextField();
        textField.setPromptText("Optionale Bildbeschriftung");
        HBox.setHgrow(textField, Priority.ALWAYS);
        if (preferences != null) {
            textField.setText(preferences.get(PREF_LAST_IMAGE_ALT, ""));
        }

        Label sizeLabel = new Label("Breite (%):");
        Spinner<Integer> widthSpinner = new Spinner<>();
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100, 80, 5));
        widthSpinner.setEditable(true);
        widthSpinner.setPrefWidth(80);
        int savedWidth = preferences != null ? preferences.getInt(PREF_LAST_IMAGE_WIDTH, 80) : 80;
        widthSpinner.getValueFactory().setValue(Math.max(10, Math.min(100, savedWidth)));

        Button btnBrowse = new Button("Durchsuchen...");
        btnBrowse.setOnAction(e -> {
            e.consume();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Bild auswählen");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Bilddateien", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                    new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));

            String lastDirectory = preferences != null ? preferences.get(PREF_LAST_IMAGE_DIRECTORY, "") : "";
            if (!lastDirectory.isBlank()) {
                File dir = new File(lastDirectory);
                if (dir.isDirectory()) {
                    fileChooser.setInitialDirectory(dir);
                }
            } else if (projectDirectory != null && projectDirectory.isDirectory()) {
                fileChooser.setInitialDirectory(projectDirectory);
            }

            File selectedFile = fileChooser.showOpenDialog(alert.getDialogWindow());
            if (selectedFile != null) {
                pathField.setText(selectedFile.getAbsolutePath());
                if (preferences != null && selectedFile.getParentFile() != null) {
                    preferences.put(PREF_LAST_IMAGE_DIRECTORY, selectedFile.getParentFile().getAbsolutePath());
                }
            }
        });

        HBox pathBox = new HBox(10, pathField, btnBrowse);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        HBox sizeBox = new HBox(8, sizeLabel, widthSpinner);
        sizeBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.getChildren().addAll(pathLabel, pathBox, textLabel, textField, sizeBox);
        alert.setCustomContent(contentBox);
        alert.applyTheme(themeIndex);
        ButtonType insertButton = new ButtonType("Einfügen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        alert.setButtonTypes(insertButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait(owner);
        if (result.isEmpty() || result.get() != insertButton) {
            return ActionResult.cancelled();
        }

        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            CustomAlert error = new CustomAlert(CustomAlert.AlertType.WARNING);
            error.setTitle("Kein Arbeitsverzeichnis");
            error.setHeaderText("Kein Arbeitsverzeichnis");
            error.setContentText("Bitte im Hauptfenster ein Projektverzeichnis wählen.");
            error.applyTheme(themeIndex);
            error.initOwner(owner);
            error.showAndWait(owner);
            return ActionResult.cancelled();
        }

        String imagePath = pathField.getText();
        String caption = textField.getText();
        if (imagePath == null || imagePath.isBlank()) {
            return ActionResult.failed("Kein Bildpfad angegeben");
        }

        try {
            File sourceImage = new File(imagePath.trim());
            if (!sourceImage.isFile()) {
                return ActionResult.failed("Bilddatei nicht gefunden");
            }

            File targetImage = MarkdownImageSupport.copyImageToProjectDirectory(sourceImage, projectDirectory);
            int widthPercent = widthSpinner.getValue() == null ? 80 : widthSpinner.getValue();
            String markdown = MarkdownImageSupport.buildMarkdown(targetImage.getName(), caption, widthPercent);
            editor.insertText("\n\n" + markdown + "\n\n");
            editor.setImageDirectories(mdDirectory != null ? mdDirectory : projectDirectory, projectDirectory);

            if (preferences != null) {
                preferences.put(PREF_LAST_IMAGE_PATH, sourceImage.getAbsolutePath());
                preferences.putInt(PREF_LAST_IMAGE_WIDTH, widthPercent);
                if (caption != null && !caption.isBlank()) {
                    preferences.put(PREF_LAST_IMAGE_ALT, caption);
                }
            }
            return ActionResult.done("Bild eingefügt: " + targetImage.getName());
        } catch (IOException ex) {
            return ActionResult.failed("Bild konnte nicht kopiert werden: " + ex.getMessage());
        }
    }

    static ActionResult edit(Window owner, int themeIndex, Preferences preferences, ManuskriptTextEditor editor) {
        ManuskriptTextEditor.ImageBlockInfo info = editor.getImageBlockAtCaret();
        if (info == null) {
            return ActionResult.failed("Kein Bild am Cursor – Bild anklicken oder Cursor im Bild platzieren");
        }

        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
        alert.setTitle("Bild bearbeiten");
        alert.setHeaderText("Bild bearbeiten");
        alert.initOwner(owner);

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));

        Label fileLabel = new Label("Datei:");
        Label fileValue = new Label(info.imagePath());
        fileValue.setWrapText(true);

        Label textLabel = new Label("Beschriftung:");
        TextField textField = new TextField();
        textField.setPromptText("Optionale Bildbeschriftung");
        if (info.caption() != null) {
            textField.setText(info.caption());
        }

        Label sizeLabel = new Label("Breite (%):");
        Spinner<Integer> widthSpinner = new Spinner<>();
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100, info.widthPercent(), 5));
        widthSpinner.setEditable(true);
        widthSpinner.setPrefWidth(80);

        contentBox.getChildren().addAll(fileLabel, fileValue, textLabel, textField, sizeLabel, widthSpinner);
        alert.setCustomContent(contentBox);
        alert.applyTheme(themeIndex);
        alert.setButtonTypes(new ButtonType("Übernehmen"), new ButtonType("Abbrechen"));

        Optional<ButtonType> result = alert.showAndWait(owner);
        if (result.isEmpty() || result.get().getButtonData().isCancelButton()) {
            return ActionResult.cancelled();
        }

        int widthPercent = widthSpinner.getValue() == null ? info.widthPercent() : widthSpinner.getValue();
        if (!editor.updateImageBlockAtCaret(textField.getText(), widthPercent)) {
            return ActionResult.failed("Bild konnte nicht aktualisiert werden");
        }
        if (preferences != null) {
            preferences.putInt(PREF_LAST_IMAGE_WIDTH, widthPercent);
            String caption = textField.getText();
            if (caption != null && !caption.isBlank()) {
                preferences.put(PREF_LAST_IMAGE_ALT, caption);
            }
        }
        return ActionResult.done("Bild aktualisiert");
    }

    static ActionResult delete(ManuskriptTextEditor editor) {
        if (editor.deleteImageBlockAtCaret()) {
            return ActionResult.done("Bild entfernt");
        }
        return ActionResult.failed("Kein Bild am Cursor – Bild anklicken oder Cursor im Bild platzieren");
    }
}
