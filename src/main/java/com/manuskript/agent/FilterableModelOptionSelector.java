package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Filterbares Modell-Dropdown mit {@link ModelOption} (ID + Anzeigetext inkl. Kosten).
 */
public class FilterableModelOptionSelector extends VBox {

    private static final StringConverter<ModelOption> CONVERTER = new StringConverter<>() {
        @Override
        public String toString(ModelOption option) {
            return option == null ? "" : option.displayText;
        }

        @Override
        public ModelOption fromString(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            String id = ModelOption.stripIdFromDisplay(text);
            return new ModelOption(id, text.trim());
        }
    };

    private final TextField filterField;
    private final ComboBox<ModelOption> modelCombo;
    private final Button loadButton;
    private final ObservableList<ModelOption> allModels = FXCollections.observableArrayList();
    private final FilteredList<ModelOption> filteredModels;

    public FilterableModelOptionSelector(boolean withLoadButton) {
        setSpacing(4);

        filterField = new TextField();
        filterField.setPromptText("Modell filtern…");
        filterField.setMaxWidth(Double.MAX_VALUE);

        filteredModels = new FilteredList<>(allModels, option -> true);
        modelCombo = new ComboBox<>(filteredModels);
        modelCombo.setConverter(CONVERTER);
        modelCombo.setEditable(true);
        modelCombo.setMaxWidth(Double.MAX_VALUE);
        modelCombo.setPrefWidth(520);
        modelCombo.setPromptText("Modell wählen oder eingeben");

        loadButton = new Button("Modelle laden");
        loadButton.setVisible(withLoadButton);
        loadButton.setManaged(withLoadButton);

        HBox selectRow = new HBox(10, modelCombo, loadButton);
        HBox.setHgrow(modelCombo, Priority.ALWAYS);
        selectRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(filterField, selectRow);

        filterField.textProperty().addListener((obs, old, text) -> applyFilter(text));
    }

    public void setOnLoad(Runnable handler) {
        loadButton.setOnAction(e -> {
            if (handler != null) {
                handler.run();
            }
        });
    }

    public void setModelOptions(List<ModelOption> options) {
        allModels.setAll(options != null ? options : List.of());
        applyFilter(filterField.getText());
    }

    public String getModelId() {
        ModelOption value = modelCombo.getValue();
        if (value != null && value.id != null && !value.id.isBlank()) {
            return value.id.trim();
        }
        if (modelCombo.isEditable() && modelCombo.getEditor() != null) {
            return ModelOption.stripIdFromDisplay(modelCombo.getEditor().getText());
        }
        return "";
    }

    public void setModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            modelCombo.setValue(null);
            if (modelCombo.isEditable() && modelCombo.getEditor() != null) {
                modelCombo.getEditor().clear();
            }
            return;
        }
        ModelOption existing = findById(modelId);
        if (existing != null) {
            modelCombo.setValue(existing);
        } else {
            ModelOption custom = new ModelOption(modelId, modelId);
            allModels.add(custom);
            modelCombo.setValue(custom);
        }
        if (modelCombo.isEditable() && modelCombo.getEditor() != null) {
            ModelOption selected = modelCombo.getValue();
            modelCombo.getEditor().setText(selected != null ? selected.displayText : modelId);
        }
    }

    public void setInitialEditorText(String modelId) {
        if (modelCombo.isEditable() && modelCombo.getEditor() != null) {
            modelCombo.getEditor().setText(modelId != null ? modelId : "");
        }
        setModelId(modelId);
    }

    public void setSelectorDisabled(boolean disabled) {
        filterField.setDisable(disabled);
        modelCombo.setDisable(disabled);
        loadButton.setDisable(disabled);
    }

    private ModelOption findById(String modelId) {
        for (ModelOption option : allModels) {
            if (modelId.equals(option.id)) {
                return option;
            }
        }
        return null;
    }

    private void applyFilter(String text) {
        String needle = text == null ? "" : text.trim().toLowerCase();
        filteredModels.setPredicate(option -> {
            if (needle.isEmpty()) {
                return true;
            }
            return option.id.toLowerCase().contains(needle)
                    || option.displayText.toLowerCase().contains(needle);
        });
    }
}
