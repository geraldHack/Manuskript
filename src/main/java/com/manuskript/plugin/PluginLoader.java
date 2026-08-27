package com.manuskript.plugin;

import com.manuskript.ApplicationPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Lädt {@link ManuskriptPlugin}-Implementierungen aus JARs in {@code plugins/}.
 * Nur Dateien mit Service-Descriptor gelten als Plugin; andere JARs werden nicht geladen.
 */
public final class PluginLoader {

    private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);

    static final String SERVICE_PATH = "META-INF/services/com.manuskript.plugin.ManuskriptPlugin";

    private static final List<URLClassLoader> LOADERS = new ArrayList<>();

    private PluginLoader() {
    }

    public static PluginLoadResult load() {
        return loadFromDirectories(pluginDirectories());
    }

    static PluginLoadResult loadFromDirectories(List<File> directories) {
        List<ManuskriptPlugin> plugins = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (directories != null) {
            for (File dir : directories) {
                loadFromDirectory(dir, seen, plugins, errors);
            }
        }
        return new PluginLoadResult(List.copyOf(plugins), List.copyOf(errors));
    }

    public static List<File> pluginDirectories() {
        List<File> dirs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addDirectory(dirs, seen, ApplicationPaths.resolvePluginsDirectory());
        addDirectory(dirs, seen, new File(System.getProperty("user.dir", "."), "plugins"));
        return dirs;
    }

    public static boolean hasPluginDescriptor(File jar) {
        if (jar == null || !jar.isFile() || !jar.getName().toLowerCase().endsWith(".jar")) {
            return false;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            return jarFile.getEntry(SERVICE_PATH) != null;
        } catch (IOException e) {
            logger.debug("JAR konnte nicht gelesen werden: {}", jar.getAbsolutePath(), e);
            return false;
        }
    }

    private static void addDirectory(List<File> dirs, Set<String> seen, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        String key = canonicalKey(dir);
        if (key == null || !seen.add(key)) {
            return;
        }
        dirs.add(dir);
    }

    private static void loadFromDirectory(File dir, Set<String> seenJars,
                                          List<ManuskriptPlugin> plugins, List<String> errors) {
        File[] jars = dir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            String key = canonicalKey(jar);
            if (key == null || !seenJars.add(key)) {
                continue;
            }
            if (!hasPluginDescriptor(jar)) {
                continue;
            }
            try {
                loadPluginJar(jar, plugins);
            } catch (Exception | java.util.ServiceConfigurationError e) {
                logger.warn("Plugin-JAR konnte nicht geladen werden: {}", jar.getAbsolutePath(), e);
                errors.add(jar.getName() + ": " + message(e));
            }
        }
    }

    private static void loadPluginJar(File jar, List<ManuskriptPlugin> plugins) throws Exception {
        URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toURI().toURL()},
                ManuskriptPlugin.class.getClassLoader());
        LOADERS.add(loader);
        ServiceLoader<ManuskriptPlugin> serviceLoader = ServiceLoader.load(ManuskriptPlugin.class, loader);
        boolean found = false;
        for (ManuskriptPlugin plugin : serviceLoader) {
            if (plugin == null) {
                continue;
            }
            found = true;
            plugins.add(plugin);
            logger.info("Plugin geladen: {} ({}) aus {}", plugin.label(), plugin.id(), jar.getName());
        }
        if (!found) {
            throw new IllegalStateException("Service-Datei vorhanden, aber keine Implementierung gefunden");
        }
    }

    private static String canonicalKey(File file) {
        try {
            Path path = file.getCanonicalFile().toPath();
            return path.toString();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private static String message(Throwable e) {
        String text = e.getMessage();
        return text != null && !text.isBlank() ? text : e.getClass().getSimpleName();
    }

    public record PluginLoadResult(List<ManuskriptPlugin> plugins, List<String> errors) {
    }
}
