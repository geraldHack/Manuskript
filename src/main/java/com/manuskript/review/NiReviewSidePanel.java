package com.manuskript.review;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.function.Consumer;

/**
 * Rechte Spalte: Änderungen und lesbare Kommentare, ohne Dialog.
 */
public final class NiReviewSidePanel {

    private final VBox root = new VBox(10);
    private final VBox cards = new VBox(10);
    private final Label title = new Label("Lektorat");
    private final VBox composerBox = new VBox(6);
    private final TextArea composer = new TextArea();
    private Consumer<String> onAccept;
    private Consumer<String> onReject;
    private Consumer<String> onJumpChange;
    private Consumer<String> onJumpComment;
    private Consumer<String> onResolveComment;
    private Consumer<String> onAddComment;
    private Consumer<String> onDeleteChange;
    private Consumer<String> onDeleteComment;
    private boolean authorActions;
    private boolean showComposer;

    public NiReviewSidePanel() {
        title.getStyleClass().add("ni-review-title");
        title.setWrapText(true);

        Label composerHint = new Label("Markierung im Text, dann Kommentar hier schreiben.");
        composerHint.getStyleClass().add("ni-review-hint");
        composerHint.setWrapText(true);
        composer.setPromptText("Kommentar zur Markierung …");
        composer.setWrapText(true);
        composer.setPrefRowCount(4);
        composer.getStyleClass().add("ni-review-composer");
        Button add = compactButton("Kommentar setzen", this::submitComposer);
        add.setMaxWidth(Double.MAX_VALUE);
        composerBox.getChildren().addAll(composerHint, composer, add);
        composerBox.getStyleClass().add("ni-review-composer-box");
        composerBox.setVisible(false);
        composerBox.setManaged(false);

        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("ni-review-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        cards.setFillWidth(true);

        root.setPadding(new Insets(12));
        root.setMinWidth(260);
        root.setPrefWidth(320);
        root.getChildren().addAll(title, composerBox, scroll);
        root.getStyleClass().add("ni-review-side-panel");
    }

    public VBox getRoot() {
        return root;
    }

    public void setAuthorActions(boolean authorActions) {
        this.authorActions = authorActions;
        if (authorActions) {
            setComposerVisible(false);
        }
    }

    public void setComposerVisible(boolean visible) {
        showComposer = visible;
        composerBox.setVisible(visible);
        composerBox.setManaged(visible);
    }

    public void focusComposer() {
        if (showComposer) {
            composer.requestFocus();
        }
    }

    public void setOnAddComment(Consumer<String> onAddComment) {
        this.onAddComment = onAddComment;
    }

    public void setHandlers(Consumer<String> onAccept, Consumer<String> onReject,
                            Consumer<String> onJumpChange, Consumer<String> onJumpComment,
                            Consumer<String> onResolveComment,
                            Consumer<String> onDeleteChange, Consumer<String> onDeleteComment) {
        this.onAccept = onAccept;
        this.onReject = onReject;
        this.onJumpChange = onJumpChange;
        this.onJumpComment = onJumpComment;
        this.onResolveComment = onResolveComment;
        this.onDeleteChange = onDeleteChange;
        this.onDeleteComment = onDeleteComment;
    }

    public void refresh(NiReviewDocument document) {
        cards.getChildren().clear();
        if (document == null) {
            title.setText("Lektorat");
            return;
        }
        int changes = document.openChanges().size();
        int comments = document.openComments().size();
        title.setText(changes + " Änderungen · " + comments + " Kommentare");
        for (NiReviewChange change : document.openChanges()) {
            cards.getChildren().add(changeCard(change));
        }
        for (NiReviewComment comment : document.openComments()) {
            cards.getChildren().add(commentCard(comment));
        }
        if (cards.getChildren().isEmpty()) {
            Label empty = new Label(showComposer
                    ? "Noch keine Anmerkungen. Text ändern oder rechts einen Kommentar setzen."
                    : "Keine offenen Anmerkungen.");
            empty.setWrapText(true);
            empty.getStyleClass().add("ni-review-hint");
            bindWrap(empty);
            cards.getChildren().add(empty);
        }
    }

    private void submitComposer() {
        String text = composer.getText();
        if (text == null || text.isBlank() || onAddComment == null) {
            return;
        }
        onAddComment.accept(text);
        composer.clear();
    }

    private VBox changeCard(NiReviewChange change) {
        String kindText = labelFor(change)
                + (NiReviewChange.STATUS_UNRESOLVED.equals(change.getStatus()) ? " · nicht zuordenbar" : "");
        HBox head = cardHeader(iconFor(change), kindText);

        VBox body = new VBox(4);
        String oldText = change.getOldText();
        String newText = change.getNewText();
        if (!NiReviewTexts.collapse(oldText).isEmpty()) {
            body.getChildren().add(diffLine("− ", oldText, "ni-review-old"));
        }
        if (!NiReviewTexts.collapse(newText).isEmpty()) {
            body.getChildren().add(diffLine("+ ", newText, "ni-review-new"));
        }

        HBox buttons = actionRow();
        if (authorActions) {
            buttons.getChildren().add(compactButton("Übernehmen", () -> {
                if (onAccept != null) {
                    onAccept.accept(change.getId());
                }
            }));
            buttons.getChildren().add(compactButton("Verwerfen", () -> {
                if (onReject != null) {
                    onReject.accept(change.getId());
                }
            }));
        }
        buttons.getChildren().add(compactButton("Im Text", () -> {
            if (onJumpChange != null) {
                onJumpChange.accept(change.getId());
            }
        }));
        if (!authorActions && onDeleteChange != null) {
            buttons.getChildren().add(compactButton("Löschen", () -> onDeleteChange.accept(change.getId())));
        }

        VBox card = new VBox(8, head, body, buttons);
        card.getStyleClass().addAll("ni-review-card", "ni-review-card-change", kindClass(change));
        return card;
    }

    private VBox commentCard(NiReviewComment comment) {
        String headText = "Kommentar"
                + (NiReviewComment.STATUS_UNRESOLVED.equals(comment.getStatus()) ? " · nicht zuordenbar" : "");
        HBox head = cardHeader(icon("“", Color.web("#c45a1c")), headText);

        Label body = new Label(comment.getText() == null ? "" : comment.getText());
        body.setWrapText(true);
        body.setMinHeight(Region.USE_PREF_SIZE);
        body.getStyleClass().add("ni-review-card-body");
        bindWrap(body);

        HBox buttons = actionRow();
        buttons.getChildren().add(compactButton("Im Text", () -> {
            if (onJumpComment != null) {
                onJumpComment.accept(comment.getId());
            }
        }));
        if (authorActions) {
            buttons.getChildren().add(compactButton("Erledigt", () -> {
                if (onResolveComment != null) {
                    onResolveComment.accept(comment.getId());
                }
            }));
        } else if (onDeleteComment != null) {
            buttons.getChildren().add(compactButton("Löschen", () -> onDeleteComment.accept(comment.getId())));
        }

        VBox card = new VBox(8, head, body, buttons);
        card.getStyleClass().addAll("ni-review-card", "ni-review-card-comment");
        return card;
    }

    private Label diffLine(String prefix, String raw, String styleClass) {
        String preview = prefix + NiReviewTexts.previewChange(raw);
        Label line = new Label(preview);
        line.setWrapText(true);
        line.getStyleClass().addAll("ni-review-card-body", styleClass);
        bindWrap(line);
        if (NiReviewTexts.wasTruncated(raw)) {
            Tooltip.install(line, new Tooltip(NiReviewTexts.collapse(raw)));
        }
        return line;
    }

    private void bindWrap(Label label) {
        label.maxWidthProperty().bind(cards.widthProperty().subtract(24));
        label.setMinHeight(Region.USE_PREF_SIZE);
    }

    private static HBox cardHeader(StackPane icon, String text) {
        Label kind = new Label(text);
        kind.getStyleClass().add("ni-review-card-title");
        kind.setWrapText(true);
        HBox.setHgrow(kind, Priority.ALWAYS);
        HBox head = new HBox(8, icon, kind);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("ni-review-card-head");
        return head;
    }

    private static HBox actionRow() {
        HBox buttons = new HBox(6);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.getStyleClass().add("ni-review-card-actions");
        return buttons;
    }

    private static Button compactButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("dialog-button", "ni-review-compact");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static StackPane iconFor(NiReviewChange change) {
        return switch (change.getKind()) {
            case NiReviewChange.KIND_DELETE -> icon("−", Color.web("#e57373"));
            case NiReviewChange.KIND_INSERT -> icon("+", Color.web("#81c784"));
            default -> icon("↔", Color.web("#64b5f6"));
        };
    }

    private static String kindClass(NiReviewChange change) {
        return switch (change.getKind()) {
            case NiReviewChange.KIND_DELETE -> "ni-review-kind-delete";
            case NiReviewChange.KIND_INSERT -> "ni-review-kind-insert";
            default -> "ni-review-kind-replace";
        };
    }

    private static StackPane icon(String symbol, Color fill) {
        Circle circle = new Circle(11, fill);
        Text text = new Text(symbol);
        text.setFill(Color.web("#1a1a1a"));
        text.setFont(Font.font("System", FontWeight.BOLD, 13));
        StackPane pane = new StackPane(circle, text);
        pane.setMinSize(22, 22);
        pane.setMaxSize(22, 22);
        pane.getStyleClass().add("ni-review-icon");
        return pane;
    }

    private static String labelFor(NiReviewChange change) {
        return switch (change.getKind()) {
            case NiReviewChange.KIND_DELETE -> "Löschung";
            case NiReviewChange.KIND_INSERT -> "Einfügung";
            default -> "Ersetzung";
        };
    }
}
