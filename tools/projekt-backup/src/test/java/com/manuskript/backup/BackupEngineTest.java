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
    void uncompressedWritesFolderNotZip() throws Exception {
        Path project = temp.resolve("roman");
        Files.createDirectories(project.resolve("kapitel"));
        Files.writeString(project.resolve("kapitel").resolve("01.md"), "Hallo", StandardCharsets.UTF_8);
        Path dest = temp.resolve("backups");
        Path copy = BackupEngine.createBackup(project, dest, false, null, 10);
        assertTrue(Files.isDirectory(copy));
        assertFalse(copy.getFileName().toString().endsWith(".zip"));
        assertEquals("Hallo", Files.readString(copy.resolve("kapitel").resolve("01.md")));
        Path out = temp.resolve("restore-folder");
        BackupEngine.restore(copy, out, null);
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
        assertTrue(Files.isDirectory(zip));
        assertFalse(zip.getFileName().toString().endsWith(".zip"));
        assertEquals("x", Files.readString(zip.resolve("ok.txt")));
        assertTrue(Files.notExists(zip.resolve("backups")));
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

    @Test
    void listsSiblingBooksInProjectFolder() throws Exception {
        Path folder = temp.resolve("Manuskripte");
        Path gott = folder.resolve("Gott");
        Path held = folder.resolve("Held");
        Path skip = folder.resolve("data");
        Files.createDirectories(gott);
        Files.createDirectories(held);
        Files.createDirectories(skip);
        Files.writeString(gott.resolve("01.docx"), "x");
        Files.writeString(held.resolve("01.docx"), "y");
        Files.writeString(skip.resolve("01.docx"), "no");
        List<Path> books = ProjectScan.booksToBackup(gott, true);
        assertEquals(2, books.size());
        assertTrue(books.contains(gott.toAbsolutePath().normalize()));
        assertTrue(books.contains(held.toAbsolutePath().normalize()));
        assertEquals(List.of(gott.toAbsolutePath().normalize()), ProjectScan.booksToBackup(gott, false));
    }

    @Test
    void allProjectsIncludesSeriesBooks() throws Exception {
        Path folder = temp.resolve("Manuskripte");
        Path gott = folder.resolve("Gott");
        Path serie = folder.resolve("Demirantha");
        Path band1 = serie.resolve("Band 1");
        Path band2 = serie.resolve("Band 2");
        Files.createDirectories(gott);
        Files.createDirectories(band1);
        Files.createDirectories(band2);
        Files.writeString(gott.resolve("01.docx"), "g");
        Files.writeString(band1.resolve("01.docx"), "1");
        Files.writeString(band2.resolve("01.docx"), "2");

        List<Path> fromStandalone = ProjectScan.booksToBackup(gott, true);
        List<Path> fromSeries = ProjectScan.booksToBackup(band1, true);
        assertEquals(3, fromStandalone.size());
        assertEquals(3, fromSeries.size());
        assertTrue(fromStandalone.contains(gott.toAbsolutePath().normalize()));
        assertTrue(fromStandalone.contains(band1.toAbsolutePath().normalize()));
        assertTrue(fromStandalone.contains(band2.toAbsolutePath().normalize()));
        assertEquals(fromStandalone, fromSeries);
        assertEquals(folder.toAbsolutePath().normalize(), ProjectScan.collectionOf(gott));
        assertEquals(folder.toAbsolutePath().normalize(), ProjectScan.collectionOf(band1));
    }

    @Test
    void allProjectsWritesOneArchivePerBook() throws Exception {
        Path folder = temp.resolve("werke");
        Path a = folder.resolve("Alpha");
        Path b = folder.resolve("Beta");
        Files.createDirectories(a.resolve("kapitel"));
        Files.createDirectories(b.resolve("kapitel"));
        Files.writeString(a.resolve("a.docx"), "A", StandardCharsets.UTF_8);
        Files.writeString(b.resolve("b.docx"), "B", StandardCharsets.UTF_8);
        Files.writeString(a.resolve("kapitel").resolve("a.md"), "A", StandardCharsets.UTF_8);
        Files.writeString(b.resolve("kapitel").resolve("b.md"), "B", StandardCharsets.UTF_8);
        Path dest = temp.resolve("alle-backups");
        BackupTarget target = new BackupTarget();
        target.destination = dest.toString();
        target.compress = true;
        target.allProjects = true;
        target.keep = 10;
        BackupEngine.Batch batch = BackupEngine.backup(a, target, null, null);
        assertEquals(2, batch.paths.size());
        assertTrue(batch.errors.isEmpty());
        assertTrue(batch.paths.stream().anyMatch(path -> path.getFileName().toString().startsWith("Alpha-")));
        assertTrue(batch.paths.stream().anyMatch(path -> path.getFileName().toString().startsWith("Beta-")));
        try (ZipFile zip = new ZipFile(batch.paths.stream()
                .filter(path -> path.getFileName().toString().startsWith("Alpha-"))
                .findFirst().orElseThrow().toFile())) {
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().replace('\\', '/').equals("kapitel/a.md")));
        }
    }

    @Test
    void copyKeepsAllProjectsFlag() {
        BackupTarget source = new BackupTarget();
        source.allProjects = true;
        assertTrue(source.copy().allProjects);
    }
}
