package com.manuskript.review;

import com.manuskript.ManuskriptTextEditor;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.util.Duration;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;

/**
 * Kapitel-Editor: Lektor sieht Word-artige Überarbeitung, Autor übernimmt oder verwirft.
 */
public final class NiReviewChapterSupport {

    private final ManuskriptTextEditor editor;
    private final Label banner = new Label();
    private final Button acceptButton = new Button("Übernehmen");
    private final Button rejectButton = new Button("Verwerfen");
    private final Button commentButton = new Button("Kommentar");
    private final VBox bannerBox;
    private final HBox bannerActions;
    private final NiReviewSidePanel sidePanel = new NiReviewSidePanel();
    private SplitPane hostSplit;
    private NiReviewSession session;
    private File bookDir;
    private String chapterKey;
    private boolean suppress;
    private int suppressGeneration;
    private boolean lektorMode;
    private String selectedChangeId;
    private String lastDisplay = "";
    private String editBaseline;
    private String pendingEditorText;
    private final PauseTransition editPause = new PauseTransition(Duration.millis(400));
    private Runnable onBaseChanged;

    public NiReviewChapterSupport(ManuskriptTextEditor editor) {
        this.editor = editor;
        banner.setWrapText(true);
        banner.setMaxWidth(Double.MAX_VALUE);
        acceptButton.getStyleClass().addAll("dialog-button", "ni-review-accept", "ni-review-compact");
        rejectButton.getStyleClass().addAll("dialog-button", "ni-review-compact");
        acceptButton.setOnAction(e -> acceptSelectedOrFirst());
        rejectButton.setOnAction(e -> rejectSelectedOrFirst());
        commentButton.getStyleClass().addAll("dialog-button", "ni-review-compact");
        commentButton.setOnAction(e -> sidePanel.focusComposer());
        commentButton.setVisible(false);
        commentButton.setManaged(false);
        acceptButton.setVisible(false);
        acceptButton.setManaged(false);
        rejectButton.setVisible(false);
        rejectButton.setManaged(false);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setMinHeight(Region.USE_PREF_SIZE);
        bannerActions = new HBox(10, acceptButton, rejectButton, commentButton);
        bannerActions.setAlignment(Pos.CENTER_LEFT);
        bannerBox = new VBox(8, banner, bannerActions);
        banner.getStyleClass().add("ni-review-banner-text");
        bannerBox.setPadding(new Insets(8, 12, 8, 12));
        bannerBox.getStyleClass().add("ni-review-banner");
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);
        bannerActions.setVisible(false);
        bannerActions.setManaged(false);
        sidePanel.setOnAddComment(this::addCommentFromPanel);
        editPause.setOnFinished(e -> flushLektorEdits());
        editor.setUndoInterceptor(this::undoLektorEdit);
        editor.setRedoInterceptor(this::redoLektorEdit);
        sidePanel.getRoot().setVisible(false);
        sidePanel.getRoot().setManaged(false);
    }

    public void setOnBaseChanged(Runnable onBaseChanged) {
        this.onBaseChanged = onBaseChanged;
    }

    public VBox getBanner() {
        return bannerBox;
    }

    public VBox getSidePanel() {
        return sidePanel.getRoot();
    }

    public void bindSplitPane(SplitPane split) {
        this.hostSplit = split;
        if (split != null && !split.getStyleClass().contains("ni-review-split")) {
            split.getStyleClass().add("ni-review-split");
        }
        hideSidePanel();
    }

    public NiReviewSession session() {
        return session;
    }

    public boolean isReviewDisplayActive() {
        return session != null && (lektorMode || session.hasOpenItems());
    }

    public String textForSave() {
        return session != null ? session.baseText() : null;
    }

    public void onChapterLoaded(File bookDirectory, String key, String liveMarkdown) {
        bookDir = bookDirectory;
        chapterKey = key;
        session = null;
        selectedChangeId = null;
        lastDisplay = "";
        editBaseline = null;
        pendingEditorText = null;
        editPause.stop();
        boolean lektorCopy = NiReviewRole.forBook(bookDirectory) == NiReviewRole.LEKTOR;
        NiReviewDocument document = NiReviewStore.loadReview(bookDirectory, key);
        boolean hasReview = document != null && document.hasOpenItems();
        boolean returned = NiReviewStore.isReturned(bookDirectory, key) || (hasReview && !lektorCopy);
        lektorMode = NiReviewRole.isLektorEditing(bookDirectory, returned);
        if (lektorMode) {
            String base = NiReviewProject.readChapterSnapshot(bookDirectory, key);
            if (base == null) {
                base = liveMarkdown == null ? "" : liveMarkdown;
            }
            if (document == null) {
                document = new NiReviewDocument();
                document.setChapterKey(key);
                document.setBaseHash(NiReviewHashes.sha256(base));
            }
            session = new NiReviewSession(base, document, NiReviewSession.PersistMode.PROJECT);
            applyLektorUi();
            showBanner("Überarbeitung: Streichungen rot, Einfügungen grün. Kommentar rechts schreiben.");
            showSidePanel();
            editor.setEditable(true);
            applyDisplay();
            return;
        }
        boolean inLektorat = NiReviewStore.isInLektorat(bookDirectory, key);
        if (hasReview) {
            session = new NiReviewSession(liveMarkdown, document, NiReviewSession.PersistMode.PROJECT);
            applyAuthorUi();
            showBanner("Rücksendung liegt vor. Du kannst weiter schreiben — "
                    + "Übernehmen oder Verwerfen gilt für die Anmerkungen.");
            applyDisplay();
            editor.setEditable(true);
            showSidePanel();
            blinkFirstFindingLater();
            return;
        }
        applyAuthorUi();
        editor.setEditable(true);
        editor.clearNiReviewMarks();
        hideSidePanel();
        if (inLektorat) {
            showBanner("Dieses Kapitel ist im Lektorat. Weiter schreiben erschwert die Rückspielung.");
        } else {
            hideBanner();
        }
    }

    public void onEditorTextChanged(String text) {
        if (session == null || suppress) {
            return;
        }
        if (!lektorMode && !session.hasOpenItems()) {
            return;
        }
        if (editBaseline == null) {
            editBaseline = lastDisplay;
        }
        pendingEditorText = text == null ? "" : text;
        editPause.playFromStart();
        if (onBaseChanged != null) {
            onBaseChanged.run();
        }
    }

    public void persist() throws IOException {
        flushLektorEdits();
        flushAuthorEdits();
        writeReview();
    }

    public void closedOrCleared() {
        editPause.stop();
        editBaseline = null;
        pendingEditorText = null;
        session = null;
        lektorMode = false;
        lastDisplay = "";
        hideBanner();
        commentButton.setVisible(false);
        commentButton.setManaged(false);
        showAuthorDecisionButtons(false);
        sidePanel.setComposerVisible(false);
        hideSidePanel();
        if (editor != null) {
            editor.setEditable(true);
            editor.clearNiReviewMarks();
        }
    }

    private void accept(String id) {
        if (session == null) {
            return;
        }
        session.accept(id);
        selectedChangeId = null;
        if (onBaseChanged != null) {
            onBaseChanged.run();
        }
        afterDecision();
    }

    private void reject(String id) {
        if (session == null) {
            return;
        }
        session.reject(id);
        selectedChangeId = null;
        afterDecision();
    }

    private void acceptSelectedOrFirst() {
        String id = currentChangeId();
        if (id != null) {
            accept(id);
        }
    }

    private void rejectSelectedOrFirst() {
        String id = currentChangeId();
        if (id != null) {
            reject(id);
        }
    }

    private String currentChangeId() {
        if (session == null) {
            return null;
        }
        if (selectedChangeId != null && session.findChange(selectedChangeId) != null
                && session.findChange(selectedChangeId).isOpen()) {
            return selectedChangeId;
        }
        var open = session.document().openChanges();
        return open.isEmpty() ? null : open.get(0).getId();
    }

    private void resolveComment(String id) {
        if (session == null) {
            return;
        }
        session.pushUndoCheckpoint();
        session.resolveComment(id);
        afterDecision();
    }

    private void deleteChange(String id) {
        if (session == null) {
            return;
        }
        session.pushUndoCheckpoint();
        session.deleteChange(id);
        afterDecision();
    }

    private void deleteComment(String id) {
        if (session == null) {
            return;
        }
        session.pushUndoCheckpoint();
        session.deleteComment(id);
        afterDecision();
    }

    private boolean undoLektorEdit() {
        if (!lektorMode || session == null || editBaseline != null) {
            return false;
        }
        if (!session.undoCheckpoint()) {
            return false;
        }
        applyDisplay();
        try {
            writeReview();
        } catch (IOException ignored) {
        }
        return true;
    }

    private boolean redoLektorEdit() {
        if (!lektorMode || session == null || editBaseline != null) {
            return false;
        }
        if (!session.redoCheckpoint()) {
            return false;
        }
        applyDisplay();
        try {
            writeReview();
        } catch (IOException ignored) {
        }
        return true;
    }

    private void afterDecision() {
        try {
            persist();
        } catch (IOException ignored) {
        }
        if (lektorMode) {
            applyDisplay();
            return;
        }
        if (session == null || !session.hasOpenItems()) {
            String base = session != null ? session.baseText() : null;
            session = null;
            editor.setEditable(true);
            editor.clearNiReviewMarks();
            hideSidePanel();
            if (base != null) {
                suppress = true;
                try {
                    editor.setText(base);
                } finally {
                    suppress = false;
                }
            }
            hideBanner();
            return;
        }
        applyDisplay();
    }

    private void flushLektorEdits() {
        editPause.stop();
        if (!lektorMode || session == null || suppress || editBaseline == null) {
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
        applyDisplay();
        placeCaretAfterEdit(change);
    }

    private void flushAuthorEdits() {
        editPause.stop();
        if (lektorMode || session == null || suppress || editBaseline == null) {
            return;
        }
        String now = pendingEditorText != null ? pendingEditorText : editor.getText();
        String from = editBaseline;
        editBaseline = null;
        pendingEditorText = null;
        if (from.equals(now)) {
            return;
        }
        int caret = session.applyAuthorDisplayEdit(from, now);
        applyDisplay();
        int length = editor.getText() == null ? 0 : editor.getText().length();
        int safe = Math.max(0, Math.min(length, caret));
        editor.selectRange(safe, safe);
    }

    private void writeReview() throws IOException {
        if (session == null || bookDir == null || chapterKey == null) {
            return;
        }
        if (!lektorMode && !session.hasOpenItems()) {
            NiReviewStore.deleteReview(bookDir, chapterKey);
            return;
        }
        NiReviewStore.saveReview(bookDir, chapterKey, session.document());
    }

    private void applyDisplay() {
        if (session == null) {
            return;
        }
        suppress = true;
        int token = ++suppressGeneration;
        try {
            NiReviewDisplay.Result result = session.display();
            lastDisplay = result.text();
            editor.replaceAllTextPreservingCaretAndViewport(result.text(), false);
            editor.clearUndoHistory();
            editor.applyNiReviewSpans(result, this::revealChange, this::revealComment);
            if (!lektorMode) {
                applyAuthorUi();
            }
            sidePanel.refresh(session.document());
        } finally {
            Platform.runLater(() -> {
                if (token == suppressGeneration) {
                    suppress = false;
                }
            });
        }
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

    private void addCommentFromPanel(String text) {
        flushLektorEdits();
        if (!lektorMode || session == null || text == null || text.isBlank()) {
            return;
        }
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        NiReviewDisplay.BaseHit hit = NiReviewDisplay.toBaseRange(
                start, end, session.baseText(), session.document());
        session.pushUndoCheckpoint();
        session.addComment(hit.start(), hit.end(), text, start == end);
        applyDisplay();
        try {
            persist();
        } catch (IOException ignored) {
        }
        if (onBaseChanged != null) {
            onBaseChanged.run();
        }
    }

    private void showSidePanel() {
        VBox panel = sidePanel.getRoot();
        panel.setVisible(true);
        panel.setManaged(true);
        if (hostSplit != null && !hostSplit.getItems().contains(panel)) {
            hostSplit.getItems().add(panel);
            hostSplit.setDividerPositions(0.74);
        }
    }

    private void hideSidePanel() {
        VBox panel = sidePanel.getRoot();
        if (hostSplit != null) {
            hostSplit.getItems().remove(panel);
        }
        panel.setVisible(false);
        panel.setManaged(false);
    }

    private void revealChange(String id) {
        jump(id, false);
    }

    private void revealComment(String id) {
        jump(id, true);
    }

    private void jump(String id, boolean comment) {
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
        if (!comment) {
            selectedChangeId = id;
        }
        editor.revealMatchAt(start, end);
        editor.blinkRangeTwice(start, end);
    }

    private void blinkFirstFindingLater() {
        Platform.runLater(() -> Platform.runLater(this::blinkFirstFinding));
    }

    private void blinkFirstFinding() {
        if (session == null) {
            return;
        }
        for (NiReviewDisplay.Span span : session.display().spans()) {
            if (span.displayEnd() > span.displayStart()) {
                editor.revealMatchAt(span.displayStart(), span.displayEnd());
                editor.blinkRangeTwice(span.displayStart(), span.displayEnd());
                return;
            }
        }
    }

    private void applyLektorUi() {
        sidePanel.setAuthorActions(false);
        sidePanel.setComposerVisible(true);
        sidePanel.setHandlers(this::accept, this::reject, this::revealChange, this::revealComment,
                this::resolveComment, this::deleteChange, this::deleteComment);
        commentButton.setVisible(true);
        commentButton.setManaged(true);
        showAuthorDecisionButtons(false);
        bannerActions.setVisible(true);
        bannerActions.setManaged(true);
    }

    private void applyAuthorUi() {
        sidePanel.setAuthorActions(true);
        sidePanel.setComposerVisible(false);
        sidePanel.setHandlers(this::accept, this::reject, this::revealChange, this::revealComment,
                this::resolveComment, null, null);
        commentButton.setVisible(false);
        commentButton.setManaged(false);
        showAuthorDecisionButtons(session != null && !session.document().openChanges().isEmpty());
    }

    private void showAuthorDecisionButtons(boolean visible) {
        acceptButton.setVisible(visible);
        acceptButton.setManaged(visible);
        rejectButton.setVisible(visible);
        rejectButton.setManaged(visible);
        boolean any = visible || commentButton.isVisible();
        bannerActions.setVisible(any);
        bannerActions.setManaged(any);
    }

    private void showBanner(String text) {
        banner.setText(text);
        bannerBox.setVisible(true);
        bannerBox.setManaged(true);
    }

    private void hideBanner() {
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);
    }

    public boolean isSuppressingEditorEvents() {
        return suppress;
    }
}
