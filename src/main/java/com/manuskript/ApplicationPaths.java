package com.manuskript;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Auflösung von Programmverzeichnis und gebündeltem Demo-Ordner (jpackage-.app).
 */
public final class ApplicationPaths {

    /** System-Property / Logback-Property für das Log-Verzeichnis. */
    public static final String LOG_DIR_PROPERTY = "manuskript.log.dir";

    private ApplicationPaths() {
    }

    /**
     * Verzeichnis der laufenden Anwendung (bei jpackage: {@code Contents/app} bzw. {@code app/}).
     */
    public static File getApplicationHomeDirectory() {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            File fromJpackage = resolveJpackageAppHome(new File(appPath));
            if (fromJpackage != null && fromJpackage.isDirectory()) {
                return fromJpackage;
            }
        }

        try {
            String codeLocation = Launcher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            if (codeLocation != null && codeLocation.endsWith(".jar")) {
                File jarDir = new File(codeLocation).getParentFile();
                if (jarDir != null && jarDir.isDirectory()) {
                    return jarDir;
                }
            }
        } catch (Exception ignored) {
            // Fallback unten
        }

        return new File(System.getProperty("user.dir", "."));
    }

    /**
     * {@code jpackage.app-path} zeigt auf den nativen Launcher (Datei) oder ein Verzeichnis.
     * Ziel ist immer der Ordner mit JAR/config ({@code Contents/app} bzw. {@code app/}).
     */
    static File resolveJpackageAppHome(File appPath) {
        if (appPath == null) {
            return null;
        }
        if (appPath.isDirectory()) {
            if (looksLikeAppHome(appPath)) {
                return appPath;
            }
            File nested = new File(appPath, "app");
            if (looksLikeAppHome(nested)) {
                return nested;
            }
            File contentsApp = new File(appPath, "Contents/app");
            if (looksLikeAppHome(contentsApp)) {
                return contentsApp;
            }
            return appPath;
        }

        File parent = appPath.getParentFile();
        if (parent == null) {
            return null;
        }

        // macOS: …/Manuskript.app/Contents/MacOS/Manuskript → …/Contents/app
        if ("MacOS".equalsIgnoreCase(parent.getName())) {
            File contents = parent.getParentFile();
            if (contents != null) {
                File app = new File(contents, "app");
                if (app.isDirectory()) {
                    return app;
                }
            }
        }

        // Windows/Linux: Launcher neben app/
        File siblingApp = new File(parent, "app");
        if (siblingApp.isDirectory()) {
            return siblingApp;
        }

        if (looksLikeAppHome(parent)) {
            return parent;
        }
        return parent;
    }

    private static boolean looksLikeAppHome(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        return new File(dir, "config").isDirectory()
                || new File(dir, "manuskript-standalone.jar").isFile()
                || new File(dir, "Manuskripte").isDirectory();
    }

    /**
     * Demo-/Projektstamm {@code Manuskripte}: zuerst im App-Bundle, sonst im Arbeitsverzeichnis.
     */
    public static File resolveManuskripteDirectory() {
        File bundled = new File(getApplicationHomeDirectory(), "Manuskripte");
        if (bundled.isDirectory()) {
            return bundled;
        }
        File relative = new File("Manuskripte");
        if (relative.isDirectory()) {
            return relative;
        }
        return bundled;
    }

    /**
     * Pfad unter {@code config/…}: zuerst App-Home (jpackage {@code Contents/app}),
     * sonst relatives Arbeitsverzeichnis (Dev mit {@code mvn javafx:run}).
     *
     * @param relativePath z. B. {@code config/defaultCovers} oder {@code config/plugins}
     */
    public static File resolveConfigPath(String relativePath) {
        return resolveBundledPath(relativePath != null && !relativePath.isBlank()
                ? relativePath
                : "config");
    }

    /**
     * Gebündelte Ressource relativ zum App-Home (jpackage {@code Contents/app}),
     * mit Fallback auf das Arbeitsverzeichnis (Entwicklung).
     *
     * @param relativePath z. B. {@code pandoc}, {@code pandoc/epub.css}, {@code ffmpeg}
     */
    public static File resolveBundledPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return getApplicationHomeDirectory();
        }
        String normalized = relativePath.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            return new File(normalized);
        }
        File bundled = new File(getApplicationHomeDirectory(), normalized);
        if (bundled.exists()) {
            return bundled;
        }
        File relative = new File(normalized);
        if (relative.exists()) {
            return relative;
        }
        return bundled;
    }

    /** Gebündelter {@code pandoc/}-Ordner (Vorlagen, CSS, Binary). */
    public static File resolvePandocDirectory() {
        return resolveBundledPath("pandoc");
    }

    /**
     * Schreibbares Log-Verzeichnis.
     * Relatives {@code logs/} (CWD) funktioniert in jpackage-.app oft nicht
     * (CWD={@code /}, Bundle nicht schreibbar) — deshalb: App-Home wenn möglich,
     * sonst Nutzerdaten ({@code ~/Library/Logs/Manuskript} u. Ä.).
     */
    public static File resolveLogDirectory() {
        String override = System.getProperty(LOG_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            File dir = new File(override.trim());
            ensureDirectory(dir);
            return dir;
        }

        File besideApp = new File(getApplicationHomeDirectory(), "logs");
        if (tryPrepareWritableDir(besideApp)) {
            return besideApp;
        }

        File cwdLogs = new File(System.getProperty("user.dir", "."), "logs");
        if (tryPrepareWritableDir(cwdLogs)) {
            return cwdLogs;
        }

        File userLogs = defaultUserLogDirectory();
        ensureDirectory(userLogs);
        return userLogs;
    }

    /** Absoluter Log-Pfad mit {@code /} (für Logback). */
    public static String resolveLogDirectoryPath() {
        return resolveLogDirectory().getAbsolutePath().replace('\\', '/');
    }

    private static File defaultUserLogDirectory() {
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return new File(home, "Library/Logs/Manuskript");
        }
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) {
                return new File(local, "Manuskript/logs");
            }
            return new File(home, "AppData/Local/Manuskript/logs");
        }
        String xdg = System.getenv("XDG_STATE_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return new File(xdg, "manuskript/logs");
        }
        return new File(home, ".local/state/manuskript/logs");
    }

    private static boolean tryPrepareWritableDir(File dir) {
        try {
            File parent = dir.getParentFile();
            if (parent != null && !parent.exists()) {
                return false;
            }
            if (parent != null && !Files.isWritable(parent.toPath()) && !dir.exists()) {
                return false;
            }
            ensureDirectory(dir);
            if (!dir.isDirectory() || !Files.isWritable(dir.toPath())) {
                return false;
            }
            File probe = new File(dir, ".write-probe");
            Files.writeString(probe.toPath(), "ok");
            Files.deleteIfExists(probe.toPath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensureDirectory(File dir) {
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException ignored) {
            // Aufrufer / Logback melden Schreibfehler
        }
    }
}
