package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Makro-Verwaltung für den Canvas-Kapitel-Editor (wie Legacy-Fenster).
 */
public class ChapterMacroWindow {

    public interface Host {
        ChapterEditorHost getChapterEditor();

        void updateStatus(String message);

        void updateStatusError(String message);

        javafx.stage.Stage getOwnerStage();

        Window getOwnerWindow();

        int getThemeIndex();

        void applyThemeToNode(Node node, int themeIndex);
    }

    private final Host host;
    private final ObservableList<Macro> macros = FXCollections.observableArrayList();
    private final Preferences preferences = Preferences.userNodeForPackage(EditorWindow.class);

    private CustomStage stage;
    private ComboBox<String> cmbMacroList;
    private VBox macroDetailsPanel;
    private TableView<MacroStep> tblMacroSteps;
    private TextField txtMacroSearch;
    private TextField txtMacroReplace;
    private TextField txtMacroStepDescription;
    private CheckBox chkMacroRegex;
    private CheckBox chkMacroCaseSensitive;
    private CheckBox chkMacroWholeWord;

    private Macro currentMacro;

    public ChapterMacroWindow(Host host) {
        this.host = host;
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    public void toggle() {
        if (isShowing()) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        if (stage == null) {
            createStage();
        }
        applyThemeToPanel();
        stage.setTitleBarTheme(host.getThemeIndex());
        stage.show();
        stage.toFront();
        host.updateStatus("Makro-Fenster geöffnet");
    }

    public void hide() {
        if (stage != null) {
            stage.hide();
        }
        host.updateStatus("Makro-Fenster geschlossen");
    }

    public void applyTheme(int themeIndex) {
        if (stage != null && stage.isShowing()) {
            stage.setTitleBarTheme(themeIndex);
            applyThemeToPanel();
        }
    }

    private void applyThemeToPanel() {
        if (stage != null && stage.getScene() != null) {
            host.applyThemeToNode(stage.getScene().getRoot(), host.getThemeIndex());
        }
    }

    private void createStage() {
        stage = StageManager.createStage("Makros");
        stage.setTitle("Makro-Verwaltung");
        stage.setTitleBarTheme(host.getThemeIndex());
        stage.initModality(Modality.NONE);
        Window owner = host.getOwnerWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox root = buildPanel();
        Scene scene = new Scene(root);
        String css = ResourceManager.getCssResource("css/manuskript.css");
        if (css != null) {
            scene.getStylesheets().add(css);
        }
        stage.setSceneWithTitleBar(scene);
        applyThemeToPanel();
        loadWindowProperties();

        stage.setOnCloseRequest(event -> {
            event.consume();
            preferences.putDouble("macro_window_x", stage.getX());
            preferences.putDouble("macro_window_y", stage.getY());
            preferences.putDouble("macro_window_width", stage.getWidth());
            preferences.putDouble("macro_window_height", stage.getHeight());
            stage.hide();
        });

        MacroStorage.loadInto(macros);
        refreshMacroList();
    }

    private void loadWindowProperties() {
        var bounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                preferences, "macro_window", 1200.0, 800.0);
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, bounds);
        stage.xProperty().addListener((o, a, b) -> preferences.putDouble("macro_window_x", b.doubleValue()));
        stage.yProperty().addListener((o, a, b) -> preferences.putDouble("macro_window_y", b.doubleValue()));
        stage.widthProperty().addListener((o, a, b) -> preferences.putDouble("macro_window_width", b.doubleValue()));
        stage.heightProperty().addListener((o, a, b) -> preferences.putDouble("macro_window_height", b.doubleValue()));
    }

    private VBox buildPanel() {
        VBox macroPanel = new VBox(10);
        macroPanel.setPadding(new Insets(10));
        macroPanel.getStyleClass().add("macro-panel");
        applyThemeClasses(macroPanel);

        HBox macroControls = new HBox(10);
        macroControls.setAlignment(Pos.CENTER_LEFT);

        Label macroLabel = new Label("Makros:");
        cmbMacroList = new ComboBox<>();
        cmbMacroList.setPromptText("Makro auswählen…");
        cmbMacroList.setPrefWidth(200);

        Button btnNew = new Button("Neues Makro");
        btnNew.getStyleClass().addAll("button", "primary");
        Button btnDelete = new Button("Makro löschen");
        btnDelete.getStyleClass().addAll("button", "danger");
        Button btnCsv = new Button("Als CSV exportieren");
        btnCsv.getStyleClass().addAll("button", "primary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnRun = new Button("Makro ausführen");
        btnRun.getStyleClass().addAll("button", "success");

        macroControls.getChildren().addAll(macroLabel, cmbMacroList, btnNew, btnDelete, btnCsv, spacer, btnRun);

        macroDetailsPanel = new VBox(5);
        macroDetailsPanel.setVisible(false);
        VBox.setVgrow(macroDetailsPanel, Priority.ALWAYS);

        Label stepsLabel = new Label("Makro-Schritte:");
        txtMacroStepDescription = field(400, "Beschreibung des Schritts…");
        HBox descriptionBox = new HBox(10, new Label("Schritt-Beschreibung:"), txtMacroStepDescription);
        descriptionBox.setAlignment(Pos.CENTER_LEFT);

        txtMacroSearch = field(200, "Suchtext");
        txtMacroReplace = field(200, "Ersetzungstext");
        chkMacroRegex = new CheckBox("Regex");
        chkMacroCaseSensitive = new CheckBox("Case");
        chkMacroWholeWord = new CheckBox("Word");
        HBox searchReplaceBox = new HBox(10,
                new Label("Suchen:"), txtMacroSearch,
                new Label("Ersetzen:"), txtMacroReplace,
                chkMacroRegex, chkMacroCaseSensitive, chkMacroWholeWord);
        searchReplaceBox.setAlignment(Pos.CENTER_LEFT);

        tblMacroSteps = new TableView<>();
        tblMacroSteps.getStyleClass().add("macro-table");
        VBox.setVgrow(tblMacroSteps, Priority.ALWAYS);
        buildMacroTableColumns();

        HBox stepButtons = new HBox(5,
                button("Schritt hinzufügen", this::addMacroStep),
                button("Schritt entfernen", this::removeMacroStep),
                button("↑", this::moveMacroStepUp),
                button("↓", this::moveMacroStepDown));
        stepButtons.setAlignment(Pos.CENTER_LEFT);

        macroDetailsPanel.getChildren().addAll(stepsLabel, descriptionBox, searchReplaceBox, tblMacroSteps, stepButtons);

        btnNew.setOnAction(e -> createNewMacro());
        btnDelete.setOnAction(e -> deleteCurrentMacro());
        btnCsv.setOnAction(e -> exportMacroCsv());
        btnRun.setOnAction(e -> runCurrentMacro());
        cmbMacroList.setOnAction(e -> selectMacro());

        macroPanel.getChildren().addAll(macroControls, macroDetailsPanel);
        return macroPanel;
    }

    private static TextField field(double width, String prompt) {
        TextField f = new TextField();
        f.setPrefWidth(width);
        f.setPromptText(prompt);
        return f;
    }

    private static Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("button");
        b.setOnAction(e -> action.run());
        return b;
    }

    private void applyThemeClasses(Node panel) {
        panel.getStyleClass().removeAll("weiss-theme", "theme-dark", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        int t = host.getThemeIndex();
        switch (t) {
            case 0 -> panel.getStyleClass().add("weiss-theme");
            case 2 -> panel.getStyleClass().add("pastell-theme");
            case 3 -> panel.getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> panel.getStyleClass().addAll("theme-dark", "gruen-theme");
            case 5 -> panel.getStyleClass().addAll("theme-dark", "lila-theme");
            default -> panel.getStyleClass().add("theme-dark");
        }
    }

    private void buildMacroTableColumns() {
        TableColumn<MacroStep, Integer> colNum = new TableColumn<>("Schritt");
        colNum.setPrefWidth(50);
        colNum.setCellValueFactory(new PropertyValueFactory<>("stepNumber"));

        TableColumn<MacroStep, Boolean> colEnabled = new TableColumn<>("Aktiv");
        colEnabled.setPrefWidth(50);
        colEnabled.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        colEnabled.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    MacroStep step = getTableRow().getItem();
                    if (step != null) {
                        step.setEnabled(checkBox.isSelected());
                        persist();
                    }
                });
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(Boolean.TRUE.equals(item));
                    setGraphic(checkBox);
                }
            }
        });

        TableColumn<MacroStep, String> colDesc = new TableColumn<>("Beschreibung");
        colDesc.setPrefWidth(200);
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));

        TableColumn<MacroStep, String> colSearch = new TableColumn<>("Suchen");
        colSearch.setPrefWidth(150);
        colSearch.setCellValueFactory(new PropertyValueFactory<>("searchText"));

        TableColumn<MacroStep, String> colReplace = new TableColumn<>("Ersetzen");
        colReplace.setPrefWidth(150);
        colReplace.setCellValueFactory(new PropertyValueFactory<>("replaceText"));

        TableColumn<MacroStep, String> colOptions = new TableColumn<>("Optionen");
        colOptions.setPrefWidth(100);
        colOptions.setCellValueFactory(new PropertyValueFactory<>("optionsString"));

        TableColumn<MacroStep, String> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(120);
        colStatus.setCellValueFactory(param -> {
            MacroStep step = param.getValue();
            if (step == null) {
                return new SimpleStringProperty("");
            }
            SimpleStringProperty prop = new SimpleStringProperty();
            Runnable update = () -> prop.set(!step.isEnabled() ? "Deaktiviert"
                    : step.getReplacementCount() == 0 ? "Keine Ersetzungen"
                    : step.getReplacementCount() + " Ersetzungen");
            update.run();
            step.replacementCountProperty().addListener((o, a, b) -> update.run());
            step.enabledProperty().addListener((o, a, b) -> update.run());
            return prop;
        });

        TableColumn<MacroStep, String> colActions = new TableColumn<>("Aktionen");
        colActions.setPrefWidth(100);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Bearbeiten");
            {
                editButton.setOnAction(e -> {
                    MacroStep step = getTableRow().getItem();
                    if (step != null) {
                        editMacroStep(step);
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getTableRow() == null || getTableRow().getItem() == null ? null : editButton);
            }
        });

        tblMacroSteps.getColumns().addAll(colNum, colEnabled, colDesc, colSearch, colReplace, colOptions, colStatus, colActions);
    }

    private void persist() {
        try {
            MacroStorage.saveFrom(macros);
            host.updateStatus("Makros gespeichert: " + macros.size() + " Makros");
        } catch (Exception e) {
            host.updateStatusError("Fehler beim Speichern: " + e.getMessage());
        }
    }

    private void refreshMacroList() {
        var names = macros.stream().map(Macro::getName).toList();
        cmbMacroList.getItems().setAll(names);
        if (!names.isEmpty()) {
            String preferred = names.contains("Text-Bereinigung") ? "Text-Bereinigung" : names.get(0);
            cmbMacroList.setValue(preferred);
            selectMacro();
        } else {
            currentMacro = null;
            macroDetailsPanel.setVisible(false);
            tblMacroSteps.setItems(null);
        }
    }

    private void selectMacro() {
        String name = cmbMacroList.getValue();
        if (name == null) {
            currentMacro = null;
            macroDetailsPanel.setVisible(false);
            tblMacroSteps.setItems(null);
            return;
        }
        currentMacro = macros.stream().filter(m -> name.equals(m.getName())).findFirst().orElse(null);
        if (currentMacro != null) {
            macroDetailsPanel.setVisible(true);
            tblMacroSteps.setItems(currentMacro.getSteps());
            host.updateStatus("Makro ausgewählt: " + currentMacro.getName());
        }
    }

    private void createNewMacro() {
        TextInputDialog dialog = new TextInputDialog("Neues Makro");
        dialog.setTitle("Neues Makro erstellen");
        dialog.setHeaderText("Makro-Name eingeben");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            Macro macro = new Macro(trimmed);
            macros.add(macro);
            cmbMacroList.getItems().add(trimmed);
            cmbMacroList.setValue(trimmed);
            currentMacro = macro;
            macroDetailsPanel.setVisible(true);
            tblMacroSteps.setItems(macro.getSteps());
            persist();
            host.updateStatus("Neues Makro erstellt: " + trimmed);
        });
    }

    private void deleteCurrentMacro() {
        if (currentMacro == null) {
            return;
        }
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Makro löschen");
        alert.setHeaderText("Makro löschen bestätigen");
        alert.setContentText("Möchten Sie das Makro '" + currentMacro.getName() + "' wirklich löschen?");
        alert.applyTheme(host.getThemeIndex());
        Optional<ButtonType> result = alert.showAndWait(host.getOwnerStage());
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String name = currentMacro.getName();
            macros.remove(currentMacro);
            cmbMacroList.getItems().remove(name);
            cmbMacroList.setValue(null);
            currentMacro = null;
            macroDetailsPanel.setVisible(false);
            tblMacroSteps.setItems(null);
            persist();
            host.updateStatus("Makro gelöscht: " + name);
        }
    }

    private void addMacroStep() {
        if (currentMacro == null) {
            host.updateStatus("Bitte zuerst ein Makro auswählen");
            return;
        }
        String searchText = txtMacroSearch.getText() != null ? txtMacroSearch.getText().trim() : "";
        if (searchText.isEmpty()) {
            host.updateStatus("Bitte Suchtext eingeben");
            return;
        }
        MacroStep step = new MacroStep(
                currentMacro.getSteps().size() + 1,
                searchText,
                txtMacroReplace.getText() != null ? txtMacroReplace.getText() : "",
                txtMacroStepDescription.getText(),
                chkMacroRegex.isSelected(),
                chkMacroCaseSensitive.isSelected(),
                chkMacroWholeWord.isSelected());
        currentMacro.addStep(step);
        txtMacroSearch.clear();
        txtMacroReplace.clear();
        txtMacroStepDescription.clear();
        chkMacroRegex.setSelected(false);
        chkMacroCaseSensitive.setSelected(false);
        chkMacroWholeWord.setSelected(false);
        persist();
        host.updateStatus("Schritt zum Makro hinzugefügt");
    }

    private void removeMacroStep() {
        if (currentMacro == null) {
            return;
        }
        MacroStep selected = tblMacroSteps.getSelectionModel().getSelectedItem();
        if (selected != null) {
            currentMacro.removeStep(selected);
            persist();
            host.updateStatus("Schritt entfernt");
        }
    }

    private void moveMacroStepUp() {
        if (currentMacro == null) {
            return;
        }
        MacroStep selected = tblMacroSteps.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = currentMacro.getSteps().indexOf(selected);
            currentMacro.moveStepUp(selected);
            persist();
            Platform.runLater(() -> {
                int newIdx = Math.max(0, idx - 1);
                tblMacroSteps.getSelectionModel().select(newIdx);
                tblMacroSteps.scrollTo(newIdx);
            });
        }
    }

    private void moveMacroStepDown() {
        if (currentMacro == null) {
            return;
        }
        MacroStep selected = tblMacroSteps.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = currentMacro.getSteps().indexOf(selected);
            currentMacro.moveStepDown(selected);
            persist();
            Platform.runLater(() -> {
                int newIdx = Math.min(currentMacro.getSteps().size() - 1, idx + 1);
                tblMacroSteps.getSelectionModel().select(newIdx);
                tblMacroSteps.scrollTo(newIdx);
            });
        }
    }

    private void editMacroStep(MacroStep step) {
        Dialog<MacroStep> dialog = new Dialog<>();
        dialog.setTitle("Makro-Schritt bearbeiten");
        dialog.setHeaderText("Schritt " + step.getStepNumber() + " bearbeiten");
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Löschen", ButtonBar.ButtonData.OTHER),
                ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));
        TextField searchField = new TextField(step.getSearchText());
        TextField replaceField = new TextField(step.getReplaceText());
        TextField descField = new TextField(step.getDescription());
        CheckBox regex = new CheckBox("Regex");
        regex.setSelected(step.isUseRegex());
        CheckBox caseSens = new CheckBox("Case-Sensitive");
        caseSens.setSelected(step.isCaseSensitive());
        CheckBox wholeWord = new CheckBox("Ganzes Wort");
        wholeWord.setSelected(step.isWholeWord());
        grid.addRow(0, new Label("Suchen:"), searchField);
        grid.addRow(1, new Label("Ersetzen:"), replaceField);
        grid.addRow(2, new Label("Beschreibung:"), descField);
        grid.addRow(3, new Label("Optionen:"), new VBox(5, regex, caseSens, wholeWord));
        dialog.getDialogPane().setContent(grid);
        styleDialog(dialog);

        dialog.setResultConverter(button -> {
            if (button != null && button.getButtonData() == ButtonBar.ButtonData.OTHER) {
                if (currentMacro != null) {
                    currentMacro.removeStep(step);
                    persist();
                }
                return null;
            }
            if (button == null || button.getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                return null;
            }
            step.setSearchText(searchField.getText());
            step.setReplaceText(replaceField.getText());
            step.setDescription(descField.getText());
            step.setUseRegex(regex.isSelected());
            step.setCaseSensitive(caseSens.isSelected());
            step.setWholeWord(wholeWord.isSelected());
            persist();
            tblMacroSteps.refresh();
            return step;
        });
        dialog.showAndWait();
    }

    private void runCurrentMacro() {
        if (currentMacro == null || currentMacro.getSteps().isEmpty()) {
            host.updateStatusError("Kein Makro mit Schritten ausgewählt");
            return;
        }
        ChapterEditorHost editor = host.getChapterEditor();
        if (editor == null) {
            host.updateStatusError("Kein Kapitel-Editor aktiv");
            return;
        }
        editor.requestEditorFocus();
        MacroExecutor.execute(currentMacro, editor, msg -> {
            if (msg != null && (msg.toLowerCase().contains("fehler") || msg.contains("⚠"))) {
                host.updateStatusError(msg);
            } else {
                host.updateStatus(msg);
            }
        });
    }

    private void exportMacroCsv() {
        if (currentMacro == null) {
            host.updateStatusError("Kein Makro ausgewählt");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Makro als CSV speichern");
        chooser.setInitialFileName(currentMacro.getName() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV-Dateien", "*.csv"));
        String lastDir = preferences.get("lastSaveDirectory", null);
        if (lastDir != null) {
            chooser.setInitialDirectory(new File(lastDir));
        }
        File file = chooser.showSaveDialog(host.getOwnerStage());
        if (file == null) {
            return;
        }
        try {
            preferences.put("lastSaveDirectory", file.getParent());
            StringBuilder csv = new StringBuilder("Schritt,Aktiv,Beschreibung,Suchen,Ersetzen,Regex,Case,Word\n");
            for (MacroStep step : currentMacro.getSteps()) {
                csv.append(step.getStepNumber()).append(',')
                        .append(step.isEnabled()).append(',')
                        .append(escapeCsv(step.getDescription())).append(',')
                        .append(escapeCsv(step.getSearchText())).append(',')
                        .append(escapeCsv(step.getReplaceText())).append(',')
                        .append(step.isUseRegex()).append(',')
                        .append(step.isCaseSensitive()).append(',')
                        .append(step.isWholeWord()).append('\n');
            }
            Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8);
            host.updateStatus("Makro exportiert: " + file.getName());
        } catch (Exception e) {
            host.updateStatusError("CSV-Export fehlgeschlagen: " + e.getMessage());
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void styleDialog(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        String manuskriptCss = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCss != null && !pane.getStylesheets().contains(manuskriptCss)) {
            pane.getStylesheets().add(manuskriptCss);
        }
        int t = host.getThemeIndex();
        pane.getStyleClass().removeAll("weiss-theme", "theme-dark", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        switch (t) {
            case 0 -> pane.getStyleClass().add("weiss-theme");
            case 2 -> pane.getStyleClass().add("pastell-theme");
            case 3 -> pane.getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> pane.getStyleClass().addAll("theme-dark", "gruen-theme");
            case 5 -> pane.getStyleClass().addAll("theme-dark", "lila-theme");
            default -> pane.getStyleClass().add("theme-dark");
        }
    }
}
