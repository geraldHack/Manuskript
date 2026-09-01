package com.manuskript.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Filterbares Modell-Dropdown mit {@link ModelOption} (ID + Anzeigetext inkl. Kosten).
 * Bei großen OpenRouter-Katalogen: kompakte Tag-Zeile (scrollbar, begrenzt) + Liste darunter.
 */
public class FilterableModelOptionSelector extends VBox {

    private static final int LARGE_CATALOG_THRESHOLD = 40;
    private static final double TAG_ROW_MAX_HEIGHT = 72.0;
    private static final double MODEL_LIST_PREF_HEIGHT = 220.0;

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
    private final FlowPane tagPane;
    private final ScrollPane tagScroll;
    private final ListView<ModelOption> modelList;
    private final Label pricingLabel;
    private final Set<String> activeTags = new HashSet<>();

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
        modelCombo.setCellFactory(list -> createModelListCell());
        modelCombo.setButtonCell(createModelListCell());

        loadButton = new Button("Modelle laden");
        loadButton.setVisible(withLoadButton);
        loadButton.setManaged(withLoadButton);

        tagPane = new FlowPane(6, 4);
        tagPane.setPrefWrapLength(640);
        tagScroll = new ScrollPane(tagPane);
        tagScroll.setFitToWidth(true);
        tagScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tagScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tagScroll.setPrefViewportHeight(36);
        tagScroll.setMaxHeight(TAG_ROW_MAX_HEIGHT);
        tagScroll.setVisible(false);
        tagScroll.setManaged(false);
        tagScroll.getStyleClass().add("model-tag-scroll");

        modelList = new ListView<>(filteredModels);
        modelList.setCellFactory(list -> createModelListCell());
        modelList.setPrefHeight(MODEL_LIST_PREF_HEIGHT);
        modelList.setMinHeight(120);
        modelList.setMaxHeight(MODEL_LIST_PREF_HEIGHT);
        modelList.setVisible(false);
        modelList.setManaged(false);
        modelList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                applySelection(selected);
            }
        });

        pricingLabel = new Label();
        pricingLabel.setWrapText(true);
        pricingLabel.setMaxWidth(Double.MAX_VALUE);
        pricingLabel.getStyleClass().add("model-option-pricing");
        pricingLabel.setVisible(false);
        pricingLabel.setManaged(false);

        HBox selectRow = new HBox(10, modelCombo, loadButton);
        HBox.setHgrow(modelCombo, Priority.ALWAYS);
        selectRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(filterField, tagScroll, selectRow, pricingLabel, modelList);

        filterField.textProperty().addListener((obs, old, text) -> applyFilter());
        modelCombo.valueProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                applySelection(selected);
                if (modelList.isVisible()) {
                    modelList.getSelectionModel().select(selected);
                }
            }
        });
    }

    private ListCell<ModelOption> createModelListCell() {
        return new ListCell<>() {
            private final Label idLabel = new Label();
            private final Label costLabel = new Label();
            private final VBox content = new VBox(2, idLabel, costLabel);

            {
                idLabel.getStyleClass().add("model-option-id");
                costLabel.getStyleClass().add("model-option-pricing");
                costLabel.setWrapText(true);
                content.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(ModelOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                idLabel.setText(item.id);
                String pricing = item.pricingText();
                costLabel.setText(pricing);
                costLabel.setVisible(!pricing.isEmpty());
                costLabel.setManaged(!pricing.isEmpty());
                setText(null);
                setGraphic(content);
                setTooltip(new Tooltip(item.displayText));
            }
        };
    }

    private void applySelection(ModelOption selected) {
        modelCombo.setValue(selected);
        if (modelCombo.isEditable() && modelCombo.getEditor() != null) {
            modelCombo.getEditor().setText(selected.id);
        }
        String pricing = selected.pricingText();
        if (pricing.isEmpty()) {
            pricingLabel.setText("");
            pricingLabel.setVisible(false);
            pricingLabel.setManaged(false);
        } else {
            pricingLabel.setText(pricing);
            pricingLabel.setVisible(true);
            pricingLabel.setManaged(true);
        }
    }

    public void setOnLoad(Runnable handler) {
        loadButton.setOnAction(e -> {
            if (handler != null) {
                handler.run();
            }
        });
    }

    public void setModelOptions(List<ModelOption> options) {
        String keepId = getModelId();
        List<ModelOption> resolved = options != null ? options : List.of();
        allModels.setAll(resolved);
        rebuildTagFilters(resolved);
        updateLargeCatalogUi(resolved.size());
        applyFilter();
        if (keepId != null && !keepId.isBlank()) {
            setModelId(keepId);
        } else if (!resolved.isEmpty()) {
            applySelection(resolved.get(0));
            modelList.getSelectionModel().select(0);
        }
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
            modelList.getSelectionModel().clearSelection();
            pricingLabel.setText("");
            pricingLabel.setVisible(false);
            pricingLabel.setManaged(false);
            return;
        }
        ModelOption existing = findById(modelId);
        if (existing != null) {
            applySelection(existing);
            modelList.getSelectionModel().select(existing);
        } else {
            ModelOption custom = new ModelOption(modelId, modelId);
            allModels.add(custom);
            applySelection(custom);
            modelList.getSelectionModel().select(custom);
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
        modelList.setDisable(disabled);
        tagPane.setDisable(disabled);
    }

    private ModelOption findById(String modelId) {
        for (ModelOption option : allModels) {
            if (modelId.equals(option.id)) {
                return option;
            }
        }
        return null;
    }

    private void rebuildTagFilters(List<ModelOption> options) {
        tagPane.getChildren().clear();
        activeTags.clear();
        List<String> uiTags = OpenRouterModelTags.pickUiTags(options);
        if (uiTags.isEmpty()) {
            tagScroll.setVisible(false);
            tagScroll.setManaged(false);
            return;
        }
        for (String tag : uiTags) {
            ToggleButton chip = new ToggleButton(OpenRouterModelTags.label(tag));
            chip.setUserData(tag);
            chip.setTooltip(new Tooltip(OpenRouterModelTags.tooltip(tag)));
            chip.getStyleClass().add("model-tag-chip");
            chip.setPadding(new Insets(2, 8, 2, 8));
            chip.selectedProperty().addListener((obs, was, on) -> {
                if (Boolean.TRUE.equals(on)) {
                    activeTags.add(tag);
                } else {
                    activeTags.remove(tag);
                }
                applyFilter();
            });
            tagPane.getChildren().add(chip);
        }
        tagScroll.setVisible(true);
        tagScroll.setManaged(true);
    }

    private void updateLargeCatalogUi(int count) {
        boolean large = count >= LARGE_CATALOG_THRESHOLD;
        modelList.setVisible(large);
        modelList.setManaged(large);
        if (large) {
            VBox.setVgrow(modelList, Priority.ALWAYS);
            modelList.setMaxHeight(Double.MAX_VALUE);
        } else {
            VBox.setVgrow(modelList, Priority.NEVER);
            modelList.setMaxHeight(MODEL_LIST_PREF_HEIGHT);
            modelList.getSelectionModel().clearSelection();
        }
    }

    private void applyFilter() {
        String needle = filterField.getText();
        needle = needle == null ? "" : needle.trim().toLowerCase();
        String finalNeedle = needle;
        filteredModels.setPredicate(option -> {
            if (option == null) {
                return false;
            }
            if (!activeTags.isEmpty()) {
                for (String tag : activeTags) {
                    if (!option.hasTag(tag)) {
                        return false;
                    }
                }
            }
            if (finalNeedle.isEmpty()) {
                return true;
            }
            return option.id.toLowerCase().contains(finalNeedle)
                    || option.displayText.toLowerCase().contains(finalNeedle);
        });
    }
}
