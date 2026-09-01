package com.manuskript.backup;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein Backup-Ziel mit eigenem Zeitplan und eigener Verschlüsselung.
 */
public final class BackupTarget {

    public String id = UUID.randomUUID().toString();
    public String name = "Ziel";
    public boolean enabled = true;
    public String type = BackupKind.FILESYSTEM.name();
    public String destination = "";
    public String sshHost = "";
    public int sshPort = 22;
    public String sshUser = "";
    public String sshRemotePath = "";
    public String sshKeyPath = "";
    public String sshPassword = "";
    public boolean sshAcceptUnknownHost = false;
    public boolean compress = true;
    public boolean encrypt = false;
    public String encryptPassword = "";
    public String schedule = BackupSchedule.OFF.name();
    public int keep = 10;
    public String lastBackupIso = "";
    public String lastBackupFile = "";
    public String lastError = "";

    public BackupKind kind() {
        return BackupKind.fromId(type);
    }

    public BackupSchedule scheduleEnum() {
        return BackupSchedule.fromId(schedule);
    }

    public Instant lastBackupInstant() {
        if (lastBackupIso == null || lastBackupIso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(lastBackupIso);
        } catch (Exception e) {
            return null;
        }
    }

    public void markSuccess(String fileLabel) {
        lastBackupIso = Instant.now().toString();
        lastBackupFile = fileLabel == null ? "" : fileLabel;
        lastError = "";
    }

    public void markError(String message) {
        lastError = message == null ? "" : message;
    }

    public BackupTarget copy() {
        BackupTarget copy = new BackupTarget();
        copy.id = UUID.randomUUID().toString();
        copy.name = name + " Kopie";
        copy.enabled = enabled;
        copy.type = type;
        copy.destination = destination;
        copy.sshHost = sshHost;
        copy.sshPort = sshPort;
        copy.sshUser = sshUser;
        copy.sshRemotePath = sshRemotePath;
        copy.sshKeyPath = sshKeyPath;
        copy.sshPassword = sshPassword;
        copy.sshAcceptUnknownHost = sshAcceptUnknownHost;
        copy.compress = compress;
        copy.encrypt = encrypt;
        copy.encryptPassword = encryptPassword;
        copy.schedule = schedule;
        copy.keep = keep;
        copy.lastBackupIso = "";
        copy.lastBackupFile = "";
        copy.lastError = "";
        return copy;
    }

    public String displayName() {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return kind() == BackupKind.SSH ? "SSH-Ziel" : "Ordner-Ziel";
    }
}
