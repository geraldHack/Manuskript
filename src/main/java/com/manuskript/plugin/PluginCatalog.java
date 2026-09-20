package com.manuskript.plugin;

import com.manuskript.ApplicationPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Mitgelieferte Plugins im Katalog aktivieren (Kopie nach {@code plugins/}) oder deaktivieren (löschen).
 */
public final class PluginCatalog {

    private static final Logger logger = LoggerFactory.getLogger(PluginCatalog.class);

    private PluginCatalog() {
    }

    public record Entry(File catalogFile, String fileName, String id, String label, boolean enabled) {
        public String displayLabel() {
            if (label != null && !label.isBlank()) {
                return label;
            }
            if (id != null && !id.isBlank()) {
                return id;
            }
            return fileName;
        }
    }

    public static File catalogDirectory() {
        File dir = firstExistingOrFallback(
                ApplicationPaths.resolvePluginCatalogDirectory(),
                new File(System.getProperty("user.dir", "."), "plugin-catalog"));
        if (dir != null && !dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Kopiert eine JAR in den Katalog. Liegt dieselbe Datei bereits aktiv in {@code plugins/},
     * wird sie mit aktualisiert.
     */
    public static File installJar(File source, String fileName) {
        if (source == null || !source.isFile()) {
            throw new IllegalArgumentException("Plugin-JAR fehlt");
        }
        if (!PluginCatalogUrls.isAllowedFileName(fileName)) {
            throw new IllegalArgumentException("Ungültiger Dateiname: " + fileName);
        }
        File catalogDir = catalogDirectory();
        if (catalogDir == null) {
            throw new IllegalStateException("Plugin-Katalog-Ordner fehlt");
        }
        try {
            Files.createDirectories(catalogDir.toPath());
            File dest = new File(catalogDir, fileName);
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            copySiblingNotes(source, dest);
            File pluginsDir = activeDirectory();
            File active = pluginsDir != null ? new File(pluginsDir, fileName) : null;
            if (active != null && active.isFile()) {
                Files.copy(dest.toPath(), active.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copySiblingNotes(dest, active);
                logger.info("Plugin aktualisiert (aktiv): {}", fileName);
            } else {
                logger.info("Plugin in den Katalog gelegt: {}", fileName);
            }
            return dest;
        } catch (IOException e) {
            throw new IllegalStateException("Installieren fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public static File activeDirectory() {
        File active = ApplicationPaths.resolvePluginsDirectory();
        if (active != null && (active.isDirectory() || active.mkdirs())) {
            return active;
        }
        File repo = new File(System.getProperty("user.dir", "."), "plugins");
        repo.mkdirs();
        return repo;
    }

    public static List<Entry> list() {
        return list(
                ApplicationPaths.resolveBundledPluginCatalogDirectory(),
                catalogDirectory(),
                activeDirectory());
    }

    static List<Entry> list(File catalogDir, File pluginsDir) {
        Map<String, Entry> byName = new LinkedHashMap<>();
        addCatalogJars(byName, catalogDir, pluginsDir);
        return List.copyOf(byName.values());
    }

    static List<Entry> list(File bundledCatalog, File writableCatalog, File pluginsDir) {
        Map<String, Entry> byName = new LinkedHashMap<>();
        addCatalogJars(byName, bundledCatalog, pluginsDir);
        addCatalogJars(byName, writableCatalog, pluginsDir);
        return List.copyOf(byName.values());
    }

    static void addCatalogJars(Map<String, Entry> byName, File catalogDir, File pluginsDir) {
        File[] jars = catalogDir != null && catalogDir.isDirectory()
                ? catalogDir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"))
                : null;
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            if (!PluginLoader.hasPluginDescriptor(jar)) {
                continue;
            }
            PluginJarName.Parsed parsed = PluginJarName.parse(jar.getName());
            if (parsed != null && parsed.version() != null && !parsed.version().isBlank()) {
                continue;
            }
            Peek peek = peek(jar);
            boolean enabled = isEnabledIn(pluginsDir, jar.getName(), peek.id);
            byName.put(jar.getName().toLowerCase(),
                    new Entry(jar, jar.getName(), peek.id, peek.label, enabled));
        }
    }

    public static void setEnabled(Entry entry, boolean enabled) {
        if (entry == null || entry.catalogFile() == null) {
            return;
        }
        PluginLoader.unload();
        setEnabled(entry.catalogFile(), activeDirectory(), enabled);
        if (!enabled) {
            removePluginCopies(entry.fileName(), entry.id());
        }
    }

    static void setEnabled(File catalogJar, File pluginsDir, boolean enabled) {
        if (catalogJar == null || pluginsDir == null) {
            throw new IllegalArgumentException("Katalog-JAR oder Zielordner fehlt");
        }
        File target = new File(pluginsDir, catalogJar.getName());
        try {
            if (enabled) {
                Files.createDirectories(pluginsDir.toPath());
                Files.copy(catalogJar.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copySiblingNotes(catalogJar, target);
                logger.info("Plugin aktiviert: {}", target.getName());
            } else if (target.isFile()) {
                deleteWithRetry(target.toPath());
                deleteSiblingNotes(target);
                logger.info("Plugin deaktiviert: {}", target.getName());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    (enabled ? "Kopieren" : "Löschen") + " fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public static void installNotes(String jarFileName, String text) {
        if (!PluginCatalogUrls.isAllowedFileName(jarFileName)) {
            return;
        }
        File catalogDir = catalogDirectory();
        if (catalogDir == null) {
            return;
        }
        Path notes = PluginNotes.beside(new File(catalogDir, jarFileName).toPath());
        if (notes == null) {
            return;
        }
        try {
            Files.createDirectories(notes.getParent());
            Files.writeString(notes, text == null ? "" : text);
            File pluginsDir = activeDirectory();
            File activeJar = pluginsDir != null ? new File(pluginsDir, jarFileName) : null;
            if (activeJar != null && activeJar.isFile()) {
                Path activeNotes = PluginNotes.beside(activeJar.toPath());
                if (activeNotes != null) {
                    Files.copy(notes, activeNotes, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            logger.warn("Plugin-Notiz nicht geschrieben: {}", jarFileName, e);
        }
    }

    private static boolean isEnabledIn(File pluginsDir, String fileName, String id) {
        if (pluginsDir == null || !pluginsDir.isDirectory()) {
            return false;
        }
        if (fileName != null && new File(pluginsDir, fileName).isFile()) {
            return true;
        }
        if (id == null || id.isBlank()) {
            return false;
        }
        File canonical = new File(pluginsDir, id + ".jar");
        if (canonical.isFile()) {
            return true;
        }
        File[] jars = pluginsDir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"));
        if (jars == null) {
            return false;
        }
        for (File jar : jars) {
            PluginJarName.Parsed parsed = PluginJarName.parse(jar.getName());
            if (parsed != null && id.equalsIgnoreCase(parsed.id())) {
                return true;
            }
        }
        return false;
    }

    private static void removePluginCopies(String fileName, String id) {
        LinkedHashSet<File> dirs = new LinkedHashSet<>();
        dirs.addAll(PluginLoader.pluginDirectories());
        File active = activeDirectory();
        if (active != null) {
            dirs.add(active);
        }
        File cwdPlugins = new File(System.getProperty("user.dir", "."), "plugins");
        if (cwdPlugins.isDirectory()) {
            dirs.add(cwdPlugins);
        }
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) {
                continue;
            }
            try {
                if (fileName != null && !fileName.isBlank()) {
                    File named = new File(dir, fileName);
                    if (named.isFile()) {
                        deleteWithRetry(named.toPath());
                    }
                    deleteSiblingNotes(named);
                }
                File[] jars = dir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"));
                if (jars == null) {
                    continue;
                }
                for (File jar : jars) {
                    if (samePlugin(jar, fileName, id)) {
                        deleteWithRetry(jar.toPath());
                        deleteSiblingNotes(jar);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Löschen fehlgeschlagen: " + e.getMessage(), e);
            }
        }
    }

    private static boolean samePlugin(File jar, String fileName, String id) {
        if (jar == null) {
            return false;
        }
        String name = jar.getName();
        if (fileName != null && fileName.equalsIgnoreCase(name)) {
            return true;
        }
        PluginJarName.Parsed parsed = PluginJarName.parse(name);
        if (parsed == null) {
            return false;
        }
        if (id != null && !id.isBlank() && id.equalsIgnoreCase(parsed.id())) {
            return true;
        }
        if (fileName != null) {
            PluginJarName.Parsed wanted = PluginJarName.parse(fileName);
            return wanted != null && wanted.id().equalsIgnoreCase(parsed.id());
        }
        return false;
    }

    private static void deleteWithRetry(Path path) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(40L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last != null ? last : new IOException("Löschen fehlgeschlagen: " + path);
    }

    private static void copySiblingNotes(File fromJar, File toJar) throws IOException {
        Path from = PluginNotes.beside(fromJar.toPath());
        Path to = PluginNotes.beside(toJar.toPath());
        if (from == null || to == null || !Files.isRegularFile(from)) {
            return;
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteSiblingNotes(File jar) throws IOException {
        Path notes = PluginNotes.beside(jar.toPath());
        if (notes != null) {
            Files.deleteIfExists(notes);
        }
    }

    private static Peek peek(File jar) {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toURI().toURL()},
                ManuskriptPlugin.class.getClassLoader())) {
            ServiceLoader<ManuskriptPlugin> serviceLoader = ServiceLoader.load(ManuskriptPlugin.class, loader);
            for (ManuskriptPlugin plugin : serviceLoader) {
                if (plugin != null) {
                    return new Peek(nullToEmpty(plugin.id()), nullToEmpty(plugin.label()));
                }
            }
        } catch (Exception | java.util.ServiceConfigurationError e) {
            logger.debug("Plugin-Metadaten nicht lesbar: {}", jar.getName(), e);
        }
        String fallback = jar.getName().replaceFirst("(?i)\\.jar$", "");
        return new Peek(fallback, fallback);
    }

    private static File firstExistingOrFallback(File preferred, File fallback) {
        if (preferred != null && preferred.isDirectory()) {
            return preferred;
        }
        if (fallback != null && fallback.isDirectory()) {
            return fallback;
        }
        return preferred != null ? preferred : fallback;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Peek(String id, String label) {
    }
}
