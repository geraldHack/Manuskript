package com.manuskript.agent;

import com.manuskript.CustomStage;
import com.manuskript.EditorDialogThemes;
import com.manuskript.MdTextArea;
import com.manuskript.MdTextAreaOptions;
import com.manuskript.PreferencesManager;
import com.manuskript.ResourceManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.prefs.Preferences;

/**
 * Eigenes Editorfenster für eine Chat-Antwort (Markdown).
 */
public final class ChatAnswerEditorWindow {

    private static final Preferences PREFS = Preferences.userNodeForPackage(ChatAnswerEditorWindow.class);
    private static final String WINDOW_PREFS_PREFIX = "chat_answer_editor_window";

    private ChatAnswerEditorWindow() {
    }

    public static void open(Window owner, String question, String answer, int themeIndex,
                            String fontFamily, int fontSizePx) {
        String text = answer != null ? answer : "";
        if (text.isBlank()) {
            return;
        }

        CustomStage stage = new CustomStage();
        String titleQuestion = question != null ? question.trim() : "";
        if (titleQuestion.length() > 60) {
            titleQuestion = titleQuestion.substring(0, 57) + "…";
        }
        stage.setCustomTitle(titleQuestion.isBlank() ? "Chat-Antwort" : "Chat-Antwort · " + titleQuestion);
        stage.setMinWidth(520);
        stage.setMinHeight(420);

        Label header = new Label(question != null && !question.isBlank()
                ? "Frage: " + question
                : "Chat-Antwort");
        header.setWrapText(true);
        header.getStyleClass().add("chat-answer-editor-header");

        MdTextArea editor = new MdTextArea(MdTextAreaOptions.builder()
                .editable(true)
                .showToolbar(true)
                .enableUndoRedo(true)
                .enableFontControls(true)
                .enableBasicFormatting(true)
                .enableSearch(true)
                .enableHideMarkupToggle(true)
                .hideMarkup(true)
                .fontFamily(fontFamily != null && !fontFamily.isBlank() ? fontFamily : "Segoe UI")
                .fontSize(fontSizePx > 0 ? fontSizePx : 14)
                .themeIndex(themeIndex)
                .build());
        editor.setText(text);
        VBox.setVgrow(editor, Priority.ALWAYS);

        Button closeButton = new Button("Schließen");
        closeButton.setOnAction(e -> stage.close());
        HBox footer = new HBox(closeButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 0, 0, 0));

        VBox center = new VBox(8, header, editor, footer);
        center.setPadding(new Insets(12));
        BorderPane root = new BorderPane(center);
        root.getStyleClass().add("chat-answer-editor-root");
        EditorDialogThemes.applyToNode(root, themeIndex);

        Scene scene = new Scene(root, 760, 640);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        stage.setTitleBarTheme(themeIndex);

        Rectangle2D bounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                PREFS, WINDOW_PREFS_PREFIX, 760, 640);
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, bounds);
        stage.xProperty().addListener((obs, o, n) ->
                PREFS.putDouble(WINDOW_PREFS_PREFIX + "_x", n.doubleValue()));
        stage.yProperty().addListener((obs, o, n) ->
                PREFS.putDouble(WINDOW_PREFS_PREFIX + "_y", n.doubleValue()));
        stage.widthProperty().addListener((obs, o, n) ->
                PREFS.putDouble(WINDOW_PREFS_PREFIX + "_width", n.doubleValue()));
        stage.heightProperty().addListener((obs, o, n) ->
                PREFS.putDouble(WINDOW_PREFS_PREFIX + "_height", n.doubleValue()));

        stage.show();
        stage.toFront();
    }
}
