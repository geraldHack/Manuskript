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
        return list(catalogDirectory(), activeDirectory());
    }

    static List<Entry> list(File catalogDir, File pluginsDir) {
        Map<String, Entry> byName = new LinkedHashMap<>();
        File[] jars = catalogDir != null && catalogDir.isDirectory()
                ? catalogDir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"))
                : null;
        if (jars == null) {
            return List.of();
        }
        for (File jar : jars) {
            if (!PluginLoader.hasPluginDescriptor(jar)) {
                continue;
            }
            Peek peek = peek(jar);
            boolean enabled = pluginsDir != null && new File(pluginsDir, jar.getName()).isFile();
            byName.putIfAbsent(jar.getName().toLowerCase(),
                    new Entry(jar, jar.getName(), peek.id, peek.label, enabled));
        }
        return List.copyOf(byName.values());
    }

    public static void setEnabled(Entry entry, boolean enabled) {
        if (entry == null || entry.catalogFile() == null) {
            return;
        }
        setEnabled(entry.catalogFile(), activeDirectory(), enabled);
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
                Files.delete(target.toPath());
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
