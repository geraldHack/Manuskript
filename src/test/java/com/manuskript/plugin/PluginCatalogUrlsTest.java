package com.manuskript.plugin;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCatalogUrlsTest {

    @Test
    void allowsHttpsDownloadsOnSpoteroxe() {
        assertTrue(PluginCatalogUrls.isAllowed(URI.create(PluginCatalogUrls.INDEX_URL)));
        assertTrue(PluginCatalogUrls.isAllowed(
                URI.create("https://spoteroxe.de/downloads/plugins/openrouter-monitor-1.0.0.jar")));
        assertTrue(PluginCatalogUrls.isAllowed(
                URI.create("https://spoteroxe.de/downloads/plugins/openrouter-monitor-1.0.0.txt")));
        assertTrue(PluginCatalogUrls.isAllowed(
                URI.create("https://www.spoteroxe.de/downloads/plugins/foo.jar")));
    }

    @Test
    void rejectsOtherHostsSchemesAndPaths() {
        assertFalse(PluginCatalogUrls.isAllowed(URI.create("http://spoteroxe.de/downloads/manuskript-plugins.json")));
        assertFalse(PluginCatalogUrls.isAllowed(URI.create("https://evil.example/downloads/x.jar")));
        assertFalse(PluginCatalogUrls.isAllowed(URI.create("https://spoteroxe.de/other/x.jar")));
        assertFalse(PluginCatalogUrls.isAllowed(URI.create("https://spoteroxe.de/downloads/")));
        assertFalse(PluginCatalogUrls.isAllowed(
                URI.create("https://spoteroxe.de/downloads/foo.jar?token=1")));
        assertFalse(PluginCatalogUrls.isAllowed(URI.create("https://user@spoteroxe.de/downloads/x.jar")));
        assertFalse(PluginCatalogUrls.isAllowed(URI.create("https://spoteroxe.de:8443/downloads/x.jar")));
    }

    @Test
    void validatesIdsNamesAndHashes() {
        assertTrue(PluginCatalogUrls.OFFICIAL_IDS.contains("schreib-statistik"));
        assertTrue(PluginCatalogUrls.isAllowedId("openrouter-monitor"));
        assertFalse(PluginCatalogUrls.isAllowedId("../etc"));
        assertTrue(PluginCatalogUrls.isAllowedFileName("openrouter-monitor.jar"));
        assertTrue(PluginCatalogUrls.isAllowedNotesFileName("openrouter-monitor-1.0.0.txt"));
        assertFalse(PluginCatalogUrls.isAllowedFileName("../openrouter-monitor.jar"));
        assertFalse(PluginCatalogUrls.isAllowedFileName("openrouter-monitor.zip"));
        assertFalse(PluginCatalogUrls.isAllowedNotesFileName("../notes.txt"));
        assertTrue(PluginCatalogUrls.isAllowedSha256("a".repeat(64)));
        assertEquals("aabb", PluginCatalogUrls.normalizeSha256("AABB"));
        assertFalse(PluginCatalogUrls.isAllowedSha256("xyz"));
    }
}
