package com.manuskript.backup;

import com.manuskript.plugin.PluginHost;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Ziele verwalten, manuell sichern, wiederherstellen.
 */
public final class BackupWindow {

    private static BackupWindow visible;

    private final PluginHost host;
    private final BackupSettings settings;
    private final ObservableList<BackupTarget> items;
    private Stage stage;
    private ListView<BackupTarget> list;
    private TextField nameField;
    private CheckBox enabledBox;
    private ComboBox<BackupKind> kindBox;
    private VBox filesystemBox;
    private VBox sshBox;
    private TextField destField;
    private TextField sshHost;
    private Spinner<Integer> sshPort;
    private TextField sshUser;
    private TextField sshRemote;
    private TextField sshKey;
    private PasswordField sshPassword;
    private CheckBox sshUnknownHost;
    private CheckBox compressBox;
    private CheckBox encryptBox;
    private PasswordField encryptPassword;
    private PasswordField encryptRepeat;
    private CheckBox rememberEncrypt;
    private ComboBox<BackupSchedule> scheduleBox;
    private Spinner<Integer> keepSpinner;
    private Label sourcePathLabel;
    private Label status;
    private boolean applying;

    public BackupWindow(PluginHost host) {
        this.host = host;
        this.settings = BackupSettings.load(host.configDir());
        if (settings.targets == null) {
            settings.targets = new java.util.ArrayList<>();
        }
        this.items = FXCollections.observableArrayList(settings.targets);
    }

    public static void promptScheduled(PluginHost host, BackupTarget target) {
        Platform.runLater(() -> {
            BackupWindow window = visible;
            if (window == null) {
                window = new BackupWindow(host);
            }
            String notice = "Geplantes Backup für „"
                    + (target == null ? "Ziel" : target.displayName())
                    + "“ ist fällig. Passwort prüfen und sichern.";
            window.show(notice);
            if (target != null) {
                window.selectById(target.id);
            }
        });
    }

    public void show() {
        show(null);
    }

    public void show(String notice) {
        visible = this;
        if (stage != null && stage.isShowing()) {
            refreshSourcePath();
            if (notice != null && status != null) {
                status.setText(notice);
            }
            stage.toFront();
            return;
        }
        stage = host.createThemedStage("Projekt-Backup");
        host.attachScene(stage, new Scene(buildUi(notice), 860, 640));
        stage.setOnHidden(e -> {
            if (visible == this) {
                visible = null;
            }
        });
        stage.show();
        refreshSourcePath();
    }

    private void selectById(String id) {
        if (list == null || id == null) {
            return;
        }
        for (BackupTarget item : items) {
            if (id.equals(item.id)) {
                list.getSelectionModel().select(item);
                return;
            }
        }
    }

    private VBox buildUi(String notice) {
        Label intro = new Label(
                "Mehrere Ziele, jedes mit eigenem Rhythmus. Dateisystem (USB, Dropbox, iCloud, …) "
                        + "oder SSH/SCP. Gesichert wird das aktuell geöffnete Buch (nicht die Projektwurzel). "
                        + "Der Überwachungsmodus läuft ohne dieses Fenster, solange "
                        + "Manuskript geöffnet und das Plugin aktiv ist.");
        intro.setWrapText(true);
        intro.getStyleClass().add("dialog-label");

        sourcePathLabel = new Label();
        sourcePathLabel.setWrapText(true);
        sourcePathLabel.getStyleClass().add("dialog-label");
        refreshSourcePath();

        list = new ListView<>(items);
        list.setPrefWidth(220);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(BackupTarget item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName() + " · " + item.kind().label());
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (applying) {
                return;
            }
            if (old != null && old != now) {
                persistTarget(old);
            }
            loadTarget(now);
        });

        Button add = new Button("Neues Ziel");
        add.getStyleClass().add("dialog-button");
        add.setOnAction(e -> addTarget());
        Button copy = new Button("Duplizieren");
        copy.getStyleClass().add("dialog-button");
        copy.setOnAction(e -> duplicateTarget());
        Button remove = new Button("Löschen");
        remove.getStyleClass().add("dialog-button");
        remove.setOnAction(e -> removeTarget());
        VBox listCol = new VBox(8, list, new HBox(8, add, copy, remove));
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox form = buildForm();
        HBox.setHgrow(form, Priority.ALWAYS);
        HBox body = new HBox(16, listCol, form);
        HBox.setHgrow(form, Priority.ALWAYS);

        status = new Label(notice != null ? notice : "Überwachung aktiv, sobald das Plugin geladen ist.");
        status.setWrapText(true);
        status.getStyleClass().add("dialog-label");

        VBox root = new VBox(12, intro, sourcePathLabel, body, status);
        root.setPadding(new Insets(16));
        if (!items.isEmpty()) {
            list.getSelectionModel().select(0);
        } else {
            loadTarget(null);
        }
        return root;
    }

    private VBox buildForm() {
        nameField = new TextField();
        nameField.setPromptText("Name");
        enabledBox = new CheckBox("Aktiv");
        kindBox = new ComboBox<>();
        kindBox.getItems().addAll(BackupKind.values());
        kindBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BackupKind kind) {
                return kind == null ? "" : kind.label();
            }

            @Override
            public BackupKind fromString(String string) {
                return BackupKind.fromId(string);
            }
        });
        kindBox.setCellFactory(view -> kindCell());
        kindBox.setButtonCell(kindCell());
        kindBox.valueProperty().addListener((obs, o, n) -> {
            applyKindVisibility(n);
            persistFromUi();
        });

        destField = new TextField();
        destField.setPromptText("Zielordner");
        HBox.setHgrow(destField, Priority.ALWAYS);
        Button browse = new Button("Ordner…");
        browse.getStyleClass().add("dialog-button");
        browse.setOnAction(e -> chooseFolder());
        HBox destRow = new HBox(8, destField, browse);
        destRow.setAlignment(Pos.CENTER_LEFT);
        HBox clouds = new HBox(8);
        clouds.setAlignment(Pos.CENTER_LEFT);
        for (CloudFolders.Entry entry : CloudFolders.available()) {
            Button button = new Button(entry.label());
            button.getStyleClass().add("dialog-button");
            Path target = CloudFolders.backupsSubfolder(entry.path());
            button.setOnAction(e -> {
                destField.setText(target.toString());
                persistFromUi();
            });
            clouds.getChildren().add(button);
        }
        if (clouds.getChildren().isEmpty()) {
            Label none = new Label("Keine bekannten Cloud-Ordner — Ordner selbst wählen.");
            none.setWrapText(true);
            none.getStyleClass().add("dialog-label");
            clouds.getChildren().add(none);
        }
        filesystemBox = new VBox(8, labeled("Zielordner"), destRow, clouds);

        sshHost = new TextField();
        sshHost.setPromptText("Host");
        sshPort = new Spinner<>(1, 65535, 22);
        sshPort.setEditable(true);
        sshPort.setPrefWidth(90);
        sshUser = new TextField();
        sshUser.setPromptText("Benutzer");
        sshRemote = new TextField();
        sshRemote.setPromptText("Remote-Pfad (absolut)");
        sshKey = new TextField();
        sshKey.setPromptText("Privater Schlüssel (optional)");
        HBox.setHgrow(sshKey, Priority.ALWAYS);
        Button keyBrowse = new Button("Schlüssel…");
        keyBrowse.getStyleClass().add("dialog-button");
        keyBrowse.setOnAction(e -> chooseKey());
        sshPassword = new PasswordField();
        sshPassword.setPromptText("Passwort oder Schlüssel-Passphrase");
        sshUnknownHost = new CheckBox("Unbekannten Host-Key akzeptieren");
        sshBox = new VBox(8,
                labeled("SSH"),
                new HBox(8, sshHost, sshPort),
                sshUser,
                sshRemote,
                new HBox(8, sshKey, keyBrowse),
                sshPassword,
                sshUnknownHost);
        sshBox.setVisible(false);
        sshBox.setManaged(false);

        compressBox = new CheckBox("Komprimieren");
        encryptBox = new CheckBox("Verschlüsseln (AES, nur mit diesem Plugin wiederherstellbar)");
        encryptBox.setWrapText(true);
        encryptPassword = new PasswordField();
        encryptPassword.setPromptText("Verschlüsselungs-Passwort");
        encryptRepeat = new PasswordField();
        encryptRepeat.setPromptText("Passwort wiederholen");
        rememberEncrypt = new CheckBox("Passwort merken (für Zeitplan)");
        encryptPassword.disableProperty().bind(encryptBox.selectedProperty().not());
        encryptRepeat.disableProperty().bind(encryptBox.selectedProperty().not());
        rememberEncrypt.disableProperty().bind(encryptBox.selectedProperty().not());

        scheduleBox = new ComboBox<>();
        scheduleBox.getItems().addAll(BackupSchedule.values());
        scheduleBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BackupSchedule schedule) {
                return schedule == null ? "" : schedule.label();
            }

            @Override
            public BackupSchedule fromString(String string) {
                return BackupSchedule.fromId(string);
            }
        });
        scheduleBox.setCellFactory(view -> scheduleCell());
        scheduleBox.setButtonCell(scheduleCell());
        keepSpinner = new Spinner<>(1, 99, 10);
        keepSpinner.setEditable(true);
        keepSpinner.setPrefWidth(80);

        Button saveTarget = new Button("Ziel speichern");
        saveTarget.getStyleClass().add("dialog-button");
        saveTarget.setOnAction(e -> persistFromUi());
        Button saveNow = new Button("Jetzt dieses Ziel sichern");
        saveNow.getStyleClass().add("dialog-button");
        saveNow.setOnAction(e -> startBackup(saveNow));
        Button restore = new Button("Wiederherstellen…");
        restore.getStyleClass().add("dialog-button");
        restore.setOnAction(e -> startRestore());
        HBox actions = new HBox(10, saveTarget, saveNow, restore);

        bindPersist();

        VBox form = new VBox(8,
                labeled("Ziel"),
                nameField,
                enabledBox,
                kindBox,
                filesystemBox,
                sshBox,
                compressBox,
                encryptBox,
                encryptPassword,
                encryptRepeat,
                rememberEncrypt,
                new HBox(8, labeled("Rhythmus"), scheduleBox),
                new HBox(8, labeled("Alte Backups behalten"), keepSpinner),
                actions);
        return form;
    }

    private void bindPersist() {
        nameField.textProperty().addListener((o, w, n) -> persistFromUi());
        destField.textProperty().addListener((o, w, n) -> persistFromUi());
        sshHost.textProperty().addListener((o, w, n) -> persistFromUi());
        sshUser.textProperty().addListener((o, w, n) -> persistFromUi());
        sshRemote.textProperty().addListener((o, w, n) -> persistFromUi());
        sshKey.textProperty().addListener((o, w, n) -> persistFromUi());
        sshPassword.textProperty().addListener((o, w, n) -> persistFromUi());
        encryptPassword.textProperty().addListener((o, w, n) -> persistFromUi());
        enabledBox.selectedProperty().addListener((o, w, n) -> persistFromUi());
        compressBox.selectedProperty().addListener((o, w, n) -> persistFromUi());
        encryptBox.selectedProperty().addListener((o, w, n) -> persistFromUi());
        rememberEncrypt.selectedProperty().addListener((o, w, n) -> persistFromUi());
        sshUnknownHost.selectedProperty().addListener((o, w, n) -> persistFromUi());
        scheduleBox.valueProperty().addListener((o, w, n) -> persistFromUi());
        keepSpinner.valueProperty().addListener((o, w, n) -> persistFromUi());
        sshPort.valueProperty().addListener((o, w, n) -> persistFromUi());
    }

    private static ListCell<BackupKind> kindCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(BackupKind item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    private static ListCell<BackupSchedule> scheduleCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(BackupSchedule item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    private static Label labeled(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dialog-title");
        return label;
    }

    private BackupTarget selected() {
        return list == null ? null : list.getSelectionModel().getSelectedItem();
    }

    private void loadTarget(BackupTarget target) {
        applying = true;
        boolean empty = target == null;
        nameField.setDisable(empty);
        enabledBox.setDisable(empty);
        kindBox.setDisable(empty);
        if (target == null) {
            nameField.clear();
            applying = false;
            return;
        }
        nameField.setText(target.name);
        enabledBox.setSelected(target.enabled);
        kindBox.setValue(target.kind());
        destField.setText(target.destination);
        sshHost.setText(target.sshHost);
        sshPort.getValueFactory().setValue(target.sshPort > 0 ? target.sshPort : 22);
        sshUser.setText(target.sshUser);
        sshRemote.setText(target.sshRemotePath);
        sshKey.setText(target.sshKeyPath);
        sshPassword.setText(target.sshPassword == null ? "" : target.sshPassword);
        sshUnknownHost.setSelected(target.sshAcceptUnknownHost);
        compressBox.setSelected(target.compress);
        encryptBox.setSelected(target.encrypt);
        encryptPassword.setText(target.encryptPassword == null ? "" : target.encryptPassword);
        encryptRepeat.setText(target.encryptPassword == null ? "" : target.encryptPassword);
        rememberEncrypt.setSelected(target.encryptPassword != null && !target.encryptPassword.isEmpty());
        scheduleBox.setValue(target.scheduleEnum());
        keepSpinner.getValueFactory().setValue(Math.max(1, target.keep));
        applyKindVisibility(target.kind());
        applying = false;
        if (target.lastError != null && !target.lastError.isBlank()) {
            status.setText("Letzter Fehler: " + target.lastError);
        } else if (target.lastBackupFile != null && !target.lastBackupFile.isBlank()) {
            status.setText("Letztes Backup: " + target.lastBackupFile);
        }
    }

    private void applyKindVisibility(BackupKind kind) {
        boolean ssh = kind == BackupKind.SSH;
        if (filesystemBox != null) {
            filesystemBox.setVisible(!ssh);
            filesystemBox.setManaged(!ssh);
        }
        if (sshBox != null) {
            sshBox.setVisible(ssh);
            sshBox.setManaged(ssh);
        }
    }

    private void persistFromUi() {
        persistTarget(selected());
    }

    private void persistTarget(BackupTarget target) {
        if (applying || target == null) {
            return;
        }
        applyFormTo(target);
        settings.replaceById(target);
        settings.targets = orderedTargets();
        try {
            settings.save(host.configDir());
        } catch (Exception e) {
            status.setText("Einstellungen nicht gespeichert: " + e.getMessage());
            return;
        }
        list.refresh();
    }

    private void applyFormTo(BackupTarget target) {
        target.name = nameField.getText() == null ? "" : nameField.getText().trim();
        target.enabled = enabledBox.isSelected();
        target.type = kindBox.getValue() == null ? BackupKind.FILESYSTEM.name() : kindBox.getValue().name();
        target.destination = destField.getText() == null ? "" : destField.getText().trim();
        target.sshHost = sshHost.getText() == null ? "" : sshHost.getText().trim();
        target.sshPort = sshPort.getValue() == null ? 22 : sshPort.getValue();
        target.sshUser = sshUser.getText() == null ? "" : sshUser.getText().trim();
        target.sshRemotePath = sshRemote.getText() == null ? "" : sshRemote.getText().trim();
        target.sshKeyPath = sshKey.getText() == null ? "" : sshKey.getText().trim();
        target.sshPassword = sshPassword.getText() == null ? "" : sshPassword.getText();
        target.sshAcceptUnknownHost = sshUnknownHost.isSelected();
        target.compress = compressBox.isSelected();
        target.encrypt = encryptBox.isSelected();
        if (rememberEncrypt.isSelected()) {
            String pass = encryptPassword.getText() == null ? "" : encryptPassword.getText();
            target.encryptPassword = pass;
        } else {
            target.encryptPassword = "";
        }
        target.schedule = scheduleBox.getValue() == null ? BackupSchedule.OFF.name() : scheduleBox.getValue().name();
        target.keep = keepSpinner.getValue() == null ? 10 : keepSpinner.getValue();
    }

    private java.util.List<BackupTarget> orderedTargets() {
        return new java.util.ArrayList<>(items);
    }

    private void addTarget() {
        persistFromUi();
        applying = true;
        BackupTarget target = new BackupTarget();
        target.name = "Ziel " + (items.size() + 1);
        items.add(target);
        settings.replaceById(target);
        settings.targets = orderedTargets();
        list.getSelectionModel().select(target);
        applying = false;
        loadTarget(target);
        persistFromUi();
    }

    private void duplicateTarget() {
        BackupTarget current = selected();
        if (current == null) {
            addTarget();
            return;
        }
        persistFromUi();
        BackupTarget copy = current.copy();
        applying = true;
        items.add(copy);
        settings.replaceById(copy);
        settings.targets = orderedTargets();
        list.getSelectionModel().select(copy);
        applying = false;
        loadTarget(copy);
        persistFromUi();
    }

    private void removeTarget() {
        BackupTarget current = selected();
        if (current == null) {
            return;
        }
        items.remove(current);
        settings.targets = orderedTargets();
        try {
            settings.save(host.configDir());
        } catch (Exception ignored) {
            // ignore
        }
        if (!items.isEmpty()) {
            list.getSelectionModel().select(0);
        } else {
            loadTarget(null);
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Backup-Ordner");
        Path start = destField.getText() == null || destField.getText().isBlank()
                ? null
                : Path.of(destField.getText().trim());
        if (start != null && Files.isDirectory(start)) {
            chooser.setInitialDirectory(start.toFile());
        }
        var dir = chooser.showDialog(stage);
        if (dir != null) {
            destField.setText(dir.getAbsolutePath());
            persistFromUi();
        }
    }

    private void chooseKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("SSH-Schlüssel");
        Path home = Path.of(System.getProperty("user.home", "."), ".ssh");
        if (Files.isDirectory(home)) {
            chooser.setInitialDirectory(home.toFile());
        }
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            sshKey.setText(file.getAbsolutePath());
            persistFromUi();
        }
    }

    private void refreshSourcePath() {
        if (sourcePathLabel == null) {
            return;
        }
        Path project = host.projectRoot().orElse(null);
        if (project != null && Files.isDirectory(project)) {
            sourcePathLabel.setText("Aktuelles Buch: " + project);
        } else {
            sourcePathLabel.setText("Aktuelles Buch: keines — bitte zuerst ein Buch öffnen.");
        }
    }

    private void startBackup() {
        startBackup(null);
    }

    private void startBackup(Button saveNow) {
        persistFromUi();
        refreshSourcePath();
        BackupTarget target = selected();
        if (target == null) {
            status.setText("Bitte ein Ziel wählen oder hinzufügen.");
            return;
        }
        Path project = host.projectRoot().orElse(null);
        if (project == null || !Files.isDirectory(project)) {
            status.setText("Kein Buch geöffnet. Nach einem Projektwechsel zuerst ein Buch öffnen, dann sichern.");
            return;
        }
        char[] password = null;
        if (encryptBox.isSelected()) {
            String a = encryptPassword.getText() == null ? "" : encryptPassword.getText();
            String b = encryptRepeat.getText() == null ? "" : encryptRepeat.getText();
            if (a.isEmpty()) {
                status.setText("Passwort für die Verschlüsselung eingeben.");
                return;
            }
            if (!a.equals(b)) {
                status.setText("Passwörter stimmen nicht überein.");
                return;
            }
            password = a.toCharArray();
        }
        if (saveNow != null) {
            saveNow.setDisable(true);
        }
        status.setText("Backup startet …");
        char[] passCopy = password == null ? null : password.clone();
        BackupTarget snapshot = target;
        Consumer<String> progress = message -> Platform.runLater(() -> status.setText(message));
        CompletableFuture.runAsync(() -> {
            try {
                Path file = BackupEngine.createBackup(project, snapshot, passCopy, progress);
                snapshot.markSuccess(file.toString());
                settings.save(host.configDir());
                Platform.runLater(() -> {
                    status.setText("Gespeichert: " + file);
                    list.refresh();
                });
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                snapshot.markError(message);
                try {
                    settings.save(host.configDir());
                } catch (Exception ignored) {
                    // ignore
                }
                Platform.runLater(() -> status.setText("Fehler: " + message));
            } finally {
                if (passCopy != null) {
                    java.util.Arrays.fill(passCopy, '\0');
                }
                if (saveNow != null) {
                    Platform.runLater(() -> saveNow.setDisable(false));
                }
            }
        });
    }

    private void startRestore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Backup öffnen");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Backups", "*.zip", "*.enc", "*.zip.enc"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
        BackupTarget target = selected();
        if (target != null && target.kind() == BackupKind.FILESYSTEM
                && target.destination != null && !target.destination.isBlank()) {
            Path dest = Path.of(target.destination);
            if (Files.isDirectory(dest)) {
                chooser.setInitialDirectory(dest.toFile());
            }
        }
        var file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Wiederherstellen nach");
        host.projectRoot().ifPresent(path -> {
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                dirChooser.setInitialDirectory(parent.toFile());
            }
        });
        var targetDir = dirChooser.showDialog(stage);
        if (targetDir == null) {
            return;
        }
        char[] password = null;
        if (file.getName().toLowerCase().endsWith(".enc")) {
            String a = encryptPassword.getText() == null ? "" : encryptPassword.getText();
            if (a.isEmpty()) {
                status.setText("Passwort für die Wiederherstellung eingeben.");
                return;
            }
            password = a.toCharArray();
        }
        status.setText("Stelle wieder her …");
        char[] passCopy = password == null ? null : password.clone();
        Path backup = file.toPath();
        Path into = targetDir.toPath();
        CompletableFuture.runAsync(() -> {
            try {
                BackupEngine.restore(backup, into, passCopy);
                Platform.runLater(() -> status.setText("Wiederhergestellt nach " + into));
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Platform.runLater(() -> status.setText("Fehler: " + message));
            } finally {
                if (passCopy != null) {
                    java.util.Arrays.fill(passCopy, '\0');
                }
            }
        });
    }
}
