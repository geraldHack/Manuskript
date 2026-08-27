package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import com.manuskript.plugin.PluginCatalog;
import com.manuskript.launcher.ProgramLauncher;
import com.manuskript.launcher.ProgramLauncherStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Setup: Feature-Pakete, Plugins, externe Programme und optionale Werkzeuge
 * (Pandoc, FFmpeg, Whisper, LanguageTool, KI).
 */
public final class SetupAssistantWindow {

    private SetupAssistantWindow() {
    }

    public static void show(Window owner, int themeIndex) {
        show(owner, themeIndex, false, null);
    }

    public static void show(Window owner, int themeIndex, boolean wait) {
        show(owner, themeIndex, wait, null);
    }

    public static void show(Window owner, int themeIndex, boolean wait, Runnable onClosed) {
        CustomStage stage = StageManager.createStage("Setup-Assistent", owner, false);
        stage.setCustomTitle("Setup-Assistent");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Tab functionsTab = new Tab("Funktionen", wrapScroll(buildFunctionsPane(themeIndex), themeIndex));
        functionsTab.setClosable(false);

        Tab launchersTab = new Tab("Externe Programme");
        launchersTab.setClosable(false);
        launchersTab.setContent(wrapScroll(buildLaunchersPane(stage, themeIndex), themeIndex));

        Tab pluginsTab = new Tab("Plugins");
        pluginsTab.setClosable(false);
        pluginsTab.setContent(wrapScroll(buildPluginsPane(themeIndex), themeIndex));

        VBox checksBox = new VBox(10);
        checksBox.setPadding(new Insets(4, 0, 4, 0));

        final Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            checksBox.getChildren().clear();
            for (SetupItem item : runChecks()) {
                checksBox.getChildren().add(buildRow(item, stage, themeIndex, refreshHolder[0]));
            }
            EditorDialogThemes.applyToNode(checksBox, themeIndex);
        };

        Label toolsIntro = new Label(
                "Prüft optionale Werkzeuge. Pro Eintrag entscheidest du selbst, was eingerichtet wird.\n"
                        + "Pandoc und FFmpeg liegen im Programmverzeichnis; Schreiben geht auch ohne die übrigen Komponenten.");
        toolsIntro.setWrapText(true);
        toolsIntro.getStyleClass().add("dialog-label");
        VBox toolsContent = new VBox(12, toolsIntro, new Separator(), checksBox);
        toolsContent.setPadding(new Insets(16));
        Tab toolsTab = new Tab("Werkzeuge", wrapScroll(toolsContent, themeIndex));
        toolsTab.setClosable(false);
        tabs.getTabs().addAll(functionsTab, pluginsTab, launchersTab, toolsTab);

        Button refresh = new Button("Erneut prüfen");
        refresh.getStyleClass().add("dialog-button");
        refresh.setOnAction(e -> refreshHolder[0].run());

        Button close = new Button("Schließen");
        close.getStyleClass().add("dialog-button");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, refresh, close);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(0, 16, 16, 16));

        String bg = EditorDialogThemes.color(themeIndex, 0);
        String bgStyle = "-fx-background-color: " + bg + "; -fx-background: " + bg + ";";
        VBox shell = new VBox(tabs, buttons);
        shell.setStyle(bgStyle);
        shell.getStyleClass().add("dialog-container");
        buttons.setStyle(bgStyle);

        Scene scene = new Scene(shell, 1080, 480);
        stage.setMinWidth(960);
        stage.setMinHeight(400);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        scene.setFill(Color.web(bg));
        EditorDialogThemes.applyToNode(shell, themeIndex);
        stage.setTitleBarTheme(themeIndex);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        if (onClosed != null) {
            stage.setOnHidden(e -> onClosed.run());
        }
        refreshHolder[0].run();
        if (wait) {
            stage.showAndWait();
        } else {
            stage.show();
        }
    }

    private static ScrollPane wrapScroll(Node content, int themeIndex) {
        String bg = EditorDialogThemes.color(themeIndex, 0);
        String bgStyle = "-fx-background-color: " + bg + "; -fx-background: " + bg + ";";
        if (content instanceof VBox box) {
            box.setStyle(bgStyle);
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(bgStyle + " -fx-border-color: transparent; -fx-padding: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Platform.runLater(() -> applyScrollBackground(scroll, bg));
        return scroll;
    }

    private static VBox buildFunctionsPane(int themeIndex) {
        Label intro = new Label(
                "Wähle, welche Bereiche du wirklich nutzt. Ausgeschaltete Funktionen verschwinden "
                        + "aus Toolbar und Editor, bleiben aber eingerichtet. Jederzeit hier wieder einschalten.");
        intro.setWrapText(true);
        intro.getStyleClass().add("dialog-label");

        GridPane cards = new GridPane();
        cards.setHgap(24);
        cards.setVgap(4);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        right.setHgrow(Priority.ALWAYS);
        cards.getColumnConstraints().addAll(left, right);
        int index = 0;
        for (FeaturePack pack : FeaturePack.values()) {
            cards.add(buildFeatureCard(pack, cards), index % 2, index / 2);
            index++;
        }
        applyAiLock(cards, FeaturePacks.isStoredEnabled(FeaturePack.AI));

        VBox root = new VBox(10, intro, new Separator(), cards);
        root.setPadding(new Insets(12, 16, 12, 16));
        EditorDialogThemes.applyToNode(root, themeIndex);
        return root;
    }

    private static Node buildFeatureCard(FeaturePack pack, Pane cards) {
        CheckBox enabled = new CheckBox(pack.title());
        enabled.setSelected(FeaturePacks.isStoredEnabled(pack));
        enabled.setWrapText(true);
        enabled.getStyleClass().add("dialog-title");
        enabled.selectedProperty().addListener((obs, was, now) -> {
            FeaturePacks.setEnabled(pack, Boolean.TRUE.equals(now));
            if (pack == FeaturePack.AI) {
                applyAiLock(cards, Boolean.TRUE.equals(now));
            }
        });

        Label summary = new Label(pack.summary());
        summary.setWrapText(true);
        summary.getStyleClass().add("dialog-label");
        summary.setStyle("-fx-font-weight: bold;");

        Label details = new Label(pack.details());
        details.setWrapText(true);
        details.setMaxWidth(500);
        details.getStyleClass().add("dialog-label");
        details.setStyle("-fx-opacity: 0.9; -fx-font-size: 12px;");

        VBox card = new VBox(4, enabled, summary, details);
        card.setPadding(new Insets(4, 8, 8, 0));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getProperties().put("featurePack", pack);
        card.getProperties().put("featureCheck", enabled);
        return card;
    }

    private static VBox buildPluginsPane(int themeIndex) {
        File catalogDir = PluginCatalog.catalogDirectory();
        String catalogPath = catalogDir != null ? catalogDir.getAbsolutePath() : "plugin-catalog";

        Label intro = new Label(
                "Eigene Plugins als JAR in diesen Ordner legen:");
        intro.setWrapText(true);
        intro.getStyleClass().add("dialog-label");

        TextField pathField = new TextField(catalogPath);
        pathField.setEditable(false);
        pathField.setFocusTraversable(false);
        pathField.getStyleClass().add("dialog-label");

        Label hint = new Label(
                "Anleitung zum Bauen: Manuskript-Git, Datei plugins/README.md. "
                        + "Kurzbeschreibung liegt in diesem Ordner als README.md.\n"
                        + "Unten mitgelieferte Plugins ein- oder ausschalten "
                        + "(an = nach plugins/ kopieren, aus = aus plugins/ entfernen).");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-label");
        hint.setStyle("-fx-font-size: 12px; -fx-opacity: 0.9;");

        VBox list = new VBox(8);
        List<PluginCatalog.Entry> entries = PluginCatalog.list();
        if (entries.isEmpty()) {
            Label empty = new Label("Noch keine Plugin-JARs in diesem Ordner.");
            empty.setWrapText(true);
            empty.getStyleClass().add("dialog-label");
            list.getChildren().add(empty);
        } else {
            for (PluginCatalog.Entry entry : entries) {
                list.getChildren().add(buildPluginRow(entry));
            }
        }

        VBox root = new VBox(12, intro, pathField, hint, new Separator(), list);
        root.setPadding(new Insets(16));
        EditorDialogThemes.applyToNode(root, themeIndex);
        return root;
    }

    private static Node buildPluginRow(PluginCatalog.Entry entry) {
        CheckBox enabled = new CheckBox(entry.displayLabel());
        enabled.setSelected(entry.enabled());
        enabled.setWrapText(true);
        enabled.getStyleClass().add("dialog-title");

        Label detail = new Label(entry.enabled()
                ? "Aktiv — liegt in plugins/" + entry.fileName()
                : "Aus — nur im Katalog (" + entry.fileName() + ")");
        detail.setWrapText(true);
        detail.getStyleClass().add("dialog-label");
        detail.setStyle("-fx-font-size: 12px; -fx-opacity: 0.9;");

        enabled.selectedProperty().addListener((obs, was, now) -> {
            try {
                PluginCatalog.setEnabled(entry, Boolean.TRUE.equals(now));
                detail.setText(Boolean.TRUE.equals(now)
                        ? "Aktiv — liegt in plugins/" + entry.fileName()
                        : "Aus — nur im Katalog (" + entry.fileName() + ")");
            } catch (RuntimeException ex) {
                enabled.setSelected(was);
                detail.setText("Fehler: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            }
        });

        VBox row = new VBox(4, enabled, detail);
        row.setPadding(new Insets(4, 8, 8, 0));
        return row;
    }

    private static void applyAiLock(Pane cards, boolean aiOn) {
        if (cards == null) {
            return;
        }
        for (Node node : cards.getChildren()) {
            if (!(node instanceof VBox card)) {
                continue;
            }
            Object packObj = card.getProperties().get("featurePack");
            Object checkObj = card.getProperties().get("featureCheck");
            if (!(packObj instanceof FeaturePack pack) || !(checkObj instanceof CheckBox box)) {
                continue;
            }
            if (pack.requiresAi() && pack != FeaturePack.AI) {
                box.setDisable(!aiOn);
            }
        }
    }

    private static VBox buildLaunchersPane(Window owner, int themeIndex) {
        Label intro = new Label(
                "Externe Programme als Button in der Haupt-Toolbar. "
                        + "Sie laufen getrennt von Manuskript. Platzhalter in den Argumenten: "
                        + "{projectRoot}, {configDir}, {chapterFile}.");
        intro.setWrapText(true);
        intro.getStyleClass().add("dialog-label");

        ListView<ProgramLauncher> list = new ListView<>();
        list.setPrefHeight(160);
        list.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ProgramLauncher item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayLabel());
            }
        });
        reloadLaunchers(list);

        Button add = new Button("Hinzufügen…");
        add.getStyleClass().add("dialog-button");
        add.setOnAction(e -> editLauncher(owner, themeIndex, null, () -> reloadLaunchers(list)));
        Button edit = new Button("Bearbeiten…");
        edit.getStyleClass().add("dialog-button");
        edit.setOnAction(e -> {
            ProgramLauncher selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editLauncher(owner, themeIndex, selected, () -> reloadLaunchers(list));
            }
        });
        Button remove = new Button("Entfernen");
        remove.getStyleClass().add("dialog-button");
        remove.setOnAction(e -> {
            ProgramLauncher selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            List<ProgramLauncher> all = ProgramLauncherStore.load();
            all.removeIf(item -> selected.getId() != null && selected.getId().equals(item.getId()));
            ProgramLauncherStore.save(all);
            reloadLaunchers(list);
        });
        HBox row = new HBox(10, add, edit, remove);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, intro, list, row);
        root.setPadding(new Insets(16));
        EditorDialogThemes.applyToNode(root, themeIndex);
        return root;
    }

    private static void reloadLaunchers(ListView<ProgramLauncher> list) {
        list.getItems().setAll(ProgramLauncherStore.load());
    }

    private static void editLauncher(Window owner, int themeIndex, ProgramLauncher existing, Runnable onSaved) {
        CustomStage dialog = StageManager.createModalStage(
                existing == null ? "Programm hinzufügen" : "Programm bearbeiten", owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setWidth(560);
        dialog.setHeight(420);
        dialog.setTitleBarTheme(themeIndex);

        TextField nameField = new TextField(existing != null ? nullToEmpty(existing.getLabel()) : "");
        nameField.setPromptText("Anzeigename (Toolbar)");
        TextField pathField = new TextField(existing != null ? nullToEmpty(existing.getPath()) : "");
        pathField.setPromptText("Programm, .jar oder Plugin");
        TextField argsField = new TextField(existing != null ? nullToEmpty(existing.getArguments()) : "");
        argsField.setPromptText("z. B. --config-dir={configDir}");

        ComboBox<File> pluginChoice = new ComboBox<>();
        pluginChoice.setMaxWidth(Double.MAX_VALUE);
        pluginChoice.setPromptText("Mitgeliefertes Plugin wählen…");
        List<File> bundled = bundledPluginFiles();
        pluginChoice.getItems().addAll(bundled);
        pluginChoice.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(File file) {
                return file == null ? "" : pluginDisplayName(file);
            }

            @Override
            public File fromString(String string) {
                return null;
            }
        });
        pluginChoice.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : pluginDisplayName(item));
            }
        });
        pluginChoice.setOnAction(e -> {
            File chosen = pluginChoice.getValue();
            if (chosen == null) {
                return;
            }
            pathField.setText(chosen.getAbsolutePath());
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                nameField.setText(pluginDisplayName(chosen));
            }
        });
        pluginChoice.setVisible(!bundled.isEmpty());
        pluginChoice.setManaged(!bundled.isEmpty());

        Button browse = new Button("Datei…");
        browse.getStyleClass().add("dialog-button");
        browse.setOnAction(e -> chooseLauncherFile(chosen -> {
            if (chosen == null) {
                return;
            }
            pathField.setText(chosen.getAbsolutePath());
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                nameField.setText(pluginDisplayName(chosen));
            }
        }));
        HBox pathRow = new HBox(8, pathField, browse);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label("Name");
        Label pluginLbl = new Label("Mitgelieferte Plugins");
        Label pathLbl = new Label("Datei / Befehl");
        Label argsLbl = new Label("Argumente (optional)");
        nameLbl.getStyleClass().add("dialog-label");
        pluginLbl.getStyleClass().add("dialog-label");
        pathLbl.getStyleClass().add("dialog-label");
        argsLbl.getStyleClass().add("dialog-label");
        pluginLbl.setVisible(!bundled.isEmpty());
        pluginLbl.setManaged(!bundled.isEmpty());

        Button save = new Button("Speichern");
        save.getStyleClass().add("dialog-button");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String path = pathField.getText() == null ? "" : pathField.getText().trim();
            if (path.isEmpty()) {
                return;
            }
            List<ProgramLauncher> all = ProgramLauncherStore.load();
            ProgramLauncher item = existing != null ? existing : new ProgramLauncher();
            if (item.getId() == null || item.getId().isBlank()) {
                item.setId(ProgramLauncherStore.newId());
            }
            item.setLabel(nameField.getText());
            item.setPath(path);
            item.setArguments(argsField.getText());
            boolean replaced = false;
            for (int i = 0; i < all.size(); i++) {
                if (item.getId().equals(all.get(i).getId())) {
                    all.set(i, item);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                all.add(item);
            }
            ProgramLauncherStore.save(all);
            dialog.close();
            if (onSaved != null) {
                onSaved.run();
            }
        });
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("dialog-button");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> dialog.close());
        HBox actions = new HBox(10, save, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(8, nameLbl, nameField, pluginLbl, pluginChoice, pathLbl, pathRow, argsLbl, argsField, actions);
        root.setPadding(new Insets(16));
        String bg = EditorDialogThemes.color(themeIndex, 0);
        root.setStyle("-fx-background-color: " + bg + ";");
        root.getStyleClass().add("dialog-container");
        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        EditorDialogThemes.applyToNode(root, themeIndex);
        dialog.setSceneWithTitleBar(scene);
        dialog.setFullTheme(themeIndex);
        dialog.show();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static File pluginsDirectory() {
        File catalog = ApplicationPaths.resolvePluginCatalogDirectory();
        if (catalog.isDirectory()) {
            return catalog;
        }
        File bundled = ApplicationPaths.resolvePluginsDirectory();
        if (bundled.isDirectory()) {
            return bundled;
        }
        File repo = new File(System.getProperty("user.dir", "."), "plugins");
        if (repo.isDirectory()) {
            return repo;
        }
        File cwd = new File(System.getProperty("user.dir", "."));
        return cwd.isDirectory() ? cwd : bundled;
    }

    private static void chooseLauncherFile(Consumer<File> onChosen) {
        File startDir = pluginsDirectory();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            java.awt.EventQueue.invokeLater(() -> {
                java.awt.FileDialog dialog = new java.awt.FileDialog(
                        (java.awt.Frame) null, "Programm wählen", java.awt.FileDialog.LOAD);
                if (startDir != null && startDir.isDirectory()) {
                    dialog.setDirectory(startDir.getAbsolutePath());
                }
                dialog.setVisible(true);
                String name = dialog.getFile();
                String directory = dialog.getDirectory();
                File chosen = (name == null || directory == null) ? null : new File(directory, name);
                Platform.runLater(() -> onChosen.accept(chosen));
            });
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Programm wählen");
        if (startDir != null && startDir.isDirectory()) {
            chooser.setInitialDirectory(startDir);
        }
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Programme und Plugins",
                        "*.jar", "*.sh", "*.bat", "*.exe", "*.app", "*.command"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
        File chosen = chooser.showOpenDialog(null);
        onChosen.accept(chosen);
    }

    private static List<File> bundledPluginFiles() {
        List<File> out = new ArrayList<>();
        File openRouter = findOpenRouterLauncher();
        if (openRouter != null) {
            out.add(openRouter);
        }
        File appPlugins = new File(ApplicationPaths.getApplicationHomeDirectory(), "plugins");
        File[] extra = appPlugins.isDirectory() ? appPlugins.listFiles(File::isFile) : null;
        addExtraPluginLaunchers(out, extra, openRouter);
        File appCatalog = ApplicationPaths.resolvePluginCatalogDirectory();
        if (appCatalog.isDirectory() && !appCatalog.getAbsoluteFile().equals(appPlugins.getAbsoluteFile())) {
            addExtraPluginLaunchers(out, appCatalog.listFiles(File::isFile), openRouter);
        }
        File repoPlugins = new File(System.getProperty("user.dir", "."), "plugins");
        if (!repoPlugins.getAbsoluteFile().equals(appPlugins.getAbsoluteFile())) {
            addExtraPluginLaunchers(out, repoPlugins.isDirectory() ? repoPlugins.listFiles(File::isFile) : null, openRouter);
        }
        File repoCatalog = new File(System.getProperty("user.dir", "."), "plugin-catalog");
        if (repoCatalog.isDirectory() && !repoCatalog.getAbsoluteFile().equals(appCatalog.getAbsoluteFile())) {
            addExtraPluginLaunchers(out, repoCatalog.listFiles(File::isFile), openRouter);
        }
        return out;
    }

    private static void addExtraPluginLaunchers(List<File> out, File[] extra, File openRouter) {
        if (extra == null) {
            return;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (File file : extra) {
            if (!isLaunchScript(file, windows)) {
                continue;
            }
            if (isBundledInProcessPlugin(file)) {
                continue;
            }
            File abs = file.getAbsoluteFile();
            boolean duplicate = false;
            for (File existing : out) {
                if (existing.getName().equalsIgnoreCase(abs.getName())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(abs);
            }
        }
    }

    private static File findOpenRouterLauncher() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String script = windows ? "run-openrouter-monitor.bat" : "run-openrouter-monitor.sh";
        File cwd = new File(System.getProperty("user.dir", "."));
        File[] candidates = {
                new File(ApplicationPaths.resolvePluginCatalogDirectory(), script),
                new File(ApplicationPaths.getApplicationHomeDirectory(), "plugins" + File.separator + script),
                new File(cwd, "plugin-catalog" + File.separator + script),
                new File(cwd, "plugins" + File.separator + script),
                new File(cwd, script),
                new File(cwd, "tools/openrouter-monitor/packaged/" + script)
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate.getAbsoluteFile();
            }
        }
        return null;
    }

    private static boolean isBundledInProcessPlugin(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.startsWith("run-openrouter-monitor")
                || lower.startsWith("openrouter-monitor")
                || lower.startsWith("run-mammouth-monitor")
                || lower.startsWith("mammouth-monitor");
    }

    private static boolean isLaunchScript(File file, boolean windows) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.startsWith(".")) {
            return false;
        }
        if (windows) {
            return lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".exe");
        }
        return lower.endsWith(".sh") || lower.endsWith(".command");
    }

    private static String pluginDisplayName(File file) {
        if (file == null) {
            return "";
        }
        String name = file.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("run-openrouter-monitor") || lower.startsWith("openrouter-monitor")) {
            return "OpenRouter-Monitor";
        }
        if (lower.startsWith("run-mammouth-monitor") || lower.startsWith("mammouth-monitor")) {
            return "Mammouth-Monitor";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Node buildRow(SetupItem item, Window setupStage, int themeIndex, Runnable refreshUi) {
        Label row = new Label(item.statusIcon() + "  " + item.label() + " — " + item.detail());
        row.setWrapText(true);
        row.setMaxWidth(560);
        HBox.setHgrow(row, Priority.ALWAYS);
        row.getStyleClass().add("dialog-label");
        if ("ok".equals(item.level())) {
            row.getStyleClass().add("setup-check-ok");
        } else if ("warn".equals(item.level())) {
            row.getStyleClass().add("setup-check-warn");
        }

        HBox box = new HBox(10, row);
        box.setAlignment(Pos.CENTER_LEFT);

        boolean offerSwitch = item.offerOllamaBackendSwitch();
        CheckBox switchToOllama = offerSwitch ? createOllamaBackendSwitchCheckbox() : null;
        Label switchHint = offerSwitch ? createOllamaBackendSwitchHint() : null;

        if (item.toolId() != null && item.needsAction()) {
            Button action = new Button(item.actionLabel());
            action.getStyleClass().add("dialog-button");
            boolean installOllama = item.installOllama();
            action.setOnAction(e -> runInstallDialog(
                    setupStage,
                    themeIndex,
                    item.toolId(),
                    item.actionLabel(),
                    installOllama,
                    switchToOllama != null && switchToOllama.isSelected(),
                    refreshUi));
            box.getChildren().add(action);
        }

        if (!offerSwitch) {
            return box;
        }
        VBox extras = new VBox(6, switchToOllama, switchHint);
        extras.setPadding(new Insets(0, 0, 4, 28));
        return new VBox(6, box, extras);
    }

    private static CheckBox createOllamaBackendSwitchCheckbox() {
        CheckBox box = new CheckBox("In den Parametern auf Ollama umschalten (statt OpenAI)");
        box.setSelected(true);
        box.setWrapText(true);
        box.setMaxWidth(680);
        box.getStyleClass().add("dialog-label");
        return box;
    }

    private static Label createOllamaBackendSwitchHint() {
        Label hint = new Label(
                "Hinweis: agent.backend steht aktuell auf OpenAI. Ohne Umschalten nutzen die Agenten "
                        + "weiter die Cloud-API – auch wenn Ollama lokal installiert ist. "
                        + "Das lässt sich später unter Parameter → Agenten ändern.");
        hint.setWrapText(true);
        hint.setMaxWidth(680);
        hint.getStyleClass().add("dialog-label");
        hint.setStyle("-fx-font-weight: normal; -fx-opacity: 0.85; -fx-font-size: 12px;");
        return hint;
    }

    private static void runInstallDialog(Window setupStage, int themeIndex, ToolSetupSupport.ToolId toolId,
                                         String titleText, boolean installOllama, boolean switchBackendToOllama,
                                         Runnable onDone) {
        // Owner = Setup-Assistent (nicht Hauptfenster), sonst rutscht der Assistent nach Schließen nach hinten.
        CustomStage dialog = StageManager.createModalStage(titleText, setupStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setWidth(680);
        dialog.setHeight(440);
        dialog.setTitleBarTheme(themeIndex);
        dialog.setOnHidden(e -> focusSetupStage(setupStage));

        Label title = new Label(titleText != null ? titleText : "Einrichtung");
        title.getStyleClass().add("dialog-title");
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("dialog-textarea");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setMaxWidth(Double.MAX_VALUE);
        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("dialog-button");
        closeBtn.setDisable(true);
        closeBtn.setOnAction(e -> {
            dialog.close();
            focusSetupStage(setupStage);
        });

        AtomicBoolean switchFlag = new AtomicBoolean(switchBackendToOllama);
        VBox root = new VBox(12);
        root.getChildren().add(title);
        if (installOllama && isOpenAiAgentBackend()) {
            CheckBox dialogSwitch = createOllamaBackendSwitchCheckbox();
            dialogSwitch.setSelected(switchBackendToOllama);
            dialogSwitch.selectedProperty().addListener((obs, oldVal, selected) -> switchFlag.set(selected));
            Label dialogHint = createOllamaBackendSwitchHint();
            root.getChildren().addAll(dialogSwitch, dialogHint);
            dialog.setHeight(520);
        }
        root.getChildren().addAll(bar, logArea, closeBtn);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("dialog-container");
        String bg = EditorDialogThemes.color(themeIndex, 0);
        root.setStyle("-fx-background-color: " + bg + ";");
        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        scene.setFill(Color.web(bg));
        EditorDialogThemes.applyToNode(root, themeIndex);
        dialog.setSceneWithTitleBar(scene);
        dialog.setFullTheme(themeIndex);
        dialog.show();

        Consumer<String> log = line -> Platform.runLater(() -> {
            if (line == null) {
                return;
            }
            logArea.appendText(line);
            if (!line.endsWith("\n")) {
                logArea.appendText("\n");
            }
            logArea.setScrollTop(Double.MAX_VALUE);
        });

        if (toolId == null) {
            logArea.appendText("Kein Werkzeug gewählt.\n");
            bar.setProgress(1);
            closeBtn.setDisable(false);
            return;
        }

        AtomicBoolean done = new AtomicBoolean(false);
        CompletableFuture.supplyAsync(() -> installOllama
                        ? ToolSetupSupport.ensureOllama(log)
                        : ToolSetupSupport.install(toolId, log))
                .whenComplete((error, ex) -> Platform.runLater(() -> {
                    if (done.getAndSet(true)) {
                        return;
                    }
                    bar.setProgress(1);
                    if (ex != null) {
                        logArea.appendText("\nFehler: " + ex.getMessage() + "\n");
                        title.setText("Fehlgeschlagen");
                    } else if (error != null && !error.isBlank()) {
                        logArea.appendText("\n" + error + "\n");
                        title.setText("Hinweis");
                    } else {
                        logArea.appendText("\nFertig.\n");
                        title.setText("Bereit");
                    }
                    if (switchFlag.get()) {
                        applyOllamaBackendSwitch(line -> {
                            logArea.appendText(line);
                            if (!line.endsWith("\n")) {
                                logArea.appendText("\n");
                            }
                        });
                    }
                    closeBtn.setDisable(false);
                    if (onDone != null) {
                        onDone.run();
                    }
                    if (dialog.isShowing()) {
                        dialog.toFront();
                        dialog.requestFocus();
                    }
                }));
    }

    private static void focusSetupStage(Window setupStage) {
        if (setupStage == null) {
            return;
        }
        Platform.runLater(() -> {
            if (setupStage instanceof javafx.stage.Stage s) {
                if (s.isIconified()) {
                    s.setIconified(false);
                }
                s.toFront();
            } else {
                setupStage.requestFocus();
            }
            setupStage.requestFocus();
        });
    }

    private static void applyScrollBackground(ScrollPane scroll, String bg) {
        if (scroll == null || bg == null) {
            return;
        }
        String style = "-fx-background-color: " + bg + "; -fx-background: " + bg + ";";
        scroll.setStyle(style + " -fx-border-color: transparent; -fx-padding: 0;");
        Node viewport = scroll.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle(style);
        }
        Node corner = scroll.lookup(".corner");
        if (corner != null) {
            corner.setStyle(style);
        }
    }

    static List<SetupItem> runChecks() {
        List<SetupItem> results = new ArrayList<>();
        String os = System.getProperty("os.name", "");
        boolean mac = os.toLowerCase(Locale.ROOT).contains("mac");
        results.add(new SetupItem(
                mac ? "ok" : "warn",
                "Betriebssystem",
                os + (mac ? " (empfohlen)" : " – bitte selbst testen"),
                null, false, null));

        results.add(new SetupItem("ok", "Java",
                System.getProperty("java.version", "?") + " (" + System.getProperty("java.vendor", "") + ")",
                null, false, null));

        String pandocIssue = ToolSetupSupport.pandocStatus();
        if (pandocIssue == null) {
            var exe = ToolSetupSupport.resolvePandocBinary();
            results.add(new SetupItem("ok", "Pandoc (Export)",
                    exe != null ? exe.getAbsolutePath() : "im PATH",
                    ToolSetupSupport.ToolId.PANDOC, false, "Entpacken"));
        } else {
            results.add(new SetupItem("warn", "Pandoc (Export)", pandocIssue,
                    ToolSetupSupport.ToolId.PANDOC, true, "Entpacken"));
        }

        String ffmpegIssue = ToolSetupSupport.ffmpegStatus();
        if (ffmpegIssue == null) {
            var exe = ToolSetupSupport.resolveFfmpegBinary(false);
            results.add(new SetupItem("ok", "FFmpeg (Hörbuch)",
                    exe != null ? exe.getAbsolutePath() : "im PATH",
                    ToolSetupSupport.ToolId.FFMPEG, false, "Entpacken"));
        } else {
            results.add(new SetupItem("warn", "FFmpeg (Hörbuch)", ffmpegIssue,
                    ToolSetupSupport.ToolId.FFMPEG, true, "Entpacken"));
        }

        String whisperIssue = ToolSetupSupport.whisperStatus();
        if (whisperIssue == null) {
            results.add(new SetupItem("ok", "Diktat (lokales Whisper)",
                    "whisper-cli und Modell gefunden",
                    ToolSetupSupport.ToolId.WHISPER, false, "Einrichten"));
        } else {
            results.add(new SetupItem("warn", "Diktat (lokales Whisper)", whisperIssue,
                    ToolSetupSupport.ToolId.WHISPER, true, "Einrichten"));
        }

        String agentBackend = ResourceManager.getParameter("agent.backend", "Ollama");
        boolean openAiBackend = "OpenAI".equalsIgnoreCase(agentBackend);
        if (openAiBackend) {
            String key = ResourceManager.getParameter("agent.openai.api_key", "");
            boolean ok = key != null && !key.isBlank();
            results.add(new SetupItem(
                    ok ? "ok" : "warn",
                    "KI-Agenten (OpenAI-kompatibel)",
                    ok ? "API-Key gesetzt" : "Kein agent.openai.api_key",
                    ToolSetupSupport.ToolId.KI, !ok, "Hinweis"));
        }
        boolean ollamaUp = ToolSetupSupport.httpReachable("http://127.0.0.1:11434/api/tags");
        if (ollamaUp) {
            results.add(new SetupItem(
                    openAiBackend ? "warn" : "ok",
                    "KI-Agenten (Ollama)",
                    openAiBackend
                            ? "Erreichbar – in den Parametern steht aber OpenAI"
                            : "Erreichbar",
                    ToolSetupSupport.ToolId.KI,
                    openAiBackend,
                    openAiBackend ? "Auf Ollama umschalten" : "Einrichten",
                    true,
                    openAiBackend));
        } else {
            results.add(new SetupItem(
                    "warn",
                    "KI-Agenten (Ollama)",
                    "Nicht erreichbar – installieren/starten",
                    ToolSetupSupport.ToolId.KI,
                    true,
                    "Einrichten",
                    true,
                    openAiBackend));
        }

        String ltJar = ToolSetupSupport.languageToolJarStatus();
        boolean ltUp = ToolSetupSupport.httpReachable("http://localhost:8081/v2/languages");
        if (ltJar != null) {
            results.add(new SetupItem("warn", "LanguageTool", ltJar,
                    ToolSetupSupport.ToolId.LANGUAGE_TOOL, false, "Starten"));
        } else if (ltUp) {
            results.add(new SetupItem("ok", "LanguageTool", "Server erreichbar",
                    ToolSetupSupport.ToolId.LANGUAGE_TOOL, false, "Starten"));
        } else {
            results.add(new SetupItem("warn", "LanguageTool",
                    "JAR vorhanden, Server nicht gestartet",
                    ToolSetupSupport.ToolId.LANGUAGE_TOOL, true, "Starten"));
        }

        return results;
    }

    record SetupItem(String level, String label, String detail, ToolSetupSupport.ToolId toolId,
                     boolean needsAction, String actionLabel,
                     boolean installOllama, boolean offerOllamaBackendSwitch) {
        SetupItem(String level, String label, String detail, ToolSetupSupport.ToolId toolId,
                  boolean needsAction, String actionLabel) {
            this(level, label, detail, toolId, needsAction, actionLabel, false, false);
        }

        String statusIcon() {
            return switch (level) {
                case "ok" -> "✓";
                case "warn" -> "!";
                default -> "·";
            };
        }
    }

    private static boolean isOpenAiAgentBackend() {
        return "OpenAI".equalsIgnoreCase(ResourceManager.getParameter("agent.backend", "Ollama"));
    }

    private static void applyOllamaBackendSwitch(Consumer<String> log) {
        ResourceManager.saveParameter("agent.backend", "Ollama");
        try {
            ApplicationPreferences.resourceManagerNode().flush();
        } catch (Exception e) {
            if (log != null) {
                log.accept("Hinweis: Parameter gespeichert, Flush fehlgeschlagen: " + e.getMessage());
            }
        }
        com.manuskript.agent.AgentConfigManager.invalidateCache();
        MainController.notifyOpenEditorsAgentParametersChanged();
        if (log != null) {
            log.accept("Parameter agent.backend auf Ollama gesetzt (statt OpenAI).");
        }
    }
}
