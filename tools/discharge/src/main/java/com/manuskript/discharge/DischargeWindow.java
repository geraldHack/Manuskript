package com.manuskript.discharge;

import com.manuskript.plugin.PluginHost;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Hauptfenster: Onboarding, Freunde, 1:1-Chat. */
public final class DischargeWindow {

    /** Klarer Abstand Inhalt ↔ Fensterrand (CustomStage inkl. Resize-Zonen). */
    private static final Insets WINDOW_MARGIN = new Insets(28, 32, 28, 32);
    /** Lange Nachrichten starten eingeklappt; darüber Chevron zum Aufklappen. */
    private static final int COLLAPSED_MESSAGE_ROWS = 5;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final PluginHost host;
    private final DischargeClient client;
    private final DischargeStore store;
    private final Runnable onClosed;

    private Stage stage;
    private DischargeModels.Identity identity;
    private DischargeModels.Peer selectedPeer;
    private final ListView<DischargeModels.Peer> friendList = new ListView<>();
    private final VBox chatLog = new VBox(12);
    private ScrollPane chatScroll;
    private final Label statusLabel = new Label();
    private final Label idLabel = new Label();
    private final TextArea input = new TextArea();
    private final TextField friendIdField = new TextField();
    private final VBox incomingBox = new VBox(8);
    /** Scene-Root: hält den Außenabstand zum Fensterrand. */
    private StackPane shell;
    private BorderPane root;
    private long lastFriendshipsRevision = -1;
    private boolean suppressSelectionClear;
    /** Aktueller Chat-Verlauf als Plaintext (für „Kopieren“). */
    private String lastChatPlainText = "";
    private final javafx.beans.value.ChangeListener<Number> chatHeightScrollListener =
            (obs, oldVal, newVal) -> {
                if (chatScroll != null && newVal != null && newVal.doubleValue() > 0) {
                    chatScroll.setVvalue(1.0);
                }
            };
    private Timeline windowGeometrySaveTimeline;
    private boolean windowGeometryPersistenceInstalled;

    public DischargeWindow(PluginHost host, DischargeClient client, DischargeStore store, Runnable onClosed) {
        this.host = host;
        this.client = client;
        this.store = store;
        this.onClosed = onClosed;
    }

    public void show() {
        identity = store.loadIdentity();
        if (stage == null) {
            stage = host.createThemedStage("Discharge 1.1.4");
            stage.setMinWidth(DischargeStore.WindowGeometry.MIN_WIDTH);
            stage.setMinHeight(DischargeStore.WindowGeometry.MIN_HEIGHT);
            DischargeStore.WindowGeometry geo = store.loadWindowGeometry();
            root = new BorderPane();
            shell = new StackPane(root);
            StackPane.setMargin(root, WINDOW_MARGIN);
            // "root"-StyleClass nur auf dem Shell belassen (Scene setzt sie),
            // damit CSS/Themes den Innenabstand nicht plattmachen.
            root.getStyleClass().remove("root");
            Scene scene = new Scene(shell, geo.width(), geo.height());
            host.attachScene(stage, scene);
            applyWindowGeometry(geo);
            installWindowGeometryPersistence();
            root.getStyleClass().remove("root");
            shell.getStyleClass().remove("root");
            friendList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
                if (suppressSelectionClear && n == null) {
                    return;
                }
                selectedPeer = n;
                reloadChatLog();
            });
            stage.focusedProperty().addListener((obs, was, isNow) -> {
                if (Boolean.TRUE.equals(isNow)) {
                    host.setToolbarAttention("discharge", false);
                }
            });
        }
        rebuild();
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();
        if (identity != null) {
            refreshFriends();
        }
        // Beim Öffnen nach Layout nochmals nach unten
        scrollChatToBottom();
    }

    private void applyWindowGeometry(DischargeStore.WindowGeometry geo) {
        if (stage == null || geo == null) {
            return;
        }
        DischargeStore.WindowGeometry g = geo.clamped();
        stage.setWidth(g.width());
        stage.setHeight(g.height());
        if (g.x() != null && g.y() != null) {
            stage.setX(g.x());
            stage.setY(g.y());
        }
    }

    private void installWindowGeometryPersistence() {
        if (stage == null || windowGeometryPersistenceInstalled) {
            return;
        }
        windowGeometryPersistenceInstalled = true;
        javafx.beans.value.ChangeListener<Number> listener =
                (obs, oldVal, newVal) -> scheduleSaveWindowGeometry();
        stage.widthProperty().addListener(listener);
        stage.heightProperty().addListener(listener);
        stage.xProperty().addListener(listener);
        stage.yProperty().addListener(listener);
        stage.setOnHidden(e -> {
            saveWindowGeometry();
            if (onClosed != null) {
                onClosed.run();
            }
        });
    }

    private void scheduleSaveWindowGeometry() {
        if (windowGeometrySaveTimeline != null) {
            windowGeometrySaveTimeline.stop();
        }
        windowGeometrySaveTimeline = new Timeline(
                new KeyFrame(Duration.millis(450), e -> saveWindowGeometry()));
        windowGeometrySaveTimeline.play();
    }

    private void saveWindowGeometry() {
        if (stage == null) {
            return;
        }
        try {
            store.saveWindowGeometry(stage.getWidth(), stage.getHeight(), stage.getX(), stage.getY());
        } catch (Exception ignored) {
        }
    }

    public void close() {
        if (stage != null) {
            stage.close();
        }
    }

    /** Ob das Discharge-Fenster sichtbar und fokussiert ist. */
    public boolean isStageFocused() {
        return stage != null && stage.isShowing() && stage.isFocused();
    }

    public void onPolled(DischargeModels.PollResult result) {
        if (result == null || identity == null) {
            return;
        }
        boolean relevant = false;
        for (DischargeModels.ChatMessage msg : result.messages()) {
            if (selectedPeer != null
                    && (selectedPeer.id().equals(msg.from()) || selectedPeer.id().equals(msg.to()))) {
                relevant = true;
                break;
            }
        }
        if (relevant) {
            reloadChatLog();
        }
        // Freundesliste inkl. Online-Status regelmäßig nachziehen
        lastFriendshipsRevision = result.friendshipsRevision();
        refreshFriends();
    }

    private void rebuild() {
        String bg = host.themeColor(0);
        String fg = host.themeColor(1);
        // Hintergrund auf Shell + Content, damit der Außenrand nicht „fremd“ wirkt
        String bgStyle = "-fx-background-color: " + bg + ";";
        if (shell != null) {
            shell.setStyle(bgStyle);
            StackPane.setMargin(root, WINDOW_MARGIN);
        }
        root.setStyle(bgStyle);
        statusLabel.setStyle("-fx-text-fill: " + fg + ";");
        idLabel.setStyle("-fx-text-fill: " + fg + "; -fx-font-weight: bold;");

        if (identity == null || !identity.isValid()) {
            root.setTop(null);
            root.setLeft(null);
            root.setCenter(buildOnboarding(fg));
            BorderPane.setMargin(statusLabel, new Insets(16, 0, 0, 0));
            root.setBottom(statusLabel);
            return;
        }
        idLabel.setText("Du: " + identity.id() + "  (" + nullToEmpty(identity.displayName()) + ")");
        root.setTop(buildHeader(fg));
        root.setLeft(buildFriendsPane(fg));
        root.setCenter(buildChatPane(fg));
        BorderPane.setMargin(statusLabel, new Insets(16, 0, 0, 0));
        root.setBottom(statusLabel);
    }

    private VBox buildOnboarding(String fg) {
        Label title = new Label("Willkommen bei Discharge");
        title.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label hint = new Label(
                "Gib nur deinen Namen ein (Buchstaben und Zahlen, z. B. Gerald). "
                        + "Die eindeutige ID mit Nummer vergibt Discharge automatisch.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: " + fg + ";");
        TextField nameField = new TextField();
        nameField.setPromptText("Name, z. B. Gerald");
        Button register = new Button("Registrieren");
        register.setDefaultButton(true);
        register.setOnAction(e -> doRegister(nameField.getText()));
        nameField.setOnAction(e -> doRegister(nameField.getText()));
        VBox box = new VBox(16, title, hint, nameField, register);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 8, 8, 4));
        box.setMaxWidth(440);
        return box;
    }

    private HBox buildHeader(String fg) {
        Button copy = new Button("ID kopieren");
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(identity.id());
            Clipboard.getSystemClipboard().setContent(cc);
            setStatus("ID kopiert.");
        });
        CheckBox sound = new CheckBox("Ton bei neuen Nachrichten");
        sound.setSelected(store.soundEnabled());
        sound.setStyle("-fx-text-fill: " + fg + ";");
        sound.setOnAction(e -> {
            try {
                store.setSoundEnabled(sound.isSelected());
            } catch (Exception ex) {
                setStatus("Einstellung speichern fehlgeschlagen: " + ex.getMessage());
            }
        });
        CheckBox alwaysExpand = new CheckBox("Immer ausklappen");
        alwaysExpand.setSelected(store.alwaysExpandMessages());
        alwaysExpand.setStyle("-fx-text-fill: " + fg + ";");
        alwaysExpand.setOnAction(e -> {
            try {
                store.setAlwaysExpandMessages(alwaysExpand.isSelected());
                reloadChatLog();
            } catch (Exception ex) {
                setStatus("Einstellung speichern fehlgeschlagen: " + ex.getMessage());
            }
        });
        CheckBox shareOwn = new CheckBox("Meinen Online-Status senden");
        shareOwn.setSelected(store.shareOwnPresence());
        shareOwn.setStyle("-fx-text-fill: " + fg + ";");
        shareOwn.setOnAction(e -> {
            try {
                boolean share = shareOwn.isSelected();
                store.setShareOwnPresence(share);
                client.setShareOwnPresence(share);
                if (identity != null && identity.isValid()) {
                    client.touchPresence(identity.token()).exceptionally(ex -> null);
                }
                // Freundesliste unverändert lassen — nur eigenen Status an den Server
                friendList.refresh();
                setStatus(share
                        ? "Freunde sehen dich als online (solange Discharge aktiv ist)."
                        : "Freunde sehen dich als offline. Die Anzeige der anderen bleibt.");
            } catch (Exception ex) {
                setStatus("Einstellung speichern fehlgeschlagen: " + ex.getMessage());
            }
        });
        Button refresh = new Button("Aktualisieren");
        refresh.setOnAction(e -> refreshFriends());
        HBox bar = new HBox(12, idLabel, copy, sound, alwaysExpand, shareOwn, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 16, 0));
        return bar;
    }

    private VBox buildFriendsPane(String fg) {
        Label friendsTitle = new Label("Freunde");
        friendsTitle.setStyle("-fx-text-fill: " + fg + "; -fx-font-weight: bold;");
        friendList.setPrefWidth(220);
        friendList.setCellFactory(lv -> new ListCell<>() {
            private final Circle dot = new Circle(5);
            private final Label nameLabel = new Label();
            private final HBox row = new HBox(8, dot, nameLabel);

            {
                row.setAlignment(Pos.CENTER_LEFT);
                nameLabel.setWrapText(true);
            }

            @Override
            protected void updateItem(DischargeModels.Peer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String name = item.displayName() == null || item.displayName().isBlank()
                        ? item.id() : item.displayName();
                // Immer Status der Freunde zeigen — unabhängig vom eigenen „Status senden“
                nameLabel.setText(name + "\n" + item.id()
                        + (item.online() ? "\n● online" : "\n○ offline"));
                nameLabel.setStyle("-fx-text-fill: " + host.themeColor(1) + ";");
                dot.setFill(item.online() ? Color.web("#2ecc71") : Color.web("#95a5a6"));
                setText(null);
                setGraphic(row);
            }
        });

        Label incomingTitle = new Label("Anfragen");
        incomingTitle.setStyle("-fx-text-fill: " + fg + "; -fx-font-weight: bold;");
        incomingBox.setPadding(new Insets(8, 0, 12, 0));

        friendIdField.setPromptText("Freund-ID (z. B. Anna.1234)");
        Button add = new Button("Anfragen");
        add.setOnAction(e -> doFriendRequest(friendIdField.getText()));
        friendIdField.setOnAction(e -> doFriendRequest(friendIdField.getText()));
        HBox addRow = new HBox(10, friendIdField, add);
        HBox.setHgrow(friendIdField, Priority.ALWAYS);

        VBox left = new VBox(12, friendsTitle, friendList, incomingTitle, incomingBox, addRow);
        VBox.setVgrow(friendList, Priority.ALWAYS);
        left.setPrefWidth(260);
        left.setPadding(new Insets(0, 20, 0, 0));
        return left;
    }

    private BorderPane buildChatPane(String fg) {
        chatLog.setPadding(new Insets(16));
        chatScroll = new ScrollPane(chatLog);
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("scroll-pane");
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Button copyChat = new Button("Chat kopieren");
        copyChat.setOnAction(e -> copyChatToClipboard());
        HBox chatHeader = new HBox(copyChat);
        chatHeader.setAlignment(Pos.CENTER_RIGHT);
        chatHeader.setPadding(new Insets(0, 0, 8, 0));

        input.setPromptText("Nachricht…");
        input.setPrefRowCount(3);
        input.setWrapText(true);
        Button send = new Button("Senden");
        send.setOnAction(e -> doSend());
        // Enter im Eingabefeld sendet — nicht als Default-Button (sonst klaut
        // Enter im Freund-ID-Feld die Chat-Meldung „Zuerst einen Freund wählen“).
        input.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                doSend();
            }
        });
        HBox bottom = new HBox(12, input, send);
        HBox.setHgrow(input, Priority.ALWAYS);
        bottom.setAlignment(Pos.BOTTOM_LEFT);
        bottom.setPadding(new Insets(16, 0, 0, 0));
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(0, 4, 0, 8));
        pane.setTop(chatHeader);
        pane.setCenter(chatScroll);
        pane.setBottom(bottom);
        return pane;
    }

    private void doRegister(String name) {
        String cleaned = name == null ? "" : name.trim();
        String invalid = validateDisplayName(cleaned);
        if (invalid != null) {
            setStatus(invalid);
            return;
        }
        setStatus("Registriere…");
        client.register(cleaned).whenComplete((id, err) -> Platform.runLater(() -> {
            if (err != null) {
                setStatus(rootCause(err));
                return;
            }
            try {
                store.saveIdentity(id);
                identity = id;
                setStatus("Registriert als " + id.id());
                rebuild();
                refreshFriends();
            } catch (Exception ex) {
                setStatus("Speichern fehlgeschlagen: " + ex.getMessage());
            }
        }));
    }

    private void refreshFriends() {
        if (identity == null) {
            return;
        }
        setStatus("Lade Freunde…");
        final String keepId = selectedPeer != null ? selectedPeer.id() : null;
        client.friends(identity.token()).whenComplete((snap, err) -> Platform.runLater(() -> {
            if (err != null) {
                setStatus(rootCause(err));
                return;
            }
            List<DischargeModels.Peer> items = new ArrayList<>(snap.friends());
            suppressSelectionClear = true;
            try {
                friendList.getItems().setAll(items);
                DischargeModels.Peer restore = null;
                if (keepId != null) {
                    for (DischargeModels.Peer p : items) {
                        if (keepId.equals(p.id())) {
                            restore = p;
                            break;
                        }
                    }
                }
                if (restore == null && items.size() == 1) {
                    restore = items.get(0);
                }
                if (restore != null) {
                    friendList.getSelectionModel().select(restore);
                    selectedPeer = restore;
                } else if (keepId != null) {
                    selectedPeer = null;
                    friendList.getSelectionModel().clearSelection();
                }
            } finally {
                suppressSelectionClear = false;
            }
            incomingBox.getChildren().clear();
            String fg = host.themeColor(1);
            for (DischargeModels.Peer peer : snap.incoming()) {
                Label label = new Label(peer.displayName() + " (" + peer.id() + ")");
                label.setStyle("-fx-text-fill: " + fg + ";");
                Button accept = new Button("Annehmen");
                Button reject = new Button("Ablehnen");
                accept.setOnAction(e -> respond(peer.id(), "accept"));
                reject.setOnAction(e -> respond(peer.id(), "reject"));
                HBox row = new HBox(6, label, accept, reject);
                row.setAlignment(Pos.CENTER_LEFT);
                incomingBox.getChildren().add(row);
            }
            if (snap.incoming().isEmpty()) {
                Label none = new Label("Keine offenen Anfragen");
                none.setStyle("-fx-text-fill: " + fg + "; -fx-opacity: 0.7;");
                incomingBox.getChildren().add(none);
            }
            if (selectedPeer != null) {
                reloadChatLog();
            }
            setStatus("Freunde: " + snap.friends().size()
                    + " · Anfragen: " + snap.incoming().size()
                    + " · ausstehend: " + snap.outgoing().size());
        }));
    }

    private void respond(String fromId, String action) {
        client.respondFriend(identity.token(), fromId, action).whenComplete((v, err) -> Platform.runLater(() -> {
            if (err != null) {
                setStatus(rootCause(err));
                return;
            }
            setStatus("Anfrage " + ("accept".equals(action) ? "angenommen" : "abgelehnt"));
            if ("accept".equals(action)) {
                // Sofort auswählen, sobald die Liste nachzieht
                selectedPeer = new DischargeModels.Peer(fromId, fromId, "accepted", false);
            }
            lastFriendshipsRevision = -1; // erzwingt Abgleich beim nächsten Poll
            refreshFriends();
        }));
    }

    private void doFriendRequest(String toId) {
        if (toId == null || toId.isBlank()) {
            setStatus("Bitte eine ID eingeben.");
            return;
        }
        client.requestFriend(identity.token(), toId.trim()).whenComplete((v, err) -> Platform.runLater(() -> {
            if (err != null) {
                setStatus(rootCause(err));
                return;
            }
            friendIdField.clear();
            setStatus("Anfrage gesendet an " + toId.trim());
            refreshFriends();
        }));
    }

    private void doSend() {
        if (selectedPeer == null) {
            setStatus("Zuerst einen Freund wählen.");
            return;
        }
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        // Erst bei Erfolg leeren — bei Timeout/Fehler bleibt der Text erhalten
        setStatus("Sende…");
        client.send(identity.token(), selectedPeer.id(), text).whenComplete((msg, err) -> Platform.runLater(() -> {
            if (err != null) {
                if (input.getText() == null || input.getText().isBlank()) {
                    input.setText(text);
                    input.positionCaret(text.length());
                }
                setStatus("Nicht gesendet: " + rootCause(err));
                return;
            }
            input.clear();
            try {
                store.appendHistory(msg);
                store.setPollCursor(Math.max(store.pollCursor(), msg.createdAt()));
            } catch (Exception ex) {
                setStatus("Lokal speichern fehlgeschlagen: " + ex.getMessage());
            }
            reloadChatLog();
            setStatus("Gesendet.");
        }));
    }

    private void reloadChatLog() {
        chatLog.getChildren().clear();
        lastChatPlainText = "";
        String fg = host.themeColor(1);
        if (selectedPeer == null || identity == null) {
            Label empty = new Label("Wähle einen Freund zum Chatten.");
            empty.setStyle("-fx-text-fill: " + fg + ";");
            chatLog.getChildren().add(empty);
            return;
        }
        String channel = channelId(identity.id(), selectedPeer.id());
        List<DischargeModels.ChatMessage> history = store.loadHistory(channel);
        if (history.isEmpty()) {
            Label empty = new Label("Noch keine Nachrichten.");
            empty.setStyle("-fx-text-fill: " + fg + "; -fx-opacity: 0.7;");
            chatLog.getChildren().add(empty);
            return;
        }
        StringBuilder plain = new StringBuilder();
        for (DischargeModels.ChatMessage msg : history) {
            boolean mine = identity.id().equals(msg.from());
            String who = mine ? "Du" : (selectedPeer.displayName() == null || selectedPeer.displayName().isBlank()
                    ? msg.from() : selectedPeer.displayName());
            String time = TIME_FMT.format(Instant.ofEpochSecond(Math.max(0, msg.createdAt())));
            String body = msg.text() == null ? "" : msg.text();
            String full = who + " · " + time + "\n" + body;
            if (plain.length() > 0) {
                plain.append("\n\n");
            }
            plain.append(full);

            int fullRows = Math.max(2, full.split("\n", -1).length + estimateWrapRows(body, 56));
            boolean longMessage = fullRows > COLLAPSED_MESSAGE_ROWS;
            boolean startExpanded = store.alwaysExpandMessages();

            TextArea bubble = new TextArea(full);
            bubble.setEditable(false);
            bubble.setWrapText(true);
            bubble.setFocusTraversable(true);
            bubble.setMaxWidth(520);
            String bubbleStyle = "-fx-text-fill: " + fg + "; -fx-control-inner-background: "
                    + (mine ? "rgba(80,140,220,0.25)" : "rgba(120,120,120,0.2)") + ";"
                    + " -fx-background-radius: 8; -fx-background-color: transparent;"
                    + " -fx-border-color: transparent; -fx-padding: 4;";
            bubble.setStyle(bubbleStyle);

            VBox messageBox = new VBox(2);
            messageBox.setMaxWidth(560);
            if (mine) {
                messageBox.setAlignment(Pos.TOP_RIGHT);
            } else {
                messageBox.setAlignment(Pos.TOP_LEFT);
            }

            if (longMessage) {
                Button chevron = new Button("▼");
                chevron.setFocusTraversable(false);
                chevron.setStyle("-fx-text-fill: " + fg + "; -fx-background-color: transparent;"
                        + " -fx-padding: 0 6; -fx-font-size: 11px; -fx-cursor: hand;");
                final boolean[] expanded = { startExpanded };
                Runnable applyFold = () -> {
                    if (expanded[0]) {
                        bubble.setPrefRowCount(Math.min(60, fullRows));
                        bubble.setMinHeight(Region.USE_COMPUTED_SIZE);
                        bubble.setMaxHeight(Double.MAX_VALUE);
                        chevron.setText("▲");
                        chevron.setTooltip(new javafx.scene.control.Tooltip("Nachricht einklappen"));
                    } else {
                        bubble.setPrefRowCount(COLLAPSED_MESSAGE_ROWS);
                        bubble.setMaxHeight(COLLAPSED_MESSAGE_ROWS * 18.0 + 16);
                        chevron.setText("▼");
                        chevron.setTooltip(new javafx.scene.control.Tooltip("Nachricht ausklappen"));
                    }
                };
                chevron.setOnAction(e -> {
                    expanded[0] = !expanded[0];
                    applyFold.run();
                });
                applyFold.run();
                HBox chevronRow = new HBox(chevron);
                chevronRow.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                messageBox.getChildren().addAll(chevronRow, bubble);
            } else {
                bubble.setPrefRowCount(fullRows);
                messageBox.getChildren().add(bubble);
            }

            HBox row = new HBox(messageBox);
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            row.setPadding(new Insets(2, 0, 2, 0));
            chatLog.getChildren().add(row);
        }
        lastChatPlainText = plain.toString();
        scrollChatToBottom();
    }

    private void copyChatToClipboard() {
        if (lastChatPlainText == null || lastChatPlainText.isBlank()) {
            setStatus("Kein Chat zum Kopieren.");
            return;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(lastChatPlainText);
        Clipboard.getSystemClipboard().setContent(cc);
        setStatus("Chat kopiert.");
    }

    private void scrollChatToBottom() {
        if (chatScroll == null) {
            return;
        }
        chatLog.heightProperty().removeListener(chatHeightScrollListener);
        chatLog.heightProperty().addListener(chatHeightScrollListener);

        Runnable pin = () -> {
            if (chatScroll == null) {
                return;
            }
            chatLog.applyCss();
            chatLog.layout();
            chatScroll.applyCss();
            chatScroll.layout();
            double content = chatLog.getBoundsInLocal().getHeight();
            double view = chatScroll.getViewportBounds().getHeight();
            if (content > view && view > 0) {
                chatScroll.setVvalue(1.0);
            } else {
                chatScroll.setVvalue(1.0);
            }
            if (!chatLog.getChildren().isEmpty()) {
                chatScroll.setVvalue(1.0);
            }
        };
        Platform.runLater(() -> {
            pin.run();
            Platform.runLater(() -> {
                pin.run();
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
                pause.setOnFinished(e -> {
                    pin.run();
                    javafx.animation.PauseTransition again =
                            new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
                    again.setOnFinished(ev -> pin.run());
                    again.play();
                });
                pause.play();
            });
        });
    }

    /** Grobe Zusatzzeilen für Umbruch bei langen Absätzen. */
    private static int estimateWrapRows(String text, int cols) {
        if (text == null || text.isEmpty() || cols < 8) {
            return 0;
        }
        int extra = 0;
        for (String line : text.split("\n", -1)) {
            int len = line.length();
            if (len > cols) {
                extra += (len - 1) / cols;
            }
        }
        return extra;
    }

    private static String channelId(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "_" + b : b + "_" + a;
    }

    private void setStatus(String text) {
        statusLabel.setText(text == null ? "" : text);
    }

    /**
     * Anzeigename: nur Buchstaben/Zahlen. Kein Punkt und keine selbst angehängte .nnnn-Nummer.
     * @return Fehlermeldung oder {@code null} wenn gültig
     */
    static String validateDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "Bitte einen Namen eingeben.";
        }
        if (name.indexOf('.') >= 0) {
            return "Bitte nur den Namen eingeben (ohne .Nummer). Die ID vergibt Discharge automatisch.";
        }
        if (name.length() < 2) {
            return "Name zu kurz (mindestens 2 Zeichen).";
        }
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            if (!Character.isLetter(cp) && !Character.isDigit(cp)) {
                return "Nur Buchstaben und Zahlen — keine Leerzeichen oder Sonderzeichen.";
            }
            i += Character.charCount(cp);
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String rootCause(Throwable err) {
        Throwable t = err;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = t.getClass().getSimpleName();
        }
        String lower = msg.toLowerCase();
        if (lower.contains("timed out") || lower.contains("timeout") || lower.contains("connect timed")) {
            return "Server nicht erreichbar (Timeout). Nachricht bleibt in der Eingabe.";
        }
        return msg;
    }
}
