package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Filterbares Modell-Dropdown (Substring-Suche) mit optionalem „Modelle laden“-Button.
 * <p>
 * Nutzt bewusst keine {@link javafx.collections.transformation.FilteredList} als
 * ComboBox-Items — die korreliert in JavaFX 21 leicht mit Selection-Events
 * ({@code ArrayIndexOutOfBoundsException} / korrupte Indizes).
 */
public class FilterableModelSelector extends VBox {

    private final TextField filterField;
    private final ComboBox<String> modelCombo;
    private final Button loadButton;
    private final List<String> allModels = new ArrayList<>();
    private final ObservableList<String> visibleModels = FXCollections.observableArrayList();
    private boolean useModelHistory;
    private boolean suppressEvents;
    private boolean refreshScheduled;
    private Consumer<String> onModelChanged;

    public FilterableModelSelector(boolean withLoadButton) {
        setSpacing(4);

        filterField = new TextField();
        filterField.setPromptText("Modell filtern…");
        filterField.setMaxWidth(Double.MAX_VALUE);

        modelCombo = new ComboBox<>(visibleModels);
        modelCombo.setEditable(true);
        modelCombo.setMaxWidth(Double.MAX_VALUE);
        modelCombo.setPromptText("Modell wählen oder eingeben");

        loadButton = new Button("Modelle laden");
        loadButton.setVisible(withLoadButton);
        loadButton.setManaged(withLoadButton);

        HBox selectRow = new HBox(8, modelCombo, loadButton);
        HBox.setHgrow(modelCombo, Priority.ALWAYS);
        selectRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(filterField, selectRow);

        filterField.textProperty().addListener((obs, old, text) -> scheduleRefreshVisible(null));
        modelCombo.valueProperty().addListener((obs, old, val) -> handleModelSelection(val, old));
        modelCombo.setOnAction(e -> commitModelFromCombo());
    }

    public void setUseModelHistory(boolean use) {
        useModelHistory = use;
    }

    public void setOnModelChanged(Consumer<String> handler) {
        onModelChanged = handler;
    }

    public void setOnLoad(Runnable handler) {
        loadButton.setOnAction(e -> {
            if (handler != null) {
                handler.run();
            }
        });
    }

    public void setModels(List<String> models) {
        List<String> resolved = useModelHistory
                ? ModelHistory.getHistoryWithAvailableModels(models != null ? models : List.of())
                : new ArrayList<>(models != null ? models : List.of());
        String keep = getValue();
        allModels.clear();
        allModels.addAll(resolved);
        scheduleRefreshVisible(keep);
    }

    public void addModels(List<String> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        List<String> merged = new ArrayList<>(allModels);
        for (String model : models) {
            if (model != null && !model.isBlank() && !merged.contains(model)) {
                merged.add(model);
            }
        }
        setModels(merged);
    }

    public String getValue() {
        String value = modelCombo.getValue();
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        if (modelCombo.isEditable() && modelCombo.getEditor() != null) {
            String editorText = modelCombo.getEditor().getText();
            return editorText != null ? editorText.trim() : null;
        }
        return null;
    }

    public void setValue(String model) {
        suppressEvents = true;
        try {
            if (model != null && !model.isBlank() && !allModels.contains(model)) {
                allModels.add(model);
                refreshVisibleNow(model);
            } else {
                modelCombo.setValue(model);
                if ((modelCombo.getValue() == null || modelCombo.getValue().isBlank())
                        && modelCombo.isEditable() && modelCombo.getEditor() != null && model != null) {
                    modelCombo.getEditor().setText(model);
                }
            }
        } finally {
            suppressEvents = false;
        }
    }

    public void setSelectorDisabled(boolean disabled) {
        filterField.setDisable(disabled);
        modelCombo.setDisable(disabled);
        loadButton.setDisable(disabled);
    }

    public ComboBox<String> getComboBox() {
        return modelCombo;
    }

    public Button getLoadButton() {
        return loadButton;
    }

    private void handleModelSelection(String value, String oldValue) {
        if (suppressEvents || value == null || value.isBlank() || value.equals(oldValue)) {
            return;
        }
        commitModel(value);
    }

    private void commitModelFromCombo() {
        if (suppressEvents) {
            return;
        }
        String value = getValue();
        if (value != null && !value.isBlank()) {
            commitModel(value);
        }
    }

    private void commitModel(String value) {
        if (useModelHistory) {
            ModelHistory.addModel(value);
        }
        if (!allModels.contains(value)) {
            allModels.add(value);
        }
        if (useModelHistory) {
            // Liste erst nach dem ComboBox-Event neu aufbauen (sonst Selection/Items-Race).
            List<String> withHistory = ModelHistory.getHistoryWithAvailableModels(new ArrayList<>(allModels));
            allModels.clear();
            allModels.addAll(withHistory);
            scheduleRefreshVisible(value);
        }
        if (onModelChanged != null) {
            onModelChanged.accept(value);
        }
    }

    private void scheduleRefreshVisible(String preferredValue) {
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        Platform.runLater(() -> {
            refreshScheduled = false;
            String keep = preferredValue != null ? preferredValue : getValue();
            refreshVisibleNow(keep);
        });
    }

    private void refreshVisibleNow(String preferredValue) {
        String needle = filterField.getText();
        needle = needle == null ? "" : needle.trim().toLowerCase();
        List<String> next = new ArrayList<>();
        for (String model : allModels) {
            if (model == null) {
                continue;
            }
            if (needle.isEmpty() || model.toLowerCase().contains(needle)) {
                next.add(model);
            }
        }
        suppressEvents = true;
        try {
            visibleModels.setAll(next);
            if (preferredValue != null && !preferredValue.isBlank()) {
                modelCombo.setValue(preferredValue);
                if (modelCombo.isEditable() && modelCombo.getEditor() != null) {
                    modelCombo.getEditor().setText(preferredValue);
                }
            }
        } finally {
            suppressEvents = false;
        }
    }
}
