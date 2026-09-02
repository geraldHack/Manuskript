package com.manuskript.stats;

import com.manuskript.plugin.PluginHost;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Snapshot-Statistik für das geöffnete Buch.
 */
public final class StatsWindow {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withLocale(Locale.GERMAN);

    private final PluginHost host;
    private Stage stage;
    private Label overview;
    private TableView<ChapterRow> chapterTable;
    private TableView<CountRow> speechTable;
    private TableView<CountRow> verbTable;
    private TableView<CountRow> phraseTable;
    private FlowPane imagePane;
    private Label status;

    public StatsWindow(PluginHost host) {
        this.host = host;
    }

    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            refresh();
            return;
        }
        stage = host.createThemedStage("Schreib-Statistik");
        host.attachScene(stage, new Scene(buildUi(), 780, 560));
        stage.show();
        refresh();
    }

    private VBox buildUi() {
        Label intro = new Label(
                "Kennzahlen aus allen Kapitel-Arbeitskopien (jeder data/*.md), nicht nur der Auswahl. "
                        + "Kein Tagesverlauf — Scan beim Öffnen oder über Aktualisieren.");
        intro.setWrapText(true);
        intro.getStyleClass().add("dialog-label");

        Button refresh = new Button("Aktualisieren");
        refresh.getStyleClass().add("dialog-button");
        refresh.setOnAction(e -> refresh());

        overview = new Label("—");
        overview.setWrapText(true);
        overview.getStyleClass().add("dialog-label");

        chapterTable = table("Kapitel", "Wörter", "Zuletzt");
        speechTable = countTable("Sprechantwort", "Anzahl");
        verbTable = countTable("Verb", "Anzahl");
        phraseTable = countTable("Phrase", "Anzahl");
        imagePane = new FlowPane(12, 12);
        imagePane.setPadding(new Insets(8));

        TabPane tabs = new TabPane(
                tab("Überblick", overviewBox()),
                tab("Kapitel", chapterTable),
                tab("Sprechantworten", speechBox()),
                tab("Phrasen", phraseTable),
                tab("Bilder", new ScrollPane(imagePane)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        status = new Label("");
        status.setWrapText(true);
        status.getStyleClass().add("dialog-label");

        VBox root = new VBox(10, intro, refresh, tabs, status);
        root.setPadding(new Insets(16));
        return root;
    }

    private VBox overviewBox() {
        VBox box = new VBox(8, overview);
        box.setPadding(new Insets(8));
        return box;
    }

    private VBox speechBox() {
        Label verbs = new Label("Sprechwörter — jedes Vorkommen als ganzes Wort");
        verbs.getStyleClass().add("dialog-title");
        Label phrases = new Label("Sprechantworten — dasselbe Verb, mit Folgeworten soweit vorhanden");
        phrases.getStyleClass().add("dialog-title");
        VBox.setVgrow(speechTable, Priority.ALWAYS);
        VBox.setVgrow(verbTable, Priority.SOMETIMES);
        return new VBox(8, verbs, verbTable, phrases, speechTable);
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private TableView<ChapterRow> table(String a, String b, String c) {
        TableView<ChapterRow> view = new TableView<>();
        TableColumn<ChapterRow, String> colA = new TableColumn<>(a);
        colA.setCellValueFactory(new PropertyValueFactory<>("name"));
        colA.setPrefWidth(280);
        TableColumn<ChapterRow, String> colB = new TableColumn<>(b);
        colB.setCellValueFactory(new PropertyValueFactory<>("words"));
        colB.setPrefWidth(90);
        TableColumn<ChapterRow, String> colC = new TableColumn<>(c);
        colC.setCellValueFactory(new PropertyValueFactory<>("modified"));
        colC.setPrefWidth(140);
        view.getColumns().add(colA);
        view.getColumns().add(colB);
        view.getColumns().add(colC);
        view.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private TableView<CountRow> countTable(String key, String value) {
        TableView<CountRow> view = new TableView<>();
        TableColumn<CountRow, String> colA = new TableColumn<>(key);
        colA.setCellValueFactory(new PropertyValueFactory<>("label"));
        TableColumn<CountRow, String> colB = new TableColumn<>(value);
        colB.setCellValueFactory(new PropertyValueFactory<>("count"));
        colB.setPrefWidth(90);
        view.getColumns().add(colA);
        view.getColumns().add(colB);
        view.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private void refresh() {
        Path project = host.projectRoot().orElse(null);
        if (project == null || !Files.isDirectory(project)) {
            status.setText("Kein Projekt geöffnet.");
            return;
        }
        status.setText("Scanne …");
        Path config = host.configDir();
        CompletableFuture.supplyAsync(() -> {
            try {
                return StatsEngine.scan(project, config);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((stats, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                status.setText("Fehler: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
                return;
            }
            apply(stats);
        }));
    }

    private void apply(BookStats stats) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.GERMAN, "%d Kapitel · %,d Wörter", stats.chapters.size(), stats.totalWords));
        if (!stats.chapters.isEmpty()) {
            text.append(String.format(Locale.GERMAN, " · Ø %,d Wörter", stats.averageWords));
        }
        text.append('\n');
        if (stats.shortest != null) {
            text.append("Kürzestes: ").append(stats.shortest.name())
                    .append(" (").append(stats.shortest.words()).append(" Wörter)\n");
        }
        if (stats.longest != null) {
            text.append("Längstes: ").append(stats.longest.name())
                    .append(" (").append(stats.longest.words()).append(" Wörter)\n");
        }
        text.append("Zuletzt bearbeitet:\n");
        stats.chapters.stream()
                .sorted(Comparator.comparing(BookStats.Chapter::modified).reversed())
                .limit(8)
                .forEach(chapter -> text.append("  ")
                        .append(formatTime(chapter.modified()))
                        .append("  ")
                        .append(chapter.name())
                        .append('\n'));
        overview.setText(text.toString());

        List<ChapterRow> rows = new ArrayList<>();
        stats.chapters.stream()
                .sorted(Comparator.comparing(BookStats.Chapter::modified).reversed())
                .forEach(chapter -> rows.add(new ChapterRow(
                        chapter.name(),
                        String.format(Locale.GERMAN, "%,d", chapter.words()),
                        formatTime(chapter.modified()))));
        chapterTable.getItems().setAll(rows);
        speechTable.getItems().setAll(toRows(stats.speechPhrases));
        verbTable.getItems().setAll(toRows(stats.speechVerbs));
        phraseTable.getItems().setAll(toRows(stats.phrases));

        imagePane.getChildren().clear();
        for (BookStats.ImageHit hit : stats.images) {
            VBox card = new VBox(4);
            card.setPrefWidth(180);
            if (hit.resolved() != null && Files.isRegularFile(hit.resolved())) {
                try {
                    Image image = new Image(hit.resolved().toUri().toString(), 160, 120, true, true, true);
                    ImageView view = new ImageView(image);
                    view.setFitWidth(160);
                    view.setFitHeight(120);
                    view.setPreserveRatio(true);
                    card.getChildren().add(view);
                } catch (Exception e) {
                    card.getChildren().add(new Label("Kein Vorschaubild"));
                }
            } else {
                Label missing = new Label("Datei fehlt");
                missing.getStyleClass().add("dialog-label");
                card.getChildren().add(missing);
            }
            Label caption = new Label(hit.caption() == null || hit.caption().isBlank() ? hit.path() : hit.caption());
            caption.setWrapText(true);
            caption.getStyleClass().add("dialog-label");
            Label chapter = new Label(hit.chapter());
            chapter.setWrapText(true);
            chapter.getStyleClass().add("dialog-label");
            card.getChildren().addAll(caption, chapter);
            imagePane.getChildren().add(card);
        }
        int verbHits = stats.speechVerbs.values().stream().mapToInt(Integer::intValue).sum();
        int speechHits = stats.speechPhrases.values().stream().mapToInt(Integer::intValue).sum();
        status.setText("Stand: " + stats.chapters.size() + " Kapitel, "
                + stats.images.size() + " Bilder, "
                + verbHits + " Sprechwörter, "
                + speechHits + " Sprechantworten.");
    }

    private static List<CountRow> toRows(Map<String, Integer> map) {
        List<CountRow> rows = new ArrayList<>();
        map.forEach((key, value) -> rows.add(new CountRow(key, String.valueOf(value))));
        return rows;
    }

    private static String formatTime(java.time.Instant instant) {
        if (instant == null) {
            return "—";
        }
        return TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static final class ChapterRow {
        private final String name;
        private final String words;
        private final String modified;

        public ChapterRow(String name, String words, String modified) {
            this.name = name;
            this.words = words;
            this.modified = modified;
        }

        public String getName() {
            return name;
        }

        public String getWords() {
            return words;
        }

        public String getModified() {
            return modified;
        }
    }

    public static final class CountRow {
        private final String label;
        private final String count;

        public CountRow(String label, String count) {
            this.label = label;
            this.count = count;
        }

        public String getLabel() {
            return label;
        }

        public String getCount() {
            return count;
        }
    }
}
