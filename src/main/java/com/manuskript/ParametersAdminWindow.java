package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltung aller Parameter (parameters.properties / User Preferences).
 * Pro Eintrag: Schlüssel, Wert, Hilfetext. Optisch gruppiert nach Kategorien.
 */
public class ParametersAdminWindow {

    private CustomStage stage;
    private final Window owner;
    private final Map<String, Control> keyToControl = new HashMap<>();
    private final Map<String, ParameterDef> keyToDef = new HashMap<>();

    public ParametersAdminWindow(Window owner) {
        this.owner = owner;
    }

    public static void show(Window owner) {
        ParametersAdminWindow w = new ParametersAdminWindow(owner);
        w.initializeWindow();
        w.stage.show();
    }

    private void initializeWindow() {
        stage = StageManager.createStage("Parameter-Verwaltung");
        if (owner != null && owner instanceof javafx.stage.Stage) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.setWidth(820);
        stage.setHeight(620);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (String category : ParameterRegistry.getCategories()) {
            List<ParameterDef> params = ParameterRegistry.getByCategory(category);
            if (params.isEmpty()) continue;
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            VBox content = new VBox(12);
            content.setPadding(new Insets(16));
            for (ParameterDef def : params) {
                keyToDef.put(def.getKey(), def);
                Control control = createControl(def);
                keyToControl.put(def.getKey(), control);
                Label keyLabel = new Label(def.getKey());
                keyLabel.getStyleClass().add("param-key-label");
                Label helpLabel = new Label(def.getHelpText());
                helpLabel.getStyleClass().add("param-help-label");
                helpLabel.setWrapText(true);
                helpLabel.setMaxWidth(680);
                VBox card = new VBox(4);
                card.getStyleClass().add("param-card");
                card.setPadding(new Insets(10));
                card.getChildren().addAll(keyLabel, control, helpLabel);
                content.getChildren().add(card);
            }
            scroll.setContent(content);
            Tab tab = new Tab(category, scroll);
            tab.setClosable(false);
            tabPane.getTabs().add(tab);
        }

        Button btnSave = new Button("Speichern");
        btnSave.setDefaultButton(true);
        btnSave.setOnAction(e -> saveAll());
        Button btnRestore = new Button("Standard wiederherstellen");
        btnRestore.setOnAction(e -> restoreDefaults());
        HBox buttons = new HBox(12);
        buttons.getChildren().addAll(btnSave, btnRestore);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(10, 16, 16, 16));

        VBox root = new VBox();
        root.getChildren().addAll(tabPane, buttons);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
        stage.setTitleBarTheme(theme);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(theme);
    }

    private Control createControl(ParameterDef def) {
        boolean isTextanalyse = "Textanalyse".equals(def.getCategory());
        String current = isTextanalyse
                ? ResourceManager.getTextanalysisParameter(def.getKey(), def.getDefaultValue())
                : ResourceManager.getParameter(def.getKey(), def.getDefaultValue());
        switch (def.getType()) {
            case BOOLEAN:
                CheckBox cb = new CheckBox();
                cb.setSelected(Boolean.parseBoolean(current));
                cb.setMaxWidth(400);
                return cb;
            case INT:
                Spinner<Integer> si = new Spinner<>(Integer.MIN_VALUE, Integer.MAX_VALUE, parseInt(current, 0));
                si.setEditable(true);
                si.setPrefWidth(180);
                return si;
            case DOUBLE:
                Spinner<Double> sd = new Spinner<>(-1e6, 1e6, parseDouble(current, 0.0));
                sd.setEditable(true);
                sd.setPrefWidth(180);
                return sd;
            default:
                if (isTextanalyse) {
                    TextArea ta = new TextArea(current != null ? current : "");
                    ta.setPrefRowCount(4);
                    ta.setWrapText(true);
                    ta.setPrefWidth(680);
                    ta.setMaxWidth(Double.MAX_VALUE);
                    return ta;
                }
                TextField tf = new TextField(current != null ? current : "");
                tf.setPrefWidth(400);
                return tf;
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return fallback; }
    }

    private void saveAll() {
        for (Map.Entry<String, Control> e : keyToControl.entrySet()) {
            String key = e.getKey();
            ParameterDef def = keyToDef.get(key);
            if (def == null) continue;
            String value = getValueFromControl(e.getValue(), def);
            if ("Textanalyse".equals(def.getCategory())) {
                ResourceManager.saveTextanalysisParameter(key, value);
            } else {
                ResourceManager.saveParameter(key, value);
            }
        }
        showInfo("Gespeichert", "Alle Parameter wurden gespeichert.");
    }

    private String getValueFromControl(Control c, ParameterDef def) {
        if (c instanceof CheckBox) return String.valueOf(((CheckBox) c).isSelected());
        if (c instanceof Spinner) {
            Object v = ((Spinner<?>) c).getValue();
            return v != null ? v.toString() : def.getDefaultValue();
        }
        if (c instanceof TextArea) return ((TextArea) c).getText();
        if (c instanceof TextField) return ((TextField) c).getText();
        return def.getDefaultValue();
    }

    private void restoreDefaults() {
        for (Map.Entry<String, Control> e : keyToControl.entrySet()) {
            ParameterDef def = keyToDef.get(e.getKey());
            if (def == null) continue;
            setControlToDefault(e.getValue(), def);
        }
        showInfo("Standard", "Alle Werte auf Standard zurückgesetzt. Bitte „Speichern“ klicken, um zu übernehmen.");
    }

    @SuppressWarnings("unchecked")
    private void setControlToDefault(Control c, ParameterDef def) {
        String d = def.getDefaultValue();
        if (c instanceof CheckBox) ((CheckBox) c).setSelected(Boolean.parseBoolean(d));
        else if (c instanceof Spinner) {
            Spinner<?> s = (Spinner<?>) c;
            if (s.getValue() instanceof Integer)
                ((Spinner<Integer>) s).getValueFactory().setValue(parseInt(d, 0));
            else
                ((Spinner<Double>) s).getValueFactory().setValue(parseDouble(d, 0.0));
        } else if (c instanceof TextArea) ((TextArea) c).setText(d != null ? d : "");
        else if (c instanceof TextField) ((TextField) c).setText(d != null ? d : "");
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            CustomAlert a = new CustomAlert(Alert.AlertType.INFORMATION, title);
            a.setHeaderText(null);
            a.setContentText(message);
            a.applyTheme(theme);
            a.initOwner(stage);
            a.showAndWait();
        });
    }
}
