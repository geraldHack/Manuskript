package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

        CustomStage stage = StageManager.createStage("Hörbuch erstellen");
        if (owner != null) stage.initOwner(owner);
        stage.setMinWidth(480);
        stage.setMinHeight(400);

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
        bitrateCombo.getSelectionModel().select(ChapterTtsEditorWindow.getDefaultBitrateIndex());
        bitrateCombo.setMaxWidth(Double.MAX_VALUE);
        HBox bitrateRow = new HBox(10);
        bitrateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bitrateRow.getChildren().addAll(new Label("Bitrate:"), bitrateCombo);
        HBox.setHgrow(bitrateCombo, Priority.ALWAYS);

        CheckBox stereoCheck = new CheckBox("Stereo");
        stereoCheck.setSelected(true);
        HBox stereoRow = new HBox(10);
        stereoRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        stereoRow.getChildren().addAll(stereoCheck,
                new Label(String.format("(%.1fs Stille + Fade-in am Anfang, %.1fs Stille am Ende)",
                        ChapterTtsEditorWindow.getLeadSilenceSeconds(),
                        ChapterTtsEditorWindow.getTrailSilenceSeconds())));

        Button btnAll = new Button("Alle auswählen");
        Button btnNone = new Button("Keine auswählen");
        btnAll.setOnAction(e -> rows.forEach(r -> r.setSelected(true)));
        btnNone.setOnAction(e -> rows.forEach(r -> r.setSelected(false)));

        ProgressIndicator progress = new ProgressIndicator(-1);
        progress.setVisible(false);
        Label statusLabel = new Label("");

        Button btnCreate = new Button("Kapitel als MP3 erstellen");
        Button btnCancel = new Button("Abbrechen");
        btnCreate.setOnAction(e -> {
            List<ChapterRow> selected = rows.filtered(ChapterRow::isSelected);
            if (selected.isEmpty()) {
                CustomAlert al = DialogFactory.createWarningAlert("Hörbuch", "Keine Kapitel", "Bitte mindestens ein Kapitel auswählen.", stage);
                al.applyTheme(themeIndex);
                al.showAndWait();
                return;
            }
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Zielverzeichnis für Kapitel-MP3s");
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
            statusLabel.setText("Erstelle Kapitel-MP3s …");
            btnCreate.setDisable(true);
            btnCancel.setDisable(true);

            CompletableFuture.runAsync(() -> {
                int savedCount = 0;
                String firstError = null;
                try {
                    for (int i = 0; i < selected.size(); i++) {
                        ChapterRow row = selected.get(i);
                        // Nummer = Position in der Gesamt-Kapitelliste (1-basiert), nicht in der Auswahl
                        final int chapterNumber = rows.indexOf(row) + 1;
                        final int progress_i = i + 1;
                        Platform.runLater(() -> statusLabel.setText("Kapitel " + progress_i + " von " + selected.size() + " …"));
                        List<Path> segmentFiles = loadSegmentPaths(dataDir, row.getChapterName());
                        if (segmentFiles.isEmpty()) {
                            logger.warn("Kapitel {} hat keine gültigen TTS-Segmente, wird übersprungen.", row.getChapterName());
                            continue;
                        }
                        String safeName = sanitizeFileName(row.getChapterName());
                        if (safeName.isEmpty()) safeName = "Kapitel";
                        String fileName = String.format("%03d_%s", chapterNumber, safeName);
                        Path chapterOut = outDirPath.resolve(fileName + ".mp3");
                        String err = ChapterTtsEditorWindow.concatMp3FilesWithPause(segmentFiles, chapterOut,
                                PAUSE_BETWEEN_SEGMENTS_SECONDS, 4, false,
                                bitrateKbps, isStereo, leadSilence, trailSilence);
                        if (err != null) {
                            if (firstError == null) firstError = row.getChapterName() + ": " + err;
                            continue;
                        }
                        savedCount++;
                    }
                    final int count = savedCount;
                    final String errFinal = firstError;
                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        btnCreate.setDisable(false);
                        btnCancel.setDisable(false);
                        if (errFinal != null) {
                            CustomAlert al = DialogFactory.createErrorAlert("Hörbuch", "Fehler bei mindestens einem Kapitel", errFinal, stage);
                            al.applyTheme(themeIndex);
                            al.showAndWait();
                        }
                        statusLabel.setText(count + " Kapitel als MP3 in " + outDirPath.getFileName() + " gespeichert.");
                    });
                } catch (Exception ex) {
                    logger.error("Hörbuch-Erstellung", ex);
                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        btnCreate.setDisable(false);
                        btnCancel.setDisable(false);
                        statusLabel.setText("Fehler.");
                        CustomAlert al = DialogFactory.createErrorAlert("Hörbuch", "Fehler", ex.getMessage(), stage);
                        al.applyTheme(themeIndex);
                        al.showAndWait();
                    });
                }
            });
        });
        btnCancel.setOnAction(e -> stage.close());

        HBox topButtons = new HBox(10, btnAll, btnNone);
        topButtons.setPadding(new Insets(0, 0, 5, 0));
        HBox bottomButtons = new HBox(10, progress, statusLabel, new Region(), btnCreate, btnCancel);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        bottomButtons.setPadding(new Insets(10, 0, 0, 0));
        VBox root = new VBox(10, new Label("Kapitel auswählen. Beim Erstellen wird ein Zielverzeichnis gewählt; pro Kapitel wird eine MP3 unter dem Kapitelnamen gespeichert."), topButtons, bitrateRow, stereoRow, table, bottomButtons);
        root.setPadding(new Insets(15));
        VBox.setVgrow(table, Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
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
