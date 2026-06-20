package com.manuskript.agent;

import java.util.EnumSet;
import java.util.prefs.Preferences;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Wiederverwendbare Kontext-Pills (wie im Chatbot-Tab).
 */
public class ChatbotContextPane extends VBox {

    private final ChatbotContextConfig contextConfig = new ChatbotContextConfig();
    private final Preferences preferences;
    private final FlowPane contextPills;
    private final HBox neighborSpinnersRow;
    private final Spinner<Integer> chaptersBeforeSpinner;
    private final Spinner<Integer> chaptersAfterSpinner;
    private final ComboBox<ChatbotContextSize> contextSizeCombo;

    public ChatbotContextPane(String preferencesKey) {
        super(4);
        getStyleClass().add("chatbot-context-pane");
        String key = preferencesKey == null || preferencesKey.isBlank() ? "default" : preferencesKey;
        preferences = Preferences.userNodeForPackage(ChatbotContextPane.class).node(key);

        contextConfig.addSource(ChatbotContextSource.CURRENT_CHAPTER);
        contextConfig.addSource(ChatbotContextSource.WORLD_EDITOR);

        Button addContextButton = new Button("+ Kontext");
        addContextButton.setOnAction(e -> showAddContextMenu(addContextButton));

        contextPills = new FlowPane(6, 4);
        contextPills.setPrefWrapLength(280);

        chaptersBeforeSpinner = new Spinner<>(0, 50, 3);
        chaptersBeforeSpinner.setEditable(true);
        chaptersBeforeSpinner.setPrefWidth(70);
        chaptersAfterSpinner = new Spinner<>(0, 50, 3);
        chaptersAfterSpinner.setEditable(true);
        chaptersAfterSpinner.setPrefWidth(70);
        chaptersBeforeSpinner.valueProperty().addListener((obs, o, n) -> {
            contextConfig.setChaptersBefore(n);
            persist();
        });
        chaptersAfterSpinner.valueProperty().addListener((obs, o, n) -> {
            contextConfig.setChaptersAfter(n);
            persist();
        });

        neighborSpinnersRow = new HBox(8,
                new Label("Davor:"), chaptersBeforeSpinner,
                new Label("Danach:"), chaptersAfterSpinner);
        neighborSpinnersRow.setAlignment(Pos.CENTER_LEFT);

        contextSizeCombo = new ComboBox<>();
        contextSizeCombo.getItems().setAll(ChatbotContextSize.values());
        contextSizeCombo.setConverter(contextSizeConverter());
        contextSizeCombo.setButtonCell(contextSizeListCell());
        contextSizeCombo.setCellFactory(list -> contextSizeListCell());
        contextSizeCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                contextConfig.setContextSize(n);
                persist();
            }
        });

        getChildren().addAll(addContextButton, contextPills, neighborSpinnersRow,
                new Label("Kontextgröße:"), contextSizeCombo);
        loadFromPreferences();
    }

    public ChatbotContextConfig getContextConfig() {
        return contextConfig;
    }

    public ChatbotContextSize getContextSize() {
        return contextConfig.getContextSize();
    }

    public void loadFromPreferences() {
        contextConfig.setSources(EnumSet.noneOf(ChatbotContextSource.class));
        String sources = preferences.get("context_sources", "CURRENT_CHAPTER,WORLD_EDITOR");
        if (!sources.isBlank()) {
            for (String part : sources.split(",")) {
                try {
                    contextConfig.addSource(ChatbotContextSource.valueOf(part.trim()));
                } catch (IllegalArgumentException ignored) {
                    // unbekannte Quelle
                }
            }
        }
        if (contextConfig.getSources().isEmpty()) {
            contextConfig.addSource(ChatbotContextSource.CURRENT_CHAPTER);
        }
        contextConfig.setChaptersBefore(preferences.getInt("chapters_before", 3));
        contextConfig.setChaptersAfter(preferences.getInt("chapters_after", 3));
        contextConfig.setContextSize(ChatbotContextSize.fromName(
                preferences.get("context_size", ChatbotContextSize.defaultFromParameters().name())));
        chaptersBeforeSpinner.getValueFactory().setValue(contextConfig.getChaptersBefore());
        chaptersAfterSpinner.getValueFactory().setValue(contextConfig.getChaptersAfter());
        contextSizeCombo.setValue(contextConfig.getContextSize());
        refreshContextPills();
        updateNeighborSpinnersVisibility();
    }

    private void persist() {
        StringBuilder sources = new StringBuilder();
        for (ChatbotContextSource source : contextConfig.getSources()) {
            if (sources.length() > 0) {
                sources.append(',');
            }
            sources.append(source.name());
        }
        preferences.put("context_sources", sources.toString());
        preferences.putInt("chapters_before", contextConfig.getChaptersBefore());
        preferences.putInt("chapters_after", contextConfig.getChaptersAfter());
        preferences.put("context_size", contextConfig.getContextSize().name());
    }

    private void showAddContextMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();
        for (ChatbotContextSource source : ChatbotContextSource.values()) {
            if (contextConfig.hasSource(source)) {
                continue;
            }
            MenuItem item = new MenuItem(source.getLabel());
            item.setOnAction(e -> {
                contextConfig.addSource(source);
                refreshContextPills();
                updateNeighborSpinnersVisibility();
                persist();
            });
            menu.getItems().add(item);
        }
        if (menu.getItems().isEmpty()) {
            return;
        }
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void refreshContextPills() {
        contextPills.getChildren().clear();
        for (ChatbotContextSource source : contextConfig.getSources()) {
            Button pill = new Button(source.getLabel() + " ✕");
            pill.getStyleClass().add("chatbot-context-pill");
            pill.setTooltip(new Tooltip(source.getTooltip()));
            pill.setOnAction(e -> {
                contextConfig.removeSource(source);
                refreshContextPills();
                updateNeighborSpinnersVisibility();
                persist();
            });
            contextPills.getChildren().add(pill);
        }
    }

    private void updateNeighborSpinnersVisibility() {
        boolean show = contextConfig.hasSource(ChatbotContextSource.CHAPTERS_BEFORE)
                || contextConfig.hasSource(ChatbotContextSource.CHAPTERS_AFTER);
        neighborSpinnersRow.setVisible(show);
        neighborSpinnersRow.setManaged(show);
        chaptersBeforeSpinner.setDisable(!contextConfig.hasSource(ChatbotContextSource.CHAPTERS_BEFORE));
        chaptersAfterSpinner.setDisable(!contextConfig.hasSource(ChatbotContextSource.CHAPTERS_AFTER));
    }

    private static StringConverter<ChatbotContextSize> contextSizeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(ChatbotContextSize size) {
                return size == null ? "" : size.getLabel();
            }

            @Override
            public ChatbotContextSize fromString(String string) {
                return ChatbotContextSize.fromName(string);
            }
        };
    }

    private static ListCell<ChatbotContextSize> contextSizeListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ChatbotContextSize item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getLabel());
            }
        };
    }
}
