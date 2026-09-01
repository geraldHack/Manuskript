package com.manuskript.backup;

import com.manuskript.plugin.PluginHost;
import javafx.application.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fällige Backups ohne Fenster, solange Manuskript läuft.
 */
public final class BackupMonitor {

    private final PluginHost host;
    private ScheduledExecutorService scheduler;
    private volatile boolean started;

    public BackupMonitor(PluginHost host) {
        this.host = host;
    }

    public synchronized void start() {
        if (started) {
            runDueQuietly();
            return;
        }
        started = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "projekt-backup-monitor");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::runDueQuietly, 0, 15, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        started = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public void runDueQuietly() {
        Path project = host.projectRoot().orElse(null);
        if (project == null || !Files.isDirectory(project)) {
            return;
        }
        BackupSettings settings = BackupSettings.load(host.configDir());
        if (settings.targets == null) {
            return;
        }
        boolean changed = false;
        for (BackupTarget target : settings.targets) {
            if (!target.enabled || target.scheduleEnum() == BackupSchedule.OFF) {
                continue;
            }
            if (!target.scheduleEnum().isDue(target.lastBackupInstant(), java.time.Instant.now())) {
                continue;
            }
            char[] password = encryptPasswordForSchedule(target);
            if (target.encrypt && (password == null || password.length == 0)) {
                target.markError("Verschlüsseltes Backup fällig — Passwort im Fenster hinterlegen oder merken.");
                changed = true;
                Platform.runLater(() -> BackupWindow.promptScheduled(host, target));
                continue;
            }
            try {
                Path result = BackupEngine.createBackup(project, target, password);
                target.markSuccess(result.toString());
                changed = true;
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                target.markError(message);
                changed = true;
            } finally {
                if (password != null) {
                    java.util.Arrays.fill(password, '\0');
                }
            }
        }
        if (changed) {
            try {
                settings.save(host.configDir());
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static char[] encryptPasswordForSchedule(BackupTarget target) {
        if (!target.encrypt) {
            return null;
        }
        if (target.encryptPassword == null || target.encryptPassword.isEmpty()) {
            return null;
        }
        return target.encryptPassword.toCharArray();
    }
}
