package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Dialog zur Hörbuch-Erstellung: Zielverzeichnis wählen, dann werden die ausgewählten Kapitel
 * jeweils als eine MP3 unter dem Kapitelnamen gespeichert (z. B. Kapitel_01.mp3).
 */
public class AudiobookDialog {

    private static final Logger logger = LoggerFactory.getLogger(AudiobookDialog.class);
    private static final java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(AudiobookDialog.class);

    /** Pause in Sekunden zwischen den Segmenten innerhalb eines Kapitels. */
    private static final double PAUSE_BETWEEN_SEGMENTS_SECONDS = 3.5;

    public static class ChapterRow {
        private final DocxFile docxFile;
        private final String chapterName;
        private final StringProperty ttsInfo = new SimpleStringProperty("");
        private final BooleanProperty selected = new SimpleBooleanProperty(true);

        public ChapterRow(DocxFile docxFile, String chapterName, String ttsInfo) {
            this.docxFile = docxFile;
            this.chapterName = chapterName;
            this.ttsInfo.set(ttsInfo);
        }

        public String getChapterName() { return chapterName; }
        public StringProperty ttsInfoProperty() { return ttsInfo; }
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean v) { selected.set(v); }
        public DocxFile getDocxFile() { return docxFile; }
    }

    /**
     * Zeigt den Hörbuch-Dialog. Kapitel sind die gewählten Docx-Dateien in Reihenfolge;
     * dataDir ist das Projekt-Data-Verzeichnis (z. B. currentDirectory/data).
     */
    public static void show(Window owner, List<DocxFile> chapters, File dataDir, int themeIndex) {
        if (chapters == null || chapters.isEmpty()) {
            if (owner != null) {
                CustomAlert a = DialogFactory.createWarningAlert("Hörbuch", "Keine Kapitel", "Bitte zuerst in der rechten Tabelle Kapitel auswählen.", owner instanceof javafx.stage.Stage ? (javafx.stage.Stage) owner : null);
                a.showAndWait();
            }
            return;
        }
        if (dataDir == null || !dataDir.isDirectory()) {
            if (owner != null) {
                CustomAlert a = DialogFactory.createWarningAlert("Hörbuch", "Kein Projekt", "Bitte zuerst ein Projektverzeichnis öffnen.", owner instanceof javafx.stage.Stage ? (javafx.stage.Stage) owner : null);
                a.showAndWait();
            }
            return;
        }

        ObservableList<ChapterRow> rows = FXCollections.observableArrayList();
        for (DocxFile docx : chapters) {
            String fn = docx.getFileName();
            if (fn != null && fn.toLowerCase().endsWith(".docx")) fn = fn.substring(0, fn.length() - 5);
            String name = fn != null ? fn : "Kapitel";
            String ttsInfo = checkTtsForChapter(dataDir, name);
            rows.add(new ChapterRow(docx, name, ttsInfo));
        }

        CustomStage stage = StageManager.createStage("Hoerbuch erstellen");
        if (owner != null) stage.initOwner(owner);
        stage.setMinWidth(900);
        stage.setMinHeight(550);

        javafx.geometry.Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
            prefs, "audiobook_window", 1000.0, 600.0);
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, windowBounds);
        stage.xProperty().addListener((obs, o, n) -> { if (n != null) PreferencesManager.putWindowPosition(prefs, "audiobook_window_x", n.doubleValue()); });
        stage.yProperty().addListener((obs, o, n) -> { if (n != null) PreferencesManager.putWindowPosition(prefs, "audiobook_window_y", n.doubleValue()); });
        stage.widthProperty().addListener((obs, o, n) -> { if (n != null) PreferencesManager.putWindowWidth(prefs, "audiobook_window_width", n.doubleValue()); });
        stage.heightProperty().addListener((obs, o, n) -> { if (n != null) PreferencesManager.putWindowHeight(prefs, "audiobook_window_height", n.doubleValue()); });

        // === Linke Seite: Kapitel-Auswahl und Optionen ===

        TableView<ChapterRow> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ChapterRow, Boolean> colSel = new TableColumn<>("Dabei");
        colSel.setCellValueFactory(c -> c.getValue().selectedProperty());
        colSel.setCellFactory(tc -> new javafx.scene.control.cell.CheckBoxTableCell<>());
        colSel.setEditable(true);
        TableColumn<ChapterRow, String> colName = new TableColumn<>("Kapitel");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getChapterName()));
        TableColumn<ChapterRow, String> colTts = new TableColumn<>("Sprachsynthese");
        colTts.setCellValueFactory(c -> c.getValue().ttsInfoProperty());
        table.getColumns().add(colSel);
        table.getColumns().add(colName);
        table.getColumns().add(colTts);
        table.setEditable(true);
        table.setPrefHeight(250);

        ComboBox<String> bitrateCombo = new ComboBox<>();
        bitrateCombo.getItems().addAll(ChapterTtsEditorWindow.getBitrateOptions());
        bitrateCombo.getSelectionModel().select(prefs.getInt("audiobook_bitrate_idx", ChapterTtsEditorWindow.getDefaultBitrateIndex()));
        bitrateCombo.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (n != null) prefs.putInt("audiobook_bitrate_idx", n.intValue());
        });
        bitrateCombo.setMaxWidth(Double.MAX_VALUE);
        HBox bitrateRow = new HBox(10);
        bitrateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bitrateRow.getChildren().addAll(new Label("Bitrate:"), bitrateCombo);
        HBox.setHgrow(bitrateCombo, Priority.ALWAYS);

        CheckBox stereoCheck = new CheckBox("Stereo");
        stereoCheck.setSelected(prefs.getBoolean("audiobook_stereo", true));
        stereoCheck.selectedProperty().addListener((obs, o, n) -> prefs.putBoolean("audiobook_stereo", n));
        HBox stereoRow = new HBox(10);
        stereoRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        stereoRow.getChildren().addAll(stereoCheck,
                new Label(String.format("(%.1fs Stille + Fade-in am Anfang, %.1fs Stille am Ende)",
                        ChapterTtsEditorWindow.getLeadSilenceSeconds(),
                        ChapterTtsEditorWindow.getTrailSilenceSeconds())));

        Button btnAll = new Button("Alle auswaehlen");
        Button btnNone = new Button("Keine auswaehlen");
        btnAll.setOnAction(e -> rows.forEach(r -> r.setSelected(true)));
        btnNone.setOnAction(e -> rows.forEach(r -> r.setSelected(false)));

        ProgressIndicator progress = new ProgressIndicator(-1);
        progress.setVisible(false);
        progress.setMaxSize(24, 24);
        Label statusLabel = new Label("");

        Button btnCreate = new Button("Kapitel als MP3 erstellen");
        btnCreate.getStyleClass().add("btn-success");
        Button btnCancel = new Button("Abbrechen");
        btnCancel.getStyleClass().add("btn-secondary");

        // === Rechte Seite: Protokoll ===

        Label logTitle = new Label("Protokoll");
        logTitle.getStyleClass().add("section-title");

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");

        // Log-Callback: thread-sicher, schreibt mit Zeitstempel ins Protokoll
        final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Consumer<String> logCallback = msg -> Platform.runLater(() -> {
            String ts = LocalTime.now().format(timeFmt);
            logArea.appendText("[" + ts + "] " + msg + "\n");
        });

        Button btnClearLog = new Button("Protokoll leeren");
        btnClearLog.getStyleClass().add("btn-small");
        btnClearLog.setOnAction(e -> logArea.clear());

        VBox logBox = new VBox(6, logTitle, logArea, btnClearLog);
        logBox.setPadding(new Insets(0, 0, 0, 5));
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // === Aktionen ===

        btnCreate.setOnAction(e -> {
            List<ChapterRow> selected = rows.filtered(ChapterRow::isSelected);
            if (selected.isEmpty()) {
                CustomAlert al = DialogFactory.createWarningAlert("Hoerbuch", "Keine Kapitel", "Bitte mindestens ein Kapitel auswaehlen.", stage);
                al.applyTheme(themeIndex);
                al.showAndWait();
                return;
            }
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Zielverzeichnis fuer Kapitel-MP3s");
            String lastDir = prefs.get("lastAudiobookDirectory", null);
            if (lastDir != null) {
                File dir = new File(lastDir);
                if (dir.isDirectory()) dc.setInitialDirectory(dir);
            }
            File outDir = dc.showDialog(stage);
            if (outDir == null) return;
            try { prefs.put("lastAudiobookDirectory", outDir.getAbsolutePath()); } catch (Exception ignored) { }
            Path outDirPath = outDir.toPath();
            int bitrateKbps = ChapterTtsEditorWindow.getBitrateByIndex(bitrateCombo.getSelectionModel().getSelectedIndex());
            boolean isStereo = stereoCheck.isSelected();
            double leadSilence = ChapterTtsEditorWindow.getLeadSilenceSeconds();
            double trailSilence = ChapterTtsEditorWindow.getTrailSilenceSeconds();
            progress.setVisible(true);
            statusLabel.setText("Erstelle Kapitel-MP3s ...");
            btnCreate.setDisable(true);
            btnCancel.setDisable(true);

            logCallback.accept("=== Hoerbuch-Erstellung gestartet ===");
            logCallback.accept("Zielverzeichnis: " + outDirPath.toAbsolutePath());
            logCallback.accept("Kapitel: " + selected.size() + " | Bitrate: " + bitrateKbps + " kbps | "
                + (isStereo ? "Stereo" : "Mono"));
            logCallback.accept("Lead-Silence: " + leadSilence + "s | Trail-Silence: " + trailSilence + "s");
            logCallback.accept("Pause zwischen Segmenten: " + PAUSE_BETWEEN_SEGMENTS_SECONDS + "s");
            logCallback.accept("---");

            CompletableFuture.runAsync(() -> {
                int savedCount = 0;
                String firstError = null;
                long totalStart = System.currentTimeMillis();
                try {
                    for (int i = 0; i < selected.size(); i++) {
                        ChapterRow row = selected.get(i);
                        final int chapterNumber = rows.indexOf(row) + 1;
                        final int progress_i = i + 1;
                        Platform.runLater(() -> statusLabel.setText("Kapitel " + progress_i + " von " + selected.size() + " ..."));

                        logCallback.accept("Kapitel " + progress_i + "/" + selected.size()
                            + ": " + row.getChapterName() + " (Nr. " + chapterNumber + ")");

                        List<Path> segmentFiles = loadSegmentPaths(dataDir, row.getChapterName());
                        if (segmentFiles.isEmpty()) {
                            logCallback.accept("  UEBERSPRUNGEN: Keine gueltigen TTS-Segmente.");
                            continue;
                        }
                        logCallback.accept("  Segmente: " + segmentFiles.size());

                        String safeName = sanitizeFileName(row.getChapterName());
                        if (safeName.isEmpty()) safeName = "Kapitel";
                        String fileName = String.format("%03d_%s", chapterNumber, safeName);
                        Path chapterOut = outDirPath.resolve(fileName + ".mp3");
                        logCallback.accept("  Ausgabe: " + chapterOut.getFileName());

                        long chapterStart = System.currentTimeMillis();
                        String err = ChapterTtsEditorWindow.concatMp3FilesWithPause(segmentFiles, chapterOut,
                                PAUSE_BETWEEN_SEGMENTS_SECONDS, 4, false,
                                bitrateKbps, isStereo, leadSilence, trailSilence, logCallback);
                        long chapterMs = System.currentTimeMillis() - chapterStart;

                        if (err != null) {
                            logCallback.accept("  FEHLER: " + err);
                            if (firstError == null) firstError = row.getChapterName() + ": " + err;
                            continue;
                        }
                        logCallback.accept("  OK (" + String.format("%.1f", chapterMs / 1000.0) + "s)");
                        savedCount++;
                    }

                    boolean coverCopied = false;
                    Path projectDir = dataDir.toPath().getParent();
                    if (projectDir != null) {
                        Path coverSource = projectDir.resolve("audiobook_cover.png");
                        if (Files.isRegularFile(coverSource)) {
                            try {
                                Files.copy(coverSource, outDirPath.resolve("audiobook_cover.png"), StandardCopyOption.REPLACE_EXISTING);
                                coverCopied = true;
                                logCallback.accept("Cover-Bild (audiobook_cover.png) kopiert.");
                            } catch (IOException ioEx) {
                                logCallback.accept("Cover-Bild konnte nicht kopiert werden: " + ioEx.getMessage());
                            }
                        }
                    }

                    long totalMs = System.currentTimeMillis() - totalStart;
                    logCallback.accept("---");
                    logCallback.accept("=== Fertig: " + savedCount + " Kapitel in "
                        + String.format("%.1f", totalMs / 1000.0) + "s ===");

                    final int count = savedCount;
                    final String errFinal = firstError;
                    final boolean coverInfo = coverCopied;
                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        btnCreate.setDisable(false);
                        btnCancel.setDisable(false);
                        if (errFinal != null) {
                            CustomAlert al = DialogFactory.createErrorAlert("Hoerbuch", "Fehler bei mindestens einem Kapitel", errFinal, stage);
                            al.applyTheme(themeIndex);
                            al.showAndWait();
                        }
                        String statusMsg = count + " Kapitel als MP3 in " + outDirPath.getFileName() + " gespeichert.";
                        if (coverInfo) statusMsg += " Cover-Bild wurde mitkopiert.";
                        statusLabel.setText(statusMsg);
                    });
                } catch (Exception ex) {
                    logger.error("Hoerbuch-Erstellung", ex);
                    logCallback.accept("SCHWERER FEHLER: " + ex.getMessage());
                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        btnCreate.setDisable(false);
                        btnCancel.setDisable(false);
                        statusLabel.setText("Fehler.");
                        CustomAlert al = DialogFactory.createErrorAlert("Hoerbuch", "Fehler", ex.getMessage(), stage);
                        al.applyTheme(themeIndex);
                        al.showAndWait();
                    });
                }
            });
        });
        btnCancel.setOnAction(e -> stage.close());

        // === Layout zusammenbauen ===

        Label infoLabel = new Label("Kapitel auswaehlen. Pro Kapitel wird eine MP3 unter dem Kapitelnamen gespeichert.");
        infoLabel.setWrapText(true);

        HBox topButtons = new HBox(10, btnAll, btnNone);
        topButtons.setPadding(new Insets(0, 0, 5, 0));

        HBox bottomButtons = new HBox(10, progress, statusLabel, new Region(), btnCreate, btnCancel);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        bottomButtons.setPadding(new Insets(10, 0, 0, 0));

        VBox leftPane = new VBox(10, infoLabel, topButtons, bitrateRow, stereoRow, table, bottomButtons);
        leftPane.setPadding(new Insets(10));
        leftPane.setMinWidth(420);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox rightPane = new VBox(6, logBox);
        rightPane.setPadding(new Insets(10));
        rightPane.setMinWidth(300);
        VBox.setVgrow(logBox, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setDividerPositions(prefs.getDouble("audiobook_divider", 0.55));
        splitPane.getDividers().get(0).positionProperty().addListener((obs, o, n) -> {
            if (n != null) prefs.putDouble("audiobook_divider", n.doubleValue());
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(splitPane, 1000, 600);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        // Theme-Klassen explizit auf alle Elemente setzen (wie ChapterTtsEditorWindow)
        applyThemeToNode(splitPane, themeIndex);
        applyThemeToNode(leftPane, themeIndex);
        applyThemeToNode(rightPane, themeIndex);
        applyThemeToNode(logBox, themeIndex);
        applyThemeToNode(logArea, themeIndex);
        applyThemeToNode(logTitle, themeIndex);
        applyThemeToNode(btnClearLog, themeIndex);
        applyThemeToNode(table, themeIndex);
        applyThemeToNode(bitrateCombo, themeIndex);
        applyThemeToNode(stereoCheck, themeIndex);
        applyThemeToNode(infoLabel, themeIndex);
        applyThemeToNode(statusLabel, themeIndex);
        applyThemeToNode(btnAll, themeIndex);
        applyThemeToNode(btnNone, themeIndex);
        applyThemeToNode(btnCreate, themeIndex);
        applyThemeToNode(btnCancel, themeIndex);
        applyThemeToNode(bitrateRow, themeIndex);
        applyThemeToNode(stereoRow, themeIndex);
        applyThemeToNode(topButtons, themeIndex);
        applyThemeToNode(bottomButtons, themeIndex);
        for (Node c : bitrateRow.getChildren()) applyThemeToNode(c, themeIndex);
        for (Node c : stereoRow.getChildren()) applyThemeToNode(c, themeIndex);
        stage.show();
    }

    private static String checkTtsForChapter(File dataDir, String chapterName) {
        Path segmentsPath = Paths.get(dataDir.getPath(), chapterName + "-tts-segments.json");
        if (!Files.isRegularFile(segmentsPath)) return "—";
        try {
            List<ChapterTtsEditorWindow.TtsSegment> list = loadSegments(segmentsPath);
            List<Path> valid = collectValidAudioPaths(list);
            if (valid.isEmpty()) return "—";
            return valid.size() + " Segment(e)";
        } catch (Exception e) {
            return "—";
        }
    }

    private static List<Path> loadSegmentPaths(File dataDir, String chapterName) {
        Path segmentsPath = Paths.get(dataDir.getPath(), chapterName + "-tts-segments.json");
        if (!Files.isRegularFile(segmentsPath)) return List.of();
        try {
            List<ChapterTtsEditorWindow.TtsSegment> list = loadSegments(segmentsPath);
            return collectValidAudioPaths(list);
        } catch (Exception e) {
            logger.warn("Segmente nicht ladbar: {}", segmentsPath, e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ChapterTtsEditorWindow.TtsSegment> loadSegments(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.reflect.TypeToken<List<ChapterTtsEditorWindow.TtsSegment>> type = new com.google.gson.reflect.TypeToken<List<ChapterTtsEditorWindow.TtsSegment>>() {};
        List<ChapterTtsEditorWindow.TtsSegment> list = gson.fromJson(json, (java.lang.reflect.Type) type.getType());
        return list != null ? list : List.of();
    }

    private static List<Path> collectValidAudioPaths(List<ChapterTtsEditorWindow.TtsSegment> segments) {
        List<ChapterTtsEditorWindow.TtsSegment> byStart = new ArrayList<>(segments);
        byStart.sort(Comparator.comparingInt(s -> s.start));
        List<Path> paths = new ArrayList<>();
        for (ChapterTtsEditorWindow.TtsSegment s : byStart) {
            if (s.audioPath == null || s.audioPath.isEmpty()) continue;
            Path p = Paths.get(s.audioPath);
            if (Files.isRegularFile(p)) paths.add(p);
        }
        return paths;
    }

    private static void applyThemeToNode(Node node, int themeIndex) {
        if (node == null) return;
        node.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
        if (themeIndex == 0) node.getStyleClass().add("weiss-theme");
        else if (themeIndex == 1) node.getStyleClass().add("theme-dark");
        else if (themeIndex == 2) node.getStyleClass().add("pastell-theme");
        else if (themeIndex == 3) { node.getStyleClass().add("theme-dark"); node.getStyleClass().add("blau-theme"); }
        else if (themeIndex == 4) { node.getStyleClass().add("theme-dark"); node.getStyleClass().add("gruen-theme"); }
        else if (themeIndex == 5) { node.getStyleClass().add("theme-dark"); node.getStyleClass().add("lila-theme"); }
    }

    /** Erzeugt einen für Dateinamen sicheren String: Umlaute ersetzen, nur ASCII-Buchstaben/Ziffern/Leerzeichen behalten. */
    private static String sanitizeFileName(String rawName) {
        if (rawName == null) return "";
        String name = rawName.trim();
        if (name.isEmpty()) return "";
        // Pfadanteil entfernen
        name = name.replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        // Umlaute und ß ersetzen
        name = name.replace("ä", "ae").replace("Ä", "Ae")
                    .replace("ö", "oe").replace("Ö", "Oe")
                    .replace("ü", "ue").replace("Ü", "Ue")
                    .replace("ß", "ss");
        // Nur ASCII-Buchstaben, Ziffern und Leerzeichen behalten, Rest durch Leerzeichen ersetzen
        name = name.replaceAll("[^A-Za-z0-9 ]", " ");
        // Mehrfache Leerzeichen zusammenfassen und trimmen
        name = name.replaceAll("\\s+", " ").trim();
        return name;
    }
}
