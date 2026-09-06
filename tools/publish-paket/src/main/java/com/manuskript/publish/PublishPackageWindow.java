package com.manuskript.publish;

import com.manuskript.plugin.PluginHost;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fenster: Checkliste, Metadaten, Cover, Keywords, Klappentext, Hilfe.
 */
public final class PublishPackageWindow {

    private final PluginHost host;
    private Stage stage;
    private PublishPackageModel model;
    private Path projectRoot;

    private Label summaryLabel;
    private VBox checklistBox;
    private Label statusLabel;

    private TextField titleField;
    private TextField subtitleField;
    private TextField authorField;
    private TextField isbnField;
    private TextField languageField;
    private TextField ageField;
    private CheckBox rightsBox;
    private CheckBox impressumBox;

    private TextField coverPathField;
    private Label coverInfoLabel;

    private final List<TextField> keywordFields = new ArrayList<>();
    private final List<Label> keywordCounters = new ArrayList<>();

    private TextArea blurbArea;
    private Label blurbCounter;
    private TextArea blurbHintsArea;
    private CheckBox blurbToExportBox;
    private Button generateBlurbButton;
    private volatile boolean generatingBlurb;
    private TextArea authorHintsArea;
    private Button generateKeywordsButton;
    private volatile boolean generatingKeywords;

    public PublishPackageWindow(PluginHost host) {
        this.host = host;
    }

    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            reload();
            return;
        }
        stage = host.createThemedStage("Publish-Paket");
        host.attachScene(stage, new Scene(buildUi(), 820, 620));
        stage.show();
        reload();
    }

    private VBox buildUi() {
        Label intro = new Label(
                "Vorbereitung für Amazon KDP eBook und Tolino Media — Checkliste, Cover, Keywords und Klappentext. "
                        + "Kein Upload; Daten in data/publish_package.json.");
        intro.setWrapText(true);
        intro.getStyleClass().add("dialog-label");

        Button refresh = button("Aktualisieren", e -> reload());
        Button save = button("Speichern", e -> save());
        HBox actions = new HBox(8, refresh, save);
        actions.setAlignment(Pos.CENTER_LEFT);

        TabPane tabs = new TabPane(
                tab("Übersicht", overviewTab()),
                tab("Buchdaten", metadataTab()),
                tab("Cover", coverTab()),
                tab("Keywords", keywordsTab()),
                tab("Klappentext", blurbTab()),
                tab("Hilfe", helpTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("dialog-label");

        VBox root = new VBox(10, intro, actions, tabs, statusLabel);
        root.setPadding(new Insets(16));
        return root;
    }

    private VBox overviewTab() {
        summaryLabel = new Label("—");
        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("dialog-title");

        checklistBox = new VBox(6);
        ScrollPane scroll = new ScrollPane(checklistBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button copySummary = button("Zusammenfassung kopieren", e -> copySummary());
        return new VBox(10, summaryLabel, scroll, copySummary);
    }

    private VBox metadataTab() {
        titleField = field();
        subtitleField = field();
        authorField = field();
        isbnField = field();
        languageField = field();
        ageField = field();
        rightsBox = new CheckBox("Ich besitze die Veröffentlichungsrechte an Text und Bildern");
        impressumBox = new CheckBox("Impressum / Pflichtangaben für den Kanal notiert");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int r = 0;
        addRow(grid, r++, "Titel", titleField);
        addRow(grid, r++, "Untertitel", subtitleField);
        addRow(grid, r++, "Autor", authorField);
        addRow(grid, r++, "ISBN (optional)", isbnField);
        addRow(grid, r++, "Sprache", languageField);
        addRow(grid, r++, "Altershinweis", ageField);

        Button apply = button("In Checkliste übernehmen", e -> {
            pullFromUi();
            refreshChecklist();
            setStatus("Buchdaten übernommen (noch nicht gespeichert).");
        });

        VBox box = new VBox(12, grid, rightsBox, impressumBox, apply);
        box.setPadding(new Insets(8));
        return box;
    }

    private VBox coverTab() {
        coverPathField = field();
        HBox pathRow = new HBox(8, coverPathField, button("…", e -> browseCover()));
        HBox.setHgrow(coverPathField, Priority.ALWAYS);

        coverInfoLabel = new Label("—");
        coverInfoLabel.setWrapText(true);
        coverInfoLabel.getStyleClass().add("dialog-label");

        Button analyze = button("Cover prüfen", e -> {
            pullFromUi();
            refreshChecklist();
            updateCoverInfo();
        });

        Label hint = new Label(
                "KDP empfohlen: 1600×2560 px (B×H), Ratio ~1,6:1, Upload oft JPEG/TIFF.\n"
                        + "Tolino: Hochformat, Breite ≥ 1600 px, Datei möglichst ≤ 5 MB.");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-label");

        return new VBox(10,
                labeled("Cover-Pfad", pathRow),
                analyze,
                coverInfoLabel,
                hint);
    }

    private VBox keywordsTab() {
        Label hintsLabel = new Label(
                "Besondere Vorgaben (z. B. „LitRPG“, „Space Opera“, „langsamer Slow-Burn“, Zielgruppe). "
                        + "Die KI nutzt außerdem Klappentext und Welt-Editor-Dateien.");
        hintsLabel.setWrapText(true);
        hintsLabel.getStyleClass().add("dialog-label");

        authorHintsArea = new TextArea();
        authorHintsArea.setWrapText(true);
        authorHintsArea.setPrefRowCount(3);
        authorHintsArea.setPromptText("Optional: Genre, Tropes, Suchsprache, Was Leser eingeben würden …");

        generateKeywordsButton = button("Keywords per KI erzeugen", e -> generateKeywords());
        Button copy = button("Keywords kopieren", e -> copyKeywords());
        HBox actions = new HBox(8, generateKeywordsButton, copy);

        keywordFields.clear();
        keywordCounters.clear();
        VBox rows = new VBox(8);
        for (int i = 0; i < PlatformRules.KDP_KEYWORD_SLOTS; i++) {
            TextField kw = field();
            Label counter = new Label("0 / " + PlatformRules.KDP_KEYWORD_MAX_CHARS);
            counter.getStyleClass().add("dialog-label");
            final int index = i;
            kw.textProperty().addListener((obs, o, n) -> updateKeywordCounter(index));
            keywordFields.add(kw);
            keywordCounters.add(counter);
            HBox row = new HBox(8, kw, counter);
            HBox.setHgrow(kw, Priority.ALWAYS);
            rows.getChildren().add(labeled("Keyword " + (i + 1), row));
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label hint = new Label(
                "KDP: sieben Suchphrasen (nicht einzelne Wörter), je ≤ 50 Zeichen. "
                        + "Keine Wiederholung von Titelwörtern, keine Claims wie „Bestseller“.");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-label");
        return new VBox(10, hintsLabel, authorHintsArea, actions, scroll, hint);
    }

    private void generateKeywords() {
        if (generatingKeywords) {
            return;
        }
        pullFromUi();
        if (projectRoot == null) {
            setStatus("Kein Projekt — Keywords brauchen den aktuellen Buchordner.");
            return;
        }
        generatingKeywords = true;
        if (generateKeywordsButton != null) {
            generateKeywordsButton.setDisable(true);
        }
        setStatus("Keywords werden erzeugt (Agenten-Backend) …");

        String system = KeywordAiSupport.systemPrompt();
        String user = KeywordAiSupport.userPrompt(model, projectRoot, model.authorHints);
        host.completeChat(system, user, 4096).whenComplete((raw, error) -> Platform.runLater(() -> {
            generatingKeywords = false;
            if (generateKeywordsButton != null) {
                generateKeywordsButton.setDisable(false);
            }
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                setStatus("KI fehlgeschlagen: "
                        + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
                return;
            }
            List<String> phrases = KeywordAiSupport.parseKeywords(raw);
            if (phrases.isEmpty()) {
                setStatus("KI lieferte keine brauchbaren Phrasen. Vorgaben prüfen oder erneut versuchen.");
                return;
            }
            model.ensureKeywordSlots();
            for (int i = 0; i < PlatformRules.KDP_KEYWORD_SLOTS; i++) {
                String phrase = i < phrases.size() ? phrases.get(i) : "";
                model.setKeyword(i, phrase);
                if (i < keywordFields.size()) {
                    keywordFields.get(i).setText(phrase);
                    updateKeywordCounter(i);
                }
            }
            refreshChecklist();
            setStatus(phrases.size() + " Keyword-Phrasen übernommen — prüfen und speichern.");
        }));
    }

    private VBox blurbTab() {
        Label hintsLabel = new Label(
                "Optional: Stil, Genre, Zielgruppe, was betont werden soll. "
                        + "Autor-Hinweise aus dem Keywords-Tab werden ebenfalls genutzt.");
        hintsLabel.setWrapText(true);
        hintsLabel.getStyleClass().add("dialog-label");

        blurbHintsArea = new TextArea();
        blurbHintsArea.setWrapText(true);
        blurbHintsArea.setPrefRowCount(3);
        blurbHintsArea.setPromptText("z. B. Space Opera, düster, Fokus auf Crew-Dynamik, kein Endspoiler …");

        generateBlurbButton = button("Klappentext per KI erzeugen", e -> generateBlurb());

        blurbToExportBox = new CheckBox("Klappentext in Buch-Export übernehmen (data/pandoc_metadata.json)");
        blurbToExportBox.setSelected(true);
        blurbToExportBox.setWrapText(true);

        blurbArea = new TextArea();
        blurbArea.setWrapText(true);
        blurbArea.setPrefRowCount(14);
        VBox.setVgrow(blurbArea, Priority.ALWAYS);
        blurbCounter = new Label("0 / " + PlatformRules.KDP_DESCRIPTION_MAX_CHARS);
        blurbCounter.getStyleClass().add("dialog-label");
        blurbArea.textProperty().addListener((obs, o, n) -> updateBlurbCounter());

        Button copy = button("Klappentext kopieren", e -> {
            pullFromUi();
            copyText(model.blurb == null ? "" : model.blurb);
            setStatus("Klappentext in die Zwischenablage kopiert.");
        });
        Label hint = new Label(
                "KDP Description: bis 4000 Zeichen (inkl. einfachem HTML wie <b>, <i>, <br>). "
                        + "Plaintext oder HTML speichern und bei Bedarf im Browser formatieren.");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-label");
        return new VBox(10, hintsLabel, blurbHintsArea, generateBlurbButton, blurbToExportBox, blurbArea, blurbCounter, copy, hint);
    }

    private void generateBlurb() {
        if (generatingBlurb) {
            return;
        }
        pullFromUi();
        if (projectRoot == null) {
            setStatus("Kein Projekt — Klappentext braucht den aktuellen Buchordner.");
            return;
        }
        generatingBlurb = true;
        if (generateBlurbButton != null) {
            generateBlurbButton.setDisable(true);
        }
        setStatus("Klappentext wird erzeugt (Agenten-Backend) …");

        String system = BlurbAiSupport.systemPrompt();
        String user = BlurbAiSupport.userPrompt(model, projectRoot, model.blurbHints);
        host.completeChat(system, user, 4096).whenComplete((raw, error) -> Platform.runLater(() -> {
            generatingBlurb = false;
            if (generateBlurbButton != null) {
                generateBlurbButton.setDisable(false);
            }
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                setStatus("KI fehlgeschlagen: "
                        + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
                return;
            }
            String blurb = BlurbAiSupport.parseBlurb(raw);
            if (blurb.isBlank()) {
                setStatus("KI lieferte keinen Klappentext — erneut versuchen oder Vorgaben prüfen.");
                return;
            }
            model.blurb = blurb;
            if (blurbArea != null) {
                blurbArea.setText(blurb);
            }
            updateBlurbCounter();
            refreshChecklist();
            syncBlurbIfEnabled("Klappentext übernommen (" + blurb.length() + " Zeichen)");
        }));
    }

    private void syncBlurbIfEnabled(String successPrefix) {
        if (model == null || !model.exportBlurb() || projectRoot == null) {
            if (successPrefix != null) {
                setStatus(successPrefix + " — prüfen und speichern.");
            }
            return;
        }
        try {
            PublishPackageStore.syncBlurbToExport(projectRoot, model);
            setStatus(successPrefix + " — auch in Export-Metadaten (abstract) übernommen.");
        } catch (Exception e) {
            setStatus(successPrefix + " — Export-Metadaten konnten nicht geschrieben werden: " + e.getMessage());
        }
    }

    private VBox helpTab() {
        Button kdp = button("KDP öffnen", e -> host.openInBrowser(PlatformRules.HELP_KDP_HOME));
        Button cover = button("KDP Cover-Hilfe", e -> host.openInBrowser(PlatformRules.HELP_KDP_COVER));
        Button keywords = button("KDP Keywords-Hilfe", e -> host.openInBrowser(PlatformRules.HELP_KDP_KEYWORDS));
        Button tolino = button("Tolino Media", e -> host.openInBrowser(PlatformRules.HELP_TOLINO));
        Label note = new Label(
                "Offene Links im Systembrowser. Export des Manuskripts weiterhin über „Buch exportieren“ / Pandoc.");
        note.setWrapText(true);
        note.getStyleClass().add("dialog-label");
        return new VBox(10, kdp, cover, keywords, tolino, note);
    }

    private void reload() {
        Optional<Path> root = host.projectRoot();
        if (root.isEmpty()) {
            model = new PublishPackageModel();
            projectRoot = null;
            pushToUi();
            refreshChecklist();
            setStatus("Kein Projekt geöffnet.");
            return;
        }
        projectRoot = root.get();
        model = PublishPackageStore.loadOrSeed(projectRoot);
        pushToUi();
        refreshChecklist();
        updateCoverInfo();
        setStatus("Projekt: " + projectRoot.getFileName());
    }

    private void save() {
        if (projectRoot == null) {
            setStatus("Kein Projekt — Speichern nicht möglich.");
            return;
        }
        pullFromUi();
        try {
            PublishPackageStore.save(projectRoot, model);
            if (model.exportBlurb()) {
                PublishPackageStore.syncBlurbToExport(projectRoot, model);
                setStatus("Gespeichert: data/publish_package.json — Klappentext in Export-Metadaten übernommen.");
            } else {
                setStatus("Gespeichert: data/publish_package.json");
            }
            refreshChecklist();
        } catch (Exception e) {
            setStatus("Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private void pullFromUi() {
        if (model == null) {
            model = new PublishPackageModel();
        }
        model.title = text(titleField);
        model.subtitle = text(subtitleField);
        model.author = text(authorField);
        model.isbn = text(isbnField);
        model.language = text(languageField);
        model.ageRating = text(ageField);
        model.rightsConfirmed = rightsBox != null && rightsBox.isSelected();
        model.impressumNoted = impressumBox != null && impressumBox.isSelected();
        model.coverPath = text(coverPathField);
        model.blurb = blurbArea == null ? "" : blurbArea.getText();
        model.blurbHints = blurbHintsArea == null ? "" : blurbHintsArea.getText();
        model.blurbToExport = blurbToExportBox == null || blurbToExportBox.isSelected();
        model.authorHints = authorHintsArea == null ? "" : authorHintsArea.getText();
        model.ensureKeywordSlots();
        for (int i = 0; i < keywordFields.size(); i++) {
            model.setKeyword(i, keywordFields.get(i).getText());
        }
    }

    private void pushToUi() {
        if (model == null) {
            model = new PublishPackageModel();
        }
        model.ensureKeywordSlots();
        setText(titleField, model.title);
        setText(subtitleField, model.subtitle);
        setText(authorField, model.author);
        setText(isbnField, model.isbn);
        setText(languageField, model.language);
        setText(ageField, model.ageRating);
        if (rightsBox != null) {
            rightsBox.setSelected(model.rightsConfirmed);
        }
        if (impressumBox != null) {
            impressumBox.setSelected(model.impressumNoted);
        }
        setText(coverPathField, model.coverPath);
        if (blurbArea != null) {
            blurbArea.setText(model.blurb == null ? "" : model.blurb);
        }
        if (blurbHintsArea != null) {
            blurbHintsArea.setText(model.blurbHints == null ? "" : model.blurbHints);
        }
        if (blurbToExportBox != null) {
            blurbToExportBox.setSelected(model.exportBlurb());
        }
        if (authorHintsArea != null) {
            authorHintsArea.setText(model.authorHints == null ? "" : model.authorHints);
        }
        for (int i = 0; i < keywordFields.size(); i++) {
            keywordFields.get(i).setText(model.keywordAt(i));
            updateKeywordCounter(i);
        }
        updateBlurbCounter();
    }

    private void refreshChecklist() {
        if (checklistBox == null) {
            return;
        }
        if (model == null) {
            model = new PublishPackageModel();
        }
        CoverAnalyzer.CoverInfo cover = CoverAnalyzer.analyze(model.coverPath);
        ChapterTocChecker.TocResult toc = projectRoot == null
                ? new ChapterTocChecker.TocResult(0, 0, 0, List.of("Kein Projekt"))
                : ChapterTocChecker.check(projectRoot, PublishPackageStore.loadSelection(projectRoot));
        ChecklistEngine.Report report = ChecklistEngine.evaluate(model, cover, toc);
        if (summaryLabel != null) {
            summaryLabel.setText(report.summaryLine());
        }
        checklistBox.getChildren().clear();
        for (ChecklistItem item : report.items()) {
            Label row = new Label("[" + item.statusLabel() + "] " + item.label() + " — " + item.detail());
            row.setWrapText(true);
            row.getStyleClass().add("dialog-label");
            switch (item.status()) {
                case FAIL -> row.setStyle("-fx-text-fill: #b00020;");
                case WARN -> row.setStyle("-fx-text-fill: #9a6b00;");
                case PASS -> row.setStyle("-fx-text-fill: #1b7a3d;");
                default -> {
                }
            }
            checklistBox.getChildren().add(row);
        }
    }

    private void updateCoverInfo() {
        if (coverInfoLabel == null) {
            return;
        }
        CoverAnalyzer.CoverInfo cover = CoverAnalyzer.analyze(coverPathField == null ? "" : coverPathField.getText());
        if (!cover.exists()) {
            coverInfoLabel.setText(cover.error());
            return;
        }
        if (!cover.readable()) {
            coverInfoLabel.setText(cover.error());
            return;
        }
        coverInfoLabel.setText(String.format(
                "Maße: %d × %d px · Ratio Höhe/Breite: %.2f · %s · Format: %s",
                cover.width(),
                cover.height(),
                PlatformRules.coverRatio(cover.width(), cover.height()),
                ChecklistEngine.formatBytes(cover.bytes()),
                cover.format().isEmpty() ? "?" : cover.format()));
    }

    private void updateKeywordCounter(int index) {
        if (index < 0 || index >= keywordFields.size()) {
            return;
        }
        String text = keywordFields.get(index).getText();
        int len = text == null ? 0 : text.length();
        Label counter = keywordCounters.get(index);
        counter.setText(len + " / " + PlatformRules.KDP_KEYWORD_MAX_CHARS);
        if (len > PlatformRules.KDP_KEYWORD_MAX_CHARS) {
            counter.setStyle("-fx-text-fill: #b00020;");
        } else {
            counter.setStyle("");
        }
    }

    private void updateBlurbCounter() {
        if (blurbCounter == null || blurbArea == null) {
            return;
        }
        int len = blurbArea.getText() == null ? 0 : blurbArea.getText().length();
        blurbCounter.setText(len + " / " + PlatformRules.KDP_DESCRIPTION_MAX_CHARS);
        if (len > PlatformRules.KDP_DESCRIPTION_MAX_CHARS) {
            blurbCounter.setStyle("-fx-text-fill: #b00020;");
        } else {
            blurbCounter.setStyle("");
        }
    }

    private void browseCover() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Cover wählen");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Bilder", "*.png", "*.jpg", "*.jpeg", "*.tif", "*.tiff", "*.webp"));
        if (projectRoot != null) {
            chooser.setInitialDirectory(projectRoot.toFile());
        }
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            coverPathField.setText(file.getAbsolutePath());
            updateCoverInfo();
        }
    }

    private void copyKeywords() {
        pullFromUi();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PlatformRules.KDP_KEYWORD_SLOTS; i++) {
            String kw = model.keywordAt(i).trim();
            if (!kw.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(kw);
            }
        }
        copyText(sb.toString());
        setStatus("Keywords in die Zwischenablage kopiert.");
    }

    private void copySummary() {
        pullFromUi();
        CoverAnalyzer.CoverInfo cover = CoverAnalyzer.analyze(model.coverPath);
        ChapterTocChecker.TocResult toc = projectRoot == null
                ? new ChapterTocChecker.TocResult(0, 0, 0, List.of())
                : ChapterTocChecker.check(projectRoot, PublishPackageStore.loadSelection(projectRoot));
        ChecklistEngine.Report report = ChecklistEngine.evaluate(model, cover, toc);
        StringBuilder sb = new StringBuilder();
        sb.append("Publish-Paket — ").append(report.summaryLine()).append('\n');
        if (!blank(model.title)) {
            sb.append("Titel: ").append(model.title.trim()).append('\n');
        }
        if (!blank(model.author)) {
            sb.append("Autor: ").append(model.author.trim()).append('\n');
        }
        for (ChecklistItem item : report.items()) {
            sb.append('[').append(item.statusLabel()).append("] ")
                    .append(item.label()).append(": ").append(item.detail()).append('\n');
        }
        copyText(sb.toString());
        setStatus("Zusammenfassung kopiert.");
    }

    private void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static Button button(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.getStyleClass().add("dialog-button");
        button.setOnAction(handler);
        return button;
    }

    private static TextField field() {
        TextField field = new TextField();
        field.getStyleClass().add("dialog-field");
        return field;
    }

    private static void addRow(GridPane grid, int row, String label, TextField field) {
        Label l = new Label(label);
        l.getStyleClass().add("dialog-label");
        grid.add(l, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private static VBox labeled(String title, javafx.scene.Node node) {
        Label l = new Label(title);
        l.getStyleClass().add("dialog-label");
        return new VBox(4, l, node);
    }

    private static String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText();
    }

    private static void setText(TextField field, String value) {
        if (field != null) {
            field.setText(value == null ? "" : value);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
