package com.manuskript.dictation;

import com.manuskript.CustomStage;
import com.manuskript.EditorDialogThemes;
import com.manuskript.MdTextArea;
import com.manuskript.MdTextAreaOptions;
import com.manuskript.NovelManager;
import com.manuskript.ResourceManager;
import com.manuskript.StageManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Einfacher Canvas-Editor für {@code data/dictation-glossary.txt}.
 */
public final class DictationGlossaryWindow {

    private static final Logger logger = LoggerFactory.getLogger(DictationGlossaryWindow.class);
    private static final String PREF_FONT_FAMILY = "dictation_glossary_font_family";
    private static final String PREF_FONT_SIZE = "dictation_glossary_font_size";

    private DictationGlossaryWindow() {
    }

    public static void show(Window owner, int themeIndex, String docxFilePath,
                            String editorFontFamily, int editorFontSizePx) {
        if (docxFilePath == null || docxFilePath.isBlank()) {
            return;
        }

        NovelManager.ensureDictationGlossary(docxFilePath);
        Path glossaryPath = NovelManager.dictationGlossaryPath(docxFilePath);
        Preferences prefs = Preferences.userNodeForPackage(DictationGlossaryWindow.class);
        String fontFamily = prefs.get(PREF_FONT_FAMILY,
                editorFontFamily != null && !editorFontFamily.isBlank() ? editorFontFamily : "Segoe UI");
        double fontSize = prefs.getDouble(PREF_FONT_SIZE,
                editorFontSizePx > 0 ? editorFontSizePx : 16.0);

        CustomStage stage = StageManager.createStage("Diktat-Glossar", owner, false);
        stage.setCustomTitle("Diktat-Glossar");
        stage.setWidth(720);
        stage.setHeight(560);
        if (owner instanceof Stage ownerStage) {
            stage.initOwner(ownerStage);
        }

        Label pathLabel = new Label(glossaryPath != null ? glossaryPath.toString() : "");
        pathLabel.setWrapText(true);
        pathLabel.getStyleClass().add("status-label");

        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");

        MdTextArea editor = new MdTextArea(MdTextAreaOptions.builder()
                .editable(true)
                .showToolbar(true)
                .enableUndoRedo(true)
                .enableFontControls(true)
                .enableSearch(true)
                .hideMarkup(false)
                .themeIndex(themeIndex)
                .fontFamily(fontFamily)
                .fontSize(fontSize)
                .onFontFamilyChanged(value -> prefs.put(PREF_FONT_FAMILY, value))
                .onFontSizeChanged(value -> prefs.putDouble(PREF_FONT_SIZE, value))
                .build());
        editor.getEditor().setMarkdownHeadingsEnabled(false);
        editor.setText(NovelManager.loadDictationGlossary(docxFilePath));
        VBox.setVgrow(editor, Priority.ALWAYS);

        Button addFromWorld = new Button("Begriffe aus World-Editor hinzufügen");
        addFromWorld.setTooltip(new Tooltip(
                "Übernimmt ##-Überschriften aus characters.txt und worldbuilding.txt "
                        + "(ohne Duplikate). Bestehende Einträge bleiben erhalten."));
        addFromWorld.setOnAction(e -> {
            List<String> worldTerms = DictationVocabulary.collectWorldEditorTerms(
                    NovelManager.loadCharacters(docxFilePath),
                    NovelManager.loadWorldbuilding(docxFilePath));
            if (worldTerms.isEmpty()) {
                statusLabel.setText("Keine World-Editor-Begriffe gefunden "
                        + "(##-Überschriften in characters.txt / worldbuilding.txt).");
                return;
            }
            StringBuilder merged = new StringBuilder(editor.getText() != null ? editor.getText() : "");
            int added = DictationVocabulary.mergeTermsIntoGlossaryText(merged, worldTerms);
            editor.setText(merged.toString());
            if (added == 0) {
                statusLabel.setText("Keine neuen Begriffe – alles war schon im Glossar ("
                        + worldTerms.size() + " geprüft).");
            } else {
                statusLabel.setText(added + " Begriff(e) hinzugefügt (aus "
                        + worldTerms.size() + " World-Editor-Einträgen).");
            }
        });

        Button save = new Button("Speichern");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            NovelManager.saveDictationGlossary(docxFilePath, editor.getText());
            statusLabel.setText("Gespeichert.");
            logger.debug("Diktat-Glossar gespeichert: {}", glossaryPath);
        });

        Button close = new Button("Schließen");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, addFromWorld, save, close);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, pathLabel, editor, buttons, statusLabel);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("dialog-container");
        EditorDialogThemes.applyToNode(root, themeIndex);

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        scene.setFill(javafx.scene.paint.Color.web(EditorDialogThemes.color(themeIndex, 0)));
        stage.setTitleBarTheme(themeIndex);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        stage.show();
    }
}
