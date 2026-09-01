package com.manuskript.backup;

/**
 * Wohin das Archiv kopiert wird.
 */
public enum BackupKind {
    FILESYSTEM("Dateisystem"),
    SSH("SSH / SCP");

    private final String label;

    BackupKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BackupKind fromId(String id) {
        if (id == null || id.isBlank()) {
            return FILESYSTEM;
        }
        try {
            return BackupKind.valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FILESYSTEM;
        }
    }
}
