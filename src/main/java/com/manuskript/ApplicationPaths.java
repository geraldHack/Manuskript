package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Auflösung von Programmverzeichnis, Nutzer-Projektstamm und gebündeltem Demo-Ordner.
 */
public final class ApplicationPaths {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationPaths.class);

    /** System-Property / Logback-Property für das Log-Verzeichnis. */
    public static final String LOG_DIR_PROPERTY = "manuskript.log.dir";

    /** Schreibbarer Projektstamm unter Dokumente (außerhalb der App). */
    public static final String USER_PROJECTS_FOLDER_NAME = "Manuskript";

    /** Name des gebündelten Demo-Projekts. */
    public static final String DEMO_PROJECT_NAME = "Der Gott von Demirantha";

    /** Preference: Gott-Demo wurde bereits einmal in den Nutzerordner kopiert. */
    static final String DEMO_SEEDED_PREF = "_demo_gott_seeded";

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
            File codeSource = resolveCodeSourceFile(Launcher.class);
            if (codeSource != null) {
                String name = codeSource.getName().toLowerCase();
                if (name.endsWith(".jar")) {
                    File jarDir = codeSource.getParentFile();
                    if (jarDir != null && jarDir.isDirectory()) {
                        return jarDir;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback unten
        }

        return new File(System.getProperty("user.dir", "."));
    }

    /**
     * CodeSource-Location als {@link File} (Windows-sicher: kein {@code URI.getPath()} mit führendem {@code /C:/}).
     */
    public static File resolveCodeSourceFile(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        try {
            var location = clazz.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            return Path.of(location.toURI()).toFile();
        } catch (Exception e) {
            try {
                var location = clazz.getProtectionDomain().getCodeSource().getLocation();
                if (location == null) {
                    return null;
                }
                String s = location.toString();
                if (s.startsWith("file:")) {
                    return Path.of(URI.create(s)).toFile();
                }
                return new File(location.getPath());
            } catch (Exception ignored) {
                return null;
            }
        }
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
     * Aktive In-Process-Plugins ({@code Contents/app/plugins}). Nur JARs hier werden geladen.
     */
    public static File resolvePluginsDirectory() {
        File packaged = new File(getApplicationHomeDirectory(), "plugins");
        if (packaged.isDirectory()) {
            return canonicalOrSelf(packaged);
        }
        File repo = new File(System.getProperty("user.dir", "."), "plugins");
        if (repo.isDirectory()) {
            return canonicalOrSelf(repo);
        }
        return packaged;
    }

    /**
     * Mitgelieferte, noch nicht aktivierte Plugins ({@code plugin-catalog/}).
     * Werden erst nach Auswahl im Setup nach {@link #resolvePluginsDirectory()} kopiert.
     */
    public static File resolvePluginCatalogDirectory() {
        File packaged = new File(getApplicationHomeDirectory(), "plugin-catalog");
        if (packaged.isDirectory()) {
            return canonicalOrSelf(packaged);
        }
        File repo = new File(System.getProperty("user.dir", "."), "plugin-catalog");
        if (repo.isDirectory()) {
            return canonicalOrSelf(repo);
        }
        return packaged;
    }

    private static File canonicalOrSelf(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    /**
     * Gebündelte Demo-Vorlage {@code Manuskripte} im App-Bundle bzw. Arbeitsverzeichnis.
     * Nicht als Nutzer-Projektstamm verwenden — Updates überschreiben diesen Ordner.
     */
    public static File resolveBundledDemoDirectory() {
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
     * Standard-Projektstamm für die UI.
     * Installierte App: {@code ~/Documents/Manuskript}.
     * Entwicklung: lokaler Ordner {@code Manuskripte/}, falls vorhanden.
     */
    public static File resolveManuskripteDirectory() {
        if (ApplicationPreferences.isPackagedApplication()) {
            return defaultUserProjectsDirectory();
        }
        File relative = new File("Manuskripte");
        if (relative.isDirectory()) {
            return relative;
        }
        File bundled = new File(getApplicationHomeDirectory(), "Manuskripte");
        if (bundled.isDirectory()) {
            return bundled;
        }
        return defaultUserProjectsDirectory();
    }

    /**
     * Schreibbarer Default außerhalb der App: {@code ~/Documents/Manuskript}.
     * Nicht das Home-Verzeichnis selbst — {@code FileSystemView} liefert unter macOS oft {@code ~}.
     */
    public static File defaultUserProjectsDirectory() {
        File fromHome = defaultUserProjectsDirectory(System.getProperty("user.home", "."));
        File documentsParent = fromHome.getParentFile();
        if (documentsParent != null && documentsParent.isDirectory()) {
            return fromHome;
        }
        try {
            File chooserStart = FileSystemView.getFileSystemView().getDefaultDirectory();
            if (looksLikeDocumentsFolder(chooserStart)) {
                return new File(chooserStart, USER_PROJECTS_FOLDER_NAME);
            }
        } catch (Exception ignored) {
            // ~/Documents/Manuskript anlegen
        }
        return fromHome;
    }

    static File defaultUserProjectsDirectory(String userHome) {
        return userDocumentsDirectory(userHome).toPath().resolve(USER_PROJECTS_FOLDER_NAME).toFile();
    }

    /**
     * Nutzer-Dokumente ({@code ~/Documents} oder {@code ~/Dokumente}).
     */
    public static File userDocumentsDirectory() {
        File fromHome = userDocumentsDirectory(System.getProperty("user.home", "."));
        if (fromHome.isDirectory() || fromHome.mkdirs()) {
            return fromHome;
        }
        try {
            File chooserStart = FileSystemView.getFileSystemView().getDefaultDirectory();
            if (looksLikeDocumentsFolder(chooserStart)) {
                return chooserStart;
            }
        } catch (Exception ignored) {
            // Home-Dokumente
        }
        return fromHome;
    }

    static File userDocumentsDirectory(String userHome) {
        Path home = Path.of(userHome != null && !userHome.isBlank() ? userHome : ".");
        Path documents = home.resolve("Documents");
        Path dokumente = home.resolve("Dokumente");
        if (Files.isDirectory(dokumente) && !Files.isDirectory(documents)) {
            return dokumente.toFile();
        }
        return documents.toFile();
    }

    static boolean looksLikeDocumentsFolder(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        String name = dir.getName();
        return "Documents".equalsIgnoreCase(name)
                || "Dokumente".equalsIgnoreCase(name)
                || "My Documents".equalsIgnoreCase(name);
    }

    /**
     * Falsch gewählter Stamm direkt im Home ({@code ~/Manuskript} statt {@code ~/Documents/Manuskript}).
     */
    public static boolean isLegacyHomeProjectsDirectory(File dir) {
        return isLegacyHomeProjectsDirectory(dir, new File(System.getProperty("user.home", ".")));
    }

    static boolean isLegacyHomeProjectsDirectory(File dir, File userHome) {
        if (dir == null || userHome == null) {
            return false;
        }
        File dirAbs = dir.getAbsoluteFile();
        File homeAbs = userHome.getAbsoluteFile();
        File parent = dirAbs.getParentFile();
        return parent != null
                && parent.getAbsolutePath().equals(homeAbs.getAbsolutePath())
                && USER_PROJECTS_FOLDER_NAME.equalsIgnoreCase(dirAbs.getName());
    }

    public static boolean shouldRelocateProjectRoot(File currentRoot) {
        return isInsideApplicationHome(currentRoot) || isLegacyHomeProjectsDirectory(currentRoot);
    }

    /** {@code true}, wenn {@code dir} unter dem App-Home liegt (Updates können ihn löschen). */
    public static boolean isInsideApplicationHome(File dir) {
        if (dir == null) {
            return false;
        }
        try {
            File appHome = getApplicationHomeDirectory().getCanonicalFile();
            File current = dir.getCanonicalFile();
            while (current != null) {
                if (current.equals(appHome)) {
                    return true;
                }
                current = current.getParentFile();
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * Legt {@code ~/Documents/Manuskript} an und kopiert das Gott-Demo nur beim ersten Mal.
     */
    public static File ensureUserProjectsWithDemo() {
        File userDir = defaultUserProjectsDirectory();
        try {
            Files.createDirectories(userDir.toPath());
            boolean alreadySeeded = isDemoSeeded();
            boolean copied = seedGottDemoOnce(resolveBundledDemoDirectory(), userDir, alreadySeeded);
            if (!alreadySeeded) {
                markDemoSeeded();
            }
            if (copied) {
                logger.info("Demo-Projekt nach {} kopiert (nur Erststart)", userDir.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.warn("Nutzer-Projektverzeichnis konnte nicht vorbereitet werden: {}", userDir, e);
        }
        return userDir;
    }

    /**
     * Verschiebt den Projektstamm aus dem App-Bundle oder {@code ~/Manuskript}
     * nach Dokumente/Manuskript. Vorhandene Zielordner werden nicht überschrieben.
     */
    public static File relocateProjectRootIfNeeded(File currentRoot) {
        File userDir = ensureUserProjectsWithDemo();
        if (currentRoot == null || !currentRoot.isDirectory()) {
            return userDir;
        }
        if (!shouldRelocateProjectRoot(currentRoot)) {
            return currentRoot;
        }
        try {
            File canonicalUser = userDir.getCanonicalFile();
            File canonicalCurrent = currentRoot.getCanonicalFile();
            if (canonicalUser.equals(canonicalCurrent)) {
                return userDir;
            }
            copyMissingProjectFolders(canonicalCurrent, canonicalUser);
        } catch (Exception e) {
            logger.warn("Migration der Projekte nach Dokumente/Manuskript fehlgeschlagen", e);
        }
        return userDir;
    }

    static void copyMissingProjectFolders(File from, File to) throws IOException {
        File[] children = from.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        Files.createDirectories(to.toPath());
        for (File child : children) {
            File dest = new File(to, child.getName());
            if (!dest.exists()) {
                copyDirectory(child.toPath(), dest.toPath());
                logger.info("Projekt nach Dokumente/Manuskript übernommen: {}", dest.getName());
            }
        }
    }

    static boolean isDemoSeeded() {
        try {
            return "true".equals(ApplicationPreferences.resourceManagerNode().get(DEMO_SEEDED_PREF, ""));
        } catch (Exception e) {
            return false;
        }
    }

    static void markDemoSeeded() {
        try {
            ApplicationPreferences.resourceManagerNode().put(DEMO_SEEDED_PREF, "true");
        } catch (Exception e) {
            logger.warn("Demo-Seed-Flag konnte nicht gespeichert werden", e);
        }
    }

    /**
     * Kopiert das Gott-Demo einmalig, wenn das Ziel noch nicht existiert.
     *
     * @return {@code true} wenn kopiert wurde
     */
    static boolean seedGottDemoOnce(File bundledManuskripte, File userProjectsDir, boolean alreadySeeded)
            throws IOException {
        if (alreadySeeded || userProjectsDir == null) {
            return false;
        }
        Files.createDirectories(userProjectsDir.toPath());
        File source = findGottDemo(bundledManuskripte);
        if (source == null) {
            return false;
        }
        File dest = new File(userProjectsDir, source.getName());
        if (dest.exists()) {
            return false;
        }
        copyDirectory(source.toPath(), dest.toPath());
        return true;
    }

    static File findGottDemo(File bundledManuskripte) {
        if (bundledManuskripte == null || !bundledManuskripte.isDirectory()) {
            return null;
        }
        File exact = new File(bundledManuskripte, DEMO_PROJECT_NAME);
        if (exact.isDirectory()) {
            return exact;
        }
        File[] children = bundledManuskripte.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            String name = child.getName().toLowerCase();
            if (name.contains("gott") && name.contains("demirantha")) {
                return child;
            }
        }
        return null;
    }

    static void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(from -> {
                try {
                    if (".DS_Store".equals(from.getFileName().toString())) {
                        return;
                    }
                    Path relative = source.relativize(from);
                    Path to = target.resolve(relative.toString());
                    if (Files.isDirectory(from)) {
                        Files.createDirectories(to);
                    } else {
                        Path parent = to.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
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
        // Ältere Windows-App-Images: Ressourcen neben der EXE (Parent von app/)
        File home = getApplicationHomeDirectory();
        File parent = home != null ? home.getParentFile() : null;
        if (parent != null && "app".equalsIgnoreCase(home.getName())) {
            File legacy = new File(parent, normalized);
            if (legacy.exists()) {
                return legacy;
            }
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
