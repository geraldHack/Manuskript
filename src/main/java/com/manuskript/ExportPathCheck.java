package com.manuskript;

import java.io.File;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Prüft Export-Pfade auf diesem Rechner. Windows-Pfade ({@code G:\exporte}) dürfen
 * unter macOS/Linux nicht als relative Ordner angelegt und als Erfolg gemeldet werden.
 */
public final class ExportPathCheck {

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:[\\\\/]");

    private ExportPathCheck() {
    }

    public static boolean isWindowsOs() {
        return isWindowsOs(System.getProperty("os.name", ""));
    }

    static boolean isWindowsOs(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean looksLikeWindowsPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String trimmed = path.trim();
        if (WINDOWS_DRIVE.matcher(trimmed).find()) {
            return true;
        }
        return trimmed.startsWith("\\\\");
    }

    public static boolean looksLikeUnixAbsolutePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String trimmed = path.trim();
        if (looksLikeWindowsPath(trimmed)) {
            return false;
        }
        return trimmed.startsWith("/") && !trimmed.startsWith("//");
    }

    public static boolean isForeignToThisOs(String path) {
        return isForeignToOs(path, System.getProperty("os.name", ""));
    }

    static boolean isForeignToOs(String path, String osName) {
        if (path == null || path.isBlank()) {
            return false;
        }
        boolean windows = isWindowsOs(osName);
        if (!windows && looksLikeWindowsPath(path)) {
            return true;
        }
        if (windows && looksLikeUnixAbsolutePath(path)) {
            File file = new File(path.trim());
            return !file.exists();
        }
        return false;
    }

    /** Leert plattformfremde gespeicherte Pfade, damit sie nicht wiederverwendet werden. */
    public static String nativeOrEmpty(String path) {
        if (path == null || path.isBlank() || isForeignToThisOs(path)) {
            return "";
        }
        return path.trim();
    }

    /**
     * @return Fehlermeldung oder {@code null}, wenn das Verzeichnis auf diesem System nutzbar ist
     */
    public static String outputDirectoryProblem(String raw, File projectDir) {
        return outputDirectoryProblem(raw, projectDir, System.getProperty("os.name", ""));
    }

    static String outputDirectoryProblem(String raw, File projectDir, String osName) {
        if (raw == null || raw.isBlank()) {
            return "Bitte wählen Sie ein Zielverzeichnis.";
        }
        String path = raw.trim();
        if (isForeignToOs(path, osName)) {
            return "Der Zielpfad stammt von einem anderen Betriebssystem und ist hier ungültig:\n"
                    + path
                    + "\nBitte das Verzeichnis über „Durchsuchen“ neu wählen.";
        }
        File dir = new File(path);
        if (!dir.isAbsolute() && projectDir != null) {
            dir = new File(projectDir, path);
        }
        if (!dir.isAbsolute()) {
            return "Bitte ein vollständiges Zielverzeichnis wählen (über „Durchsuchen“), keinen relativen Pfad:\n"
                    + path;
        }
        if (dir.exists() && !dir.isDirectory()) {
            return "Das Ziel ist kein Ordner:\n" + dir.getAbsolutePath();
        }
        if (!dir.exists()) {
            File parent = dir.getParentFile();
            if (parent == null || !parent.isDirectory()) {
                return "Zielverzeichnis existiert nicht:\n" + dir.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * @return Fehlermeldung oder {@code null}, wenn die Datei existiert
     */
    public static String existingFileProblem(String raw, String label) {
        return existingFileProblem(raw, label, System.getProperty("os.name", ""));
    }

    static String existingFileProblem(String raw, String label, String osName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String path = raw.trim();
        String name = label == null || label.isBlank() ? "Datei" : label;
        if (isForeignToOs(path, osName)) {
            return name + "-Pfad stammt von einem anderen Betriebssystem und ist hier ungültig:\n"
                    + path
                    + "\nBitte die Datei neu wählen.";
        }
        File file = new File(path);
        if (!file.isFile()) {
            return name + " nicht gefunden:\n" + path;
        }
        return null;
    }
}
