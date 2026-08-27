package com.manuskript.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsJarWithoutServiceFile() throws Exception {
        File jar = tempDir.resolve("plain.jar").toFile();
        try (JarOutputStream out = new JarOutputStream(java.nio.file.Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry("com/example/Tool.class"));
            out.write(new byte[] {1, 2, 3});
            out.closeEntry();
        }
        assertFalse(PluginLoader.hasPluginDescriptor(jar));
    }

    @Test
    void acceptsJarWithServiceFile() throws Exception {
        File jar = tempDir.resolve("plugin.jar").toFile();
        try (JarOutputStream out = new JarOutputStream(java.nio.file.Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry(PluginLoader.SERVICE_PATH));
            out.write("com.example.DummyPlugin\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertTrue(PluginLoader.hasPluginDescriptor(jar));
    }

    @Test
    void loadsMonitorPluginViaServiceLoaderWhenJarExists() throws Exception {
        File jar = new File("tools/openrouter-monitor/target/openrouter-monitor.jar");
        org.junit.jupiter.api.Assumptions.assumeTrue(jar.isFile(), "Monitor-JAR wurde nicht gebaut");
        File dir = tempDir.resolve("plugins").toFile();
        assertTrue(dir.mkdirs());
        java.nio.file.Files.copy(jar.toPath(), dir.toPath().resolve("openrouter-monitor.jar"));
        PluginLoader.PluginLoadResult result = PluginLoader.loadFromDirectories(List.of(dir));
        assertTrue(result.errors().isEmpty(), () -> result.errors().toString());
        assertEquals(1, result.plugins().size());
        ManuskriptPlugin plugin = result.plugins().get(0);
        assertEquals("openrouter-monitor", plugin.id());
        assertEquals("OpenRouter-Monitor", plugin.label());
    }

    @Test
    void ignoresNonJarFiles() throws Exception {
        File zip = tempDir.resolve("notes.zip").toFile();
        try (ZipOutputStream out = new ZipOutputStream(java.nio.file.Files.newOutputStream(zip.toPath()))) {
            out.putNextEntry(new ZipEntry(PluginLoader.SERVICE_PATH));
            out.write("com.example.DummyPlugin\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertFalse(PluginLoader.hasPluginDescriptor(zip));
        assertFalse(PluginLoader.hasPluginDescriptor(null));
    }
}
