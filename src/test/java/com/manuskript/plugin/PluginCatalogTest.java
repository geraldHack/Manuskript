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
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry(PluginLoader.SERVICE_PATH));
            out.write("com.example.Dummy\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

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
    void installJarRejectsPathInFileName() throws Exception {
        File source = tempDir.resolve("ok.jar").toFile();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source.toPath()))) {
            out.putNextEntry(new JarEntry(PluginLoader.SERVICE_PATH));
            out.write("com.example.Dummy\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
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
}
