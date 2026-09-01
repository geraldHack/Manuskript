package com.manuskript.backup;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Backup-Rhythmus. Läuft nur, während Manuskript geöffnet ist
 * (Überwachungsmodus plus Prüfung beim Start).
 */
public enum BackupSchedule {
    OFF("Aus"),
    HOURLY("Stündlich"),
    DAILY("Täglich"),
    WEEKLY("Wöchentlich"),
    MONTHLY("Monatlich");

    private final String label;

    BackupSchedule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BackupSchedule fromId(String id) {
        if (id == null || id.isBlank()) {
            return OFF;
        }
        try {
            return BackupSchedule.valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFF;
        }
    }

    public boolean isDue(Instant lastBackup, Instant now) {
        if (this == OFF) {
            return false;
        }
        Instant nowInstant = now == null ? Instant.now() : now;
        if (lastBackup == null) {
            return true;
        }
        return switch (this) {
            case OFF -> false;
            case HOURLY -> lastBackup.isBefore(nowInstant.minus(1, ChronoUnit.HOURS));
            case DAILY -> lastBackup.isBefore(nowInstant.minus(1, ChronoUnit.DAYS));
            case WEEKLY -> lastBackup.isBefore(nowInstant.minus(7, ChronoUnit.DAYS));
            case MONTHLY -> lastBackup.isBefore(nowInstant.minus(30, ChronoUnit.DAYS));
        };
    }
}
