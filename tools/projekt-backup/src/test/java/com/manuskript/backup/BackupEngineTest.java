package com.manuskript.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupEngineTest {

    @TempDir
    Path temp;

    @Test
    void scheduleDueAfterInterval() {
        Instant now = Instant.parse("2026-08-28T12:00:00Z");
        assertFalse(BackupSchedule.OFF.isDue(null, now));
        assertTrue(BackupSchedule.DAILY.isDue(null, now));
        assertFalse(BackupSchedule.DAILY.isDue(now.minus(12, ChronoUnit.HOURS), now));
        assertTrue(BackupSchedule.HOURLY.isDue(now.minus(61, ChronoUnit.MINUTES), now));
        assertFalse(BackupSchedule.HOURLY.isDue(now.minus(20, ChronoUnit.MINUTES), now));
        assertTrue(BackupSchedule.DAILY.isDue(now.minus(25, ChronoUnit.HOURS), now));
        assertTrue(BackupSchedule.WEEKLY.isDue(now.minus(8, ChronoUnit.DAYS), now));
        assertFalse(BackupSchedule.WEEKLY.isDue(now.minus(2, ChronoUnit.DAYS), now));
        assertTrue(BackupSchedule.MONTHLY.isDue(now.minus(31, ChronoUnit.DAYS), now));
    }

    @Test
    void zipAndRestoreRoundtrip() throws Exception {
        Path project = temp.resolve("roman");
        Files.createDirectories(project.resolve("kapitel"));
        Files.writeString(project.resolve("kapitel").resolve("01.md"), "Hallo", StandardCharsets.UTF_8);
        Path dest = temp.resolve("backups");
        Path zip = BackupEngine.createBackup(project, dest, true, null, 10);
        assertTrue(Files.isRegularFile(zip));
        try (ZipFile file = new ZipFile(zip.toFile())) {
            assertTrue(file.stream().anyMatch(entry -> entry.getName().replace('\\', '/').equals("kapitel/01.md")));
        }
        Path out = temp.resolve("restore");
        BackupEngine.restore(zip, out, null);
        assertEquals("Hallo", Files.readString(out.resolve("kapitel").resolve("01.md")));
    }

    @Test
    void encryptDecryptRoundtrip() throws Exception {
        Path project = temp.resolve("buch");
        Files.createDirectories(project);
        Files.writeString(project.resolve("outline.txt"), "Plot", StandardCharsets.UTF_8);
        Path dest = temp.resolve("enc-backups");
        char[] password = "geheim-123".toCharArray();
        Path enc = BackupEngine.createBackup(project, dest, true, password, 5);
        assertTrue(enc.getFileName().toString().endsWith(".zip.enc"));
        assertThrows(IllegalArgumentException.class,
                () -> BackupEngine.restore(enc, temp.resolve("fail"), "wrong".toCharArray()));
        Path out = temp.resolve("enc-restore");
        BackupEngine.restore(enc, out, password);
        assertEquals("Plot", Files.readString(out.resolve("outline.txt")));
    }

    @Test
    void skipsDestinationInsideProject() throws Exception {
        Path project = temp.resolve("nested");
        Files.createDirectories(project);
        Files.writeString(project.resolve("ok.txt"), "x", StandardCharsets.UTF_8);
        Path dest = project.resolve("backups");
        Path zip = BackupEngine.createBackup(project, dest, false, null, 10);
        try (ZipFile file = new ZipFile(zip.toFile())) {
            assertTrue(file.stream().noneMatch(entry -> entry.getName().startsWith("backups/")));
        }
    }

    @Test
    void pruneKeepsNewest() throws Exception {
        Path dest = temp.resolve("keep");
        Files.createDirectories(dest);
        Path older = dest.resolve("roman-1.zip");
        Path newer = dest.resolve("roman-2.zip");
        Files.writeString(older, "a");
        Thread.sleep(20);
        Files.writeString(newer, "b");
        BackupEngine.pruneOld(dest, "roman", 1);
        assertFalse(Files.exists(older));
        assertTrue(Files.exists(newer));
    }

    @Test
    void sanitizeFileName() {
        assertEquals("Mein_Roman", BackupEngine.sanitize("Mein Roman"));
        assertEquals("projekt", BackupEngine.sanitize("   "));
    }
}
