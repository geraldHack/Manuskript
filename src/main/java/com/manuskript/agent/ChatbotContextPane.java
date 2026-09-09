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
    private final String defaultSourcesCsv;
    private final FlowPane contextPills;
    private final HBox neighborSpinnersRow;
    private final Spinner<Integer> chaptersBeforeSpinner;
    private final Spinner<Integer> chaptersAfterSpinner;
    private final ComboBox<ChatbotContextSize> contextSizeCombo;

    public ChatbotContextPane(String preferencesKey) {
        this(preferencesKey, "CURRENT_CHAPTER,WORLD_EDITOR");
    }

    /**
     * @param defaultSourcesCsv kommagetrennte {@link ChatbotContextSource}-Namen für Erststart
     */
    public ChatbotContextPane(String preferencesKey, String defaultSourcesCsv) {
        super(4);
        getStyleClass().add("chatbot-context-pane");
        String key = preferencesKey == null || preferencesKey.isBlank() ? "default" : preferencesKey;
        preferences = Preferences.userNodeForPackage(ChatbotContextPane.class).node(key);
        this.defaultSourcesCsv = defaultSourcesCsv == null || defaultSourcesCsv.isBlank()
                ? "CURRENT_CHAPTER,WORLD_EDITOR"
                : defaultSourcesCsv;

        Button addContextButton = new Button("+ Kontext");
        addContextButton.setOnAction(e -> showAddContextMenu(addContextButton));

        contextPills = new FlowPane(6, 4);
        contextPills.getStyleClass().add("chatbot-context-pills");
        contextPills.setPrefWrapLength(280);

        chaptersBeforeSpinner = new Spinner<>(0, 50, 3);
        chaptersBeforeSpinner.setEditable(true);
        chaptersBeforeSpinner.setPrefWidth(70);
        chaptersBeforeSpinner.getStyleClass().add("agent-chrome-spinner");
        chaptersAfterSpinner = new Spinner<>(0, 50, 3);
        chaptersAfterSpinner.setEditable(true);
        chaptersAfterSpinner.setPrefWidth(70);
        chaptersAfterSpinner.getStyleClass().add("agent-chrome-spinner");
        applyTheme(AgentFindingStyles.themeIndex());
        chaptersBeforeSpinner.valueProperty().addListener((obs, o, n) -> {
            contextConfig.setChaptersBefore(n == null ? 0 : n);
            syncNeighborSource(ChatbotContextSource.CHAPTERS_BEFORE, n);
            persist();
        });
        chaptersAfterSpinner.valueProperty().addListener((obs, o, n) -> {
            contextConfig.setChaptersAfter(n == null ? 0 : n);
            syncNeighborSource(ChatbotContextSource.CHAPTERS_AFTER, n);
            persist();
        });

        neighborSpinnersRow = new HBox(8,
                chromeLabel("Davor:"), chaptersBeforeSpinner,
                chromeLabel("Danach:"), chaptersAfterSpinner);
        neighborSpinnersRow.getStyleClass().add("agent-chrome-row");
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
                chromeLabel("Kontextgröße:"), contextSizeCombo);
        loadFromPreferences();
    }

    public ChatbotContextConfig getContextConfig() {
        return contextConfig;
    }

    public void applyTheme(int themeIndex) {
        AgentChromeSpinnerSupport.apply(chaptersBeforeSpinner, themeIndex);
        AgentChromeSpinnerSupport.apply(chaptersAfterSpinner, themeIndex);
    }

    public ChatbotContextSize getContextSize() {
        return contextConfig.getContextSize();
    }

    public void loadFromPreferences() {
        contextConfig.setSources(EnumSet.noneOf(ChatbotContextSource.class));
        String sources = preferences.get("context_sources", defaultSourcesCsv);
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
                ensureNeighborCount(source);
                refreshContextPills();
                updateNeighborSpinnersVisibility();
                persist();
            });
            menu.getItems().add(item);
        }
        if (menu.getItems().isEmpty()) {
            return;
        }
        AgentPopupSupport.showMenuBelow(menu, anchor);
    }

    private void refreshContextPills() {
        contextPills.getChildren().clear();
        for (ChatbotContextSource source : contextConfig.getSources()) {
            contextPills.getChildren().add(createContextPill(source, () -> {
                contextConfig.removeSource(source);
                refreshContextPills();
                updateNeighborSpinnersVisibility();
                persist();
            }));
        }
    }

    static Label chromeLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("agent-chrome-label");
        return label;
    }

    /** Kontext-Chip: gleicher Button-Look wie „+ Kontext“, Text und × auf einer Fläche. */
    static Button createContextPill(ChatbotContextSource source, Runnable onRemove) {
        Button pill = new Button(source.getLabel() + "  ×");
        pill.getStyleClass().add("chatbot-context-pill");
        pill.setTooltip(new Tooltip(source.getTooltip()));
        pill.setOnAction(e -> onRemove.run());
        return pill;
    }

    private void updateNeighborSpinnersVisibility() {
        boolean show = contextConfig.hasSource(ChatbotContextSource.CHAPTERS_BEFORE)
                || contextConfig.hasSource(ChatbotContextSource.CHAPTERS_AFTER);
        neighborSpinnersRow.setVisible(show);
        neighborSpinnersRow.setManaged(show);
        if (show) {
            AgentScrollPaneSupport.ensureOverflowForChrome(this);
        } else {
            AgentScrollPaneSupport.restoreFillIfChromeCollapsed(this);
        }
    }

    private void syncNeighborSource(ChatbotContextSource source, Integer count) {
        if (source == null) {
            return;
        }
        int value = count == null ? 0 : count;
        if (value <= 0) {
            if (contextConfig.hasSource(source)) {
                contextConfig.removeSource(source);
                refreshContextPills();
                updateNeighborSpinnersVisibility();
            }
            return;
        }
        if (!contextConfig.hasSource(source)) {
            contextConfig.addSource(source);
            refreshContextPills();
            updateNeighborSpinnersVisibility();
        }
    }

    private void ensureNeighborCount(ChatbotContextSource source) {
        if (source == ChatbotContextSource.CHAPTERS_BEFORE
                && (chaptersBeforeSpinner.getValue() == null || chaptersBeforeSpinner.getValue() <= 0)) {
            chaptersBeforeSpinner.getValueFactory().setValue(1);
        }
        if (source == ChatbotContextSource.CHAPTERS_AFTER
                && (chaptersAfterSpinner.getValue() == null || chaptersAfterSpinner.getValue() <= 0)) {
            chaptersAfterSpinner.getValueFactory().setValue(1);
        }
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
