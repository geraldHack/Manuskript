package com.manuskript.review;

import com.manuskript.ApplicationPaths;
import com.manuskript.CustomAlert;
import com.manuskript.CustomStage;
import com.manuskript.ManuskriptTextEditor;
import com.manuskript.MdTextArea;
import com.manuskript.MdTextAreaOptions;
import com.manuskript.StageManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lektorenmodus: ZIP als Paket, kein Kapitel-Import.
 */
public final class NiReviewPackageWindow {

    private static NiReviewPackageWindow active;

    private final CustomStage stage;
    private final Path zipPath;
    private final NiReviewZip.Loaded loaded;
    private final MdTextArea mdTextArea;
    private final ManuskriptTextEditor editor;
    private final NiReviewSidePanel sidePanel = new NiReviewSidePanel();
    private final ComboBox<String> chapterBox = new ComboBox<>();
    private final Label status = new Label();
    private NiReviewSession session;
    private String currentKey;
    private String lastDisplay = "";
    private String editBaseline;
    private String pendingEditorText;
    private boolean suppressEdit;
    private int suppressGeneration;
    private final PauseTransition editPause = new PauseTransition(Duration.millis(400));

    public NiReviewPackageWindow(Window owner, Path zipPath, NiReviewZip.Loaded loaded, int themeIndex) {
        this.zipPath = zipPath;
        this.loaded = loaded;
        this.stage = StageManager.createStage("Lektoratspaket: " + zipPath.getFileName());
        if (owner != null) {
            stage.initOwner(owner);
        }
        mdTextArea = new MdTextArea(MdTextAreaOptions.builder().showToolbar(true).build());
        editor = mdTextArea.getEditor();
        sidePanel.setAuthorActions(false);
        sidePanel.setComposerVisible(true);
        sidePanel.setOnAddComment(this::addCommentFromPanel);
        sidePanel.setHandlers(null, null, this::revealChange, this::revealComment, null,
                this::deleteChange, this::deleteComment);
        editor.setUndoInterceptor(this::undoLektorEdit);
        editor.setRedoInterceptor(this::redoLektorEdit);

        List<String> keys = new ArrayList<>();
        for (NiReviewManifest.ChapterRef ref : loaded.manifest().getChapters()) {
            keys.add(ref.getChapterKey());
        }
        chapterBox.getItems().addAll(keys);
        chapterBox.setOnAction(e -> loadChapter(chapterBox.getValue()));

        Button createZip = new Button("Lektorat-ZIP erstellen");
        createZip.setOnAction(e -> savePackageAs());
        Button save = new Button("Lektoratspaket speichern");
        save.setOnAction(e -> savePackage());
        Button comment = new Button("Kommentar");
        comment.setOnAction(e -> sidePanel.focusComposer());
        HBox toolbar = new HBox(8, new Label("Kapitel:"), chapterBox, comment, createZip, save, status);
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        SplitPane split = new SplitPane(mdTextArea, sidePanel.getRoot());
        split.setDividerPositions(0.72);
        VBox.setVgrow(split, Priority.ALWAYS);
        BorderPane root = new BorderPane(split);
        root.setTop(toolbar);
        Scene scene = new Scene(root, 1100, 720);
        stage.setScene(scene);

        editPause.setOnFinished(e -> flushLektorEdits());
        editor.setOnTextChanged(text -> {
            if (suppressEdit || session == null) {
                return;
            }
            if (editBaseline == null) {
                editBaseline = lastDisplay;
            }
            pendingEditorText = text == null ? "" : text;
            editPause.playFromStart();
        });

        if (!keys.isEmpty()) {
            chapterBox.getSelectionModel().select(0);
            loadChapter(keys.get(0));
        }
        active = this;
        stage.setOnHidden(e -> {
            if (active == this) {
                active = null;
            }
        });
    }

    public static boolean hasOpenPackage() {
        return active != null;
    }

    public static void createReturnZip(Window owner) {
        if (active == null) {
            CustomAlert alert = new CustomAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Lektorat-ZIP erstellen");
            alert.setContentText("Zuerst das empfangene Lektoratspaket öffnen, dann die Rückgabe-ZIP erstellen.");
            alert.showAndWait(owner);
            return;
        }
        active.savePackageAs();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    private void loadChapter(String key) {
        if (key == null) {
            return;
        }
        persistCurrent();
        editPause.stop();
        editBaseline = null;
        pendingEditorText = null;
        currentKey = key;
        String snapshot = loaded.snapshots().getOrDefault(key, "");
        NiReviewDocument document = loaded.reviews().get(key);
        if (document == null) {
            document = new NiReviewDocument();
            document.setChapterKey(key);
            loaded.reviews().put(key, document);
        }
        session = new NiReviewSession(snapshot, document, NiReviewSession.PersistMode.PACKAGE);
        rebuildDisplay();
        status.setText("Snapshot – Änderungen nur in der ZIP");
    }

    private void rebuildDisplay() {
        if (session == null) {
            return;
        }
        suppressEdit = true;
        int token = ++suppressGeneration;
        try {
            NiReviewDisplay.Result result = session.display();
            lastDisplay = result.text();
            editor.replaceAllTextPreservingCaretAndViewport(result.text(), false);
            editor.clearUndoHistory();
            editor.applyNiReviewSpans(result, this::revealChange, this::revealComment);
            sidePanel.refresh(session.document());
        } finally {
            Platform.runLater(() -> {
                if (token == suppressGeneration) {
                    suppressEdit = false;
                }
            });
        }
    }

    private void revealChange(String id) {
        jumpTo(id, false);
    }

    private void revealComment(String id) {
        jumpTo(id, true);
    }

    private void jumpTo(String id, boolean comment) {
        if (session == null || id == null) {
            return;
        }
        int start = Integer.MAX_VALUE;
        int end = -1;
        for (NiReviewDisplay.Span span : session.display().spans()) {
            boolean match = comment
                    ? span.kind() == NiReviewDisplay.SpanKind.COMMENT && id.equals(span.itemId())
                    : id.equals(span.itemId()) && span.kind() != NiReviewDisplay.SpanKind.COMMENT;
            if (match) {
                start = Math.min(start, span.displayStart());
                end = Math.max(end, span.displayEnd());
            }
        }
        if (end < start) {
            return;
        }
        editor.revealMatchAt(start, end);
        editor.requestFocus();
    }

    private void addCommentFromPanel(String text) {
        flushLektorEdits();
        if (session == null || text == null || text.isBlank()) {
            return;
        }
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        NiReviewDisplay.BaseHit hit = NiReviewDisplay.toBaseRange(start, end, session.baseText(), session.document());
        session.pushUndoCheckpoint();
        session.addComment(hit.start(), hit.end(), text, start == end);
        rebuildDisplay();
    }

    private void deleteChange(String id) {
        if (session == null) {
            return;
        }
        session.pushUndoCheckpoint();
        session.deleteChange(id);
        rebuildDisplay();
    }

    private void deleteComment(String id) {
        if (session == null) {
            return;
        }
        session.pushUndoCheckpoint();
        session.deleteComment(id);
        rebuildDisplay();
    }

    private boolean undoLektorEdit() {
        if (session == null || editBaseline != null) {
            return false;
        }
        if (!session.undoCheckpoint()) {
            return false;
        }
        rebuildDisplay();
        return true;
    }

    private boolean redoLektorEdit() {
        if (session == null || editBaseline != null) {
            return false;
        }
        if (!session.redoCheckpoint()) {
            return false;
        }
        rebuildDisplay();
        return true;
    }

    private void persistCurrent() {
        flushLektorEdits();
        if (currentKey == null || session == null) {
            return;
        }
        loaded.reviews().put(currentKey, session.document());
    }

    private void flushLektorEdits() {
        editPause.stop();
        if (session == null || suppressEdit || editBaseline == null) {
            return;
        }
        String now = pendingEditorText != null ? pendingEditorText : editor.getText();
        String from = editBaseline;
        editBaseline = null;
        pendingEditorText = null;
        if (from.equals(now)) {
            return;
        }
        session.pushUndoCheckpoint();
        NiReviewChange change = session.recordDisplayEdit(from, now);
        rebuildDisplay();
        placeCaretAfterEdit(change);
    }

    private void placeCaretAfterEdit(NiReviewChange change) {
        if (session == null || change == null) {
            return;
        }
        NiReviewDisplay.Span insert = null;
        NiReviewDisplay.Span delete = null;
        for (NiReviewDisplay.Span span : session.display().spans()) {
            if (!change.getId().equals(span.itemId())) {
                continue;
            }
            if (span.kind() == NiReviewDisplay.SpanKind.INSERT) {
                insert = span;
            } else if (span.kind() == NiReviewDisplay.SpanKind.DELETE) {
                delete = span;
            }
        }
        if (insert != null) {
            editor.selectRange(insert.displayEnd(), insert.displayEnd());
        } else if (delete != null) {
            editor.selectRange(delete.displayStart(), delete.displayStart());
        }
    }

    private void savePackage() {
        persistCurrent();
        writeReturnedTo(zipPath);
    }

    private void savePackageAs() {
        persistCurrent();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Rückgabe-ZIP speichern");
        chooser.setInitialFileName(NiReviewProject.returnZipFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lektoratspaket", "*.zip"));
        File documents = ApplicationPaths.userDocumentsDirectory();
        if (documents != null && documents.isDirectory()) {
            chooser.setInitialDirectory(documents);
        }
        File dest = chooser.showSaveDialog(stage);
        if (dest == null) {
            return;
        }
        dest = NiReviewProject.withNiZipExtension(dest);
        writeReturnedTo(dest.toPath());
    }

    private void writeReturnedTo(Path dest) {
        try {
            NiReviewZip.writeReturned(dest, loaded);
            status.setText("ZIP erstellt: " + dest.getFileName());
        } catch (Exception e) {
            CustomAlert alert = new CustomAlert(javafx.scene.control.Alert.AlertType.ERROR, "Lektorat-ZIP erstellen");
            alert.setContentText(e.getMessage());
            alert.showAndWait(stage);
        }
    }
}
