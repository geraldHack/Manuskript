package com.manuskript.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void copiesAndDeletesJarBetweenCatalogAndPlugins() throws Exception {
        File catalog = tempDir.resolve("catalog").toFile();
        File plugins = tempDir.resolve("plugins").toFile();
        assertTrue(catalog.mkdirs());
        assertTrue(plugins.mkdirs());
        File jar = new File(catalog, "demo.jar");
        writeDescriptor(jar);

        List<PluginCatalog.Entry> listed = PluginCatalog.list(catalog, plugins);
        assertEquals(1, listed.size());
        assertFalse(listed.get(0).enabled());

        Files.writeString(catalog.toPath().resolve("demo.txt"), "Demo\n1.0.0\n\nNotiz.\n");
        PluginCatalog.setEnabled(jar, plugins, true);
        assertTrue(new File(plugins, "demo.jar").isFile());
        assertTrue(new File(plugins, "demo.txt").isFile());
        assertTrue(PluginCatalog.list(catalog, plugins).get(0).enabled());

        PluginCatalog.setEnabled(jar, plugins, false);
        assertFalse(new File(plugins, "demo.jar").exists());
        assertFalse(new File(plugins, "demo.txt").exists());
        assertFalse(PluginCatalog.list(catalog, plugins).get(0).enabled());
    }

    @Test
    void listSkipsVersionedCatalogJars() throws Exception {
        File catalog = tempDir.resolve("catalog-v").toFile();
        File plugins = tempDir.resolve("plugins-v").toFile();
        assertTrue(catalog.mkdirs());
        assertTrue(plugins.mkdirs());
        File latest = new File(catalog, "projekt-backup.jar");
        File versioned = new File(catalog, "projekt-backup-1.0.5.jar");
        writeDescriptor(latest);
        writeDescriptor(versioned);
        List<PluginCatalog.Entry> listed = PluginCatalog.list(catalog, plugins);
        assertEquals(1, listed.size());
        assertEquals("projekt-backup.jar", listed.get(0).fileName());
    }

    @Test
    void installJarRejectsPathInFileName() throws Exception {
        File source = tempDir.resolve("ok.jar").toFile();
        writeDescriptor(source);
        try {
            PluginCatalog.installJar(source, "../evil.jar");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Ungültiger Dateiname"));
        }
    }

    @Test
    void ignoresJarsWithoutServiceFile() throws Exception {
        File catalog = tempDir.resolve("catalog").toFile();
        File plugins = tempDir.resolve("plugins").toFile();
        assertTrue(catalog.mkdirs());
        File jar = new File(catalog, "plain.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry("readme.txt"));
            out.write("nope".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertTrue(PluginCatalog.list(catalog, plugins).isEmpty());
    }

    @Test
    void listMergesBundledCatalogAndWritableOverlay() throws Exception {
        File bundled = tempDir.resolve("bundled-catalog").toFile();
        File writable = tempDir.resolve("user-catalog").toFile();
        File plugins = tempDir.resolve("plugins-merge").toFile();
        assertTrue(bundled.mkdirs());
        assertTrue(writable.mkdirs());
        assertTrue(plugins.mkdirs());
        writeDescriptor(new File(bundled, "bundled-only.jar"));
        writeDescriptor(new File(bundled, "shared.jar"));
        writeDescriptor(new File(writable, "shared.jar"));
        writeDescriptor(new File(writable, "user-only.jar"));

        List<PluginCatalog.Entry> listed = PluginCatalog.list(bundled, writable, plugins);
        assertEquals(3, listed.size());
        assertEquals("user-only.jar", listed.stream()
                .filter(e -> "user-only.jar".equals(e.fileName()))
                .findFirst()
                .orElseThrow()
                .catalogFile()
                .getName());
        File shared = listed.stream()
                .filter(e -> "shared.jar".equals(e.fileName()))
                .findFirst()
                .orElseThrow()
                .catalogFile();
        assertEquals(writable.getCanonicalFile(), shared.getParentFile().getCanonicalFile());
    }

    private static void writeDescriptor(File jar) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry(PluginLoader.SERVICE_PATH));
            out.write("com.example.Dummy\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
