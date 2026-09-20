package com.manuskript.backup;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Findet Buchordner wie die Projektübersicht: Einzelbücher und Bände in Serienordnern.
 */
final class ProjectScan {

    private ProjectScan() {
    }

    static Path folderOf(Path currentBook) {
        return collectionOf(currentBook);
    }

    /**
     * Projektordner der Übersicht: über dem Buch, bzw. über der Serie, wenn es noch
     * weitere Bücher oder Serien daneben gibt.
     */
    static Path collectionOf(Path currentBook) {
        if (currentBook == null) {
            return null;
        }
        Path book = currentBook.toAbsolutePath().normalize();
        Path parent = book.getParent();
        if (parent == null) {
            return book;
        }
        if (isSeriesFolder(parent)) {
            Path grand = parent.getParent();
            if (grand != null && projectCount(grand) >= 2) {
                return grand;
            }
        }
        return parent;
    }

    static List<Path> booksToBackup(Path currentBook, boolean allProjects) {
        if (currentBook == null || !Files.isDirectory(currentBook)) {
            return List.of();
        }
        Path book = currentBook.toAbsolutePath().normalize();
        if (!allProjects) {
            return List.of(book);
        }
        List<Path> books = listBooks(collectionOf(book));
        if (books.isEmpty()) {
            return List.of(book);
        }
        if (!books.contains(book) && Files.isDirectory(book) && looksLikeBook(book)) {
            books.add(book);
            sortBooks(books);
        }
        return books;
    }

    static List<Path> listBooks(Path folder) {
        Set<Path> books = new LinkedHashSet<>();
        if (folder == null || !Files.isDirectory(folder)) {
            return new ArrayList<>();
        }
        for (Path child : children(folder)) {
            if (looksLikeBook(child)) {
                books.add(child);
                continue;
            }
            for (Path nested : children(child)) {
                if (looksLikeBook(nested)) {
                    books.add(nested);
                }
            }
        }
        List<Path> list = new ArrayList<>(books);
        sortBooks(list);
        return list;
    }

    static boolean looksLikeBook(Path dir) {
        if (dir == null || !Files.isDirectory(dir) || skipped(fileName(dir))) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (Files.isRegularFile(child) && name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    static boolean skipped(String dirName) {
        if (dirName == null || dirName.isBlank() || dirName.startsWith(".")) {
            return true;
        }
        String n = dirName.toLowerCase(Locale.ROOT);
        return n.equals("data") || n.equals("backup") || n.equals("backups") || n.equals("config")
                || n.equals("logs") || n.equals("export") || n.equals("exporte") || n.equals("target")
                || n.equals("src") || n.equals("node_modules") || n.equals("__pycache__")
                || n.equals("archiv");
    }

    private static boolean isSeriesFolder(Path dir) {
        return !looksLikeBook(dir) && hasBookChildren(dir) && !hasSeriesChildren(dir);
    }

    private static boolean hasBookChildren(Path dir) {
        for (Path child : children(dir)) {
            if (looksLikeBook(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSeriesChildren(Path dir) {
        for (Path child : children(dir)) {
            if (!looksLikeBook(child) && hasBookChildren(child)) {
                return true;
            }
        }
        return false;
    }

    private static int projectCount(Path folder) {
        int count = 0;
        for (Path child : children(folder)) {
            if (looksLikeBook(child) || hasBookChildren(child)) {
                count++;
            }
        }
        return count;
    }

    private static List<Path> children(Path folder) {
        List<Path> list = new ArrayList<>();
        if (folder == null || !Files.isDirectory(folder)) {
            return list;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path child : stream) {
                if (Files.isDirectory(child) && !skipped(fileName(child))) {
                    list.add(child.toAbsolutePath().normalize());
                }
            }
        } catch (IOException ignored) {
            return list;
        }
        return list;
    }

    private static void sortBooks(List<Path> books) {
        books.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
    }

    private static String fileName(Path path) {
        return path.getFileName() == null ? "" : path.getFileName().toString();
    }
}
