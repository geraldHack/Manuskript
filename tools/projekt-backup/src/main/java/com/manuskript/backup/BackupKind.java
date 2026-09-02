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
        String trimmed = id.trim();
        for (BackupKind kind : values()) {
            if (kind.name().equalsIgnoreCase(trimmed) || kind.label.equalsIgnoreCase(trimmed)) {
                return kind;
            }
        }
        return FILESYSTEM;
    }
}
