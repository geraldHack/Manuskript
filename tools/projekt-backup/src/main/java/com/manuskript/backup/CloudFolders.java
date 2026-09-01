package com.manuskript.backup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Bekannte Sync-Ordner (Dropbox, iCloud, OneDrive, Google Drive) — Ziel bleibt ein Ordner.
 */
public final class CloudFolders {

    public record Entry(String label, Path path) {
    }

    private CloudFolders() {
    }

    public static List<Entry> available() {
        Path home = Path.of(System.getProperty("user.home", "."));
        List<Entry> found = new ArrayList<>();
        addFirstExisting(found, "Dropbox", List.of(
                home.resolve("Dropbox"),
                home.resolve("Library").resolve("CloudStorage").resolve("Dropbox")));
        addFirstExisting(found, "iCloud Drive", List.of(
                home.resolve("Library").resolve("Mobile Documents").resolve("com~apple~CloudDocs")));
        addFirstExisting(found, "OneDrive", List.of(
                home.resolve("OneDrive"),
                home.resolve("OneDrive - Personal")));
        addCloudStoragePrefix(found, home, "OneDrive", "OneDrive");
        addCloudStoragePrefix(found, home, "Google Drive", "GoogleDrive");
        addFirstExisting(found, "Google Drive", List.of(
                home.resolve("Google Drive"),
                home.resolve("GoogleDrive")));
        return found;
    }

    public static Path backupsSubfolder(Path root) {
        return root.resolve("Manuskript-Backups");
    }

    private static void addFirstExisting(List<Entry> into, String label, List<Path> candidates) {
        if (hasLabel(into, label)) {
            return;
        }
        for (Path path : candidates) {
            if (path != null && Files.isDirectory(path)) {
                into.add(new Entry(label, path));
                return;
            }
        }
    }

    private static void addCloudStoragePrefix(List<Entry> into, Path home, String label, String prefix) {
        if (hasLabel(into, label)) {
            return;
        }
        Path cloud = home.resolve("Library").resolve("CloudStorage");
        if (!Files.isDirectory(cloud)) {
            return;
        }
        try (Stream<Path> stream = Files.list(cloud)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .startsWith(prefix.toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .ifPresent(path -> into.add(new Entry(label, path)));
        } catch (Exception ignored) {
            // ignore
        }
    }

    private static boolean hasLabel(List<Entry> into, String label) {
        return into.stream().anyMatch(entry -> label.equals(entry.label()));
    }
}
