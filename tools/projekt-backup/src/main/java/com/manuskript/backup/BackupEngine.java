package com.manuskript.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ZIP-Backup eines Projektordners, optional AES-verschlüsselt, lokal oder per SSH.
 */
public final class BackupEngine {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BackupEngine() {
    }

    public static Path createBackup(
            Path projectRoot,
            Path destinationDir,
            boolean compress,
            char[] password,
            int keepCount) throws Exception {
        BackupTarget target = new BackupTarget();
        target.type = BackupKind.FILESYSTEM.name();
        target.destination = destinationDir == null ? "" : destinationDir.toString();
        target.compress = compress;
        target.keep = keepCount;
        return createBackup(projectRoot, target, password, null);
    }

    public static Path createBackup(Path projectRoot, BackupTarget target, char[] password) throws Exception {
        return createBackup(projectRoot, target, password, null);
    }

    public static Path createBackup(
            Path projectRoot,
            BackupTarget target,
            char[] password,
            Consumer<String> progress) throws Exception {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("Kein Projektverzeichnis");
        }
        if (target == null) {
            throw new IllegalArgumentException("Kein Ziel");
        }
        report(progress, "Prüfe Ziel …");
        preflight(target);

        Path projectReal = projectRoot.toAbsolutePath().normalize();
        String stamp = LocalDateTime.now().format(STAMP);
        String base = sanitize(projectRoot.getFileName().toString()) + "-" + stamp;
        boolean encrypt = password != null && password.length > 0;
        Path staging = Files.createTempDirectory("msk-backup-");
        Path tempZip = staging.resolve(base + ".zip");
        Path archive = encrypt ? staging.resolve(base + ".zip.enc") : tempZip;
        try {
            Path skip = skipDirectory(projectReal, target);
            report(progress, "Packe Projekt …");
            writeZip(projectReal, skip, tempZip, target.compress, progress);
            if (encrypt) {
                report(progress, "Verschlüssele …");
                BackupCrypto.encrypt(tempZip, archive, password);
                Files.deleteIfExists(tempZip);
            }
            if (target.kind() == BackupKind.SSH) {
                report(progress, "Lade per SSH hoch …");
                String remote = SshBackupTransport.upload(archive, target);
                SshBackupTransport.prune(target, sanitize(projectRoot.getFileName().toString()), target.keep);
                return Path.of(remote);
            }
            report(progress, "Schreibe Zielordner …");
            Path destDir = filesystemDestination(target);
            Files.createDirectories(destDir);
            Path destReal = destDir.toRealPath();
            Path finalPath = destReal.resolve(archive.getFileName().toString());
            try {
                Files.move(archive, finalPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.copy(archive, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            pruneOld(destReal, sanitize(projectRoot.getFileName().toString()), target.keep);
            return finalPath;
        } finally {
            deleteTree(staging);
        }
    }

    public static void restore(Path backupFile, Path targetDir, char[] password) throws Exception {
        if (backupFile == null || !Files.isRegularFile(backupFile)) {
            throw new IllegalArgumentException("Backup-Datei fehlt");
        }
        Files.createDirectories(targetDir);
        Path zip = backupFile;
        Path temp = null;
        try {
            if (backupFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".enc")) {
                temp = Files.createTempFile("backup-restore-", ".zip");
                BackupCrypto.decrypt(backupFile, temp, password);
                zip = temp;
            }
            unzip(zip, targetDir);
        } finally {
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
        }
    }

    static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "projekt";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        return cleaned.isBlank() ? "projekt" : cleaned;
    }

    static void writeZip(Path projectReal, Path skipDirOrNull, Path zipFile, boolean compress) throws IOException {
        writeZip(projectReal, skipDirOrNull, zipFile, compress, null);
    }

    static void writeZip(
            Path projectReal,
            Path skipDirOrNull,
            Path zipFile,
            boolean compress,
            Consumer<String> progress) throws IOException {
        Path skip = skipDirOrNull == null ? null : skipDirOrNull.toAbsolutePath().normalize();
        int[] fileCount = {0};
        try (OutputStream out = Files.newOutputStream(zipFile);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(compress ? Deflater.DEFAULT_COMPRESSION : Deflater.NO_COMPRESSION);
            Files.walkFileTree(projectReal, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path real = absolute(dir);
                    if (skip != null && !real.equals(projectReal) && (real.equals(skip) || real.startsWith(skip))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileName = file.getFileName().toString();
                    if (fileName.equals(".DS_Store") || fileName.equals("Thumbs.db")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path real = absolute(file);
                    if (skip != null && real.startsWith(skip)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!real.startsWith(projectReal)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String entryName = projectReal.relativize(real).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zip.putNextEntry(entry);
                    try (InputStream in = Files.newInputStream(file)) {
                        in.transferTo(zip);
                    }
                    zip.closeEntry();
                    fileCount[0]++;
                    if (fileCount[0] == 1 || fileCount[0] % 25 == 0) {
                        report(progress, "Packe Projekt … (" + fileCount[0] + " Dateien)");
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        report(progress, "Archiv fertig (" + fileCount[0] + " Dateien)");
    }

    static void unzip(Path zipFile, Path targetDir) throws IOException {
        Path targetReal = targetDir.toAbsolutePath().normalize();
        try (InputStream in = Files.newInputStream(zipFile);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path dest = targetReal.resolve(entry.getName()).normalize();
                if (!dest.startsWith(targetReal)) {
                    throw new IOException("Ungültiger ZIP-Pfad: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    static void pruneOld(Path destDir, String projectPrefix, int keepCount) throws IOException {
        if (keepCount <= 0) {
            return;
        }
        String prefix = projectPrefix + "-";
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(destDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(prefix)
                                && (name.endsWith(".zip") || name.endsWith(".zip.enc"));
                    })
                    .forEach(files::add);
        }
        files.sort(Comparator.comparingLong((Path path) -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).reversed());
        for (int i = keepCount; i < files.size(); i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    static Instant lastModifiedOrNull(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    private static void preflight(BackupTarget target) throws IOException {
        if (target.kind() == BackupKind.SSH) {
            SshBackupTransport.probe(target);
            return;
        }
        Path dest = filesystemDestination(target);
        Path probe = dest;
        while (probe != null && !Files.exists(probe)) {
            probe = probe.getParent();
        }
        if (probe == null || !Files.isDirectory(probe)) {
            throw new IOException("Zielordner nicht erreichbar: " + dest);
        }
        if (!Files.isWritable(probe)) {
            throw new IOException("Zielordner nicht beschreibbar: " + probe);
        }
        // Externe Volumes unter /Volumes/…: fehlendes Volume nicht still neu anlegen.
        Path abs = dest.toAbsolutePath().normalize();
        if (abs.getNameCount() >= 2 && "Volumes".equals(abs.getName(0).toString())) {
            Path volume = abs.getRoot().resolve(abs.subpath(0, 2));
            if (!Files.isDirectory(volume)) {
                throw new IOException("Volume nicht gemountet: " + volume);
            }
        }
    }

    private static Path filesystemDestination(BackupTarget target) {
        if (target.destination == null || target.destination.isBlank()) {
            throw new IllegalArgumentException("Kein Zielordner");
        }
        return Path.of(target.destination.trim());
    }

    private static Path skipDirectory(Path projectReal, BackupTarget target) {
        if (target.kind() != BackupKind.FILESYSTEM) {
            return null;
        }
        try {
            Path dest = filesystemDestination(target);
            if (!Files.isDirectory(dest)) {
                return dest.toAbsolutePath().normalize();
            }
            Path destReal = dest.toRealPath();
            if (destReal.startsWith(projectReal)) {
                return destReal;
            }
        } catch (Exception ignored) {
            // Ziel existiert noch nicht
        }
        return null;
    }

    /** toRealPath() kann auf Cloud-/Netzlaufwerken hängen — absolute+normalize reicht für ZIP-Pfade. */
    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null && message != null) {
            progress.accept(message);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // temp
        }
    }
}
